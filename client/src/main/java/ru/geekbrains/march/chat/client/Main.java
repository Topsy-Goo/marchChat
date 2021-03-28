package ru.geekbrains.march.chat.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

//import static ru.geekbrains.march.chat.server.ServerApp.DEBUG;
//import static ru.geekbrains.march.chat.server.ServerApp.WNDTITLE_APPNAME;

public class Main extends Application
{
    public static final String
            WNDTITLE_APPNAME = "March Chat";

    //Controller controller = null;

    @Override public void start (Stage primaryStage) throws Exception
    {
//if (DEBUG) System.out.println("Main.start().s");
        Parent root = FXMLLoader.load(getClass().getResource ("/window.fxml"));
        primaryStage.setTitle (WNDTITLE_APPNAME);
        Scene scene = new Scene (root, 350, 250);
        primaryStage.setScene (scene);
        primaryStage.show();
        primaryStage.setMinWidth(350);
        primaryStage.setMinHeight(250);
//if (DEBUG) System.out.println("Main.start().e");
    }// start ()


    public static void main (String[] args)
    {
//if (DEBUG) System.out.println("Main.main().s");
        launch (args);
//if (DEBUG) System.out.println("Main.main().e");
    }// main ()


}// class Main

