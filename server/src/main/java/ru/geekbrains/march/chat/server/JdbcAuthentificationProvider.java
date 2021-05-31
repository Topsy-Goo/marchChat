package ru.geekbrains.march.chat.server;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;

import static ru.geekbrains.march.chat.server.ServerApp.TABLE_NAME;

public class JdbcAuthentificationProvider implements Authentificator
{
    public static final String
            FLD_LOGIN = "login",
            FLD_PASS = "password",
            FLD_NICK = "nickname",
            FRMT_DROP_TABLE = "DROP TABLE IF EXISTS [%s];",
            FRMT_STMENT_SEL_1BY2 = "SELECT %s FROM [%s] WHERE %s = '%s' AND %s = '%s';",
            FRMT_STMENT_SEL_1BY1 = "SELECT %s FROM [%s] WHERE %s = '%s';",
            FRMT_PREPSTMENT_INS_3FLD = "INSERT INTO [%s] (%s, %s, %s) VALUES (?, ?, ?);",
            FRMT_PREPSTMENT_DEL_BY1  = "DELETE FROM [%s] WHERE %s = ?;",
            FRMT_PREPSTMENT_UPD_SET1BY1 = "UPDATE [%s] SET %s = ? WHERE %s = ?;"
            ;
    private Statement statement;
    private PreparedStatement psUpdate1By1, psInsert3Fld, psDeleteBy1;
    private DbConnection dbConnection;
    private static final Logger LOGGER = LogManager.getLogger (JdbcAuthentificationProvider.class);

    public JdbcAuthentificationProvider ()
    {
        LOGGER.info("JdbcAuthentificationProvider() начало");
        dbConnection = new DbConnection();
        statement = dbConnection.getStatement();
        Connection connection = dbConnection.getConnection();

        //createDbTable (connection); < этот вызов помогает проверить, поддерживается ли используемый здесь синтаксис
        try
        {   psUpdate1By1 = connection.prepareStatement(
                        String.format(FRMT_PREPSTMENT_UPD_SET1BY1, TABLE_NAME, FLD_NICK, FLD_NICK));
            psInsert3Fld = connection.prepareStatement(
                        String.format(FRMT_PREPSTMENT_INS_3FLD, TABLE_NAME, FLD_LOGIN, FLD_PASS, FLD_NICK));
            psDeleteBy1 = connection.prepareStatement(
                        String.format(FRMT_PREPSTMENT_DEL_BY1, TABLE_NAME, FLD_LOGIN));
        }
        catch (SQLException sqle)
        {   sqle.printStackTrace ();
            close();
            throw new RuntimeException("\nCannot create object JdbcAuthentificationProvider.");
        }
        finally {LOGGER.info("JdbcAuthentificationProvider():конец");}

    }// JdbcAuthentificationProvider ()


