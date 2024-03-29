package ru.geekbrains.server;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.geekbrains.server.errorhandlers.AlreadyLoggedInException;
import ru.geekbrains.server.errorhandlers.UnableToPerformException;
import ru.geekbrains.server.errorhandlers.UserNotFoundException;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.sql.SQLException;
import java.util.Arrays;

import static ru.geekbrains.server.ServerApp.*;

public class ClientHandler {
    public static final int THREAD_SLEEPINTERVAL_250 = 250;
    private static final Logger LOGGER = LogManager.getLogger(ClientHandler.class);
    private final static String CLIENT_CREATED = "клиент создан.";
    private final static String FORMAT_UNABLE_SEND_MESSAGE_TO = "Не удалось отправить сообщение:\n\t%s";
    private final static String SERVER_OFF = "Сервер прекратил работу.";
    private final static String PROMPT_LOGINERROR_BUSY = "Учётная запись в настоящий момент используется.";
    private final static String PROMPT_LOGINERROR_INVALID = "Указаны некорректные логин и/или пароль.";
    private final static String PROMPT_DATABASE_ERROR = "Ошибка базы данных.";
    private final static String ENTER_CHAT = "(вошёл в чат)";
    private final static String PROMPT_RENAMING_ERROR = "Ошибка.";
    private final static String PROMPT_RENAMING_FAILED = "Не удалось переименовать. Возможно, указанное имя пустое или уже используется.";
    private String nickname;
    private boolean connectionGettingClosed = false;
    private Socket socket;
    private Server server;
    private DataInputStream dis;
    private DataOutputStream dos;
    private Thread threadClientToServer, threadMain;

    public ClientHandler (Server serv, Socket serverSideSocket) {
        LOGGER.fatal("--------------------------------------------");
        if (serverSideSocket == null || serv == null) { throw new IllegalArgumentException(); }

        this.server = serv;
        this.socket = serverSideSocket;
        this.threadMain = Thread.currentThread();
        try {
            dis = new DataInputStream(socket.getInputStream());
            dos = new DataOutputStream(socket.getOutputStream());
            threadClientToServer = new Thread(this::runThreadClientToServer);
            threadClientToServer.start();
        }
        catch (IOException ioe) {
            LOGGER.throwing(Level.ERROR, ioe);//ioe.printStackTrace();
            close();
        }
        LOGGER.info("конструктор ClientHandler отработал.");
    }

    // Закрытие соединения и завершение работы.
    private void close () {
        connectionGettingClosed = true;
        if (server != null) {
            server.syncClientLogout(this);
            server = null;
        }
        try {
            if (threadClientToServer != null) { threadClientToServer.join(1000); }
            if (socket != null && !socket.isClosed()) { socket.close(); }
        }
        catch (InterruptedException | IOException e) {
            LOGGER.throwing(Level.ERROR, e);//e.printStackTrace();
        }
        finally {
            threadClientToServer = null;
            socket = null;
            dos = null;
            dis = null;
            LOGGER.debug(String.format("ClientHandler.close(): клиент %s закрылся.", nickname));
            nickname = null;
            threadMain = null;
        }
    }

    private String readInputStreamUTF () {
        int sleeptimer = 0;
        String msg = null;
        try {
            while (!connectionGettingClosed) {
                if (dis.available() > 0) {
                    msg = dis.readUTF();
                    LOGGER.trace("от клиента получено сообщение: " + msg);
                    break;
                }
                else {
                    Thread.sleep(THREAD_SLEEPINTERVAL_250);

                    if (++sleeptimer > 5000 / THREAD_SLEEPINTERVAL_250) {
                        if (threadMain == null || !threadMain.isAlive()) { break; }
                        sleeptimer = 0;
                    }
                }
            }
        }
        catch (InterruptedException e) {
            LOGGER.throwing(e);//e.printStackTrace();
        }
        catch (IOException e) {
            msg = null;
            LOGGER.throwing(Level.ERROR, e);//e.printStackTrace();
        }
        return msg;
    }

    //Run-метод потока threadClientToServer.
    private void runThreadClientToServer () {
        LOGGER.info("поток «" + Thread.currentThread().getName() + "» начал работу.");
        String msg;
        syncSendMessageToClient(CMD_CONNECTED);

        while (!connectionGettingClosed && (msg = readInputStreamUTF()) != null) {
            msg = msg.trim().toLowerCase();
            if (msg.isEmpty()) { continue; }

            if (msg.equals(CMD_EXIT)) { onCmdExit(); }
            else if (server != null) //< если сервер ещё не упал
            {
                if (msg.equals(CMD_CLIENTS_LIST)) { onCmdClientsList(); }
                else if (msg.equals(CMD_LOGIN)) { onCmdLogin(); }
                else if (nickname != null) //< сообщения только для зарегистрированного клиента
                {
                    if (msg.equals(CMD_LOGIN_READY)) { onCmdLoginReady(); }
                    else if (msg.equals(CMD_CHANGE_NICKNAME)) { onCmdChangeNickname(); }
                    else {
                        boolean boolSent = false;
                        if (msg.equals(CMD_CHAT_MSG)) {
                            boolSent = server.syncBroadcastMessage(readInputStreamUTF(), this);
                            if (!boolSent) {
                                LOGGER.warn("сервер не смог отправить публичное сообщение нашего клиента.");
                            }
                        }
                        else if (msg.equals(CMD_PRIVATE_MSG)) {//< Клиент отправил личное сообщение к.-л. клиенту.

                            boolSent = server.syncSendPrivateMessage(readInputStreamUTF(), // Кому
                                                                     readInputStreamUTF(), // Сообщение
                                                                     this); // От кого
                            if (!boolSent) {
                                LOGGER.warn("сервер не смог отправить приватное сообщение нашего клиента.");
                            }
                        }
                        else {
                            LOGGER.error(
                                "runThreadClientToServer(): получено незарегистрированное сообщение: " +
                                msg + ".");
                            throw new UnsupportedOperationException(
                                "ERROR @ runThreadClientToServer() : незарегистрированное сообщение: " +
                                msg + ".");
                        }
                        if (!boolSent) { syncSendMessageToClient(String.format(FORMAT_UNABLE_SEND_MESSAGE_TO, msg)); }
                    }
                }
            }
        }
        LOGGER.info("ClientHandler.runThreadClientToServer() - закрывается");
        close();
        LOGGER.info("поток «" + Thread.currentThread().getName() + "» завершил работу.");
    }

//----------------------------------------- команды ------------------------
    //Обработчик команды CMD_EXIT
    private void onCmdExit () { connectionGettingClosed = true; }

