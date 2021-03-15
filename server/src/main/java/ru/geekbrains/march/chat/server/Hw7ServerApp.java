package ru.geekbrains.march.chat.server;

public class Hw7ServerApp
{
    public static final String
            msgEXIT = "/exit",
            msgSTAT = "/stat",
            msgWHOAMI = "/who_am_i",
            msgONLINE = "/online",
            msgLOGIN = "/login",
            loginPREFIX = msgLOGIN + ' ',
            privatePREFIX = "/w ",
            SERVER_ADDRESS = "localhost";

    public static final int
            SERVER_PORT = 8189,
            SLEEP_INTERVAL = 250;


    public static void main (String[] args)
    {
        new Hw7Server (SERVER_PORT);
    // завершение работы с клиентом
        System.out.println ("Сервер завершил работу.");

    }// main ()

}// class Hw7ServerApp
