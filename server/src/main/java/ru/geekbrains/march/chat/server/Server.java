package ru.geekbrains.march.chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;

public class Server
{
    public static final int PORT_MAX = 65535;
    public static final int PORT_MIN = 0;
    public static final boolean VALIDATE_AND_ADD = true;

    private int port = 0;
    private List<ClientHandler> clients = null;

    public Server (int port)
    {
        if (port < PORT_MIN || port > PORT_MAX)    throw new IllegalArgumentException();
        this.port = port;
        clients = new LinkedList<>();

    // создали сокет на порте 8189 (нужно использовать любой свободный порт). Если порт занят,
    // то получим исключение, но, скорее всего, порт 8189 будет свободен.
        try (ServerSocket servsocket = new ServerSocket (port))
        {
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
            System.out.println("Не удалось создать ClientHandler.");
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
        boolean boolSent = false;
        if (msg != null && !msg.isEmpty() && clients != null)
        {
            for (ClientHandler client : clients)
                boolSent = client.sendMessageToClient (from.getClientName() + ":\n\t" + msg);
        }
        return boolSent;
    }// broadcastMessage ()

//Пересылаем указанное сообщение автору и указанному клиенту. Сообщение получаем в формате: «адресат сообщение».
    public boolean sendPrivateMessage (String msg, ClientHandler from)
    {
        boolean boolSent = false;
        if (msg != null && !msg.isEmpty() && clients != null)
        {
            int index = msg.trim().indexOf(' ');
            if (index >= 0)
            {
                String name = msg.substring (0, index);
                for (ClientHandler client : clients)
                {
                    if (name.equals (client.getClientName()))
                    {
                        from.sendMessageToClient (String.format("Личное сообщение для %s:\n\t%s",
                                                  name,
                                                  msg.substring (index+1)));

                        boolSent = client.sendMessageToClient (String.format("Вам личное сообщение от %s:\n\t%s",
                                                               from.getClientName(),
                                                               msg.substring (index+1)));
                        break;
                    }
                }//for
            }
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


}// class Server
