package ru.geekbrains.march.chat.server;

import java.sql.*;

public class JdbcAuthentificationProvider implements Authentificator
{
    private static final String
            CLASS_NAME = "org.sqlite.JDBC",
            TABLE_NAME = "marchchat users",
            FORMAT_PREPARE_STATEMENT_INSERT = "INSERT INTO [%s] (login, password, nickname) VALUES (?, ?, ?);",
            DROP_TABLE_FORMAT = "DROP TABLE IF EXISTS [%s];",
            FORMAT_PREPARE_STATEMENT_DELETE = "DELETE FROM [%s] WHERE nickname = ?;",
            FORMAT_STATEMENT_AUTHENTIFICATION = "SELECT nickname FROM [%s] WHERE login = '%s' AND password = '%s';",
            FORMAT_PREPARE_STATEMENT_UPDATE = "UPDATE [%s] SET nickname = ? WHERE nickname = ?;",
            FORMAT_STATEMENT_CHECK = "SELECT nickname FROM [%s] WHERE nickname = '%s';"
            ;
    private final Connection connection;
    private final PreparedStatement psRename, psAdd, psDelete;


    public JdbcAuthentificationProvider (Connection connection)
    {
        this.connection = connection;
        try
        {   Class.forName (CLASS_NAME);
            psRename = connection.prepareStatement (String.format (FORMAT_PREPARE_STATEMENT_UPDATE, TABLE_NAME));
            psAdd = connection.prepareStatement (String.format (FORMAT_PREPARE_STATEMENT_INSERT, TABLE_NAME));
            psDelete = connection.prepareStatement (String.format (FORMAT_PREPARE_STATEMENT_DELETE, TABLE_NAME));
        }
        catch (ClassNotFoundException e)
        {   System.out.printf("\nERROR @ JdbcAuthentificationProvider(): Class «%s» not found.", CLASS_NAME);
            e.printStackTrace();
            throw new RuntimeException();
        }
        catch (SQLException sqle)
        {   sqle.printStackTrace ();
            close();
            throw new RuntimeException();
        }
    }// JdbcAuthentificationProvider ()


    @Override public void close () //< сейчас Authentificator extends Closable
    {
        try
        {   if (psRename != null)   psRename.close();
            if (psAdd != null)      psAdd.close();
            if (psDelete != null)   psDelete.close();
        }
        catch (SQLException sqle)  {  sqle.printStackTrace();  }
    }// close ()

//Возвращаем ник пользователя, которому в БД соответствуют введённые им логин и пароль.
    @Override public String authenticate (String login, String password)
    {
        String nickname = null;
        if (validateStrings (login, password))
        {
            try (Statement stnt = connection.createStatement();
                 ResultSet rs = stnt.executeQuery(
                        String.format(FORMAT_STATEMENT_AUTHENTIFICATION, TABLE_NAME, login, password));)
            {
                if (rs.next())
                    nickname = rs.getString ("nickname");
            }
            catch (SQLException throwables)
            {   throwables.printStackTrace();
                throw new RuntimeException();
            }
        }
        return nickname;
    }// authenticate ()


// В БД изменяем поле nickname == prevName на newName. (У поля nickname есть атрибут UNIQUE.)
    @Override public String rename (String prevName, String newName)
    {
        String result = null;   //< индикатор неустранимой ошибки
        if (validateStrings (prevName, newName))
        {
            try (Statement stnt = connection.createStatement();)
            {
                psRename.setString (1, newName);
                psRename.setString (2, prevName);
                psRename.executeUpdate();

            // Теперь проверяем, сделана ли в БД соотв. запись.
                 try (ResultSet rs = stnt.executeQuery (String.format (FORMAT_STATEMENT_CHECK, TABLE_NAME, newName));)
                 {
                     if (rs.next())  result = rs.getString ("nickname");
                     else
                        result = ""; //< индикатор того, что запись не состоялась
                 }
                 catch(SQLException throwables){throwables.printStackTrace();}
            }
            catch (SQLException throwables)
            {   throwables.printStackTrace();
                throw new RuntimeException();
            }
        }
        return result;
    }// rename ()


// Добавляем данные пользователя в БД. (Сейчас он не используется.)
    @Override public boolean add (String login, String password, String nickname)
    {
        boolean boolOk = false;
        if (validateStrings (login, password, nickname))
        {
            try
            {   psAdd.setString (1, login);
                psAdd.setString (2, password);
                psAdd.setString (3, nickname);
                psAdd.executeUpdate();
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
    @Override public void remove (String nick)
    {
        if (validateStrings (nick))
        {
            try
            {   psDelete.setString (1, nick);
                psDelete.executeUpdate();
            }
            catch (SQLException throwables)
            {   System.out.print("\nERROR @ remove(): User data deletion failed.");
                throwables.printStackTrace();
                throw new RuntimeException();
            }
        }
    }// remove ()


// Удаляем таблицу с базой данных. (Этот метод сейчас не используется.)
    private void dropDbTable ()
    {
        try (Statement stnt = connection.createStatement())
        {
            stnt.executeUpdate (String.format (DROP_TABLE_FORMAT, TABLE_NAME));
        }
        catch (SQLException throwables)
        {   System.out.print("\nERROR @ dropDbTable(): Data base deletion failed.");
            throwables.printStackTrace();
            throw new RuntimeException();
        }
    }// dropDbTable ()


//(Вспомогательная.) Проверяет строку на пригодность для использования в качестве логина, пароля, ника.
    private static boolean validateStrings (String ... lines)
    {
        if (lines != null)
        for (String s : lines)
            if (s == null || s.trim().isEmpty())
                return false;
        return true;
    }// validateString ()


}// class JdbcAuthentificationProvider
