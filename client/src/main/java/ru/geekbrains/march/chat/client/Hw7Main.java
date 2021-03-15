package ru.geekbrains.march.chat.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Hw7Main extends Application
{
    @Override public void start (Stage primaryStage) throws Exception
    {
        Parent root = FXMLLoader.load(getClass().getResource("/hw7window.fxml"));
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

}// class Hw7Main
