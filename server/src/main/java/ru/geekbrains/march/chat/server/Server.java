package ru.geekbrains.march.chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

import static ru.geekbrains.march.chat.server.ServerApp.*;

public class Server
{
    private static final String
            FORMAT_NO_SUCH_USER = "\nКлиент %s отсутствует в чате.",
            SERVERNAME_BASE_ = "ЧатСервер_",
            SESSION_START = "\nНачало сессии.",
            WAITING_FOR_CLIENTS = "\n\tЖдём подклюение клиента... ",
            UNABLE_TOCREATE_HANDLER = "\nНе удалось создать ClientHandler."
            ;
    private static int serverNameCounter = 0;
    public static final int PORT_MAX = 65535;
    public static final int PORT_MIN = 0;
    public static final boolean
                VALIDATE_AND_ADD = true,
                VALIDATE_AND_RENAME = !VALIDATE_AND_ADD,
                REMOVE_AND_UPDATE = true, REMOVE_SILENT = !REMOVE_AND_UPDATE;
    private final String serverName;

    private final int CONSOLE_THREAD_SLEEPINTERVAL = 250;

    private int port = 0;
    //private final List<ClientHandler> clients;
    private final Map<String, ClientHandler> map;
    private Thread threadConsoleToClient;
    private final Thread threadMain;
    private boolean serverGettingOff = false;


