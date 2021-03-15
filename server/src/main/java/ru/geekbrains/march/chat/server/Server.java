package ru.geekbrains.march.chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

import static ru.geekbrains.march.chat.server.ServerApp.*;

public class Server
{
    private static final String
            format_NO_SUCH_USER = "Клиент %s отсутствует в чате.";

    private static int serverNameCounter = 0;
    public static final int PORT_MAX = 65535;
    public static final int PORT_MIN = 0;
    public static final boolean VALIDATE_AND_ADD = true;
    private final String serverName;

    private int port = 0;
    private List<ClientHandler> clients;
    private Thread threadConsoleToClient,
                   threadMain;
    private boolean connectionGettingClosed = false;


    public Server (int port)
    {
        if (port < PORT_MIN || port > PORT_MAX)    throw new IllegalArgumentException();
        this.port = port;
        clients = new LinkedList<>();
        threadMain = Thread.currentThread();
        serverName = "ЧатСервер_" + serverNameCounter ++;

    // создали сокет на порте 8189 (нужно использовать любой свободный порт). Если порт занят,
    // то получим исключение, но, скорее всего, порт 8189 будет свободен.
        try (ServerSocket servsocket = new ServerSocket (port))
        {
            threadConsoleToClient = new Thread(() -> runThreadConsoleToClient());
            threadConsoleToClient.start();
            System.out.println ("\nНачало сессии.");

            while (true)
            {
                System.out.println ("\tЖдём подклюение клиента.");
    // ожидаем подключений (бесконечно, если подключений так и не будет). Если подключение придёт, то в
    // socket окажется подключение к клиенту (клиент должен знать, что мы его ждём на порте 8189).
                Socket socket = servsocket.accept();
                new ClientHandler (this, socket);

    // цикл чтения байтов из входного потока (закоментируем этот фрагмент, чтобы он не мешал воспользоваться
    // некоторыми усовершенствованиями, которые находястя в следующем за ним фрагменте)
                //int x;
                //while ((x = socket.getInputStream().read()) != -1)
                //    System.out.print ((char)x);
                /* На выходе мы получаем исключение, т.к. client завершился первым. В общем, это нормально. */

/*  Необязательный шаг: оборачиваем потоки ввода и вывода в более удобные дата-потоки (это позволит нам,
    например, обмен байтами заменить на обмен строками).
    (Сейчас этот фрагмент закомментирован, т.к. мы перенесли работу с клиентом в отдельный класс. Кроме того,
    мы заключили в цикл операцию подключения клиента, что в совокупности дало нам возможность подключать не
    одного, а многих клиентов. Напомним, что ServerSocket служит для подключения многих клиентов, для каждого
    из которых им создаётся Socket; для дальнейшей работы подключения ServerSocket не требуется.)
*/              //DataInputStream dis = new DataInputStream (socket.getInputStream());
                //DataOutputStream dos = new DataOutputStream (socket.getOutputStream());
                //while (true)
                //{
                //    String s = dis.readUTF();
                //    if (s.trim().equals("/exit")) //< эксперимент
                //        break;
                //    dos.writeUTF ("ECHO: "+s); //< эксперимент
                //}
            }//while
        }
        catch (IOException ioe)
        {
            ioe.printStackTrace();
            System.out.println ("Не удалось создать ClientHandler.");
        }
        finally
        {
            serverGettingDown();
        }
    // (Закрытие ServerSocket не означает разрыв всех созданных соединений, а означает лишь невозможность
    // подключение новых клиентов.)
    }// Server (int port)

//Проверяем имя клиента на уникальность и при необходимости добавляем его в список подключенных клиентов.
    public boolean validateUser (ClientHandler client, boolean add)
    {
        //boolean boolValid = false;
        String name = (client != null) ? client.getClientName() : null;

        if (name != null && !name.isEmpty())
        {
            for (ClientHandler cl : clients)
            {
                if (cl != client)
                {
                    String n = cl.getClientName();
                    if (n != null && n.equals(name))
                        return false;
                }
            }
            if (add == VALIDATE_AND_ADD)   return addClient (client);
            else
            return true;
        }
        return false;
    }// validateUser ()

//Добавление клиента в список подключенных клиентов.
    private boolean addClient (ClientHandler client)
    {
        boolean boolOk = clients.add (client);
        if (boolOk)
            System.out.println("\nServer: Клиент " + client.getClientName() + " добавлен.");
        return boolOk;
    }// addClient ()

//Удаляем клиента из списка подключенных клиентов.
    public void removeClient (ClientHandler client)
    {
        if (clients.remove (client))
            System.out.println("Server: Клиент " + client.getClientName() + " удалён.");

    }// removeClient ()

//Рассылаем указанное сообщение всем клиентам из нашего списка подключенных клиентов.
    public boolean broadcastMessage (String msg, ClientHandler from)
    {
        //if (from == null)   securityCheckThread();
        boolean boolSent = false;

        if (msg != null && !msg.isEmpty() && clients != null)
        {
            for (ClientHandler client : clients)
            {
                String name = (from != null) ? from.getClientName() : serverName; //< сообщение исходит от сервера (введено в консоли)
                boolSent = client.sendMessageToClient (name + ":\n\t" + msg);
            }
        }
        return boolSent;
    }// broadcastMessage ()

//Пересылаем указанное сообщение автору и указанному клиенту. Сообщение получаем в формате: «адресат сообщение».
    public boolean sendPrivateMessage (String nameTo, String message, ClientHandler clientFrom)
    {
        //if (from == null)   securityCheckThread();
        boolean boolSent = false;

        if (message != null && !message.isEmpty() &&
            nameTo  != null && !nameTo.isEmpty()  &&
            clients != null)
        {
            String nameFrom = (clientFrom != null) ? clientFrom.getClientName() : serverName;

            for (ClientHandler clientTo : clients)
            {
                if (nameTo.equals (clientTo.getClientName()))
                {
                    if (clientFrom != null) //< если сообщение не от сервера, то дублируем его отправителю
                        clientFrom.sendMessageToClient (String.format(
                                    "Личное сообщение для %s:\n\t%s",
                                    nameTo,
                                    message));

                    if (clientFrom == null)
                    {
                        if (message.equalsIgnoreCase (CMD_EXIT)) //< Server научился отключать пользователей.
                            boolSent = clientTo.sendMessageToClient (CMD_EXIT);
                    }
                    else boolSent = clientTo.sendMessageToClient (String.format(
                                        "Вам личное сообщение от %s:\n\t%s",
                                        nameFrom,
                                        message));
                    break;
                }
            }
            //проверка отправки сообщения несуществующему клиенту (по результатам разбора ДЗ-7)
            if (!boolSent)
                if (clientFrom == null) System.out.printf (format_NO_SUCH_USER, nameTo);
                else
                clientFrom.sendMessageToClient (String.format (format_NO_SUCH_USER, nameTo));
        }
        return boolSent;
    }// sendPrivateMessage ()


// Подготовка к «отключению» сервра.
    private void serverGettingDown ()
    {
        if (clients != null) //< закрываем всех клиентов.
        {
            for (ClientHandler client : clients)
                client.onServerDown();
        }
    }// serverGettingDown ()


/* (Пришлось лишить ClientHandler возможности общаться с клиентами через консоль из-за странного глюка: если
 клиент покидает чат отправив серверу /exit, то этот метод падает вместе со всеми (падает main tread ?), и
  тащит за собой Server (что приводит к отключению остальных клиентов). Причина глюка пока не выяснена, но,
  видимо, дело в сканере или ещё каком-то общем ресурсе.)    //*/
    private void runThreadConsoleToClient () //поток threadConsoleToClient
    {
        String msg;
        int timer = 0;
        try (Scanner sc = new Scanner(System.in))
        {
            while (!connectionGettingClosed)
            {
                if (System.in.available() > 0)
                {
                    msg = sc.nextLine().trim();
                    if (!msg.isEmpty())
                    {
                        if (msg.equalsIgnoreCase(CMD_EXIT))
                        {
                            connectionGettingClosed = true; //< Пока это закрывает только поток threadConsoleToClient
                        }
                        else if (msg.startsWith(PRIVATE_PREFIX))
                        {
                            String[] sarr = msg.split ("\\s", 3);

                            if (sarr.length > 2)
                                sendPrivateMessage (sarr[1], sarr[2], null);
                        }
                        else broadcastMessage (msg, null);
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
                    }
                }
            }//while
        }
        catch (InterruptedException intex) {intex.printStackTrace();}
        catch (IOException ioex) {ioex.printStackTrace();}
        finally
        {
            serverGettingDown();
        }
        System.out.println ("(Поток Server.threadConsoleToClient закрылся.)");
    }// runThreadConsoleToClient ()     //*/


//(Вспомогательная.) Проверем является ли вызывающий поток нашим потоком.
//    private void securityCheckThread ()
//    {
//        Thread threadThis = Thread.currentThread();
//        if (threadThis != threadMain &&
//            threadThis != threadConsoleToClient)
//            throw new SecurityException ("securityCheckThread() вызван из постороннего потока!");
//    }// securityCheckThread ()


}// class Server
