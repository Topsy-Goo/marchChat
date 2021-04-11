package ru.geekbrains.march.chat.server;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static ru.geekbrains.march.chat.server.ServerApp.*;

public class Server
{
    private static final String
            FORMAT_NO_SUCH_USER = "\nКлиент %s отсутствует в чате.",
            SERVERNAME_BASE_ = "ЧатСервер-",
            SESSION_START = "Начало сессии.",
            WAITING_FOR_CLIENTS = "Ждём подклюение клиента... ",
            //UNABLE_TOCREATE_HANDLER = "\nНе удалось создать ClientHandler.",
            FORMAT_RENAMING_TO_ = "(меняет имя на %s)",
            FORMAT_LEFT_CHAT = "(%s вышел из чата)",
            SERVER_IS_OFF = "Сервер завершил работу."
            ;
    private static int serverNameCounter = 0;
    public static final int PORT_MAX = 65535;
    public static final int PORT_MIN = 0;
    private final String SERVERNAME;

    private final int THREADS_POOL = 4;
    private Map<String, ClientHandler> map;
    private String[] publicCliendsList;
    private boolean serverGettingOff;
    //private ExecutorService executorservice;
    private final static Object syncAuth = new Object();
    private static Authentificator authentificator;
    private static Integer authentificatorUsers = 0;
    private static long messageCounter = 0; //< для учёта сообщений при логгировании
    private static final Logger LOGGER = LogManager.getLogger (Server.class);


