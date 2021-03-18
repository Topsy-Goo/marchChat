package ru.geekbrains.march.chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import static ru.geekbrains.march.chat.server.ServerApp.*;

public class Hw8Server
{
    private static final String
            FORMAT_NO_SUCH_USER = "\nКлиент %s отсутствует в чате.",
            SERVERNAME_BASE_ = "ЧатСервер_",
            SESSION_START = "\nНачало сессии.",
            WAITING_FOR_CLIENTS = "\n\tЖдём подклюение клиента... ",
            UNABLE_TOCREATE_HANDLER = "\nНе удалось создать Hw8ClientHandler."
            ;
    private static int serverNameCounter = 0;
    public static final int PORT_MAX = 65535;
    public static final int PORT_MIN = 0;
    public static final boolean
                VALIDATE_AND_ADD = true, VALIDATE_AND_RENAME = !VALIDATE_AND_ADD,
                MODE_UPDATE = true, MODE_SILENT = !MODE_UPDATE;
    private final String SERVERNAME;

    private final int CONSOLE_THREAD_SLEEPINTERVAL = 250;

    private int port = 0;
    private final Map<String, Hw8ClientHandler> map;
    private String[] publicCliendsList;
    private Thread threadConsoleToClient;
    private final Thread threadMain;
    private boolean serverGettingOff = false;


    public Hw8Server (int port)
    {
        if (port < PORT_MIN || port > PORT_MAX)    throw new IllegalArgumentException();

        map = new HashMap<>();
        syncPublicUpdateClientsList();
        this.port = port;
        threadMain = Thread.currentThread();
        SERVERNAME = SERVERNAME_BASE_ + serverNameCounter ++;

        try (ServerSocket servsocket = new ServerSocket (this.port))
        {
            threadConsoleToClient = new Thread(() -> runThreadConsoleToClient());
            threadConsoleToClient.start();
            System.out.print (SESSION_START);

            while (!serverGettingOff)
            {
                System.out.print (WAITING_FOR_CLIENTS);
                Socket socket = servsocket.accept();
                if (!serverGettingOff)
                    new Hw8ClientHandler (this, socket);
                else
                    socket.close();
            }//while
        }
        catch (IOException ioe)
        {
            ioe.printStackTrace();
            System.out.print (UNABLE_TOCREATE_HANDLER);
        }
        finally
        {
            serverGettingDown();
        }
    }// Hw8Server (int port)


// Подготовка к «отключению» сервра.
    private void serverGettingDown ()
    {
        serverGettingOff = true;
        if (map != null) //< закрываем всех клиентов.
        {
            for (Map.Entry<String, Hw8ClientHandler> entry : map.entrySet())
                entry.getValue().onServerDown();
        }
    }// serverGettingDown ()


//Консоль для связи сервера с клиентами; сейчас сервер может посылать клиентам приватные сообщения и
// выборочно отключать клиентов (посылать приватное сообщение CMD_EXIT; клиенты лишены такой возможности).
    private void runThreadConsoleToClient () //поток threadConsoleToClient
    {
        String msg;
        int timer = 0;
        try (Scanner sc = new Scanner(System.in))
        {
            while (!serverGettingOff)
            if (System.in.available() > 0)
            {
                msg = sc.nextLine().trim();

                if (!msg.isEmpty())
                if (msg.equalsIgnoreCase (CMD_EXIT)) //< Сервер можно закрыть руками.
                {
                    serverGettingOff = true; //< так мы закрываем наш поток -- threadConsoleToClient, …
                    new Socket (SERVER_ADDRESS, SERVER_PORT).close(); //< …а так освобождаем основной поток от чар метода accept().
                }
                else if (msg.equalsIgnoreCase(CMD_PRIVATE_MSG))
                {
                    System.out.print ("Личное сообщение для кого: ");
                    String nameTo = sc.nextLine().trim();
                    System.out.print ("Текст сообщения: ");
                    String message = sc.nextLine();
                    System.out.print ('\n' +
                            (syncSendPrivateMessage (nameTo, message, null)
                            ? "Отправлено."
                            : "Не отправлено."));
                }
                else syncBroadcastMessage (msg, null);
            }
            else
            {   Thread.sleep(CONSOLE_THREAD_SLEEPINTERVAL);
                timer ++;

                if (timer > 5000 / CONSOLE_THREAD_SLEEPINTERVAL)
                {
                    if (!threadMain.isAlive())  //< проверяем родительский поток
                        break;
                    timer = 0;
                }
            }
        }
        catch (InterruptedException | IOException ex) {ex.printStackTrace();} //для sleep() | //для available()
        finally
        {
            serverGettingDown();
        }
        System.out.print ("\n(Поток Server.threadConsoleToClient закрылся.)");
    }// runThreadConsoleToClient ()


