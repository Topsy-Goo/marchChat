package ru.geekbrains.march.chat.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main8 extends Application
{

    @Override public void start (Stage primaryStage) throws Exception
    {
        Parent root = FXMLLoader.load(getClass().getResource("/window8.fxml"));
        primaryStage.setTitle ("March chat");
        Scene scene = new Scene (root, 350, 250);
        primaryStage.setScene (scene);
        primaryStage.show();
        primaryStage.setMinWidth(350);
        primaryStage.setMinHeight(250);
    }// start ()


    public static void main (String[] args)
    {
        launch (args);

    }// main ()

}
