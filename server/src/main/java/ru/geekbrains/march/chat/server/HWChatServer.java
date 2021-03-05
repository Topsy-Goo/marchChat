package ru.geekbrains.march.chat.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

public class HWChatServer
{
    public static final String msgEXIT = "/exit",
                                msgSTAT = "/stat";

    public static final int PORT_NUMBER = 18189;


    public static void main (String[] args) throws Exception
    {
        try (ServerSocket servsocket = new ServerSocket (PORT_NUMBER))
        {
            System.out.println ("\nНачало сессии.");
            Socket socket = servsocket.accept();

            System.out.printf("\tПодключение через порт %d установлено.\n\tПолученные байты:\n", PORT_NUMBER);
            DataInputStream dis = new DataInputStream (socket.getInputStream());
            DataOutputStream dos = new DataOutputStream (socket.getOutputStream());

            new Thread (() -> consoleInputThread (dos)).start();

            int msgCounter = 0;
            String s;
            boolean exitApplication = false;

            while (!exitApplication)
            {
                switch (s = dis.readUTF())
                {
                    case msgEXIT:   exitApplication = true;
                        break;
                    case msgSTAT:   dos.writeUTF (String.format ("Количество сообщений - %d", msgCounter));
                        break;
                    default:        msgCounter ++;
                        System.out.println (s);
                        //dos.writeUTF ("ECHO: " + s);
                }
            }
        }
        catch (IOException ioe) { ioe.printStackTrace(); }

        appExit();
    }// main ()


// Поток для считывания консольного ввода.
    private static void consoleInputThread (DataOutputStream dos)
    {
        Scanner sc = new Scanner (System.in);
        String s;

        while (true)
        {
            s = sc.next();
            if (s != null && !s.isEmpty())
            {
                try
                {
                    dos.writeUTF (s);
                }
                catch (IOException e)   {   e.printStackTrace();   }

                if (s.equals (msgEXIT))
                    break;
            }
        }
        sc.close();
        appExit();

    }// consoleInputThread ()


    private static void appExit ()
    {
        System.out.println ("Сессия завершилась.");
        System.exit(0);
    }

}// class HWChatServer
