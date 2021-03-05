package ru.geekbrains.march.chat.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import ru.geekbrains.march.chat.server.HWChatServer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URL;
import java.util.ResourceBundle;

public class HWChatClient extends Application implements Initializable
{
    private static final String SERVER_ADDRESS = "localhost";
    private static boolean appGettingOff = false;
    private static Thread  threadDisListener = null;
    private DataInputStream dis;
    private DataOutputStream dos;

    @FXML TextArea txtareaMessages;
    @FXML TextField txtfieldMessage;


    public static void main (String[] args)
    {
        launch (args);
        //«… The launch method does not return until the application has exited, either via a call
        // to Platform.exit() or all of the application windows have been closed. …»

        try
        {
            threadDisListener.join();
        }
        catch (InterruptedException e) { e.printStackTrace(); }

    }// main ()


    @Override public void start (Stage primaryStage) throws Exception
    {
        Parent root = FXMLLoader.load(getClass().getResource("/HWChatClient.fxml"));
        primaryStage.setTitle ("March chat");
        primaryStage.setScene (new Scene(root, 300, 450));
        primaryStage.show();
    }// start ()


    @Override public void initialize (URL location, ResourceBundle resources)
    {
        try
        {
            Socket socket = new Socket (SERVER_ADDRESS, HWChatServer.PORT_NUMBER);
            dis = new DataInputStream (socket.getInputStream());
            dos = new DataOutputStream (socket.getOutputStream());
        }
        catch (IOException ioe)
        {
            throw new RuntimeException (String.format("Unable to connect to [%s:%d].",
                                        SERVER_ADDRESS, HWChatServer.PORT_NUMBER));
        }

        threadDisListener = new Thread (() -> treadDISListener());
        threadDisListener.start();
    }// initialize ()


// Поток для чтения из входного потока
    private void treadDISListener ()
    {
        String s = "";
        while (!appGettingOff)
        {
            try
            {
                if (dis.available() > 0) //< проверяем наличие данных в стриме
                {
                    s = dis.readUTF(); //< если стрим пуст, этот вызов застопорит выполнение потока (будет ждать данные)

                    if (s.equals (HWChatServer.msgEXIT))
                        appGettingOff = true;
                    else
                        txtareaMessages.appendText (s +'\n');
                }
            }
            catch (IOException ioe) {ioe.printStackTrace();}
        }
        Platform.exit();  //< генерирует выход из метода launch() + «… This method may be called from any thread. …»
    }// treadDISListener ()


// Кнопка «Отправить»
    @FXML public void onactionSendMessage ()
    {
        String s = txtfieldMessage.getText();

        if (s.equals(HWChatServer.msgEXIT))
            onactionSendExitMessage();
        else
        {
            if (!s.equals(HWChatServer.msgSTAT))
                txtareaMessages.appendText (s +'\n');
            sendMessage (s);
            txtfieldMessage.clear();
        }
    }// onactionSendMessage ()


// Кнопка «Выход»
    @FXML public void onactionSendExitMessage ()
    {
        sendMessage (HWChatServer.msgEXIT);
        txtfieldMessage.clear();
        appExit();
    }// onactionSendExitMessage ()

// Кнопка «Статистика»
    @FXML public void onactionSendStatMessage ()
    {
        sendMessage (HWChatServer.msgSTAT);
    }


// (Вспомогательная.) Помещает указанное сообщение в выходной поток.
    private void sendMessage (String msg)
    {
        if (msg != null && !msg.isEmpty())
        try
        {
            dos.writeUTF (msg);
        }
        catch (IOException ioe)
        {
            Alert alert = new Alert (Alert.AlertType.ERROR,
                                     String.format("Не удадось отправить сообщение:\n\t%s", msg),
                                     ButtonType.OK);
            alert.showAndWait();
        }
    }// sendMessage ()


    private void appExit ()
    {
        appGettingOff = true;
        Platform.exit(); //< генерирует выход из метода launch()
    }// appExit ()

}// class HWChatClient
