package ru.geekbrains.march.chat.client;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URL;
import java.util.ResourceBundle;

public class Controller implements Initializable
{
    private Socket socket;
    private DataInputStream dis;
    private DataOutputStream dos;

    @FXML TextArea txtareaMessages;
    @FXML TextField txtfieldMessage;


    @FXML public void onactionSendMessage ()
    {
    // Отправляем текст сообщения (в виде байтов) в исходящий поток сокета
        String s = txtfieldMessage.getText();
        //byte[] bytes = s.getBytes();
        try
        {
            //socket.getOutputStream().write(bytes);
            txtfieldMessage.clear();
            txtareaMessages.appendText (s +'\n');
            dos.writeUTF (s);
        }
        catch (IOException ioe)
        {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Не удадось отправить сообщение.", ButtonType.OK);
            alert.showAndWait();
        }
    }// onactionSendMessage ()


    @Override public void initialize (URL location, ResourceBundle resources)
    {
    // создаём сокет для подключения к серверу по порт 8189 (сервре должен уже ждать нас на этом порте)
        try
        {
            socket = new Socket ("localhost", 8189);
            dis = new DataInputStream (socket.getInputStream());
            dos = new DataOutputStream (socket.getOutputStream());

            Thread t = new Thread (() ->
            {
                try
                {   while (true)
                    {
                        String s = dis.readUTF();
                        if (s.equals("exit"))
                        {
                            txtareaMessages.appendText ("Завершение сессии.");
                            break;
                        }
                        txtareaMessages.appendText (s +'\n');
                    }
                }
                catch (IOException ioe) {ioe.printStackTrace();}
            });
            t.start();
        }
        catch (IOException ioe)
        {
            //ioe.printStackTrace();
            throw new RuntimeException ("Unable to connect to [localhost:8189].");
        }
    }// initialize ()


}// class Controller
