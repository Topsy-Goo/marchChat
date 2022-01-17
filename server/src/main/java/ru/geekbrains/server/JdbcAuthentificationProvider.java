package ru.geekbrains.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.geekbrains.server.errorhandlers.UserNotFoundException;

import java.sql.*;

import static ru.geekbrains.server.ServerApp.TABLE_NAME;

public class JdbcAuthentificationProvider implements Authentificator {
    public static final String FILD_LOGIN = "login";
    public static final String FILD_PASS  = "password";
    public static final String FILD_NICK  = "nickname";
    public static final String FRMT_DROP_TABLE = "DROP TABLE IF EXISTS [%s];";
    public static final String FRMT_STMENT_SEL_1BY2 = "SELECT %s FROM [%s] WHERE %s = '%s' AND %s = '%s';";
    public static final String FRMT_STMENT_SEL_1BY1 = "SELECT %s FROM [%s] WHERE %s = '%s';";
    public static final String FRMT_PREPSTMENT_INS_3FLD = "INSERT INTO [%s] (%s, %s, %s) VALUES (?, ?, ?);";
    public static final String FRMT_PREPSTMENT_DEL_BY1 = "DELETE FROM [%s] WHERE %s = ?;";
    public static final String FRMT_PREPSTMENT_UPD_SET1BY1 = "UPDATE [%s] SET %s = ? WHERE %s = ?;";
    // Создаём SQL-таблицу, если она ещё не создана.
    private static final String FORMAT_CREATE_TABLE_IFEXISTS_SQLITE =
            "CREATE TABLE IF NOT EXISTS [%s] (" +
            "%s STRING NOT NULL UNIQUE ON CONFLICT IGNORE PRIMARY KEY, " +
            "%s STRING NOT NULL, " + "%s STRING NOT NULL UNIQUE ON CONFLICT IGNORE);";
    //  CREATE SCHEMA `marchchat`;
    private static final String FORMAT_CREATE_TABLE_IFEXISTS_MYSQL =    //< попробовали, выходит ли на связь MySQL: выходит. ура.
            "CREATE TABLE `marchchat`.`marchchat users` (" +
            "`login` VARCHAR(45) NOT NULL," + "`password` VARCHAR(45) NOT NULL," +
            "`nickname` VARCHAR(45) NOT NULL," + "PRIMARY KEY (`login`)," +
            "UNIQUE INDEX `nickname_UNIQUE` (`nickname` ASC) VISIBLE," +
            "UNIQUE INDEX `login_UNIQUE` (`login` ASC) VISIBLE);";
    private static final Logger LOGGER = LogManager.getLogger(JdbcAuthentificationProvider.class);
    private Statement statement;
    private PreparedStatement psUpdate1By1, psInsert3Fld, psDeleteBy1;
    private DbConnection dbConnection;

    public JdbcAuthentificationProvider () throws SQLException {
        LOGGER.info("JdbcAuthentificationProvider() начало");
        dbConnection = new DbConnection();
        statement = dbConnection.getStatement();
        Connection connection = dbConnection.getConnection();

        //createDbTable (connection); < этот вызов помогает проверить, поддерживается ли используемый здесь синтаксис
        try {
            psUpdate1By1 = connection.prepareStatement(
                        String.format(FRMT_PREPSTMENT_UPD_SET1BY1, TABLE_NAME, FILD_NICK, FILD_NICK));
            psInsert3Fld = connection.prepareStatement(
                        String.format(FRMT_PREPSTMENT_INS_3FLD, TABLE_NAME, FILD_LOGIN, FILD_PASS, FILD_NICK));
            psDeleteBy1 = connection.prepareStatement(
                        String.format(FRMT_PREPSTMENT_DEL_BY1, TABLE_NAME, FILD_LOGIN));
        }
        catch (SQLException e) {
            close();
            throw e;
        }
        finally {LOGGER.info("JdbcAuthentificationProvider():конец");}

    }

