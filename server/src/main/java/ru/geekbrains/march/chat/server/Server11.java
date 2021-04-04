package ru.geekbrains.march.chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static ru.geekbrains.march.chat.server.ServerApp11.*;

public class Server11
{
    private static final String
            FORMAT_NO_SUCH_USER = "\nКлиент %s отсутствует в чате.",
            SERVERNAME_BASE_ = "ЧатСервер-",
            SESSION_START = "\nНачало сессии.",
            WAITING_FOR_CLIENTS = "\n\tЖдём подклюение клиента... ",
            UNABLE_TOCREATE_HANDLER = "\nНе удалось создать ClientHandler.",
            FORMAT_RENAMING_TO_ = "(меняет имя на %s)",
            FORMAT_LEFT_CHAT = "(%s вышел из чата)",
            SERVER_IS_OFF = "\nСервер завершил работу."
            ;
    private static int serverNameCounter = 0;
    public static final int PORT_MAX = 65535;
    public static final int PORT_MIN = 0;
    private final String SERVERNAME;

    private final int CONSOLE_THREAD_SLEEPINTERVAL = 250;
    private Map<String, ClientHandler11> map;
    private String[] publicCliendsList;
    private boolean serverGettingOff;
    private Authentificator11 authentificator;
    private ExecutorService executorservice;


    public Server11 (int port)
    {
        if (port < PORT_MIN || port > PORT_MAX)    throw new IllegalArgumentException();

        SERVERNAME = SERVERNAME_BASE_ + serverNameCounter ++;
        serverGettingOff = false;
        map = new HashMap<>();
        syncUpdatePublicClientsList();
        this.authentificator = new JdbcAuthentificationProvider11();

        executorservice = Executors.newFixedThreadPool (4);
        //executorservice = Executors.newCachedThreadPool(); < через 60 сек бездействия завершает поток

        try (ServerSocket servsocket = new ServerSocket (port);)
        {
            new Thread(() -> runThreadConsoleToClient (servsocket)).start();
            System.out.print (SESSION_START);

            while (!serverGettingOff)
            {   System.out.print (WAITING_FOR_CLIENTS);
                Socket serverSideSocket = servsocket.accept();
                executorservice.execute(()->{
                    new ClientHandler11 (this, serverSideSocket);
                });
            }
        }
        catch (IOException ioe)
        {   ioe.printStackTrace();
            System.out.print (UNABLE_TOCREATE_HANDLER);
        }
        finally
        {   executorservice.shutdown();
            if (authentificator != null)  authentificator = authentificator.close();
            serverGettingDown();
            System.out.print (SERVER_IS_OFF);
            //(После закрытия ServerSocket'а открытые соединения продолжают работать, но создавать новые нет возможности.)
        }
    }// Server ()

// Подготовка к «отключению» сервра.
    private void serverGettingDown ()
    {
        if (map != null) //< закрываем всех клиентов.
        {
            for (Map.Entry<String, ClientHandler11> entry : map.entrySet())
                entry.getValue().onServerDown (SERVERNAME);
            map.clear();
            map = null;
        }
    }// serverGettingDown ()


