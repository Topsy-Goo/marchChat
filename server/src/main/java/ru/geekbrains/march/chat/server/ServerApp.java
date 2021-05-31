package ru.geekbrains.march.chat.server;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ServerApp
{
    private static final Logger LOGGER = LogManager.getLogger (ServerApp.class);
    public static final boolean DEBUG = true; //< для отладки
    public static final String
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
            SERVER_ADDRESS = "localhost",
            TABLE_NAME = "marchchat users",

    //настройки для работы с разными типами БД

        //указание зависимостей
            //(Выполняется в pom.xml (суб)проекта.)

        //тип БД:
            //SQLITE = "SQLite",
            //MYSQL = "MySQL",
            //DATABASE_TYPE = MYSQL,
            //DATABASE_TYPE = SQLITE,

        //подключение драйвера типа БД:
            //CLASS_NAME = "com.mysql.jdbc.Driver", < Для MySQL: класс Driver в папке External Libraries\com.mysql.jdbc
            CLASS_NAME = "org.sqlite.JDBC", //< Для SQLite: класс JDBC в папке External Libraries\org.sqlite
            //CLASS_NAME = "org.h2.Driver",  < Для H2 Database: класс Driver в папке External Libraries\org.h2

        //указание пути к БД:
            //DATABASE_URL = "jdbc:mysql://localhost/marchchat?user=root&password=********************"
            DATABASE_URL = "jdbc:sqlite:marchchat.db"  //< [protocol]:[subprotocol]:[name]
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
