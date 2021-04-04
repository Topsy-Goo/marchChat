package ru.geekbrains.march.chat.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import static ru.geekbrains.march.chat.server.ServerApp.*;

public class ClientHandler
{
    public static final int THREAD_SLEEPINTERVAL_250 = 250;
    private String nickname;
    private boolean connectionGettingClosed = false;

    private Socket socket;
    private Server server;
    private DataInputStream dis;
    private DataOutputStream dos;
    private Thread threadClientToServer, threadMain;

    private final static String
            CLIENT_CREATED = "клиент создан.",
            FORMAT_UNABLE_SEND_MESSAGE_TO = "Не удалось отправить сообщение:\n\t%s",
            SERVER_OFF = "Сервер прекратил работу.",
            PROMPT_LOGINERROR_BUSY = "Учётная запись в настоящий момент используется.",
            PROMPT_LOGINERROR_INVALID = "Указаны некорректные логин и/или пароль.",
            ENTER_CHAT = "(вошёл в чат)",
            PROMPT_RENAMING_ERROR = "Ошибка.",
            PROMPT_RENAMING_FAILED = "Не удалось переименовать. Возможно, указанное имя пустое или уже используется."
            ;

    public ClientHandler (Server serv, Socket serverSideSocket)
    {
        if (serverSideSocket == null || serv == null)
            throw new IllegalArgumentException();

        this.server = serv;
        this.socket = serverSideSocket;
        this.threadMain = Thread.currentThread();
        try
        {   dis = new DataInputStream (socket.getInputStream());
            dos = new DataOutputStream (socket.getOutputStream());
            threadClientToServer = new Thread(() -> runThreadClientToServer());
            threadClientToServer.start();
            syncSendMessageToClient (CMD_CONNECTED);
        }
        catch (IOException ioe)
        {   close();
            ioe.printStackTrace();
        }
        if (DEBUG) print (String.format("\n\t%s, thread's class : %s; thread's name : %s",
                                        CLIENT_CREATED,
                                        threadMain.getClass().getName(),
                                        Thread.currentThread().getName()));
    }//ClientHandler (Socket)

    private String readInputStreamUTF ()
    {
        int sleeptimer = 0;
        String msg = null;
        try
        {   while (!connectionGettingClosed)
            if (dis.available() > 0)
            {   msg = dis.readUTF();
                if (DEBUG) System.out.print(".");
                break;
            }
            else
            {   Thread.sleep(THREAD_SLEEPINTERVAL_250);
                if (++sleeptimer > 5000 / THREAD_SLEEPINTERVAL_250)
                {   if (!threadMain.isAlive())
                        break;
                    sleeptimer = 0; //syncSendMessageToClient (CMD_ONLINE);   //< «пингуем» клиента
                }
            }
        }
        catch (InterruptedException e) {e.printStackTrace();}
        catch (IOException e)
        {   connectionGettingClosed = true;
            msg = null;
            e.printStackTrace();
        }
        return msg;
    }// readInputStreamUTF ()

