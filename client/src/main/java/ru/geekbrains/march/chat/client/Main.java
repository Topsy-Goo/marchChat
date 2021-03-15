package ru.geekbrains.march.chat.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application
{
    Controller controller = null;

    @Override public void start (Stage primaryStage) throws Exception
    {
        Parent root = FXMLLoader.load(getClass().getResource ("/window.fxml"));
        primaryStage.setTitle ("March chat");
        Scene scene = new Scene (root, 330, 450);
        primaryStage.setScene (scene);
        primaryStage.show();

        System.out.println(scene.getWindow().getEventDispatcher().toString());

    }// start ()


    public static void main (String[] args)
    {
        launch (args);
    }// main ()


}// class Main

