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
    private static boolean appGettingOff = false; //< флаг завершения приложения


    public static void main (String[] args) throws Exception
    {
            System.out.println ("\nНачало сессии. Ждём подклюение клиента.");

        try (ServerSocket servsocket = new ServerSocket (PORT_NUMBER);
             Socket socket = servsocket.accept();
             DataInputStream dis = new DataInputStream (socket.getInputStream());
             DataOutputStream dos = new DataOutputStream (socket.getOutputStream());)
        {
            System.out.printf("\tПодключение через порт %d установлено.\n", PORT_NUMBER);
            System.out.printf("\tServerSocket = %s\n\tSocket = %s\n", servsocket, socket);
            System.out.println("\tПолученные данные:");

            Thread threadConsoleInput = new Thread (() -> consoleInputThread (dos));
            threadConsoleInput.start();

            int msgCounter = 0;
            String s;

            while (!appGettingOff)
            {
/*  В ожидании данных dis.readUTF() «подвещивает» поток, не позволяя обработать изменение appGettingOff.
    Избежать этого можно при пом. dis.available(), но тогда возрастает нагрузка на ЦП, которую можно
    уменьшить введением небольших пауз -- sleep(250).

    Другой вариант больше похож на шаманство, но тоже делает то, что нужно: при завершении сеанса клинет и
    сервер обмениваются сообщениями /exit до тех пор, пока их потоки в состоянии это делать. В этом случае
    и ЦП не нагружен, и исключения не бросаются. Но приходится оттачивать систему обмена сообщениями.

    (Решение с применением available() + sleep(), больше подходит для потока threadConsoleInput, т.к. в
    сети будет тормозить передачу данных, но для чата это не критично.)
*/              if (dis.available() > 0)
                {
                    switch (s = dis.readUTF())
                    {
                        case msgEXIT:    appGettingOff = true;
                            //клиент и сервер обмениваются сообщениями /exit (возможно, не один раз)
                            //dos.writeUTF(msgEXIT); //< это решает проблему с исключением в потоке клиента,
                            // читающего наши сообщения
                            break;
                        case msgSTAT:    dos.writeUTF(String.format ("Количество сообщений - %d", msgCounter));
                            break;
                        default:     msgCounter ++;
                            System.out.println(s);
                    }
                }
                else Thread.sleep(250); //< уменьшаем нагрузку на ЦП, вызванную применением available() (хорошо помогает).
            }
            threadConsoleInput.join(1000); //< К сожалению, закрытия потока threadConsoleInput мы так не дождёмся, но
            // это даст возможность клиенту писать в стрим ответные сообщения /exit без вызова исключений.
            //  Если вместо обмена /exit-сообщениями используется пара available() + sleep(), то пауза 1000мс
            //  в этом вызове не нужна.
        }
        catch (IOException ioe) { ioe.printStackTrace(); }

        System.out.println ("Сессия завершилась.");
        //System.exit(0); //< 7 бед -- 1 ответ (любой упрямый поток затыкается на раз)
    }// main ()


// Поток для считывания консольного ввода.
// Если не использовать пару sleep()+available(), то этот поток завершится сам только в случае, когда мы в
// консоли наберём /exit или в main() вызовем System.exit(0).
    private static void consoleInputThread (DataOutputStream dos)
    {
        Scanner sc = new Scanner (System.in);
        String s;
        while (!appGettingOff)
        {
            try
            {
                if (System.in.available() > 0) //< проверяет, есть ли что в стриме. Цена решения -- нагрузка на ЦП.
                {
                    s = sc.nextLine();  //< эта строка блокирует выполнение потока, если в стриме пусто (ждёт данные).

                    if (s != null && !s.isEmpty())
                    {
                        dos.writeUTF (s);

                        if (s.equalsIgnoreCase (msgEXIT))
                            appGettingOff = true;
                    }
                }
                else Thread.sleep(250); //< уменьшаем нагрузку на ЦП, вызванную применением available() (хорошо помогает).
            }
            catch (IOException e){e.printStackTrace();} //< для System.in.available() и dos.writeUTF()
            catch (InterruptedException e){System.err.println("Thread.sleep() поймал эксепшн.");}
        }
        sc.close();
        System.out.println ("\n(consoleInputThread() завершилась)"); //< для отладки
    }// consoleInputThread ()


}// class HWChatServer