    private void runThreadClientToServer ()
    {
        String msg;
        while (!connectionGettingClosed && (msg = readInputStreamUTF()) != null)
        {
            msg = msg.trim().toLowerCase();
            if (msg.isEmpty()/* || msg.equals (CMD_ONLINE)*/)
                continue;

            if (msg.equals (CMD_EXIT))  onCmdExit();
            else
            if (server != null) //< если сервер ещё не упал
            {
                if (msg.equals (CMD_CLIENTS_LIST))  onCmdClientsList();
                else
                if (msg.equals (CMD_LOGIN))  onCmdLogin();
                else
                if (nickname != null) //< сообщения только для зарегистрированного клиента
                if (msg.equals (CMD_LOGIN_READY))   onCmdLoginReady();
                else
                if (msg.equals (CMD_CHANGE_NICKNAME))   onCmdChangeNickname();
                else
                {   boolean boolSent = false;
                    if (msg.equals (CMD_CHAT_MSG))
                        boolSent = server.syncBroadcastMessage (readInputStreamUTF(), this);
                    else
                    if (msg.equals (CMD_PRIVATE_MSG)) //< Клиент отправил личное сообщение клиенту.
                    {
                        boolSent = server.syncSendPrivateMessage (
                                        readInputStreamUTF(), // Кому
                                        readInputStreamUTF(), // Сообщение
                                        this); // От кого
                    }
                    else throw new UnsupportedOperationException (
                            "ERROR @ runThreadClientToServer() : незарегистрированное сообщение.");

                    if (!boolSent)
                        syncSendMessageToClient(String.format (FORMAT_UNABLE_SEND_MESSAGE_TO, msg));
                }
            }
        }//while
        close();
        if (DEBUG) System.out.print ("\nClientHandler.runThreadClientToServer() - поток закрылся."); //для отладки
    }// runThreadClientToServer ()

//Обработчик команды CMD_EXIT
    private void onCmdExit ()
    {
        connectionGettingClosed = true;
    }// onCmdExit ()

//Обработчик команды CMD_LOGIN.
    private void onCmdLogin ()
    {
        if (nickname != null)
            throw new RuntimeException("ERROR @ runThreadClientToServer() : повторная регистрация?");

        nickname = server.syncValidateOnLogin (readInputStreamUTF(), readInputStreamUTF(), this);
        if (nickname == null)
        {   syncSendMessageToClient (CMD_BADLOGIN, PROMPT_LOGINERROR_INVALID);
        }
        else if (nickname.isEmpty())
        {   nickname = null;
            syncSendMessageToClient (CMD_BADLOGIN, PROMPT_LOGINERROR_BUSY);
        }
        else syncSendMessageToClient (CMD_LOGIN, nickname); //< ok
    }// onCmdLogin ()

// Обработчик команды CMD_LOGIN_READY.
    private void onCmdLoginReady ()
    {
        server.addClientToChat (this);
        server.syncBroadcastMessage (ENTER_CHAT, this);
    }

//Обработчик команды CMD_CHANGE_NICKNAME.
    private void onCmdChangeNickname ()
    {
        String newnickname = readInputStreamUTF(),
               result = server.syncChangeNickname (this, newnickname);

        if (result == null)
        {   syncSendMessageToClient (CMD_BADNICKNAME, PROMPT_RENAMING_ERROR);
        }
        else if (result.isEmpty())
        {   syncSendMessageToClient (CMD_BADNICKNAME, PROMPT_RENAMING_FAILED);
        }
        else
        {   nickname = newnickname;
            syncSendMessageToClient (CMD_CHANGE_NICKNAME, nickname);
        }
    }// onCmdChangeNickname ()

//Отсылаем клиенту новый список клиентов.
    private void sendClientsList ()
    {
        String[] clientslist = server.getClientsList();
        if (clientslist != null)
        {   syncSendMessageToClient (CMD_CLIENTS_LIST);
            syncSendMessageToClient (String.valueOf (clientslist.length));
            syncSendMessageToClient (clientslist);
        }
    }// sendClientsList ()

//Обработчик команды CMD_CLIENTS_LIST.
    private void onCmdClientsList ()
    {
        String[] clientslist = server.getClientsList();
        if (clientslist != null)
        {   syncSendMessageToClient (CMD_CLIENTS_LIST);
            syncSendMessageToClient (String.valueOf (clientslist.length));
            syncSendMessageToClient (clientslist);
        }
    }// onCmdClientsList ()

//(Вспомогательная.) Может вызываться из Server.
    public synchronized boolean syncSendMessageToClient (String ... lines)
    {
        boolean boolSent = false;
        if (lines != null  &&  lines.length > 0  &&  dos != null)
        try
        {   for (String msg : lines)
                dos.writeUTF(msg);
            boolSent = true;
        }
        catch (IOException e)
        {   connectionGettingClosed = true;
            e.printStackTrace();
        }
        return boolSent;
    }// syncSendMessageToClient ()

// Закрытие соединения.
    private void close ()
    {
        connectionGettingClosed = true;
        if (server != null)
        {   server.syncClientLogout (this);
            server = null;
        }
        try
        {   if (threadClientToServer != null)   threadClientToServer.join(1000);
            if (socket != null && !socket.isClosed())   socket.close();
        }
        catch (InterruptedException | IOException e) { e.printStackTrace(); }
        finally
        {   threadClientToServer = null;
            socket = null;
            dos = null;
            dis = null;
            if (DEBUG) System.out.printf ("\n(ClientHandler.close() : клиент %s закрылся.)", nickname); //для отладки
            nickname = null;
        }
    }// close ()

//Метод вызывается сервером.
    public void onServerDown (String servername)
    {
        syncSendMessageToClient (CMD_CHAT_MSG, servername, SERVER_OFF);
        syncSendMessageToClient (CMD_EXIT);
        server = null; //< чтобы никто не пытался вызывать методы сервера
        close();
    }// onServerDown ()

    public String getClientName ()  {   return nickname;  }

    @Override public String toString ()   {   return "CH:"+ getClientName();   } //< для отладки

    public void print (String s) {System.out.print(s);}
}// class ClientHandler
