package ru.geekbrains.march.chat.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.security.InvalidParameterException;

import static ru.geekbrains.march.chat.server.Server.VALIDATE_AND_ADD;
import static ru.geekbrains.march.chat.server.ServerApp.*;

public class ClientHandler
{
    private String clientName; //< После регистрации пользователя clientName == Controller.userName.
    private int msgCounter = 0;
    private boolean connectionGettingClosed = false;

    private Socket socket;
    private Server server;
    private DataInputStream dis;
    private DataOutputStream dos;
    private Thread threadClientToServer,
                   threadCloser,
                   threadMain;


    public ClientHandler (Server serv, Socket sock)
    {
        if (sock == null || serv == null)
            throw new InvalidParameterException ();

        this.server = serv;
        this.socket = sock;
        this.threadMain = Thread.currentThread();
        try
        {
            dis = new DataInputStream (socket.getInputStream()); //< IOException
            dos = new DataOutputStream (socket.getOutputStream()); //< IOException

        // Чтобы освободить поток main() для подключения других клиентов, выносим обработчики сообщений
        // в отдельные потоки.
            threadClientToServer = new Thread(() -> runThreadClientToServer());
            threadClientToServer.start();
            sendMessageToClient ("Соединение с сервером установлено.\nСервер ожидает регистрации пользователя.");
        }
        catch (IOException ioe)
        {
            startCloseThread(); //< запускаем поток, котрый будет закрывать все CLosable-объекты
            ioe.printStackTrace();
        }
    }//ClientHandler (Socket)


    private void runThreadClientToServer ()
    {
        int timer = 0;
        try
        {
            while (!connectionGettingClosed)
            {
                String msg;
                if (dis.available() > 0)
                {
                    msg = dis.readUTF().trim();

//Я считаю, что 2 цикла while здесь не подходят, т.к. сервер, например, должен иметь возможность отправить /exit
//клиенту, который ещё не авторизовался.

                    if (!msg.isEmpty())
                    if (msg.equalsIgnoreCase(CMD_EXIT)) //< Приложение клиента прислало запрос «/exit».
                    {
                        connectionGettingClosed = true;
                    }
                    else if (msg.equalsIgnoreCase(CMD_ONLINE))
                    {
                        ; // (нет необходимости реагировать на это сообщение)
                    }
                    else if (server != null) //< если сервер ещё не упал
                    {
                        if (msg.startsWith (LOGIN_PREFIX))
                        {
                            clientName = msg.substring (LOGIN_PREFIX.length());

                            if (server.validateUser (this, VALIDATE_AND_ADD))
                            {
                                dos.writeUTF (LOGIN_PREFIX + clientName);
                                server.broadcastMessage ("(вошёл в чат)", this);
                            }
                            else dos.writeUTF (CMD_LOGIN); //< Серверу не понравилось введённое пользователем имя.
                        }
                        else if (clientName != null) //< сообщения только для зарегистрированного клиента
                        {
                            if (msg.equalsIgnoreCase (CMD_STAT)) //< Приложение клиента прислало запрос «/stat».
                            {
                                sendMessageToClient("counter = " + msgCounter);
                            }
                            else if (msg.equalsIgnoreCase (CMD_WHOAMI))
                            {
                                sendMessageToClient ("Вы вошли в чат как: " + clientName);
                            }
                            else //сообщения, которые нужно считать:
                            {
                                boolean boolOk = false;
                                if (msg.startsWith (PRIVATE_PREFIX)) //< Клиент отправил личное сообщение клиенту.
                                {
                                    String[] sarr = msg.split ("\\s", 3);
                                    if (sarr.length > 2)
                                        boolOk = server.sendPrivateMessage (sarr[1], sarr[2], this);
                                       //(Если юзера нет, то сервер уже послал клиенту сообщение «такого юзера нет».)
                                }
                                else //< Клиент отправил публичное сообщение в чат.
                                {
                                    boolOk = server.broadcastMessage (msg, this);
                                }

                                if (boolOk)  msgCounter++;
                                else
                                dos.writeUTF (String.format("Не удалось отправить сообщение:\n\t%s", msg));
                            }
                        }
                    }
                }
                else
                {
                    Thread.sleep(SLEEP_INTERVAL);

                //Раз в 5 сек. проверяем, не работает ли наш поток впустую.
                    timer ++;
                    if (timer > 5000 / SLEEP_INTERVAL)
                    {
                        if (!threadMain.isAlive())
                            break;
                        dos.writeUTF(CMD_ONLINE); //< «пингуем» клиента на случай, если он отключился без предупреждения
                        timer = 0;
                    }
                }
            }//while
        }
        catch (InterruptedException e) {e.printStackTrace();}
        catch (IOException e)
        {
            System.out.println("\nОШИБКА: соединение разорвано.");
            e.printStackTrace();
        }
        finally
        {
            startCloseThread(); //< метод запустит отдельный поток и сразу вернёт управление
        }
        System.out.println ("(Поток threadClientToServer закрылся. Клиент:"+clientName+")");
    }// runThreadClientToServer ()


//(Вспомогательная.)
    public boolean sendMessageToClient (String msg)
    {
        boolean boolSent = false;
        if (msg != null  &&  !msg.isEmpty()  &&  dos != null)
        {
            try
            {   dos.writeUTF(msg);
                boolSent = true;
            }
            catch(IOException e) { e.printStackTrace(); }
        }
        return boolSent;
    }// sendMessageToClient ()


// Создаём поток, который закрывает ClientHandler. Этот метод остался от реализации, в которой
// присутствовал поток для чтени из консоли. От консоли пришлось отказаться, а метод остался.
    private void startCloseThread ()
    {
        if (threadCloser == null) //< поток ещё не запущен
        {
            threadCloser = new Thread (() -> threadCloseClientHandler());
            threadCloser.start();
        }
    }// startCloseThread ()

// (Этот метод вызывается при завершении к.-л. потоком при обработке им сообщения /exit.)
    private void threadCloseClientHandler ()
    {
        connectionGettingClosed = true;
        if (server != null)
        {
            server.broadcastMessage ("(покинул чат)", this);
            server.removeClient (this);
            server = null;
        }
        try
        {
            if (threadClientToServer != null)   threadClientToServer.join();
            //if (threadConsoleToClient != null)   threadConsoleToClient.join();
        // (Оказывается, закрытие Socket'а приводит к закрытию созданных им InputStream и OutputStream.)
            if (socket != null)
                socket.close();
        }
        catch (InterruptedException | IOException e){e.printStackTrace();}
        finally
        {
            threadClientToServer = null;
            //threadConsoleToClient = null;
            socket = null;
            dos = null;
            dis = null;
            System.out.printf("(threadCloser : Клиент %s закрылся.)\n", clientName);
        }
    }// threadCloseClientHandler ()

//Метод вызывается сервером (предположительно).
    public void onServerDown ()
    {
        if (dos != null)
        try
        {
            dos.writeUTF ("Сервер прекратил работу.");
            dos.writeUTF(CMD_EXIT);
        }
        catch(IOException e){ e.printStackTrace(); }

        server = null; //< чтобы никто не пытался вызывать методы сервера
        startCloseThread();
    }// onServerDown ()

    public String getClientName ()  {   return clientName;  }

}// class ClientHandler
