package ru.geekbrains.march.chat.server;


public class ServerApp
{
    public static final String
            CMD_EXIT = "/exit",
            CMD_STAT = "/stat",
            CMD_WHOAMI = "/who_am_i",
            CMD_ONLINE = "/online",
            CMD_LOGIN = "/login",
            LOGIN_PREFIX = CMD_LOGIN + ' ',
            PRIVATE_PREFIX = "/w ",
            SERVER_ADDRESS = "localhost";

    public static final int
            SERVER_PORT = 8189,
            SLEEP_INTERVAL = 250;


    public static void main (String[] args)
    {
        new Server(SERVER_PORT);
    // завершение работы с клиентом
        System.out.println ("Сервер завершил работу.");

    }// main ()


}// class ServerApp
