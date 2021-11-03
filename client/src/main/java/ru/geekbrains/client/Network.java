package ru.geekbrains.client;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import static ru.geekbrains.server.ServerApp.*;

public class Network {
    public final static String PROMPT_UNABLE_TO_SEND_MESSAGE = "не удадось отправить сообщение";
    public final static String PROMPT_UNABLE_TO_CONNECT = "Не удалось подключиться.";
    private static final Logger LOGGER = LogManager.getLogger(Network.class);
    protected Socket clientSideSocket;
    protected DataInputStream dis;
    protected DataOutputStream dos;
    protected Callback onConnectionFailed;
    protected Callback onSendMessageToServer;

    public void setOnConnectionFailed (Callback cb) { onConnectionFailed = cb; }

    public void setOnSendMessageToServer (Callback cb) { onSendMessageToServer = cb; } //заготовка

    // Подключение к серверу.
    public boolean connect () {
        boolean boolOk = false;
        if (clientSideSocket == null || clientSideSocket.isClosed()) {
            try {
                clientSideSocket = new Socket(SERVER_ADDRESS, SERVER_PORT);
                dis = new DataInputStream(clientSideSocket.getInputStream());
                dos = new DataOutputStream(clientSideSocket.getOutputStream());
                boolOk = true;
                LOGGER.info("connect() подключен");
            }
            catch (IOException ioe) {
                LOGGER.warn(PROMPT_UNABLE_TO_CONNECT);
                if (onConnectionFailed != null) {
                    onConnectionFailed.callback("\n" + PROMPT_UNABLE_TO_CONNECT);//closeSession ();
                }
                LOGGER.throwing(Level.ERROR, ioe);
            }
        }
        return boolOk;
    }

    //Закрытие сокета и обнуление связанных с ним переменных.
    public void disconnect () {
        LOGGER.info("disconnect() - начало отключения");
        try {
            if (clientSideSocket != null && !clientSideSocket.isClosed()) { clientSideSocket.close(); }
        }
        catch (IOException e) { LOGGER.throwing(Level.ERROR, e); }
        finally {
            clientSideSocket = null;
            dis = null;
            dos = null;
            LOGGER.info("disconnect() звершился");
        }
    }

    //(Вспомогательный метод.) Шлём на сервер строки отдельными сообщениями.
    public boolean sendMessageToServer (String... lines) {
        boolean boolSent = false;
        StringBuilder sb;
        if (lines != null && lines.length > 0 && dos != null) {
            try {
                if (DEBUG) { sb = new StringBuilder("sendMessageToServer() call on :\n\t«"); }
                for (String msg : lines) {
                    dos.writeUTF(msg);
                    if (DEBUG) { sb.append(msg).append(" | "); }
                }
                if (DEBUG) { LOGGER.debug(sb.append('»').toString()); }
                boolSent = true;
            }
            catch (IOException e) {
                LOGGER.info("ERROR @ sendMessageToServer() - " + PROMPT_UNABLE_TO_SEND_MESSAGE);
                if (onSendMessageToServer != null) { onSendMessageToServer.callback(e); }
                LOGGER.throwing(Level.ERROR, e);
            }
        }
        return boolSent;
    }

    public String readUTF () throws IOException { return dis.readUTF(); }
}