    public Authentificator close () //< сейчас Authentificator extends Closable
    {
        if (dbConnection != null)  dbConnection = dbConnection.close(); //< одной строчкой закрываем и приравниваем нулю.
        statement = null; //< закрывается на стороне dbConnection
        try
        {   if (psUpdate1By1 != null)  psUpdate1By1.close();
            if (psInsert3Fld != null)  psInsert3Fld.close();
            if (psDeleteBy1 != null)   psDeleteBy1.close();
        }
        catch (SQLException sqle)  {  sqle.printStackTrace();  }
        finally
        {   psUpdate1By1 = null;
            psInsert3Fld = null;
            psDeleteBy1 = null;
        }
        return null;
    }// close ()

//Возвращаем ник пользователя, которому в БД соответствуют введённые им логин и пароль.
    @Override public String authenticate (String login, String password)
    {
        String nickName = null;
        if (Server.validateStrings (login, password))
        {
            try (ResultSet rs = statement.executeQuery(String.format(FRMT_STMENT_SEL_1BY2,
                                                                     FLD_NICK, TABLE_NAME, FLD_LOGIN, login, FLD_PASS, password));)
            {   if (rs.next())
                    nickName = rs.getString (FLD_NICK);
            }
            catch (SQLException throwables)
            {   throwables.printStackTrace();
                throw new RuntimeException();
            }
        }
        return nickName;
    }// authenticate ()


// В БД изменяем поле nickname == prevName на newName. (У поля nickname есть атрибут UNIQUE.)
    @Override public String rename (String prevName, String newName)
    {
        String result = null;   //< индикатор ошибки
        if (Server.validateStrings (prevName, newName))
        {
            LOGGER.debug(String.format("переименование %s >> %s", prevName, newName));
            try
            {
            //Вносим имя newName в БД вместо prevName (БД настроена так, что если такое имя уже используется, то
            // она не вернёт ошибку, но и изменять ничего не станет):
                psUpdate1By1.setString (1, newName);
                psUpdate1By1.setString (2, prevName);
                if (psUpdate1By1.executeUpdate() > 0)
            // Теперь проверяем, сделана ли в БД соотв. запись. (Соотв. графа в БД настроена на уникальные значения.)
            //  Кроме того, этот метод должен вызываться из синхронизированного контекста.
                     result = isNicknamePresent(newName);
                else result = "";
            }
            catch (SQLException e)
            {   LOGGER.throwing(Level.ERROR, e);//throwables.printStackTrace();
                //throw new RuntimeException();
            }
        } else LOGGER.error("переименование : битые параметры.");
        return result;
    }// rename ()


//(Вспомогательная.) Проверяет наличие ника в базе.
    String isNicknamePresent (String nickname)
    {
        String result = null;
        if (Server.validateStrings (nickname))
            try (ResultSet rs = statement.executeQuery(String.format(FRMT_STMENT_SEL_1BY1,
                                                                     FLD_NICK, TABLE_NAME, FLD_NICK, nickname));)
            {   if (rs.next())
                     result = rs.getString (FLD_NICK); //или rs.getString (№);
                else result = ""; //< индикатор того, что чтение не состоялось
                LOGGER.debug("ответ базы : "+ result);
            }
            catch (SQLException e) { LOGGER.throwing(Level.ERROR, e);/*.printStackTrace();*/ }
        return result;
    }// isNicknamePresent ()


// Добавляем данные пользователя в БД. (Сейчас он не используется.)
    @Override public boolean add (String login, String password, String nickname)
    {
        boolean boolOk = false;
        if (Server.validateStrings (login, password, nickname))
        {
            try
            {   psInsert3Fld.setString(1, login);
                psInsert3Fld.setString(2, password);
                psInsert3Fld.setString(3, nickname);
                psInsert3Fld.executeUpdate();
                //void psInsert3Fld.addBatch();
                //int[] psInsert.executeBatch();
            }
            catch (SQLException throwables)
            {   System.out.print("\nERROR @ add(): User data creation failed.");
                throwables.printStackTrace();
                throw new RuntimeException();
            }
        }
        return boolOk;
    }// add ()


// Удаляем пользователя из БД по нику. (Этот метод сейчас не используется.)
    @Override public void remove (String login)
    {
        if (Server.validateStrings (login))
        try
        {   psDeleteBy1.setString(1, login);
            psDeleteBy1.executeUpdate();
        }
        catch (SQLException throwables)
        {   System.out.print("\nERROR @ remove(): User data deletion failed.");
            throwables.printStackTrace();
            throw new RuntimeException();
        }
    }// remove ()


// Удаляем таблицу с базой данных. (Этот метод сейчас не используется.)
    private void dropDbTable ()
    {
        try
        {   statement.executeUpdate(String.format(FRMT_DROP_TABLE, TABLE_NAME));
        }
        catch (SQLException throwables)
        {   System.out.print("\nERROR @ dropDbTable(): Data base deletion failed.");
            throwables.printStackTrace();
            throw new RuntimeException();
        }
    }// dropDbTable ()  */

// Создаём SQL-таблицу, если она ещё не создана.
    static final String FORMAT_CREATE_TABLE_IFEXISTS_SQLITE =
            "CREATE TABLE IF NOT EXISTS [%s] (" +
            "%s STRING NOT NULL UNIQUE ON CONFLICT IGNORE PRIMARY KEY, " +
            "%s STRING NOT NULL, " +
            "%s STRING NOT NULL UNIQUE ON CONFLICT IGNORE);"
            ;
//  CREATE SCHEMA `marchchat`;
    static final String FORMAT_CREATE_TABLE_IFEXISTS_MYSQL =    //< попробовали, выходит ли на связь MySQL: выходит. ура.
            "CREATE TABLE `marchchat`.`marchchat users` (" +
            "`login` VARCHAR(45) NOT NULL," +
            "`password` VARCHAR(45) NOT NULL," +
            "`nickname` VARCHAR(45) NOT NULL," +
            "PRIMARY KEY (`login`)," +
            "UNIQUE INDEX `nickname_UNIQUE` (`nickname` ASC) VISIBLE," +
            "UNIQUE INDEX `login_UNIQUE` (`login` ASC) VISIBLE);"
            ;
    private int createDbTable (Connection connection)
    {
        int result = -1;
        try (Statement stnt = connection.createStatement())
        {
            result = stnt.executeUpdate (String.format(FORMAT_CREATE_TABLE_IFEXISTS_SQLITE,
                                            TABLE_NAME, FLD_LOGIN, FLD_PASS, FLD_NICK, FLD_LOGIN));
        }
        catch (SQLException throwables)
        {   throwables.printStackTrace();
            throw new RuntimeException();
        }
        return result;
    }// createDbTable ()


    public void print (String s) {System.out.print(s);}

}// class JdbcAuthentificationProvider
