package ru.geekbrains.march.chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.InvalidParameterException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;

import static ru.geekbrains.march.chat.server.ServerApp.*;

public class Server
{
    private static final String
            FORMAT_NO_SUCH_USER = "\nКлиент %s отсутствует в чате.",
            SERVERNAME_BASE_ = "ЧатСервер_",
            SESSION_START = "\nНачало сессии.",
            WAITING_FOR_CLIENTS = "\n\tЖдём подклюение клиента... ",
            UNABLE_TOCREATE_HANDLER = "\nНе удалось создать ClientHandler.",
            FORMAT_RENAMING_TO_ = "(меняет имя на %s)",
            LEFT_CHAT = "(покинул чат)"
            ;
    private static int serverNameCounter = 0;
    public static final int PORT_MAX = 65535;
    public static final int PORT_MIN = 0;
    private final String SERVERNAME;

    private final int CONSOLE_THREAD_SLEEPINTERVAL = 250;

    private int port = 0;
    private final Map<String, ClientHandler> map;
    private String[] publicCliendsList;
    private Thread threadConsoleToClient;
    private boolean serverGettingOff;
    private Authentificator authentificator;

    public Server (int port)
    {
        Connection connection = null;
        if (port < PORT_MIN || port > PORT_MAX)    throw new InvalidParameterException();

        serverGettingOff = false;
        map = new HashMap<>();
        syncUpdatePublicClientsList();
        this.port = port;
        SERVERNAME = SERVERNAME_BASE_ + serverNameCounter ++;

        try (ServerSocket servsocket = new ServerSocket (this.port))
        {
            connection = DriverManager.getConnection("jdbc:sqlite:marchchat.db");
            authentificator = new JdbcAuthentificationProvider (connection);
            threadConsoleToClient = new Thread(() -> runThreadConsoleToClient (servsocket));
            threadConsoleToClient.start();
            System.out.print (SESSION_START);

            while (!serverGettingOff)
            {   System.out.print (WAITING_FOR_CLIENTS);
                Socket serverSideSocket = servsocket.accept();
                new ClientHandler (this, serverSideSocket);
            }
        }
        catch (SQLException sqle)
        {   sqle.printStackTrace();
            throw new RuntimeException(); //< останавливаем работу всего сервера при ошибке подключения к БД
        }
        catch (IOException ioe)
        {   ioe.printStackTrace();
            System.out.print (UNABLE_TOCREATE_HANDLER);
        }
        finally
        {   serverGettingDown();
            disconnect (connection);
        }
    // (Закрытие ServerSocket не означает разрыв всех созданных соединений, а означает лишь невозможность
    // подключение новых клиентов.)
    }// Server ()


// Разрываем соединение с БД.
    private void disconnect (Connection connection)
    {
        try
        {   if (authentificator != null)    authentificator.close();
            if (connection != null)  connection.close();
        }
        catch (SQLException | IOException e) { e.printStackTrace(); }
    }// disconnect ()


// Подготовка к «отключению» сервра.
    private void serverGettingDown ()
    {
        serverGettingOff = true;
        if (map != null) //< закрываем всех клиентов.
        {
            for (Map.Entry<String, ClientHandler> entry : map.entrySet())
                entry.getValue().onServerDown();
        }
    }// serverGettingDown ()


/* (Пришлось лишить ClientHandler возможности общаться с клиентами через консоль из-за странного глюка:
 если клиент покидает чат, отправив серверу /exit, то этот метод падает и тащит за собой Server (что
 приводит к отключению остальных клиентов). Причина глюка пока не выяснена, но, видимо, дело в сканере
 или ещё каком-то общем ресурсе.)    //*/
    private void runThreadConsoleToClient (ServerSocket servsocket) //поток threadConsoleToClient
    {
        String msg;
        int timer = 0;

        if (servsocket != null)
        try (Scanner sc = new Scanner(System.in))
        {
            while (!serverGettingOff)
            //if (System.in.available() > 0)
            {
                msg = sc.nextLine().trim();

                if (!msg.isEmpty())
                if (msg.equalsIgnoreCase(CMD_EXIT)) //< Сервер можно закрыть руками.
                {
                    serverGettingOff = true; //< так мы закрываем наш поток -- threadConsoleToClient
                    servsocket.close(); //< а так освобождаем основной поток от чар метода accept().
                    //new Socket (SERVER_ADDRESS, SERVER_PORT).close(); < этот способ закрытия приложения
                    //      не вызывает исключения, но в данном случае servsocket.close() конечно лучше.
                }
                else if (msg.equalsIgnoreCase(CMD_PRIVATE_MSG))
                {
                    //String[] tokens = msg.split ("\\s", 3);
                    //if (tokens.length > 2)
                    //    syncSendPrivateMessage(tokens[1], tokens[2], null);
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
            //else
            //{
            //    Thread.sleep(CONSOLE_THREAD_SLEEPINTERVAL);
            //    timer ++;
            //    if (timer > 5000 / CONSOLE_THREAD_SLEEPINTERVAL)
            //    {
            //        if (!threadMain.isAlive())  //< проверяем родительский поток
            //            break;
            //        timer = 0;
            //    }
            //}
        }
        catch (/*InterruptedException |*/ IOException ex) {ex.printStackTrace();} //для sleep() | для available()
        finally
        {
            serverGettingDown();
            System.out.print ("\n(Поток Server.threadConsoleToClient закрылся.)");
        }
    }// runThreadConsoleToClient ()


//Проверяем имя клиента на уникальность и при необходимости добавляем его в список подключенных клиентов.
    public synchronized String syncValidateOnLogin (String login, String password, ClientHandler client)
    {
        String nick = authentificator.authenticate (login, password);

        if (nick != null)   //< значение null покажет клиенту, что логин и/или пароль не подошли
        if (map.containsKey (nick))
            nick = "";      //< пустая строка будет индикатором повторного входа в чат
        else
        {   map.put (nick, client);
            syncUpdatePublicClientsList();
            syncBroadcastMessage (CMD_CLIENTS_LIST_CHANGED, null);
            //syncBroadcastMessage (ENTER_CHAT, client) не вызываем, т.к. сейчас у клиента nickname == null
        }
        return nick;
    }// syncValidateOnLogin ()


//Клиент запросил смену имени.
    public synchronized String syncChangeNickname (ClientHandler client, String newnickname)
    {
        String result = null;
        if (client != null)
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
    public synchronized void syncClientLogout (ClientHandler client)
    {
        if (client != null  &&  map.remove (client.getClientName()) != null)
        {
            syncUpdatePublicClientsList();
            //if (mode == MODE_UPDATE)
            {
                syncBroadcastMessage (CMD_CLIENTS_LIST_CHANGED, null);
                syncBroadcastMessage (LEFT_CHAT, client);
            }
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
    public synchronized boolean syncBroadcastMessage (String msg, ClientHandler from)
    {
        boolean boolSent = msg != null  &&  map != null  &&  !(msg = msg.trim()).isEmpty();

        if (boolSent)
        for (Map.Entry<String, ClientHandler> entry : map.entrySet())
        {
            ClientHandler client = entry.getValue();

            if (msg.equalsIgnoreCase (CMD_CLIENTS_LIST_CHANGED))
            {
                boolSent = client.syncSendMessageToClient (CMD_CLIENTS_LIST_CHANGED);
            }
            else
            {
                String name = (from != null) ? from.getClientName() : SERVERNAME; //< сообщение исходит от сервера (введено в консоли)
                boolSent = client.syncSendMessageToClient(CMD_CHAT_MSG, name + ":\n\t" + msg);
            }
        }
        return boolSent;
    }// syncBroadcastMessage ()


//Пересылаем указанное сообщение автору и указанному клиенту. Сообщение получаем в формате: «адресат сообщение».
    public synchronized boolean syncSendPrivateMessage (String nameTo, String message, ClientHandler clientFrom)
    {
        boolean boolSent = false;

        if (message != null && !(message = message.trim()).isEmpty() &&
            nameTo  != null && !(nameTo = nameTo.trim()).isEmpty()  &&
            map != null)
        {
            String nameFrom = (clientFrom != null) ? clientFrom.getClientName() : SERVERNAME;

            for (Map.Entry<String, ClientHandler> entry : map.entrySet())
            {
                ClientHandler clientTo = entry.getValue();
                if (nameTo.equals (clientTo.getClientName()))
                {
                    if (clientFrom == null && message.equalsIgnoreCase (CMD_EXIT)) //< Server научился отключать пользователей.
                        boolSent = clientTo.syncSendMessageToClient (CMD_EXIT);
                    else
                        boolSent = clientTo.syncSendMessageToClient (CMD_PRIVATE_MSG, nameFrom, message);

                    //// если сообщение не от сервера, то дублируем его отправителю
                    //// (серверу отправленные им личные сообщения не дублируем)
                    //if (clientFrom != null)
                    //    clientFrom.syncSendMessageToClient (CMD_PRIVATE_MSG, nameTo, message);

                    break;
                }
            }
            //проверка отправки сообщения несуществующему клиенту (по результатам разбора ДЗ-7)
            if (!boolSent)
                if (clientFrom == null) System.out.printf (FORMAT_NO_SUCH_USER, nameTo);
                else
                clientFrom.syncSendMessageToClient (String.format(FORMAT_NO_SUCH_USER, nameTo));
        }
        else throw new InvalidParameterException (String.format("ERROR @ syncSendPrivateMessage():" +
                            "\n\tnameTo = %s,\n\tmessage = %s,\n\tclientFrom = %s,\n\tnameFrom = %s.",
                            nameTo, message, clientFrom, clientFrom.getClientName()));
        return boolSent;
    }// syncSendPrivateMessage ()


//Предоставляем публичный список участников чата всем желающим.
    public String[] getClientsList ()    {   return publicCliendsList;   }// getClientsList ()


}// class Server