    //Проверяем имя клиента на уникальность и при необходимости добавляем его в список подключенных клиентов.
    public synchronized boolean syncValidateUser (Hw8ClientHandler client, String newname, boolean add)
    {
        boolean boolOk = false;
        if (newname != null && !newname.isEmpty() &&
            map != null && !map.containsKey(newname))
        {
            if (add == VALIDATE_AND_RENAME)
                syncRemoveClient (client, MODE_SILENT);
                // (Здесь мы вносим изменения в список клиентов, а завершат переименование
                // клиента Hw8ClientHandler и Hw8CController.)

            map.put (newname, client);
            boolOk = true;
            syncPublicUpdateClientsList();
            syncBroadcastMessage (CMD_CLIENTS_LIST_CHANGED, null);
        }
        return boolOk;
    }// syncValidateUser ()


//В списке клиентов произошли изменения (добавление, удаление, переименование). Составляем список
// имён участников чата для рассылки этим самым участникам.
    private synchronized void syncPublicUpdateClientsList ()
    {
        if (map != null)
        {
            publicCliendsList = map.keySet().toArray(new String[0]);
        }
    }// syncPublicUpdateClientsList ()


    //Удаляем клиента из списка подключенных клиентов.
    public synchronized void syncRemoveClient (Hw8ClientHandler client, boolean mode)
    {
        if (client != null  &&  map.remove(client.getClientName()) != null)
        {
            syncPublicUpdateClientsList();
            if (mode == MODE_UPDATE)
                syncBroadcastMessage (CMD_CLIENTS_LIST_CHANGED, null);
        }

    }// syncRemoveClient ()


//Рассылаем указанное сообщение всем клиентам из нашего списка подключенных клиентов.
    public synchronized boolean syncBroadcastMessage (String msg, Hw8ClientHandler from)
    {
        boolean boolSent = msg != null  &&  map != null  &&  !(msg = msg.trim()).isEmpty();

        if (boolSent)
        for (Map.Entry<String, Hw8ClientHandler> entry : map.entrySet())
        {
            Hw8ClientHandler client = entry.getValue();

            if (msg.equalsIgnoreCase (CMD_CLIENTS_LIST_CHANGED))
            {
                boolSent = client.syncSendMessageToClient (CMD_CLIENTS_LIST_CHANGED);
            }
            else
            {
                String name = (from != null) ? from.getClientName() : SERVERNAME;
                boolSent = client.syncSendMessageToClient(CMD_CHAT_MSG, name + ":\n\t" + msg);
            }
        }
        return boolSent;
    }// syncBroadcastMessage ()


//Пересылаем указанное сообщение автору и указанному клиенту. Сообщение получаем в формате: «адресат сообщение».
    public synchronized boolean syncSendPrivateMessage (String nameTo, String message, Hw8ClientHandler clientFrom)
    {
        boolean boolSent = false;

        if (message != null && !message.isEmpty() &&
            nameTo  != null && !nameTo.isEmpty()  &&
            map != null)
        {
            String nameFrom = (clientFrom != null) ? clientFrom.getClientName() : SERVERNAME;

            for (Map.Entry<String, Hw8ClientHandler> entry : map.entrySet())
            {
                Hw8ClientHandler clientTo = entry.getValue();
                if (nameTo.equals (clientTo.getClientName()))
                {
                    if (clientFrom == null && message.equalsIgnoreCase (CMD_EXIT)) //< Server научился отключать пользователей.
                        boolSent = clientTo.syncSendMessageToClient (CMD_EXIT);
                    else
                        boolSent = clientTo.syncSendMessageToClient (CMD_PRIVATE_MSG, nameFrom, message);

                    break;
                }
            }
            if (!boolSent)
            if (clientFrom == null) System.out.printf (FORMAT_NO_SUCH_USER, nameTo);
            else
            clientFrom.syncSendMessageToClient (String.format(FORMAT_NO_SUCH_USER, nameTo));
        }
        return boolSent;
    }// syncSendPrivateMessage ()


//Предоставляем публичный список участников чата всем желающим.
    public String[] getClientsList ()   {   return publicCliendsList;   }// getClientsList ()


}// class Hw8Server
