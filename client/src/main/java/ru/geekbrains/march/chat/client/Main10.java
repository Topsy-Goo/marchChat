package ru.geekbrains.march.chat.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main10 extends Application
{
    public static final String
            WNDTITLE_APPNAME = "March Chat";

    @Override public void start (Stage primaryStage) throws Exception
    {
        Parent root = FXMLLoader.load(getClass().getResource("/window10.fxml"));
        primaryStage.setTitle (WNDTITLE_APPNAME);
        Scene scene = new Scene (root, 350, 250);
        primaryStage.setScene (scene);
        primaryStage.show();
        primaryStage.setMinWidth(350);
        primaryStage.setMinHeight(250);
    }

    public static void main (String[] args)
    {
        launch (args);
    }
}