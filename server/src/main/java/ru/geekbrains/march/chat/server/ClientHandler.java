package ru.geekbrains.march.chat.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import static ru.geekbrains.march.chat.server.ServerApp.*;

public class ClientHandler
{
    private String nickname; //< После регистрации пользователя clientName == Controller.userName.
    private final int C2S_THREAD_SLEEPINTERVAL = 100,
                      IDLE_TIMER_INTERVAL = 120_000;
    private boolean connectionGettingClosed = false;

    private Socket socket;
    private Server server;
    private DataInputStream dis;
    private DataOutputStream dos;
    private Thread threadClientToServer,
                   threadMain;

    private final static String
            CONNECTION_ESTABLISHED = "\nСоединение с сервером установлено.",
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
//if (DEBUG) System.out.println("ClientHandler.ClientHandler().s");
        if (serverSideSocket == null || serv == null)
            throw new IllegalArgumentException();

        this.server = serv;
        this.socket = serverSideSocket;
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
//if (DEBUG) System.out.println("ClientHandler.ClientHandler().e");
    }//ClientHandler (Socket)


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
            else //Такой же блок есть в Controller.readInputStreamUTF(). Там я описал причину,
            {    // по которой оставил этот блок без изменений.
                Thread.sleep(C2S_THREAD_SLEEPINTERVAL);

            //Раз в 5 сек. проверяем, не работает ли наш поток впустую.
                sleeptimer ++;
                if (sleeptimer > 5000 / C2S_THREAD_SLEEPINTERVAL)
                {
                    if (!threadMain.isAlive())  //< проверяем родительский поток
                        break;
                    syncSendMessageToClient (CMD_ONLINE);   //< «пингуем» клиента
                    sleeptimer = 0;
                }
            }
        }
        catch (InterruptedException e) {e.printStackTrace();}
        catch (IOException e)
        {
            connectionGettingClosed = true;
            msg = null;
            System.out.print("\nClientHandler.readInputStreamUTF() : ошибка соединения.");
            //e.printStackTrace();
        }
        return msg;
    }// readInputStreamUTF ()


    private void runThreadClientToServer ()
    {
        String msg;
        while (!connectionGettingClosed && (msg = readInputStreamUTF()) != null)
        {
    // Я считаю, что 2 цикла while здесь не подходят, т.к. у ClientHandler'а есть (или могут появиться)
    // команды, которые он должен обрабатывать независимо от состояния регистрации клиента.

            msg = msg.trim().toLowerCase();

            if (msg.isEmpty() || msg.equals (CMD_ONLINE))
                continue;

            if (msg.equals (CMD_EXIT)) //< Приложение клиента закрывается.
            {
                connectionGettingClosed = true;
            }
            else
            if (server != null) //< если сервер ещё не упал
            {
                if (msg.equals (CMD_CLIENTS_LIST)) //< клиент запросил список участников чата
                {
                    onCmdClientsList();
                }
                else
                if (msg.equals (CMD_LOGIN)) // Клиент запросил регистрацию в чате
                {
                    onCmdLogin();
                }
                else
                if (nickname != null) //< сообщения только для зарегистрированного клиента
                {
                    if (msg.equals (CMD_CHANGE_NICKNAME)) // Клиент запросил смену имени
                    {
                        onCmdChangeNickname();
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
            }
        }//while
        System.out.print ("\nClientHandler.runThreadClientToServer() - поток threadClientToServer закрылся.");
        threadClientToServer = null;
        close();
//if (DEBUG) System.out.println("ClientHandler.runThreadClientToServer().ends");
    }// runThreadClientToServer ()


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
        else //< ok
        {   syncSendMessageToClient (CMD_LOGIN, nickname);
            server.syncBroadcastMessage (ENTER_CHAT, this);
        }
    }// onCmdLogin ()


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
        {
            syncSendMessageToClient (CMD_CLIENTS_LIST);
            syncSendMessageToClient (String.valueOf (clientslist.length));
            syncSendMessageToClient (clientslist);
        }
    }// sendClientsList ()


//Обработчик команды CMD_CLIENTS_LIST.
    private void onCmdClientsList ()
    {
        String[] clientslist = server.getClientsList();
        if (clientslist != null)
        {
            syncSendMessageToClient (CMD_CLIENTS_LIST);
            syncSendMessageToClient (String.valueOf (clientslist.length));
            syncSendMessageToClient (clientslist);
        }
    }// onCmdClientsList ()


//(Вспомогательная.)
    public synchronized boolean syncSendMessageToClient (String ... lines)
    {
        boolean boolSent = false;
        if (lines != null  &&  lines.length > 0  &&  dos != null)
        {
            try
            {
                for (String msg : lines)
                    dos.writeUTF(msg);
                boolSent = true;
            }
            catch (IOException e)
            {
                connectionGettingClosed = true;
                System.out.print ("\nClientHandler.syncSendMessageToClient() : ошибка соединения.");
                //e.printStackTrace();
            }
        }
        return boolSent;
    }// syncSendMessageToClient ()


// (Этот метод вызывается при завершении к.-л. потоком при обработке им сообщения /exit.)
    private void close ()
    {
        connectionGettingClosed = true;
        if (server != null)
        {
            server.syncClientLogout(this);
            //server.syncBroadcastMessage (LEFT_CHAT, this);
            server = null;
        }
        try
        {   //syncSendMessageToClient (CMD_EXIT); < не нужно это здесь вызывать, т.к. мы сейчас вызываем close() в
            //      двух случаях: по приходе от клиента команды CMD_EXIT, и в onServerDown(). onServerDown() сам
            //      сам шлёт клиенту сообщение CMD_EXIT. Вроде, этого достаточно.

            if (threadClientToServer != null)   threadClientToServer.join(1000);
        // (Оказывается, закрытие Socket'а приводит к закрытию созданных им InputStream и OutputStream.)
            if (socket != null && !socket.isClosed())   socket.close();
        }
        catch (InterruptedException | IOException e) { e.printStackTrace(); }
        finally
        {
            threadClientToServer = null;
            socket = null;
            dos = null;
            dis = null;
            System.out.printf ("\n(ClientHandler.close() : Клиент %s закрылся.)\n", nickname);
            nickname = null;
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


    public String getClientName ()  {   return nickname;  }

    @Override public String toString ()   {   return "CH:"+ getClientName();   } //< для отладки

}// class ClientHandler
