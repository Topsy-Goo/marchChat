package ru.geekbrains.march.chat.server;


public class ServerApp9
{
    public static final String
            CMD_CHAT_MSG = "/chat",
            CMD_PRIVATE_MSG = "/w",
            CMD_EXIT = "/exit",
            CMD_ONLINE = "/online",
            CMD_LOGIN = "/login",
            CMD_BADLOGIN = "/badlogin",
            CMD_CHANGE_NICKNAME = "/change_nik",
            CMD_BADNICKNAME = "/login_bad",
            CMD_CLIENTS_LIST = "/clients_list",
            CMD_CLIENTS_LIST_CHANGED = "/clients_list_changed",
            SERVER_ADDRESS = "localhost",
            SERVER_IS_OFF = "\nСервер завершил работу.";

    public static final int SERVER_PORT = 8189;

    public static void main (String[] args)
    {
        new Server (SERVER_PORT);
        System.out.print (SERVER_IS_OFF);
    }
}