    private void runThreadConsoleToClient (ServerSocket servsocket) //поток threadConsoleToClient
    {
        String msg;
        if (servsocket != null)
        try (Scanner sc = new Scanner(System.in))
        {
            while (!serverGettingOff)
            {   msg = sc.nextLine().trim();

                if (!msg.isEmpty())
                if (msg.equalsIgnoreCase(CMD_EXIT)) //< Сервер можно закрыть руками.
                {   serverGettingOff = true;
                    servsocket.close();
                }
                else if (msg.equalsIgnoreCase(CMD_PRIVATE_MSG))
                {   System.out.print ("Личное сообщение для кого: ");
                    String nameTo = sc.nextLine().trim();
                    System.out.print ("Текст сообщения: ");
                    String message = sc.nextLine();
                    System.out.print ('\n' +
                            (syncSendPrivateMessage (nameTo, message, null) ? "Отправлено." : "Не отправлено."));
                }
                else syncBroadcastMessage (msg, null);
            }
        }
        catch (IOException ex) {ex.printStackTrace();}
        finally  {  System.out.print ("\n(Поток threadConsoleToClient закрылся.)");  }
    }// runThreadConsoleToClient ()


//Проверяем логин и пароль клиента. (Никуда его не добавляем на этом этапе! Только возвращаем ему ник,
// если авторизация верная.)
    public synchronized String syncValidateOnLogin (String login, String password, ClientHandler11 client)
    {
        String nick = (authentificator != null)
                    ? authentificator.authenticate (login, password)
                    : null; //< может вернуть null
    //null - индикатор ошибки БД или того, что логин/пароль не подошли.
    //пустая строка - индикатор повторного входа в чат
        if (nick != null && map.containsKey (nick))
            nick = "";
        return nick;
    }// syncValidateOnLogin ()

//Реакция сервера на то, что юзер подтвердил получение ника и готовность присоединиться к чату.
    public synchronized void addClientToChat (ClientHandler11 client)
    {
        if (client != null)
        {   map.put (client.getClientName(), client);
            syncUpdatePublicClientsList();
            syncBroadcastMessage (CMD_CLIENTS_LIST_CHANGED, null);
        }
    }

//Клиент запросил смену имени.
    public synchronized String syncChangeNickname (ClientHandler11 client, String newnickname)
    {
        String result = null;
        if (client != null && authentificator != null)
        {
            String prevnickname = client.getClientName();
            result = authentificator.rename (prevnickname, newnickname);

            if (result != null && !result.isEmpty())
            {
                map.remove (prevnickname);
                map.put (newnickname, client);
                syncUpdatePublicClientsList();
                syncBroadcastMessage (CMD_CLIENTS_LIST_CHANGED, null);
                syncBroadcastMessage (String.format (FORMAT_RENAMING_TO_, newnickname), client);
            }
        }
        return result;
    }// syncChangeNickname ()


//Удаляем клиента из списка подключенных клиентов.
    public synchronized void syncClientLogout (ClientHandler11 client)
    {
        if (client != null  &&  map.remove (client.getClientName()) != null)
        {
            syncUpdatePublicClientsList();
            syncBroadcastMessage (CMD_CLIENTS_LIST_CHANGED, null);
            syncBroadcastMessage (String.format(FORMAT_LEFT_CHAT, client.getClientName()), null);
        }
    }// syncClientLogout ()


//В списке клиентов произошли изменения (добавление, удаление, переименование; также вызывается из конструктора).
// Составляем список имён участников чата для рассылки этим самым участникам.
    private synchronized void syncUpdatePublicClientsList ()
    {
        if (map != null)
            publicCliendsList = map.keySet().toArray (new String[0]);
    }// syncUpdatePublicClientsList ()


//Рассылаем указанное сообщение всем клиентам из нашего списка подключенных клиентов.
    public synchronized boolean syncBroadcastMessage (String msg, ClientHandler11 from)
    {
        boolean boolSent = msg != null  &&  map != null  &&  !(msg = msg.trim()).isEmpty();

        if (boolSent)
        for (Map.Entry<String, ClientHandler11> entry : map.entrySet())
        {
            ClientHandler11 client = entry.getValue();

            if (msg.equalsIgnoreCase (CMD_CLIENTS_LIST_CHANGED))
                boolSent = client.syncSendMessageToClient (CMD_CLIENTS_LIST_CHANGED);
            else
            {   String name = (from != null) ? from.getClientName() : SERVERNAME; //< сообщение исходит от сервера (введено в консоли)
                boolSent = client.syncSendMessageToClient (CMD_CHAT_MSG, name, msg);
            }
        }
        return boolSent;
    }// syncBroadcastMessage ()


//Пересылаем указанное сообщение автору и указанному клиенту. Сообщение получаем в формате: «адресат сообщение».
    public synchronized boolean syncSendPrivateMessage (String nameTo, String message, ClientHandler11 clientFrom)
    {
        boolean boolSent = false;

        if (message != null && !(message = message.trim()).isEmpty() &&
            nameTo  != null && !(nameTo = nameTo.trim()).isEmpty()  &&
            map != null)
        {
            String nameFrom = (clientFrom != null) ? clientFrom.getClientName() : SERVERNAME;

            for (Map.Entry<String, ClientHandler11> entry : map.entrySet())
            {
                ClientHandler11 clientTo = entry.getValue();
                if (nameTo.equals (clientTo.getClientName()))
                {
                    if (clientFrom == null && message.equalsIgnoreCase (CMD_EXIT)) //< Server научился отключать пользователей.
                        boolSent = clientTo.syncSendMessageToClient (CMD_EXIT);
                    else
                        boolSent = clientTo.syncSendMessageToClient (CMD_PRIVATE_MSG, nameFrom, message);

                // (Приватные (личные) сообщения не дублируем тправителю, т.к. это нарушит работу механизма
                //  сохранения истории чата -- придётся вводить в класс ChatMessage лишние поля. Сейчас клиенту
                //  выводится его отправленное личное сообщение средствами Controller'а, что даже и более логично.)
                    break;
                }
            }
            //проверка отправки сообщения несуществующему клиенту (по результатам разбора ДЗ-7)
            if (!boolSent)
                if (clientFrom == null) System.out.printf (FORMAT_NO_SUCH_USER, nameTo);
                else
                clientFrom.syncSendMessageToClient (String.format(FORMAT_NO_SUCH_USER, nameTo));
        }
        else throw new IllegalArgumentException ("ERROR @ syncSendPrivateMessage(): invalid string passed in.");
        return boolSent;
    }// syncSendPrivateMessage ()


//Предоставляем публичный список участников чата всем желающим.
    public String[] getClientsList ()    {   return publicCliendsList;   }// getClientsList ()

    public void print (String s) {System.out.print(s);}

}// class Server11
