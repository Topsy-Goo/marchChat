package ru.geekbrains.march.chat.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerApp
{

    public static void main (String[] args) throws Exception
    {
    // создали сокет на порте 8189 (нужно использовать любой свободный порт). Если сокет занят,
    // то получим исключение, но, скорее всего, порт 8189 будет свободен.
        try (ServerSocket servsocket = new ServerSocket(8189))
        {
            System.out.println ("\nНачало сессии. Ждём подклюение клиента.");
    // ожидаем подключений (бесконечно, если подключений так и не будет). Если подключение придёт, то в
    // socket окажется подключение к клиенту (клиент должен знать, что мы его ждём на порте 8189).
            Socket socket = servsocket.accept();

    // цикл чтения байтов из входного потока (закоментируем этот фрагмент, чтобы он не мешал воспользоваться
    // некоторыми усовершенствованиями, которые находястя в следующем за ним фрагменте)
    //        int x;
    //        while ((x = socket.getInputStream().read()) != -1)
    //            System.out.print ((char)x);
    //        /* На выходе мы получаем исключение, т.к. client завершился первым. В общем, это нормально. */

            System.out.print ("\tПодключение через порт 8189 установлено.\n\tПолученные байты:\n");

    // (необязательный шаг) оборачиваем потоки ввода и вывода в более удобные дата-потоки
    // (это позволит нам, например, обмен байтами заменить на обмен строками)
            DataInputStream dis = new DataInputStream (socket.getInputStream());
            DataOutputStream dos = new DataOutputStream (socket.getOutputStream());
            String s;
            while ((s = dis.readUTF()) != null)
            {
                System.out.println (s); //< эксперимент
                if (s.equals("exit")) //< эксперимент
                {
                    dos.writeUTF (s);
                    break;
                }
                dos.writeUTF ("ECHO: "+s); //< эксперимент
            }
            /* Теперь не вылетаем с исключением, а просто виснем в цикле, т.к. он бесконечный. */

        }
        catch (IOException ioe) { ioe.printStackTrace(); }

    // завершение работы с клиентом
        System.out.println ("Сессия завершилась.");

    // закрываем ненужный больше сокет
    //    servsocket.close();
    }// main ()


}// class ServerApp
