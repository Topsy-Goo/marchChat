package ru.geekbrains.march.chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.InvalidParameterException;
import java.util.*;

import static ru.geekbrains.march.chat.server.ServerApp.*;

public class ServerHw8
{
    private static final String
            FORMAT_NO_SUCH_USER = "\nКлиент %s отсутствует в чате.",
            SERVERNAME_BASE_ = "ЧатСервер_",
            SESSION_START = "\nНачало сессии.",
            WAITING_FOR_CLIENTS = "\n\tЖдём подклюение клиента... ",
            UNABLE_TOCREATE_HANDLER = "\nНе удалось создать ClientHandlerHw8."
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
    private final Map<String, ServerHw8.UserEntry> map;
    private String[] publicCliendsList;
    private Thread threadConsoleToClient;
    private final Thread threadMain;
    private boolean serverGettingOff = false;

    private class UserEntry
    {
        String  login, password, name;
        ClientHandlerHw8 client;

        UserEntry (String lgn, String psw, String nm, ClientHandlerHw8 cl)
        {
            if (checkString(lgn) && checkString(psw) && checkString(nm))
            {
                login = lgn;
                password = psw;
                name = nm;
                //client = cl;
            }
            else throw new InvalidParameterException("ERROR@UserEntry() : some parameters are empty or null.");
        }

        String validate (String lgn, String psw, ClientHandlerHw8 cl)
        {
            if (login.equals(lgn)  &&  password.equals(psw))
            {
                client = cl;
                return name;
            }
            return null;
        }

        void loOut ()   {   client = null;   }

        boolean checkString (String str) {  return  str != null  &&  !str.trim().isEmpty();  }

        boolean setName (String newname)
        {
            boolean boolOk = checkString (newname);
            if (boolOk)  name = newname;
            return boolOk;
        }

        boolean isLogedIn ()    {   return client != null;   }
        String getName ()   { return name; }

        void onServerDown ()      {   if (client != null)    client.onServerDown();   }

        boolean syncSendMessageToClient (String ... lines)
        {
            return  client != null  &&  client.syncSendMessageToClient (lines);
        }
    }// class UserEntry


