package ru.geekbrains.march.chat.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import static ru.geekbrains.march.chat.server.ServerApp8.*;

public class ClientHandler8
{
    private String clientName; //< После регистрации пользователя clientName == Controller.userName.
    private final int C2S_THREAD_SLEEPINTERVAL = 100,
                      IDLE_TIMER_INTERVAL = 120_000;
    //private int msgCounter = 0;
    private boolean connectionGettingClosed = false;

    private Socket socket;
    private Server8 server;
    private DataInputStream dis;
    private DataOutputStream dos;
    private Thread threadClientToServer,
                   //threadCloser,
                   threadMain;

    private final static String
            CONNECTION_ESTABLISHED = "\nСоединение с сервером установлено."/*\nСервер ожидает регистрации пользователя.*/,
            CLIENT_CREATED = "клиент создан.",
            ENTER_CHAT = "(вошёл в чат)",
            FORMAT_RENAMING_TO_ = "(меняет имя на %s)",
            LEFT_CHAT = "(покинул чат)",
            FORMAT_UNABLE_SEND_MESSAGE_TO = "Не удалось отправить сообщение:\n\t%s",
            SERVER_OFF = "Сервер прекратил работу."
            ;

    public ClientHandler8 (Server8 serv, Socket sock)
    {
        if (sock == null || serv == null)
            throw new IllegalArgumentException();

        this.server = serv;
        this.socket = sock;
        this.threadMain = Thread.currentThread();
        try
        {
            dis = new DataInputStream (socket.getInputStream()); //< IOException
            dos = new DataOutputStream (socket.getOutputStream()); //< IOException

            threadClientToServer = new Thread(() -> runThreadClientToServer());
            threadClientToServer.start();
            syncSendMessageToClient (CMD_CHAT_MSG, CONNECTION_ESTABLISHED);
        }
        catch (IOException ioe)
        {
            close();
            ioe.printStackTrace();
        }
        System.out.print (CLIENT_CREATED);
    }// ClientHandler8 (Socket)


    private String readInputStreamUTF ()
    {
        int sleeptimer = 0;
        String msg = null;
        try
        {
            while (!connectionGettingClosed)
            if (dis.available() > 0)
            {
                msg = dis.readUTF();
                break;
            }
            else //Такой же блок есть в Controller8.readInputStreamUTF(). Там я описал причину,
            {    // по которой оставил его без изменений.
                Thread.sleep(C2S_THREAD_SLEEPINTERVAL);

                sleeptimer ++;
                if (sleeptimer > 5000 / C2S_THREAD_SLEEPINTERVAL)
                {
                    if (!threadMain.isAlive())
                        break;
                    syncSendMessageToClient (CMD_ONLINE);
                    sleeptimer = 0;
                }
            }
        }
        catch (InterruptedException e) {e.printStackTrace();}
        catch (IOException e)
        {
            connectionGettingClosed = true;
            msg = null;
            System.out.print("\nClientHandler8.readInputStreamUTF() : ошибка соединения.");
            e.printStackTrace();
        }
        return msg;
    }// readInputStreamUTF ()


