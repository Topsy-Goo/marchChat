package ru.geekbrains.march.chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;

public class Hw7Server
{
    public static final int PORT_MAX = 65535;
    public static final int PORT_MIN = 0;
    public static final boolean VALIDATE_AND_ADD = true;

    private int port = 0;
    private List<Hw7ClientHandler> clients;

    public Hw7Server (int port)
    {
        if (port < PORT_MIN || port > PORT_MAX)    throw new IllegalArgumentException();
        this.port = port;
        clients = new LinkedList<>();

        try (ServerSocket servsocket = new ServerSocket (port))
        {
            System.out.println ("\nНачало сессии.");
            while (true)
            {
                System.out.println ("\tЖдём подклюение клиента.");
                Socket socket = servsocket.accept();
                new Hw7ClientHandler (this, socket);
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
    public boolean validateUser (Hw7ClientHandler client, boolean add)
    {
        //boolean boolValid = false;
        String name = (client != null) ? client.getClientName() : null;

        if (name != null && !name.isEmpty())
        {
            for (Hw7ClientHandler cl : clients)
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
    private boolean addClient (Hw7ClientHandler client)
    {
        boolean boolOk = clients.add (client);
        if (boolOk)
            System.out.println("\nServer: Клиент " + client.getClientName() + " добавлен.");
        return boolOk;
    }// addClient ()

//Удаляем клиента из списка подключенных клиентов.
    public void removeClient (Hw7ClientHandler client)
    {
        if (clients.remove (client))
            System.out.println("Server: Клиент " + client.getClientName() + " удалён.");

    }// removeClient ()

//Рассылаем указанное сообщение всем клиентам из нашего списка подключенных клиентов.
    public boolean broadcastMessage (String msg, Hw7ClientHandler from)
    {
        boolean boolSent = false;
        if (msg != null && !msg.isEmpty() && clients != null)
        {
            for (Hw7ClientHandler client : clients)
                boolSent = client.sendMessageToClient (from.getClientName() + ":\n\t" + msg);
        }
        return boolSent;
    }// broadcastMessage ()

//Пересылаем указанное сообщение автору и указанному клиенту. Сообщение получаем в формате: «адресат сообщение».
    public boolean sendPrivateMessage (String msg, Hw7ClientHandler from)
    {
        boolean boolSent = false;
        if (msg != null && !msg.isEmpty() && clients != null)
        {
            int index = msg.trim().indexOf(' ');
            if (index >= 0)
            {
                String name = msg.substring (0, index);
                for (Hw7ClientHandler client : clients)
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
            for (Hw7ClientHandler client : clients)
                client.onServerDown();
        }
    }// serverGettingDown ()

}// class Hw7Server