    public Server (int port)
    {
        String methodname = String.format("Server(%d): ", port);
        LOGGER.info(methodname+"начал работу.");

        if (port < PORT_MIN || port > PORT_MAX)    throw new IllegalArgumentException();

        SERVERNAME = SERVERNAME_BASE_ + serverNameCounter ++;
        serverGettingOff = false;
        map = new HashMap<>();
        syncUpdatePublicClientsList();

        //несколько серверов (запущенные на одной машине) могут использовать БД парллельно
        LOGGER.info(methodname+"подключение к БД.");
        synchronized (syncAuth)
        {   if (authentificator == null)
                authentificator = new JdbcAuthentificationProvider();
            authentificatorUsers ++;
        }

        //executorservice = Executors.newFixedThreadPool (THREADS_POOL);
        //executorservice = Executors.newCachedThreadPool(); < через 60 сек бездействия завершает поток

        LOGGER.info(methodname+"создание ServerSocket.");
        try (ServerSocket servsocket = new ServerSocket (port))
        {
            LOGGER.info(methodname+"создание консольного потока.");
            new Thread(() -> runThreadConsoleToClient (servsocket)).start();
            println ("\t"+methodname);  print(SESSION_START);

            LOGGER.info(methodname+"вход в основной цикл.");
            while (!serverGettingOff)
            {
                println ("\t"+methodname);  print(WAITING_FOR_CLIENTS);
                Socket serverSideSocket = servsocket.accept();
                LOGGER.info(methodname+"получен запрос на подключение; создаём ClientHandler.");
                //executorservice.execute(()->{
                    new ClientHandler (this, serverSideSocket);
                    //print ("\n\t"+Thread.currentThread().getName());
                //});
                LOGGER.info(methodname+"ClientHandler создан.");
            }
            LOGGER.info(methodname+"выход из основного цикла.");
        }
        catch (IOException ioe)
        {   LOGGER.throwing(Level.ERROR, ioe);//ioe.printStackTrace();
        }
        finally
        {   //executorservice.shutdown();
            serverGettingDown();
            LOGGER.info (methodname+SERVER_IS_OFF);
            //(После закрытия ServerSocket'а открытые соединения продолжают работать, но создавать новые нет возможности.)
        }
        LOGGER.info(methodname+"завершил работу.");
    }// Server ()


// Подготовка к «отключению» сервра.
    private void serverGettingDown ()
    {
        synchronized (syncAuth)
        {
            if (--authentificatorUsers <= 0 && authentificator != null)
            {
                LOGGER.debug("serverGettingDown() приступил к отключению от БД; счётчик пользователей БД = "+authentificatorUsers);
                authentificator = authentificator.close();
            }
        }
        if (map != null) //< закрываем всех клиентов.
        {   LOGGER.info("serverGettingDown() приступил к отключению клиентов.");

            for (Map.Entry<String, ClientHandler> entry : map.entrySet())
                entry.getValue().onServerDown (SERVERNAME);

            map.clear();
            map = null;
            LOGGER.info("serverGettingDown() завершил отключение клиентов.");
        }
    }// serverGettingDown ()

//Run-метод потока threadConsoleToClient.
    private void runThreadConsoleToClient (ServerSocket servsocket)
    {
        LOGGER.info("консольный поток начал работу.");
        String msg;

        if (servsocket != null)
        try (Scanner sc = new Scanner(System.in))
        {
            while (!serverGettingOff)
            {   msg = sc.nextLine().trim();

                if (!msg.isEmpty())
                if (msg.equalsIgnoreCase(CMD_EXIT)) //< Сервер можно закрыть руками.
                {
                    LOGGER.info("в консоли введена команда для завершения работы сервера.");
                    serverGettingOff = true;
                    servsocket.close();
                }
                else if (msg.equalsIgnoreCase(CMD_PRIVATE_MSG))
                {
                    System.out.print ("Личное сообщение для кого: ");
                    String nameTo = sc.nextLine().trim();
                    System.out.print ("Текст сообщения: ");
                    String message = sc.nextLine();
                    LOGGER.info(String.format("в консоли набрано личное сообщение:\nкому = %s\nтекст сообщения = %s",
                             nameTo, message));
                    String result = syncSendPrivateMessage (nameTo, message, null) ? "Отправлено." : "Не отправлено.";
                    println (result);
                    //LOGGER.info("result");
                }
                else
                {   LOGGER.info("в консоли введено публичное сообщение для участников чата: "+ msg);
                    syncBroadcastMessage (msg, null);
                }
            }
        }
        catch (IOException ex)  {   LOGGER.throwing(Level.ERROR, ex); /*ex.printStackTrace();*/  }
        finally  {  LOGGER.info("консольный поток завершился.");  }
    }// runThreadConsoleToClient ()


//Проверяем логин и пароль клиента. (Никуда его не добавляем на этом этапе! Только возвращаем ему ник,
// если авторизация верная.)
    public synchronized String syncValidateOnLogin (String login, String password, ClientHandler client)
    {
        String nick;
        synchronized (syncAuth)
        {   nick = authentificator.authenticate (login, password); //< может вернуть null
        }
    //null - индикатор ошибки БД или того, что логин/пароль не подошли.
    //пустая строка - индикатор повторного входа в чат
        if (nick != null && map.containsKey (nick))
            nick = "";
        return nick;
    }// syncValidateOnLogin ()

//Реакция сервера на то, что юзер подтвердил получение ника и готовность присоединиться к чату.
    public synchronized void addClientToChat (ClientHandler client)
    {
        if (client != null)
        {   LOGGER.info("запрошено добавление в чат клиента "+client.getClientName());
            map.put (client.getClientName(), client);
            syncUpdatePublicClientsList();
            syncBroadcastMessage (CMD_CLIENTS_LIST_CHANGED, null);
        }
        else LOGGER.error("запрошено добавление в чат клиента null.");
    }

//Клиент запросил смену имени.
    public synchronized String syncChangeNickname (ClientHandler client, String newnickname)
    {
        String result = null;
        if (client != null && authentificator != null)
        {
            String prevnickname = client.getClientName();
            synchronized (syncAuth)  {   result = authentificator.rename (prevnickname, newnickname);  }

            LOGGER.info(String.format("на запрос переименовать клиента из «%s» в «%s» БД ответила: %s",
                                         prevnickname, newnickname, result));
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
            LOGGER.info("из чата удаляется клиент "+ client.getClientName());
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
        {   LOGGER.info("приступаем к обновлению локального списка клиентов.");
            publicCliendsList = map.keySet().toArray (new String[0]);
        }
        else LOGGER.error("syncUpdatePublicClientsList() : map == null.");
    }// syncUpdatePublicClientsList ()


//Рассылаем указанное сообщение всем клиентам из нашего списка подключенных клиентов.
    public synchronized boolean syncBroadcastMessage (String msg, ClientHandler from)
    {
        boolean boolSent = msg != null  &&  map != null  &&  !(msg = msg.trim()).isEmpty();

        if (boolSent)
        {
            String nameFrom = (from != null) ? from.getClientName() : SERVERNAME; //< сообщение исходит от сервера (введено в консоли)

            messageCounter ++;
            LOGGER.info(String.format("приступаем к широковещ.рассылке сообщения (№ %d):\n\t%s:%s",
                                messageCounter, nameFrom, msg));

            for (Map.Entry<String, ClientHandler> entry : map.entrySet())
            {
                ClientHandler client = entry.getValue();

                if (msg.equalsIgnoreCase (CMD_CLIENTS_LIST_CHANGED))
                    boolSent = client.syncSendMessageToClient (CMD_CLIENTS_LIST_CHANGED);
                else
                    boolSent = client.syncSendMessageToClient (CMD_CHAT_MSG, nameFrom, msg);
            }
            LOGGER.info(String.format("широковещ.рассылка завершена (№ %d).", messageCounter));
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

            messageCounter ++;
            LOGGER.info(String.format("приступаем к отправке личн.сообщения (№ %d):\n\t%s > %s:%s",
                                messageCounter, nameFrom, nameTo, message));

            for (Map.Entry<String, ClientHandler> entry : map.entrySet())
            {
                ClientHandler clientTo = entry.getValue();
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
            {
                LOGGER.warn(String.format("не удалось отправить личное сообщение (№ %d)", messageCounter));
                if (clientFrom == null) System.out.printf (FORMAT_NO_SUCH_USER, nameTo);
                else
                clientFrom.syncSendMessageToClient (String.format(FORMAT_NO_SUCH_USER, nameTo));
            }
            LOGGER.info(String.format("личн.сообщение отправлено (№ %d)", messageCounter));
        }
        else LOGGER.error("syncSendPrivateMessage(): invalid string passed in.");
        return boolSent;
    }// syncSendPrivateMessage ()


//Предоставляем публичный список участников чата всем желающим.
    public String[] getClientsList ()    {   return publicCliendsList;   }// getClientsList ()

    public void print (String s) {System.out.print(s);}
    public void println (String s) {System.out.print("\n"+s);}

}// class Server

