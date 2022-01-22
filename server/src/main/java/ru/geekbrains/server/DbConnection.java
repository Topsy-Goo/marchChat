package ru.geekbrains.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static ru.geekbrains.server.ServerApp.CLASS_NAME;
import static ru.geekbrains.server.ServerApp.DATABASE_URL;

public class DbConnection {
    private Connection connection;
    private Statement statement;

    public DbConnection () {
        try {
            Class.forName(CLASS_NAME);
            connection = DriverManager.getConnection(DATABASE_URL);
            statement = connection.createStatement();
        }
        catch (SQLException trouble) {      //для getConnection() и createStatement()
            throw new RuntimeException (trouble);
        }
        catch (ClassNotFoundException e) {  //для Class.forName
            String err = String.format("\nERROR @ DbConnection(): Class «%s» not found.\n", CLASS_NAME);
            throw new RuntimeException (err, e);
        }
    }

    public DbConnection close () {
        try {
            if (statement != null) { statement.close(); }
            if (connection != null) { connection.close(); }
        }
        catch (SQLException throwables) { throwables.printStackTrace(); }
        finally {
            connection = null;
            statement = null;
        }
        return null;
    }

    public Connection getConnection () { return connection; }

    public Statement getStatement () { return statement; }

    public void print (String s) {System.out.print(s);}
}