    private void alreadyLoggedInExceptionHandler () {
        LOGGER.error (PROMPT_LOGINERROR_BUSY);
        syncSendMessageToClient (CMD_BADLOGIN, PROMPT_LOGINERROR_BUSY);
    }

    //Обработчик команды CMD_LOGIN.
    private void onCmdLogin () {

        if (nickname != null)
            alreadyLoggedInExceptionHandler();
        else
        try {
            nickname = server.syncValidateOnLogin (readInputStreamUTF(), readInputStreamUTF(), this);
            LOGGER.debug ("от сервера получен ник: " + nickname);
            syncSendMessageToClient (CMD_LOGIN, nickname);
        }
        catch (SQLException e) {
            LOGGER.error (PROMPT_DATABASE_ERROR);
            syncSendMessageToClient (CMD_BADLOGIN, PROMPT_DATABASE_ERROR);
        }
        catch (AlreadyLoggedInException e) {
            alreadyLoggedInExceptionHandler();
        }
        catch (UserNotFoundException e) {
            LOGGER.error (PROMPT_LOGINERROR_INVALID);
            syncSendMessageToClient (CMD_BADLOGIN, PROMPT_LOGINERROR_INVALID);
        }
    }

    // Обработчик команды CMD_LOGIN_READY.
    private void onCmdLoginReady () {
        server.addClientToChat(this);
        server.syncBroadcastMessage(ENTER_CHAT, this);
    }

    //Обработчик команды CMD_CHANGE_NICKNAME.
    private void onCmdChangeNickname () {

        String newnickname = readInputStreamUTF();
        LOGGER.debug("запрошенный ник: " + newnickname);

        try {
            String result = server.syncChangeNickname(this, newnickname);
            LOGGER.debug("от сервера получен ник: " + result);
            nickname = newnickname;
            syncSendMessageToClient (CMD_CHANGE_NICKNAME, nickname);
        }
        catch (SQLException e) {
            LOGGER.error (PROMPT_RENAMING_ERROR);
            syncSendMessageToClient (CMD_BADNICKNAME, PROMPT_RENAMING_ERROR);
        }
        catch (UnableToPerformException e) {
            LOGGER.error (PROMPT_RENAMING_FAILED);
            syncSendMessageToClient (CMD_BADNICKNAME, PROMPT_RENAMING_FAILED);
        }
    }

    //Обработчик команды CMD_CLIENTS_LIST. (Клиент запрашивает список участников чата.)
    private void onCmdClientsList () { sendClientsList(); }

//----------------------------------------- вспомогательные ------------------------
    //Отсылаем клиенту новый список клиентов.
    private void sendClientsList () {
        String[] clientslist = server.getClientsList();
        LOGGER.debug("от сервера получен список: " + Arrays.toString(clientslist));
        if (clientslist != null) {
            syncSendMessageToClient(CMD_CLIENTS_LIST);
            syncSendMessageToClient(String.valueOf(clientslist.length));
            syncSendMessageToClient(clientslist);
        }
    }

    //(Вспомогательная.) Может вызываться из Server.
    public synchronized boolean syncSendMessageToClient (String... lines) {
        boolean boolSent = false;
        if (lines != null && lines.length > 0 && dos != null) {
            try {
                for (String msg : lines) {
                    dos.writeUTF(msg);
                    LOGGER.debug(String.format("клиенту %s отправлено сообщение: %s", nickname, msg));
                }
                boolSent = true;
            }
            catch (IOException e) {
                connectionGettingClosed = true;
                LOGGER.throwing(Level.ERROR, e);//e.printStackTrace();
            }
        }
        return boolSent;
    }

    //Метод вызывается сервером.
    public void onServerDown (String servername) {
        LOGGER.debug(String.format("поток «%s» начал выполнять ClientHandler.onServerDown(%s).", Thread.currentThread().toString(), servername));
        syncSendMessageToClient(CMD_CHAT_MSG, servername, SERVER_OFF);
        syncSendMessageToClient(CMD_EXIT);
        server = null; //< чтобы никто не пытался вызывать методы сервера
        close();
    }

    public String getClientName () { return nickname; }

    @Override public String toString () { return "CHandler:" + getClientName(); } //< для отладки
}
