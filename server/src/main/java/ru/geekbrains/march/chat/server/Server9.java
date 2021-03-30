package ru.geekbrains.march.chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.InvalidParameterException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;

import static ru.geekbrains.march.chat.server.ServerApp9.*;

public class Server9
{
    private static final String
            FORMAT_NO_SUCH_USER = "\nКлиент %s отсутствует в чате.",
            SERVERNAME_BASE_ = "ЧатСервер_",
            SESSION_START = "\nНачало сессии.",
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
    private final Map<String, ClientHandler9> map;
    private String[] publicCliendsList;
    private Thread threadConsoleToClient;
    private boolean serverGettingOff;
    private Authentificator authentificator;


    public Server9 (int port)
    {
        Connection connection = null;
        if (port < PORT_MIN || port > PORT_MAX)    throw new IllegalArgumentException();

        serverGettingOff = false;
        map = new HashMap<>();
        syncUpdatePublicClientsList();
        this.port = port;
        SERVERNAME = SERVERNAME_BASE_ + serverNameCounter ++;

        try (ServerSocket servsocket = new ServerSocket (this.port))
        {   connection = DriverManager.getConnection("jdbc:sqlite:marchchat.db");
            authentificator = new JdbcAuthentificationProvider (connection);
            threadConsoleToClient = new Thread(() -> runThreadConsoleToClient (servsocket));
            threadConsoleToClient.start();
            System.out.print (SESSION_START);

            while (!serverGettingOff)
            {   Socket serverSideSocket = servsocket.accept();
                new ClientHandler9 (this, serverSideSocket);
            }
        }
        catch (SQLException sqle)
        {   sqle.printStackTrace();
            throw new RuntimeException(); //< останавливаем работу всего сервера при ошибке подключения к БД
        }
        catch (IOException ioe) {  ioe.printStackTrace();  }
        finally
        {   serverGettingDown();
            disconnect (connection);
        }
    }// Server9 ()


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
        if (map != null)
            for (Map.Entry<String, ClientHandler9> entry : map.entrySet())
                entry.getValue().onServerDown();
    }


    private void runThreadConsoleToClient (ServerSocket servsocket)
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
                            (syncSendPrivateMessage (nameTo, message, null)
                            ? "Отправлено."
                            : "Не отправлено."));
                }
                else syncBroadcastMessage (msg, null);
            }
        }
        catch (IOException ex)  {  ex.printStackTrace();  }
        finally  {  serverGettingDown();  }
    }

// Авторизуем клиента по логину и паролю.
    public synchronized String syncValidateOnLogin (String login, String password, ClientHandler9 client)
    {
        String nick = authentificator.authenticate (login, password);

        if (nick != null)   //< null - индикатор ошибки или того, что логин/пароль не подошли
        if (map.containsKey (nick))
            nick = "";      //< пустая строка - индикатор повторного входа в чат
        else
        {   map.put (nick, client);
            syncUpdatePublicClientsList();
            syncBroadcastMessage (CMD_CLIENTS_LIST_CHANGED, null);
        }
        return nick;
    }

//Клиент запросил смену имени.
    public synchronized String syncChangeNickname (ClientHandler9 client, String newnickname)
    {
        String result = null;
        if (client != null)
        {   String prevnickname = client.getClientName();
            result = authentificator.rename (prevnickname, newnickname);

            if (result != null && !result.isEmpty())
            {   map.remove (prevnickname);
                map.put (newnickname, client);
                syncUpdatePublicClientsList();
                syncBroadcastMessage (CMD_CLIENTS_LIST_CHANGED, null);
                syncBroadcastMessage (String.format (FORMAT_RENAMING_TO_, newnickname), client);
            }
        }
        return result;
    }

//Удаляем клиента из списка подключенных клиентов.
    public synchronized void syncClientLogout (ClientHandler9 client)
    {
        if (client != null  &&  map.remove (client.getClientName()) != null)
        {   syncUpdatePublicClientsList();
            syncBroadcastMessage (CMD_CLIENTS_LIST_CHANGED, null);
            syncBroadcastMessage (LEFT_CHAT, client);
        }
    }

//Если в списке клиентов произошли изменения (добавление, удаление, переименование), то составляем
// новый список имён участников чата для рассылки этим самым участникам. Также вызывается из конструктора.
    private synchronized void syncUpdatePublicClientsList ()
    {
        if (map != null)
            publicCliendsList = map.keySet().toArray (new String[0]);
    }

//Рассылаем указанное сообщение всем клиентам из нашего списка подключенных клиентов.
    public synchronized boolean syncBroadcastMessage (String msg, ClientHandler9 from)
    {
        boolean boolSent = msg != null  &&  map != null  &&  !(msg = msg.trim()).isEmpty();

        if (boolSent)
        for (Map.Entry<String, ClientHandler9> entry : map.entrySet())
        {
            ClientHandler9 client = entry.getValue();

            if (msg.equalsIgnoreCase (CMD_CLIENTS_LIST_CHANGED))
                boolSent = client.syncSendMessageToClient (CMD_CLIENTS_LIST_CHANGED);
            else
            {   String name = (from != null) ? from.getClientName() : SERVERNAME;
                boolSent = client.syncSendMessageToClient (CMD_CHAT_MSG, name + ":\n\t" + msg);
            }
        }
        return boolSent;
    }

//Пересылаем указанное сообщение автору и указанному клиенту.
    public synchronized boolean syncSendPrivateMessage (String nameTo, String message, ClientHandler9 clientFrom)
    {
        boolean boolSent = false;

        if (message != null && !(message = message.trim()).isEmpty() &&
            nameTo  != null && !(nameTo = nameTo.trim()).isEmpty()  &&
            map != null)
        {
            String nameFrom = (clientFrom != null) ? clientFrom.getClientName() : SERVERNAME;

            for (Map.Entry<String, ClientHandler9> entry : map.entrySet())
            {
                ClientHandler9 clientTo = entry.getValue();
                if (nameTo.equals (clientTo.getClientName()))
                {
                    if (clientFrom == null && message.equalsIgnoreCase (CMD_EXIT)) //< Server научился отключать пользователей.
                        boolSent = clientTo.syncSendMessageToClient (CMD_EXIT);
                    else
                        boolSent = clientTo.syncSendMessageToClient (CMD_PRIVATE_MSG, nameFrom, message);
                    break;
                }
            }
            //проверка отправки сообщения несуществующему клиенту
            if (!boolSent)
                if (clientFrom == null) System.out.printf (FORMAT_NO_SUCH_USER, nameTo);
                else
                clientFrom.syncSendMessageToClient (String.format(FORMAT_NO_SUCH_USER, nameTo));
        }
        else throw new InvalidParameterException ("ERROR @ syncSendPrivateMessage(): invalid string passed in.");
        return boolSent;
    }

//Предоставляем публичный список участников чата всем желающим.
    public String[] getClientsList ()    {   return publicCliendsList;   }

}