    public Authentificator close () {
        if (dbConnection != null) {
            dbConnection = dbConnection.close(); //< одной строчкой закрываем и приравниваем нулю.
        }
        statement = null; //< закрывается на стороне dbConnection
        dbConnection = null;
        try {
            if (psUpdate1By1 != null) { psUpdate1By1.close(); }
            if (psInsert3Fld != null) { psInsert3Fld.close(); }
            if (psDeleteBy1 != null) { psDeleteBy1.close(); }
        }
        catch (SQLException e) { e.printStackTrace(); }
        finally {
            psUpdate1By1 = null;
            psInsert3Fld = null;
            psDeleteBy1 = null;
        }
        return null;
    }

/** Проверяем, зарегистрирован ли пользователь. Все недоразумения, связанные с авторизацией, возвращаются в виде исключений.
    @return ник пользователя, которому в БД соответствуют введённые им логин и пароль.
    @throws UserNotFoundException логин и/или пароль не подошли;
    @throws SQLException какая-то ошибка БД (пробрасывается).  */
    @Override public String authenticate (String login, String password) throws SQLException {

        String nickName = null;
        if (Server.validateStrings (login, password)) {

            String param = String.format (FRMT_STMENT_SEL_1BY2,
                                          FILD_NICK, TABLE_NAME, FILD_LOGIN, login, FILD_PASS, password);

            try (ResultSet rs = statement.executeQuery (param)) {
                if (rs.next())
                    nickName = rs.getString (FILD_NICK);
            }
            catch (SQLException e) {
                e.printStackTrace();
                throw e;
            }
        }
        if (nickName == null)
            throw new UserNotFoundException();
        return nickName;
    }

/** В БД изменяем поле nickname == prevName на newName. (У поля nickname есть атрибут UNIQUE.). В случае
    кл. недоразумений метод бросает исключение.
    @return новое имя пользователя или NULL.
    @throws SQLException если произошла ошибка при работе с базой.  */
    @Override public String rename (String prevName, String newName) throws SQLException {

        String result = null;   //< индикатор ошибки
        if (Server.validateStrings (prevName, newName)) {

            LOGGER.debug(String.format ("переименование %s >> %s", prevName, newName));
            try {
                //Вносим имя newName в БД вместо prevName (БД настроена так, что если такое имя уже
                // используется, то она не вернёт ошибку, но и изменять ничего не станет):
                psUpdate1By1.setString (1, newName);
                psUpdate1By1.setString (2, prevName);
                if (psUpdate1By1.executeUpdate() == 1 && isNicknamePresent (newName))
                // Теперь проверяем, сделана ли в БД соотв. запись. (Соотв. графа в БД настроена на
                // уникальные значения. Кроме того, этот метод должен вызываться из синхронизированного
                // контекста.
                    result = newName;
            }
            catch (SQLException e) {
                e.printStackTrace();
                throw e;
            }
        }
        else LOGGER.error ("переименование : битые параметры.");
        return result;
    }

    //(Вспомогательная.) Проверяет наличие ника в базе.
    private boolean isNicknamePresent (String nickname) throws SQLException {

        String dbValue = null;
        if (Server.validateStrings (nickname)) {

            String param = String.format (FRMT_STMENT_SEL_1BY1, FILD_NICK, TABLE_NAME, FILD_NICK, nickname);

            try (ResultSet rs = statement.executeQuery (param)) {
                if (rs.next())
                    dbValue = rs.getString (FILD_NICK); //или rs.getString (№);

                LOGGER.debug("ответ базы : " + dbValue);
            }
        }
        return Server.validateStrings (dbValue);
    }

    // Добавляем данные пользователя в БД. (Сейчас он не используется.)
    @Override public boolean add (String login, String password, String nickname) {
        boolean boolOk = false;
        if (Server.validateStrings (login, password, nickname)) {
            try {
                psInsert3Fld.setString(1, login);
                psInsert3Fld.setString(2, password);
                psInsert3Fld.setString(3, nickname);
                psInsert3Fld.executeUpdate();
                //void psInsert3Fld.addBatch();
                //int[] psInsert.executeBatch();
            }
            catch (SQLException throwables) {
                System.out.print ("\nERROR @ add(): User data creation failed.");
                throwables.printStackTrace();
                throw new RuntimeException();
            }
        }
        return boolOk;
    }

    // Удаляем пользователя из БД по нику. (Этот метод сейчас не используется.)
    @Override public void remove (String login) {
        if (Server.validateStrings(login)) {
            try {
                psDeleteBy1.setString(1, login);
                psDeleteBy1.executeUpdate();
            }
            catch (SQLException throwables) {
                System.out.print ("\nERROR @ remove(): User data deletion failed.");
                throwables.printStackTrace();
                throw new RuntimeException();
            }
        }
    }

    // Удаляем таблицу с базой данных. (Этот метод сейчас не используется.)
    private void dropDbTable () {
        try {
            statement.executeUpdate (String.format(FRMT_DROP_TABLE, TABLE_NAME));
        }
        catch (SQLException throwables) {
            System.out.print("\nERROR @ dropDbTable(): Data base deletion failed.");
            throwables.printStackTrace();
            throw new RuntimeException();
        }
    }

    private int createDbTable (Connection connection) {
        int result = -1;
        try (Statement stnt = connection.createStatement()) {
            result = stnt.executeUpdate (
                String.format (FORMAT_CREATE_TABLE_IFEXISTS_SQLITE,
                               TABLE_NAME, FILD_LOGIN, FILD_PASS, FILD_NICK, FILD_LOGIN));
        }
        catch (SQLException throwables) {
            throwables.printStackTrace();
            throw new RuntimeException();
        }
        return result;
    }

    public void print (String s) {System.out.print(s);}
}