    private void runThreadClientToServer ()
    {
        String msg;
        while (!connectionGettingClosed && (msg = readInputStreamUTF()) != null)
        {
    // Я считаю, что 2 цикла while здесь не подходят, т.к. у ClientHandler8 есть (или могут появиться)
    // команды, которые он должен обрабатывать независимо от состояния регистрации клиента.

            msg = msg.trim().toLowerCase();

            if (msg.isEmpty() || msg.equals(CMD_ONLINE))
                continue;

            if (msg.equals  (CMD_EXIT)) //< Приложение клиента закрывается.
            {
                connectionGettingClosed = true;
            }
            else if (server != null) //< если сервер ещё не упал
            {
                if (msg.equals (CMD_CLIENTS_LIST)) //< клиент запросил список участников чата
                {
                    sendClientsList();
                }
                else if (msg.equals (CMD_LOGIN)) // Клиент запросил регистрацию в чате
                {
                    onCmdLogin();
                }
                else
                if (clientName != null) //< сообщения только для зарегистрированного клиента
                {
                    if (msg.equals (CMD_CHANGE_NICKNAME)) // Клиент запросил смену имени
                    {
                        onCmdChangeNickname();
                    }
                    else
                    if (msg.equals (CMD_WHOAMI)) // Клиент запросил своё имя
                    {
                        syncSendMessageToClient (CMD_WHOAMI, clientName);
                    }
                    else //сообщения, которые нужно считать:
                    {
                        boolean boolSent = false;
                        if (msg.equals (CMD_CHAT_MSG))
                        {
                            boolSent = server.syncBroadcastMessage (readInputStreamUTF(), this);
                        }
                        else
                        if (msg.equals (CMD_PRIVATE_MSG)) //< Клиент отправил личное сообщение клиенту.
                        {
                            String nameTo = readInputStreamUTF();
                            String message = readInputStreamUTF();
                            boolSent = server.syncSendPrivateMessage (nameTo, message, this);
                        }
                        else throw new UnsupportedOperationException (
                                "ERROR @ runThreadClientToServer() : незарегистрированное сообщение.");

                        if (!boolSent)
                            syncSendMessageToClient (CMD_ERROR, String.format (FORMAT_UNABLE_SEND_MESSAGE_TO, msg));
                    }
                }
            }
        }//while
        System.out.print ("\nClientHandler8.runThreadClientToServer() - поток threadClientToServer закрылся.");
        threadClientToServer = null;
        close();
    }// runThreadClientToServer ()

//Обработчик команды CMD_CHANGE_NICKNAME.
    private void onCmdChangeNickname ()
    {
        String newname = readInputStreamUTF();
        if (server.syncChangeNickname (clientName, newname))
        {
            server.syncBroadcastMessage (String.format (FORMAT_RENAMING_TO_, newname), this);
            clientName = newname;
            syncSendMessageToClient (CMD_CHANGE_NICKNAME, clientName);
        }
        else syncSendMessageToClient (CMD_NICKNAME_BUSY);
    }// onCmdChangeNickname ()

//Обработчик команды CMD_LOGIN.
    private void onCmdLogin ()
    {
        if (clientName != null)
            throw new RuntimeException("ERROR @ runThreadClientToServer() : повторная регистрация?");

        clientName = server.syncValidateOnLogin (readInputStreamUTF(), readInputStreamUTF(), this);
        if (clientName != null)
        {
            syncSendMessageToClient (CMD_LOGIN, clientName);
            sendClientsList();
            server.syncBroadcastMessage (ENTER_CHAT, this);
        }
        else syncSendMessageToClient (CMD_BADLOGIN); //< Серверу не понравилось введённое пользователем имя.
    }// onCmdLogin ()

//Отсылаем клиенту новый список клиентов.
    private void sendClientsList ()
    {
        String[] clientslist = server.getClientsList();
        if (clientslist != null)
        {
            syncSendMessageToClient (CMD_CLIENTS_LIST);
            syncSendMessageToClient (String.valueOf (clientslist.length));
            syncSendMessageToClient (clientslist);
        }
    }// sendClientsList ()


//(Вспомогательная.)
    public synchronized boolean syncSendMessageToClient (String ... lines)
    {
        boolean boolSent = false;
        if (lines != null  &&  lines.length > 0  &&  dos != null)
        try
        {
            for (String msg : lines)
                dos.writeUTF(msg);
            boolSent = true;
        }
        catch (IOException e)
        {
            connectionGettingClosed = true;
            System.out.print ("\nClientHandler8.syncSendMessageToClient() : ошибка соединения.");
        }
        return boolSent;
    }// syncSendMessageToClient ()


// (Этот метод вызывается при завершении к.-л. потока при обработке им сообщения CMD_EXIT.)
    private void close ()
    {
        connectionGettingClosed = true;
        if (server != null)
        {
            server.syncRemoveClient (this);
            server.syncBroadcastMessage (LEFT_CHAT, this);
            server = null;
        }
        try
        {   //syncSendMessageToClient (CMD_EXIT); < не нужно это здесь вызывать, т.к. мы сейчас вызываем close() в
            //      двух случаях: по приходе от клиента команды CMD_EXIT, и в onServerDown(). onServerDown() сам
            //      сам шлёт клиенту сообщение CMD_EXIT. Вроде, этого достаточно.

            if (threadClientToServer != null)   threadClientToServer.join(1000);
            if (socket != null && !socket.isClosed())   socket.close();
            // (Закрытие Socket'а приводит к закрытию созданных им InputStream и OutputStream.)
        }
        catch (InterruptedException | IOException e) { e.printStackTrace(); }
        finally
        {
            threadClientToServer = null;
            socket = null;
            dos = null;
            dis = null;
            System.out.printf("\n(ClientHandler8.close() : Клиент %s закрылся.)\n", clientName);
            clientName = null;
        }
    }// close ()

//Метод вызывается сервером (предположительно).
    public void onServerDown ()
    {
        syncSendMessageToClient (CMD_CHAT_MSG, SERVER_OFF);
        syncSendMessageToClient (CMD_EXIT);
        server = null; //< чтобы никто не пытался вызывать методы сервера
        close();
    }// onServerDown ()

    public String getClientName ()  {   return clientName;  }

}
