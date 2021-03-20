package ru.geekbrains.march.chat.server;

public class ServerAppHw8
{
    public static final String
            CMD_CHAT_MSG = "/chat",
            CMD_PRIVATE_MSG = "/w",
            CMD_EXIT = "/exit",
            CMD_STAT = "/stat",
            CMD_WHOAMI = "/who_am_i",
            CMD_ONLINE = "/online",
            CMD_LOGIN = "/login",
            CMD_BADNICKNAME = "/login_bad",
            CMD_CHANGE_NICKNAME = "/change_nik",
            CMD_CLIENTS_LIST = "/clients_list",
            CMD_CLIENTS_LIST_CHANGED = "/clients_list_changed",
            SERVER_ADDRESS = "localhost",
            SERVER_IS_OFF = "\nСервер завершил работу.";

    public static final int
            SERVER_PORT = 8189;


    public static void main (String[] args)
    {
        new ServerHw8 (SERVER_PORT);
        System.out.print (SERVER_IS_OFF);
    }// main ()

}// class ServerAppHw8
