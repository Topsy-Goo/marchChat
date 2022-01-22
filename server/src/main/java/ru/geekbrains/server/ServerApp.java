package ru.geekbrains.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ServerApp {
    public static final boolean DEBUG = true; //< для отладки
    public static final String CMD_CHAT_MSG = "/chat";
    public static final String CMD_PRIVATE_MSG = "/w";
    public static final String CMD_EXIT = "/exit";
    public static final String CMD_CONNECTED = "/connected"; //< посылается клиенту из конструктора ClientHandler()
    public static final String CMD_LOGIN_READY = "/loginready";  //клиент принял от сервера ник и готов продолжить вход в чат
    public static final String CMD_LOGIN = "/login";
    public static final String CMD_BADLOGIN = "/badlogin";
    public static final String CMD_CHANGE_NICKNAME = "/change_nik";
    public static final String CMD_BADNICKNAME = "/login_bad";
    public static final String CMD_CLIENTS_LIST = "/clients_list";
    public static final String CMD_CLIENTS_LIST_CHANGED = "/clients_list_changed";
    public static final String APP_IS_OFF = "выход из приложения";
    public static final String SERVER_ADDRESS = "localhost";
    public static final String TABLE_NAME = "marchchat users";

    public static final String DATABASE_URL = "jdbc:sqlite:marchchat.db";  //< [protocol]:[subprotocol]:[db_name]
    public static final String CLASS_NAME   = "org.sqlite.JDBC"; //< Для SQLite: класс JDBC в папке External Libraries\org.sqlite
    //CLASS_NAME = "com.mysql.jdbc.Driver", < Для MySQL: класс Driver в папке External Libraries\com.mysql.jdbc
    //CLASS_NAME = "org.h2.Driver",  < Для H2 Database: класс Driver в папке External Libraries\org.h2
    //DATABASE_URL = "jdbc:mysql://localhost/marchchat?user=root&password=********************"

    public static final int SERVER_PORT = 8189;
    private static final Logger LOGGER = LogManager.getLogger(ServerApp.class);

    public static void main (String[] args) {
        LOGGER.fatal("---------------------------------------------------------------------------------");
        new Server(SERVER_PORT);
        LOGGER.info(APP_IS_OFF);
    }
}
