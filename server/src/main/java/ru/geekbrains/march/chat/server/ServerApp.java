package ru.geekbrains.march.chat.server;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ServerApp
{
    private static final Logger LOGGER = LogManager.getLogger (ServerApp.class);
    public static final boolean DEBUG = true; //< для отладки
    public static final String
            CLASS_NAME = "org.sqlite.JDBC",
                        // Для H2 Database - org.h2.Driver
                        // Для MySQL - com.mysql.jdbc.Driver
            TABLE_NAME = "marchchat users",
            DATABASE_URL = "jdbc:sqlite:marchchat.db",  // [protocol]:[subprotocol]:[name]
            CMD_CHAT_MSG = "/chat",
            CMD_PRIVATE_MSG = "/w",
            CMD_EXIT = "/exit",
            CMD_CONNECTED = "/connected", //< посылается клиенту из конструктора ClientHandler()
            CMD_LOGIN_READY = "/loginready",    //клиент принял от сервера ник и готов продолжить вход в чат
            //CMD_ONLINE = "/online",
            CMD_LOGIN = "/login",
            CMD_BADLOGIN = "/badlogin",
            CMD_CHANGE_NICKNAME = "/change_nik",
            CMD_BADNICKNAME = "/login_bad",
            CMD_CLIENTS_LIST = "/clients_list",
            CMD_CLIENTS_LIST_CHANGED = "/clients_list_changed",
            APP_IS_OFF = "выход из приложения",
            SERVER_ADDRESS = "localhost"
            ;
    public static final int SERVER_PORT = 8189;

    public static void main (String[] args)
    {
        LOGGER.fatal("---------------------------------------------------------------------------------");
        //new WaitNotifyApp().treadWaitNotifyTest();
        new Server (SERVER_PORT);
        LOGGER.info(APP_IS_OFF);
    }// main ()

/*  Я счёл нецелесообразным использовать пул потоков в классе Controller, поэтому даже не пробовал его там применять.

    На стороне сервера пул потоков я применил для назначения потоков ClientHandler'у при его создании. Целесообразность
    такого решения не могу оценить, поскольку не имею достаточно опыта в таких вопросах. Теоретически, отдельные потоки
    для каждого ClientHandler'a всяко лучше, чем один общий поток.

    В дополнение к вышеизложенному хочу заметить, что использование пулов для этих целей не решает все задачи. Так,
    например, Executors.newFixedThreadPool(N) не позволит увеличить количество потоков, если выделенных не хватило.
    А Executors.newCachedThreadPool(), как оказалось, и вовсе требует указывать таймаут бездействия потока, по
    истечении которого, поток закрывается.

    Наверное, лучшим решением былобы создание некой иерархии потоков, в которой потоки-диспетчеры слушали многие
    соединения и, в случае активности на каком-либо канале, передавали обработку входящих данных потокам-рабочим.
*/
}// class ServerApp
