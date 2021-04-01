package ru.geekbrains.march.chat.server;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static ru.geekbrains.march.chat.server.ServerApp.CLASS_NAME;
import static ru.geekbrains.march.chat.server.ServerApp.DATABASE_URL;

public class DbConnection
{
    private Connection connection;
    private Statement statement;

    public DbConnection ()
    {
        try
        {   connection = DriverManager.getConnection (DATABASE_URL);
            Class.forName (CLASS_NAME);
            statement = connection.createStatement();
        }
        catch (SQLException throwables)
        {   throwables.printStackTrace();
            throw new RuntimeException();
        }
        catch (ClassNotFoundException e)
        {   System.out.printf("\nERROR @ JdbcAuthentificationProvider(): Class «%s» not found.", CLASS_NAME);
            e.printStackTrace();
            throw new RuntimeException("\nCannot create object DbConnection.");
        }
    }// DbConnection ()


    public DbConnection close () //throws IOException
    {
        try
        {   if (connection != null)   connection.close();
            if (statement != null)   statement.close();
        }
        catch (SQLException throwables)  {  throwables.printStackTrace();  }
        finally
        {   connection = null;
            statement = null;
        }
        return null;
    }// close ()

    public Connection getConnection () { return connection; }

    public Statement getStatement () { return statement; }

    public void print (String s) {System.out.print(s);}
}// class DbConnection
