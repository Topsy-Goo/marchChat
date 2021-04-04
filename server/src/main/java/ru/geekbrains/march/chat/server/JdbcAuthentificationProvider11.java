package ru.geekbrains.march.chat.server;

import java.sql.*;

import static ru.geekbrains.march.chat.server.ServerApp.TABLE_NAME;

public class JdbcAuthentificationProvider11 implements Authentificator11
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

    public JdbcAuthentificationProvider11 ()
    {
        dbConnection = new DbConnection();
        statement = dbConnection.getStatement();
        Connection connection = dbConnection.getConnection();
        try
        {   psUpdate1By1 = connection.prepareStatement(
                        String.format (FRMT_PREPSTMENT_UPD_SET1BY1, TABLE_NAME, FLD_NICK, FLD_NICK));
            psInsert3Fld = connection.prepareStatement(
                        String.format (FRMT_PREPSTMENT_INS_3FLD, TABLE_NAME, FLD_LOGIN, FLD_PASS, FLD_NICK));
            psDeleteBy1 = connection.prepareStatement(
                        String.format (FRMT_PREPSTMENT_DEL_BY1, TABLE_NAME, FLD_LOGIN));
        }
        catch (SQLException sqle)
        {   sqle.printStackTrace ();
            close();
            throw new RuntimeException("\nCannot create object JdbcAuthentificationProvider.");
        }
    }// JdbcAuthentificationProvider ()


    public Authentificator11 close () //< сейчас Authentificator extends Closable
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
        if (validateStrings (login, password))
        {
            try (ResultSet rs = statement.executeQuery (String.format (FRMT_STMENT_SEL_1BY2,
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
        String result = null;   //< индикатор неустранимой ошибки
        if (validateStrings (prevName, newName))
        {
            try
            {   psUpdate1By1.setString (1, newName);
                psUpdate1By1.setString (2, prevName);
                psUpdate1By1.executeUpdate();
        /* Теперь проверяем, сделана ли в БД соотв. запись. (Лучше делать так -- пытаться менять и
           проверять результат, -- чем отдельным запросом выяснять, занят ник или нет, т.к., теоретически,
           между операциями запроса и смены может произойти запрос от другого пользователя на тот же самый
           ник или смена ника, и т.о. мы будем вынуждены проводить доп.проверки или синхронизацию. Здесь же
           всё происходит в одном методе и, соотв-но, в одном запросе.)    */
                try (ResultSet rs = statement.executeQuery (
                         String.format (FRMT_STMENT_SEL_1BY1, FLD_NICK, TABLE_NAME, FLD_NICK, newName));)
                {   if (rs.next())
                         result = rs.getString (FLD_NICK); //или rs.getString (№);
                    else result = ""; //< индикатор того, что запись не состоялась
                }
                catch (SQLException throwables) { throwables.printStackTrace(); }
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
        if (validateStrings (login))
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
    }// dropDbTable ()


//(Вспомогательная.) Проверяет строку на пригодность для использования в качестве логина, пароля, ника.
    public static boolean validateStrings (String ... lines)
    {
        if (lines != null)
        for (String s : lines)
            if (s == null || s.trim().isEmpty())
                return false;
        return true;
    }// validateString ()

    public void print (String s) {System.out.print(s);}
}// class JdbcAuthentificationProvider
