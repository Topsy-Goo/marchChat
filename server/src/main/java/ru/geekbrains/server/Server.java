package ru.geekbrains.server;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.geekbrains.server.errorhandlers.AlreadyLoggedInException;
import ru.geekbrains.server.errorhandlers.UnableToPerformException;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.*;

import static ru.geekbrains.server.ServerApp.*;

public class Server {
    public  static final int PORT_MAX = 65535;
    public  static final int PORT_MIN = 0;
    public  static final String FORMAT_NO_SUCH_USER = "\nКлиент %s отсутствует в чате.";
    public  static final String SERVERNAME_BASE_    = "ЧатСервер-";
    public  static final String SESSION_START       = "Начало сессии.";
    public  static final String WAITING_FOR_CLIENTS = "Ждём подклюение клиента...";
    public  static final String FORMAT_RENAMING_TO_ = "(меняет имя на %s)";
    public  static final String FORMAT_LEFT_CHAT    = "(%s вышел из чата)";
    public  static final String SERVER_IS_OFF       = "Сервер завершил работу.";
    public         final String SERVERNAME;
    public  static final Object syncAuth = new Object();
    public  static final Logger LOGGER   = LogManager.getLogger(Server.class);
    public  static final int     THREADS_POOL         = 4;
    private static       int     serverNameCounter    = 0;
    private static       Integer authentificatorUsers = 0;
    private static       long            messageCounter = 0; //< для учёта сообщений при логгировании
    private              String[]        publicCliendsList;
    private              boolean         serverGettingOff;
    private static       Authentificator authentificator;
    private              Map<String, ClientHandler> map; //< список клиентов онлайн
    private              Thread threadConsoleToClient;