    public ServerHw8 (int port)
    {
        if (port < PORT_MIN || port > PORT_MAX)    throw new IllegalArgumentException();

        map = new HashMap<>();
        tmpFillUsersMap();
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
                    new ClientHandlerHw8 (this, socket);
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
    }// ServerHw8 (int port)


//Заполняем Map тестовыми значениями.
    private void tmpFillUsersMap ()
    {   //      name                   login, pass, name
        map.put("u1111", new ServerHw8.UserEntry("1", "11", "u1111", null));
        map.put("u2222", new ServerHw8.UserEntry("2", "22", "u2222", null));
        map.put("u3333", new ServerHw8.UserEntry("3", "33", "u3333", null));
        map.put("u4444", new ServerHw8.UserEntry("4", "44", "u4444", null));
    }// tmpFillUsersMap ()


// Подготовка к «отключению» сервра.
    private void serverGettingDown ()
    {
        serverGettingOff = true;
        if (map != null) //< закрываем всех клиентов.
        {
            for (Map.Entry<String, ServerHw8.UserEntry> entry : map.entrySet())
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
    public synchronized String syncValidateOnLogin (String login, String password, ClientHandlerHw8 client)
    {
        String userName = null;

        if (login != null  &&  password != null  &&  map != null)
        {
            for (Map.Entry<String, ServerHw8.UserEntry> entry : map.entrySet())
            {
                ServerHw8.UserEntry user = entry.getValue();
                if (user.isLogedIn())   continue;

                userName = user.validate (login, password, client);
                if (userName != null)
                {
                    if (entry.getKey() != userName)
                        throw new RuntimeException("ERROR @ syncValidateOnLogin() : username != key.");

                    syncPublicUpdateClientsList();
                    syncBroadcastMessage (CMD_CLIENTS_LIST_CHANGED, null);
                    break;
                }
            }
        }
        return userName;
    }// syncValidateOnLogin ()


    public synchronized boolean syncChangeNickname (String oldname, String newname)
    {
        boolean boolOk = false;
        ServerHw8.UserEntry user = (map == null) ? null : map.get(oldname);

        if (user != null  &&  user.setName (newname))
        {
            map.remove (oldname);
            map.put (newname, user);
            syncPublicUpdateClientsList();
            syncBroadcastMessage (CMD_CLIENTS_LIST_CHANGED, null);
            boolOk = true;
        }
        return boolOk;
    }// syncChangeNickname ()


//Помечаем клиента, как покинувшего чат (просто в соответствующем UserEntry сбрасываем поле client в null).
    public synchronized void syncRemoveClient (ClientHandlerHw8 client)
    {
        ServerHw8.UserEntry user;
        if (client != null  &&  (user = map.get (client.getClientName())) != null)
        {
            user.loOut();
            syncPublicUpdateClientsList();
            syncBroadcastMessage (CMD_CLIENTS_LIST_CHANGED, null);
        }
    }// syncRemoveClient ()


//В списке клиентов произошли изменения (добавление, удаление, переименование). Составляем список
// имён участников чата для рассылки этим самым участникам.
    private synchronized void syncPublicUpdateClientsList ()
    {
        Set<String> names = new HashSet<>();
        String name;

        if (map != null)
        for (Map.Entry<String, ServerHw8.UserEntry> entry : map.entrySet())
        {
            ServerHw8.UserEntry user = entry.getValue();
            if (user.isLogedIn())
                names.add (user.getName());
        }
        publicCliendsList = names.toArray (new String[0]);

    }// syncPublicUpdateClientsList ()


//Рассылаем указанное сообщение всем клиентам из нашего списка подключенных клиентов.
    public synchronized boolean syncBroadcastMessage (String msg, ClientHandlerHw8 from)
    {
        boolean boolSent = msg != null  &&  map != null  &&  !(msg = msg.trim()).isEmpty();

        if (boolSent)
        for (Map.Entry<String, ServerHw8.UserEntry> entry : map.entrySet())
        {
            ServerHw8.UserEntry user = entry.getValue();

            if (user.isLogedIn())
                if (msg.equalsIgnoreCase (CMD_CLIENTS_LIST_CHANGED))
                {
                    boolSent = user.syncSendMessageToClient (CMD_CLIENTS_LIST_CHANGED);
                }
                else
                {
                    String name = (from != null) ? from.getClientName() : SERVERNAME;
                    boolSent = user.syncSendMessageToClient (CMD_CHAT_MSG, name + ":\n\t" + msg);
                }
        }
        return boolSent;
    }// syncBroadcastMessage ()


//Пересылаем указанное сообщение автору и указанному клиенту. Сообщение получаем в формате: «адресат сообщение».
    public synchronized boolean syncSendPrivateMessage (String nameTo, String message, ClientHandlerHw8 clientFrom)
    {
        boolean boolSent = false;

        if (message != null && !message.isEmpty() &&
            nameTo  != null && !nameTo.isEmpty()  &&
            map != null)
        {
            String nameFrom = (clientFrom != null) ? clientFrom.getClientName() : SERVERNAME;

            for (Map.Entry<String, ServerHw8.UserEntry> entry : map.entrySet())
            {
                ServerHw8.UserEntry userTo = entry.getValue();

                if (nameTo.equals (userTo.getName()))
                {
                    if (clientFrom == null && message.equalsIgnoreCase (CMD_EXIT)) //< Server научился отключать пользователей.
                        boolSent = userTo.syncSendMessageToClient (CMD_EXIT);
                    else
                        boolSent = userTo.syncSendMessageToClient (CMD_PRIVATE_MSG, nameFrom, message);

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

}// class ServerHw8
