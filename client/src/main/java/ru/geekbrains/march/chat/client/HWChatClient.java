package ru.geekbrains.march.chat.client;

import javafx.application.Application;
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
    private DataInputStream dis;
    private DataOutputStream dos;

    @FXML TextArea txtareaMessages;
    @FXML TextField txtfieldMessage;


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


    @Override public void initialize (URL location, ResourceBundle resources)
    {
        try
        {
            Socket socket = new Socket ("localhost", HWChatServer.PORT_NUMBER);
            dis = new DataInputStream (socket.getInputStream());
            dos = new DataOutputStream (socket.getOutputStream());

            Thread t = new Thread (() -> treadDISListener());
            t.start();
        }
        catch (IOException ioe)
        {
            throw new RuntimeException ("Unable to connect to [localhost:"+ HWChatServer.PORT_NUMBER +"].");
        }
    }// initialize ()


// Поток для чтения из входного потока
    private void treadDISListener ()
    {
        String s = "";
        while (true)
        {
            try
            {
                s = dis.readUTF();
            }
            catch (IOException ioe) {ioe.printStackTrace();}

            if (s.equals(HWChatServer.msgEXIT))
                break;
            txtareaMessages.appendText (s +'\n');
        }
        appExit();
    }// treadDISListener ()


    @Override public void start (Stage primaryStage) throws Exception
    {
        Parent root = FXMLLoader.load(getClass().getResource("/HWChatClient.fxml"));
        primaryStage.setTitle ("March chat");
        primaryStage.setScene (new Scene(root, 300, 450));
        primaryStage.show();
    }// start ()


    public static void main (String[] args)
    {
        launch (args);
    }// main ()


    private void appExit ()
    {
        System.exit(0);
    }

}// class HWChatClient
