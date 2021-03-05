package ru.geekbrains.march.chat.server;

import javafx.application.Platform;

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
    private static boolean appGettingOff = false;


    public static void main (String[] args) throws Exception
    {
            System.out.println ("\nНачало сессии. Ждём подклюение клиента.");

        try (ServerSocket servsocket = new ServerSocket (PORT_NUMBER);
             Socket socket = servsocket.accept();
             DataInputStream dis = new DataInputStream (socket.getInputStream());
             DataOutputStream dos = new DataOutputStream (socket.getOutputStream());)
        {
            System.out.printf("\tПодключение через порт %d установлено.\n", PORT_NUMBER);
            System.out.printf("\tServerSocket = %s\n\tSocket = %s\n" +
                              "\tDataInputStream = %s\n\tDataOutputStream = %s\n",
                              servsocket, socket, dis, dos);
            System.out.println("\tПолученные данные:");

            Thread t = new Thread (() -> consoleInputThread (dos));
            t.start();

            int msgCounter = 0;
            String s;

            while (!appGettingOff)
            {
                if (dis.available() > 0) //< ждём появления данных, чтобы избежать исключения в ситуации,
                {                        //  когда мы послали клиенту /exit, и он завершил работу
                    switch (s = dis.readUTF())
                    {
                        case msgEXIT:    appGettingOff = true;
                            break;
                        case msgSTAT:    dos.writeUTF(String.format ("Количество сообщений - %d", msgCounter));
                            break;
                        default:     msgCounter ++;
                            System.out.println(s);
                    }
                }
            }
            t.join();
        }
        catch (IOException ioe) { ioe.printStackTrace(); }

        System.out.println ("Сессия завершилась.");
    }// main ()


// Поток для считывания консольного ввода.
    private static void consoleInputThread (DataOutputStream dos)
    {
        Scanner sc = new Scanner (System.in);
        String s;

        while (!appGettingOff)
        {
            try
            {
                if (System.in.available() > 0) //< проверяем, есть ли что в стриме
                {
                    s = sc.next();  //< эта строка блокирует выполнение потока, если в стриме ничего нет (будет ждать данные).

                    if (s != null && !s.isEmpty())
                    {
                        try
                        {
                            dos.writeUTF (s);
                        }
                        catch (IOException e)   {   e.printStackTrace();   }

                        if (s.equals (msgEXIT))
                            appGettingOff = true;
                    }
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        sc.close();
        System.out.println ("\n(поток t завершился)"); //< для отладки
    }// consoleInputThread ()


}// class HWChatServer