    public Server (int port) {
        String methodname = String.format("Server(%d): ", port);
        LOGGER.info(methodname + "начал работу -------------------------------");

        if (port < PORT_MIN || port > PORT_MAX)
            throw new IllegalArgumentException();

        SERVERNAME = SERVERNAME_BASE_ + serverNameCounter++;
        serverGettingOff = false;
        map = new HashMap<>();
        syncUpdatePublicClientsList();  //< создаём publicCliendsList

        //несколько серверов (запущенные на одной машине) могут использовать БД парллельно
        LOGGER.info(methodname + "подключение к БД");
        synchronized (syncAuth) {
            if (authentificator == null)
                try {
                    authentificator = new JdbcAuthentificationProvider();
                }
                catch (SQLException e) {
                    throw new RuntimeException ("\nCannot create object JdbcAuthentificationProvider.", e);
                }
            authentificatorUsers++;
        }

        //executorservice = Executors.newFixedThreadPool (THREADS_POOL);
        //executorservice = Executors.newCachedThreadPool(); < через 60 сек бездействия завершает поток

        LOGGER.info(methodname + "создание ServerSocket.");
        try (ServerSocket servsocket = new ServerSocket(port)) {

            LOGGER.info(methodname + "создание консольного потока");
            threadConsoleToClient = new Thread(()->runThreadConsoleToClient(servsocket));
            threadConsoleToClient.start();
            LOGGER.fatal(String.format("%s\n\t%s", methodname, SESSION_START));

            LOGGER.info(methodname + "вход в основной цикл");
            while (!serverGettingOff) {
                LOGGER.fatal(String.format("%s\n\t%s", methodname, WAITING_FOR_CLIENTS));

                Socket serverSideSocket = servsocket.accept();
                LOGGER.info(methodname + "получен запрос на подключение; создаём ClientHandler");

                new ClientHandler(this, serverSideSocket);
                LOGGER.info(methodname + "ClientHandler создан");
            }
            LOGGER.info(methodname + "выход из основного цикла.");
        }
        catch (IOException ioe) {
            LOGGER.throwing (Level.ERROR, ioe);
        }
        finally {
            serverGettingDown();
            LOGGER.info (methodname + SERVER_IS_OFF);
            //(После закрытия ServerSocket'а открытые соединения продолжают работать, но создавать новые нет возможности.)
        }
        LOGGER.info (methodname + "завершил работу");
    }

/** Проверяет строку на пригодность для использования в качестве логина, пароля, ника. */
    public static boolean validateStrings (String... lines) {
        boolean result = lines != null;
        if (result) {
            for (String s : lines) {
                if (s == null || s.trim().isEmpty()) {
                    result = false;
                    break;
                }
            }
        }
        return result;
    }

// Подготовка к «отключению» сервра.
    private void serverGettingDown () {
        synchronized (syncAuth) {
            if (--authentificatorUsers <= 0 && authentificator != null) {
                LOGGER.debug ("serverGettingDown() приступил к отключению от БД; счётчик пользователей БД = " + authentificatorUsers);
                authentificator = authentificator.close();
            }
        }
        if (map != null) { //< закрываем всех клиентов.
            LOGGER.info("serverGettingDown() приступил к отключению клиентов.");
            for (Map.Entry<String, ClientHandler> entry : map.entrySet())
                entry.getValue().onServerDown(SERVERNAME);

            map.clear();
            map = null;
            LOGGER.info("serverGettingDown() завершил отключение клиентов.");
        }
    }

/** Run-метод потока threadConsoleToClient.   */
    private void runThreadConsoleToClient (ServerSocket servsocket) {
        String msg;
        LOGGER.info("консольный поток начал работу.");

        if (servsocket != null) {
            try (Scanner sc = new Scanner (System.in)) {
                while (!serverGettingOff) {
                    msg = sc.nextLine().trim();

                    if (!msg.isEmpty()) {
                        if (msg.equalsIgnoreCase(CMD_EXIT)) //< Сервер можно закрыть руками.
                        {
                            LOGGER.info("в консоли введена команда для завершения работы сервера.");
                            serverGettingOff = true;
                            servsocket.close();
                        }
                        else if (msg.equalsIgnoreCase(CMD_PRIVATE_MSG)) {

                            System.out.print("Личное сообщение для кого: ");
                            String nameTo = sc.nextLine().trim();

                            System.out.print("Текст сообщения: ");
                            String message = sc.nextLine();
                            LOGGER.info(String.format("в консоли набрано личное сообщение:\nкому = %s\nтекст сообщения = %s", nameTo, message));

                            String result = syncSendPrivateMessage(nameTo, message, null) ? "Отправлено." : "Не отправлено.";
                            println(result);
                            //LOGGER.info("result");
                        }
                        else {
                            LOGGER.info("в консоли введено публичное сообщение для участников чата: " + msg);
                            syncBroadcastMessage(msg, null);
                        }
                    }
                }
            }
            catch (IOException ex) { LOGGER.throwing(Level.ERROR, ex); /*ex.printStackTrace();*/ }
            finally { LOGGER.info("консольный поток завершился."); }
        }
    }

/** Проверяем логин и пароль клиента. Никуда его не добавляем на этом этапе! Только возвращаем
    ему ник, если авторизация верная. Метод authenticate() возвращает только никнейм; все недоразумения
    возвращаются из него в виде исключений (которые мы здесь не обрабатываем, а пропускаем дальше, и даже
    добавляем одно своё).
    @return возвращаем строку-никнейм, если авторизация прошла успешно.
    @throws AlreadyLoggedInException пользователь уже авторизован, т.е. имеем дело с попыткой повторной
            авторизации.   */
    public synchronized String syncValidateOnLogin (String login, String password, ClientHandler client)
                                                    throws SQLException
    {   String nick = null;
        synchronized (syncAuth) {
            nick = authentificator.authenticate (login, password);
        }
        if (nick != null && map.containsKey (nick))
            throw new AlreadyLoggedInException();
        return nick;
    }

//Реакция сервера на то, что юзер подтвердил получение ника и готовность присоединиться к чату.
    public synchronized void addClientToChat (ClientHandler client) {
        if (client != null) {
            LOGGER.info("запрошено добавление в чат клиента " + client.getClientName());
            map.put(client.getClientName(), client);
            syncUpdatePublicClientsList();
            syncBroadcastMessage(CMD_CLIENTS_LIST_CHANGED, null);
        }
        else { LOGGER.error("запрошено добавление в чат клиента null."); }
    }

/** Клиент запросил смену имени.
    @return новое имя пользователя.
    @throws UnableToPerformException если результат работы метода неудовлетворительный.
 */
    public synchronized String syncChangeNickname (ClientHandler client, String newnickname)
                                                   throws SQLException
    {   String result = null;
        String prevnickname = null;

        if (client != null && authentificator != null && map != null) {

            prevnickname = client.getClientName();
            synchronized (syncAuth) {
                result = authentificator.rename (prevnickname, newnickname);
            }
        }
        if (result == null || result.trim().isEmpty())
            throw new UnableToPerformException();

        map.remove (prevnickname);
        map.put (newnickname, client);

        syncUpdatePublicClientsList();
        syncBroadcastMessage (CMD_CLIENTS_LIST_CHANGED, null);
        syncBroadcastMessage (String.format(FORMAT_RENAMING_TO_, newnickname), client);
        return result;
    }

//Удаляем клиента из списка подключенных клиентов.
    public synchronized void syncClientLogout (ClientHandler client) {
        if (client != null && map.remove(client.getClientName()) != null) {
            LOGGER.info("из чата удаляется клиент " + client.getClientName());
            syncUpdatePublicClientsList();
            syncBroadcastMessage(CMD_CLIENTS_LIST_CHANGED, null);
            syncBroadcastMessage(String.format(FORMAT_LEFT_CHAT, client.getClientName()), null);
        }
    }

//В списке клиентов произошли изменения (добавление, удаление, переименование; также вызывается из конструктора). Составляем список имён участников чата для рассылки этим самым участникам.
    private synchronized void syncUpdatePublicClientsList () {
        if (map != null) {
            LOGGER.info("приступаем к обновлению локального списка клиентов.");
            publicCliendsList = map.keySet().toArray(new String[0]);
        }
        else { LOGGER.error("syncUpdatePublicClientsList() : map == null."); }
    }

//Рассылаем указанное сообщение всем клиентам из нашего списка подключенных клиентов.
    public synchronized boolean syncBroadcastMessage (String msg, ClientHandler from) {
        boolean boolSent = msg != null && map != null && !(msg = msg.trim()).isEmpty();

        if (boolSent) {
            String nameFrom = (from != null) ? from.getClientName() : SERVERNAME; //< сообщение исходит от сервера (введено в консоли)

            messageCounter++;
            LOGGER.info(String.format("приступаем к широковещ.рассылке сообщения (№ %d):\n\t%s:%s", messageCounter, nameFrom, msg));

            for (Map.Entry<String, ClientHandler> entry : map.entrySet()) {
                ClientHandler client = entry.getValue();

                if (msg.equalsIgnoreCase(CMD_CLIENTS_LIST_CHANGED))
                    boolSent = client.syncSendMessageToClient(CMD_CLIENTS_LIST_CHANGED);
                else
                    boolSent = client.syncSendMessageToClient(CMD_CHAT_MSG, nameFrom, msg);
            }
            LOGGER.info(String.format("широковещ.рассылка завершена (№ %d).", messageCounter));
        }
        return boolSent;
    }

//Пересылаем указанное сообщение автору и указанному клиенту. Сообщение получаем в формате: «адресат сообщение».
    public synchronized boolean syncSendPrivateMessage (String nameTo, String message, ClientHandler clientFrom) {
        boolean boolSent = false;

        if (message != null && !(message = message.trim()).isEmpty() && nameTo != null && !(nameTo = nameTo.trim()).isEmpty() && map != null) {
            String nameFrom = (clientFrom != null) ? clientFrom.getClientName() : SERVERNAME;

            messageCounter++;
            LOGGER.info(String.format("приступаем к отправке личн.сообщения (№ %d):\n\t%s > %s:%s", messageCounter, nameFrom, nameTo, message));

            for (Map.Entry<String, ClientHandler> entry : map.entrySet()) {
                ClientHandler clientTo = entry.getValue();
                if (nameTo.equals(clientTo.getClientName())) {
                    if (clientFrom == null && message.equalsIgnoreCase (CMD_EXIT)) //< Server научился отключать пользователей.
                        boolSent = clientTo.syncSendMessageToClient (CMD_EXIT);
                    else
                        boolSent = clientTo.syncSendMessageToClient (CMD_PRIVATE_MSG, nameFrom, message);
            // (Приватные (личные) сообщения не дублируем тправителю, т.к. это нарушит работу механизма сохранения истории чата -- придётся вводить в класс ChatMessage лишние поля. Сейчас клиенту выводится его отправленное личное сообщение средствами Controller'а, что даже и более логично.)
                    break;
                }
            }
            //проверка отправки сообщения несуществующему клиенту (по результатам разбора ДЗ-7)
            if (!boolSent) {
                LOGGER.warn(String.format("не удалось отправить личное сообщение (№ %d)", messageCounter));
                if (clientFrom == null)
                    System.out.printf(FORMAT_NO_SUCH_USER, nameTo);
                else
                    clientFrom.syncSendMessageToClient(String.format(FORMAT_NO_SUCH_USER, nameTo));
            }
            LOGGER.info (String.format("личн.сообщение отправлено (№ %d)", messageCounter));
        }
        else LOGGER.error ("syncSendPrivateMessage(): invalid string passed in.");
        return boolSent;
    }

//Предоставляем публичный список участников чата всем желающим.
    public String[] getClientsList () { return publicCliendsList; }

    public void print (String s) {System.out.print(s);}

    public void println (String s) {System.out.print("\n" + s);}

}