    public Server (int port)
    {
        if (port < PORT_MIN || port > PORT_MAX)    throw new IllegalArgumentException();

        map = new HashMap<>();
        this.port = port;
        //clients = new LinkedList<>();
        threadMain = Thread.currentThread();
        serverName = SERVERNAME_BASE_ + serverNameCounter ++;

    // создали сокет на порте 8189 (нужно использовать любой свободный порт). Если порт занят,
    // то получим исключение, но, скорее всего, порт 8189 будет свободен.
        try (ServerSocket servsocket = new ServerSocket (this.port))
        {
            threadConsoleToClient = new Thread(() -> runThreadConsoleToClient());
            threadConsoleToClient.start();
            System.out.print (SESSION_START);

            while (!serverGettingOff)
            {
                System.out.print (WAITING_FOR_CLIENTS);
    // ожидаем подключений (бесконечно, если подключений так и не будет). Если подключение придёт, то в
    // socket окажется подключение к клиенту (клиент должен знать, что мы его ждём на порте 8189).
                Socket socket = servsocket.accept();
                if (!serverGettingOff)
                    new ClientHandler (this, socket);
                else
                    socket.close();

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
            System.out.print (UNABLE_TOCREATE_HANDLER);
        }
        finally
        {
            serverGettingDown();
        }
    // (Закрытие ServerSocket не означает разрыв всех созданных соединений, а означает лишь невозможность
    // подключение новых клиентов.)
    }// Server (int port)


// Подготовка к «отключению» сервра.
    private void serverGettingDown ()
    {
        serverGettingOff = true;
        if (map != null) //< закрываем всех клиентов.
        {
            for (Map.Entry<String, ClientHandler> entry : map.entrySet())
                entry.getValue().onServerDown();
        }
    }// serverGettingDown ()


/* (Пришлось лишить ClientHandler возможности общаться с клиентами через консоль из-за странного глюка:
 если клиент покидает чат, отправив серверу /exit, то этот метод падает и тащит за собой Server (что
 приводит к отключению остальных клиентов). Причина глюка пока не выяснена, но, видимо, дело в сканере
 или ещё каком-то общем ресурсе.)    //*/
    private void runThreadConsoleToClient () //поток threadConsoleToClient
    {
        String msg;
        int timer = 0;
        try (Scanner sc = new Scanner(System.in))
        {
            while (!serverGettingOff)
            if (System.in.available() > 0)
            {
                msg = sc.nextLine().trim();

                if (!msg.isEmpty())
                if (msg.equalsIgnoreCase(CMD_EXIT)) //< Сервер можно закрыть руками.
                {
                    serverGettingOff = true; //< так мы закрываем наш поток -- threadConsoleToClient
                    new Socket (SERVER_ADDRESS, SERVER_PORT); //< а так освобождаем основной поток от чар метода accept().
                }
                else if (msg.equalsIgnoreCase(CMD_PRIVATE_MSG))
                {
                    //String[] tokens = msg.split ("\\s", 3);
                    //if (tokens.length > 2)
                    //    syncSendPrivateMessage(tokens[1], tokens[2], null);
                    System.out.print ("Личное сообщение для кого: ");
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
            else
            {
                Thread.sleep(CONSOLE_THREAD_SLEEPINTERVAL);
            //Раз в 5 сек. проверяем, не работает ли наш поток впустую.
                timer ++;
                if (timer > 5000 / CONSOLE_THREAD_SLEEPINTERVAL)
                {
                    if (!threadMain.isAlive())  //< проверяем родительский поток
                        break;
                    timer = 0;
                }
            }
        }
        catch (InterruptedException intex) {intex.printStackTrace();} //для sleep()
        catch (IOException ioex) {ioex.printStackTrace();}  //для available()
        finally
        {
            serverGettingDown();
        }
        System.out.print ("\n(Поток Server.threadConsoleToClient закрылся.)");
    }// runThreadConsoleToClient ()


//Проверяем имя клиента на уникальность и при необходимости добавляем его в список подключенных клиентов.
    public synchronized boolean syncValidateUser (ClientHandler client, String newname, boolean add)
    {
        boolean boolOk = false;
        if (newname != null && !newname.isEmpty() &&
            map != null && !map.containsKey(newname))
        {
            if (add == VALIDATE_AND_RENAME)
                syncRemoveClient (client, REMOVE_SILENT);
                // (Здесь мы вносим изменения в список клиентов, а завершат переименование
                // клиента ClientHandler и Controller.)

            map.put (newname, client);
            boolOk = true;
            onClientsListChanged();
        }
        return boolOk;
    }// syncValidateUser ()


//Удаляем клиента из списка подключенных клиентов.
    public synchronized void syncRemoveClient (ClientHandler client, boolean mode)
    {
        if (client != null)
            if (map.remove(client.getClientName()) != null)
                if (mode == REMOVE_AND_UPDATE)
                    onClientsListChanged();
    }// syncRemoveClient ()


//В списке клиентов произошли изменения (добавление, удаление, переименование).
    public void onClientsListChanged ()
    {
        syncBroadcastMessage (CMD_CLIENTS_LIST_CHANGED, null);
    }// onClientsListChanged ()


//Рассылаем указанное сообщение всем клиентам из нашего списка подключенных клиентов.
    public synchronized boolean syncBroadcastMessage (String msg, ClientHandler from)
    {
        boolean boolSent = false;

        if (msg != null && !msg.isEmpty() && map != null)
        {
            for (Map.Entry<String, ClientHandler> entry : map.entrySet())
            {
                ClientHandler client = entry.getValue();

            //for (ClientHandler client : clients)
            //{
                if (msg.equalsIgnoreCase (CMD_CLIENTS_LIST_CHANGED))
                {
                    boolSent = client.syncSendMessageToClient (CMD_CLIENTS_LIST_CHANGED);
                }
                else
                {
                    String name = (from != null) ? from.getClientName() : serverName; //< сообщение исходит от сервера (введено в консоли)
                    boolSent = client.syncSendMessageToClient(CMD_CHAT_MSG, name + ":\n\t" + msg);
                }
            }
        }
        return boolSent;
    }// syncBroadcastMessage ()


//Пересылаем указанное сообщение автору и указанному клиенту. Сообщение получаем в формате: «адресат сообщение».
    public synchronized boolean syncSendPrivateMessage (String nameTo, String message, ClientHandler clientFrom)
    {
        boolean boolSent = false;

        if (message != null && !message.isEmpty() &&
            nameTo  != null && !nameTo.isEmpty()  &&
            map != null)
        {
            String nameFrom = (clientFrom != null) ? clientFrom.getClientName() : serverName;

            for (Map.Entry<String, ClientHandler> entry : map.entrySet())
            {
                ClientHandler clientTo = entry.getValue();
                if (nameTo.equals (clientTo.getClientName()))
                {
                    if (clientFrom == null && message.equalsIgnoreCase (CMD_EXIT)) //< Server научился отключать пользователей.
                        boolSent = clientTo.syncSendMessageToClient (CMD_EXIT);
                    else
                        boolSent = clientTo.syncSendMessageToClient (CMD_PRIVATE_MSG, nameFrom, message);

                    //// если сообщение не от сервера, то дублируем его отправителю
                    //// (серверу отправленные им личные сообщения не дублируем)
                    //if (clientFrom != null)
                    //    clientFrom.syncSendMessageToClient (CMD_PRIVATE_MSG, nameTo, message);

                    break;
                }
            }
            //проверка отправки сообщения несуществующему клиенту (по результатам разбора ДЗ-7)
            if (!boolSent)
                if (clientFrom == null) System.out.printf (FORMAT_NO_SUCH_USER, nameTo);
                else
                clientFrom.syncSendMessageToClient (String.format(FORMAT_NO_SUCH_USER, nameTo));
        }
        return boolSent;
    }// syncSendPrivateMessage ()


//Составляем список имён участников чата.
    public synchronized String[] getClientsList ()
    {
        String[] namelist = null;
        if (map != null)
        {
            namelist = map.keySet().toArray (new String[0]);

            //int size = map.size();
            //namelist = new String[size];
            //for (int i=0;  i<size;  i++)
            //{
            //    ClientHandler cl = clients.get(i);
            //    namelist[i] = cl.getClientName();
            //}
        }
        return namelist;
    }// getClientsList ()

}// class Server
