package ru.geekbrains.march.chat.server;

import java.security.InvalidParameterException;
import java.sql.*;

public class JdbcAuthentificationProvider implements Authentificator
{
    private static final String
            CLASS_NAME = "org.sqlite.JDBC"
            ;
    public static boolean THROW_EXCEPTION = true, SOFT_MODE = !THROW_EXCEPTION;
    private static Connection connection;

    public JdbcAuthentificationProvider ()
    {
        try
        {   Class.forName (CLASS_NAME);
            if (createDbTable() > 0 && !tmpCreateUserSqlDB())
                throw new RuntimeException ("ERROR @ JdbcAuthentificationProvider(): DB creation failed.");
        }
        catch (ClassNotFoundException e)
        {
            System.out.printf("\nERROR @ JdbcAuthentificationProvider(): Class «%s» not found.", CLASS_NAME);
            e.printStackTrace();
            throw new RuntimeException();
        }
    }// JdbcAuthentificationProvider ()


    private static void connect ()
    {
        try
        {   connection = DriverManager.getConnection ("jdbc:sqlite:marchchat.db");
        }
        catch (SQLException throwables)  {  throwables.printStackTrace();  }
    }// connect ()

    private static void disconnect ()
    {
        try
        { if (connection != null)  connection.close(); //< SQLException (см. шаг 1)
        }
        catch (SQLException throwables)
        {   connection = null;
            throwables.printStackTrace();
        }
    }// disconnect ()


// Создаём SQL-таблицу, если она ещё не создана.
    private static final String
            TABLE_NAME = "marchchat users",
            CREATE_TABLE_IFEXISTS_FORMAT =
                "CREATE TABLE IF NOT EXISTS [%s] (login STRING NOT NULL, password STRING NOT NULL, " +
                "nickname STRING NOT NULL UNIQUE ON CONFLICT IGNORE PRIMARY KEY);"
            ;
    private int createDbTable ()
    {
        int result = -1;
        connect();
        try (Statement stnt = connection.createStatement())
        {
            result = stnt.executeUpdate (String.format (CREATE_TABLE_IFEXISTS_FORMAT, TABLE_NAME));
        }
        catch (SQLException throwables)  {  throwables.printStackTrace();  }
        finally   {  disconnect();  }
        return result;
    }// createDbTable ()


// Создаём в БД таблицу с логинами, паролями и именами пользователей.
    private static final String
            FORMAT_PREPARE_STATEMENT_INSERT = "INSERT INTO [%s] (login, password, nickname) VALUES (?, ?, ?);"
            ;
    private static final String[][] usersdata =
        {{"1", "11", "u1111"}, {"2", "22", "u2222"}, {"3", "33", "u3333"}, {"4", "44", "u4444"}}
        ;
    private boolean tmpCreateUserSqlDB ()
    {
        boolean boolOk = false;
        connect();
        try (Statement stnt = connection.createStatement())
        {
            PreparedStatement psInsert = connection.prepareStatement(
                                String.format (FORMAT_PREPARE_STATEMENT_INSERT, TABLE_NAME));

            connection.setAutoCommit (false);
            for (String[] as : usersdata)
            {
                for (int i=1, n=as.length;   i <= n;   i++)
                    psInsert.setString (i, as[i-1]);
                psInsert.executeUpdate();
            }
            connection.setAutoCommit (true);
            boolOk = true;//
        }
        catch (SQLException throwables)  {  throwables.printStackTrace();  }
        finally  {  disconnect();  }
        return boolOk;
    }// tmpCreateUserSqlDB ()


//Возвращаем ник пользователя, которому в БД соответствуют введённые им логин и пароль.
    private static final String
            FORMAT_STATEMENT_AUTHENTIFICATION = "SELECT nickname FROM [%s] WHERE login = '%s' AND password = '%s';"
            ;
    @Override public String authenticate (String login, String password)
    {
        String nickname = null;
        if (validateStrings (SOFT_MODE, login, password))
        {
            connect();
            try (Statement stnt = connection.createStatement();
                 ResultSet rs = stnt.executeQuery(String.format(FORMAT_STATEMENT_AUTHENTIFICATION, TABLE_NAME, login, password));)
            {
                if (rs.next())
                    nickname = rs.getString ("nickname");
            }
            catch (SQLException throwables)  {  throwables.printStackTrace();  }
            finally  {  disconnect();  }
        }
        return nickname;
    }// authenticate ()


// В БД изменяем поле nickname == prevName на newName. (У поля nickname есть атрибут UNIQUE.)
    private static final String
            FORMAT_PREPARE_STATEMENT_UPDATE = "UPDATE [%s] SET nickname = ? WHERE nickname = ?;",
            FORMAT_STATEMENT_CHECK = "SELECT nickname FROM [%s] WHERE nickname = '%s';"
            ;
    @Override public String rename (String prevName, String newName)
    {
        String result = null;   //< индикатор неустранимой ошибки
        if (validateStrings (SOFT_MODE, prevName, newName))
        {
            connect();
            try (Statement stnt = connection.createStatement();
                 PreparedStatement psUpdate = connection.prepareStatement(
                                String.format (FORMAT_PREPARE_STATEMENT_UPDATE, TABLE_NAME));)
            {
                psUpdate.setString (1, newName);
                psUpdate.setString (2, prevName);
                psUpdate.executeUpdate();

            // Теперь проверяем, сделана ли в БД соотв. запись.
                 try (ResultSet rs = stnt.executeQuery (String.format (FORMAT_STATEMENT_CHECK, TABLE_NAME, newName));)
                 {
                     if (rs.next())  result = rs.getString ("nickname");
                     else
                        result = ""; //< индикатор того, что запись не состоялась
                 }
                 catch(SQLException throwables){throwables.printStackTrace();}
            }
            catch (SQLException throwables)  {  throwables.printStackTrace();  }
            finally  {  disconnect();  }
        }
        return result;
    }// rename ()


// Добавляем данные пользователя в БД. (Сейчас он не используется.)
    @Override public boolean add (String login, String password, String nickname)
    {
        boolean boolOk = false;
        if (validateStrings (SOFT_MODE, login, password, nickname))
        {
            connect();
            try (PreparedStatement psInsert = connection.prepareStatement(
                                        String.format (FORMAT_PREPARE_STATEMENT_INSERT, TABLE_NAME)))
            {   connection.setAutoCommit (false);
                psInsert.setString (1, login);
                psInsert.setString (2, password);
                psInsert.setString (3, nickname);
                psInsert.executeUpdate();
                connection.setAutoCommit (true);
            }
            catch (SQLException throwables)
            {   System.out.print("\nERROR @ add(): User data creation failed.");
                throwables.printStackTrace();
            }
            finally  {  disconnect();  }
        }
        return boolOk;
    }// add ()


// Удаляем пользователя из БД по нику. (Этот метод сейчас не используется.)
    private static final String    FORMAT_PREPARE_STATEMENT_DELETE = "DELETE FROM [%s] WHERE nickname = ?;"
            ;
    @Override public void remove (String nick)
    {
        if (validateStrings (SOFT_MODE, nick))
        {
            connect();
            try (PreparedStatement psDelete = connection.prepareStatement(
                                        String.format (FORMAT_PREPARE_STATEMENT_DELETE, TABLE_NAME)))
            {   psDelete.setString (1, nick);
                psDelete.executeUpdate();
            }
            catch (SQLException throwables)
            {   System.out.print("\nERROR @ remove(): User data deletion failed.");
                throwables.printStackTrace();
            }
            finally  {  disconnect();  }
        }
    }// remove ()


// Удаляем таблицу с базой данных. (Этот метод сейчас не используется.)
    private static final String    DROP_TABLE_FORMAT = "DROP TABLE IF EXISTS [%s];"
            ;
    private void dropDbTable ()
    {
        connect();
        try (Statement stnt = connection.createStatement())
        {
            stnt.executeUpdate (String.format (DROP_TABLE_FORMAT, TABLE_NAME));
        }
        catch (SQLException throwables)
        {   System.out.print("\nERROR @ dropDbTable(): Data base deletion failed.");
            throwables.printStackTrace();
        }
        finally  {  disconnect();  }
    }// dropDbTable ()


//(Вспомогательная.) Проверяет строку на пригодность для использования в качестве логина, пароля, ника.
    private static boolean validateStrings (boolean mode, String ... lines)
    {
        if (lines != null)
        for (String s : lines)
            if (s == null || s.trim().isEmpty())
            {
                if (mode == THROW_EXCEPTION)   throw new InvalidParameterException(
                                    "ERROR @ validateString() : String must not be null, empty or blanck.");
                else return false;
            }
        return true;
    }// validateString ()

}// class JdbcAuthentificationProvider
