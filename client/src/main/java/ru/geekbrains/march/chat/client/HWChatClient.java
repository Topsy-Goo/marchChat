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
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.URL;
import java.util.ResourceBundle;

public class HWChatClient extends Application implements Initializable
{
    private static final String SERVER_ADDRESS = "localhost";
    private static boolean appGettingOff = false;
    private static Thread  threadDisListener = null;
    private static DataInputStream dis;
    private static DataOutputStream dos;

    @FXML TextArea txtareaMessages;
    @FXML TextField txtfieldMessage;


    public static void main (String[] args)
    {
        System.out.println("main() старт.");
        System.out.println("launch() старт.");
        launch (args);
        //«… The launch method does not return until the application has exited, either via a call
        // to Platform.exit() or all of the application windows have been closed. …»

        System.out.println("launch() финиш.");
        try
        {   threadDisListener.join(1000);}catch (InterruptedException e) { e.printStackTrace(); }

        try
        {   dis.close();
            dos.close();
            } catch (IOException ioe){;}

        System.out.println("main() финиш.");
    }// main ()


    @Override public void start (Stage primaryStage) throws Exception
    {
        System.out.println("stat() старт.");
        Parent root = FXMLLoader.load(getClass().getResource("/HWChatClient.fxml"));
        primaryStage.setTitle ("March chat");
        primaryStage.setScene (new Scene(root, 300, 450));
        primaryStage.show();
        System.out.println("start() финиш.");
    }// start ()


    @Override public void initialize (URL location, ResourceBundle resources)
    {
        System.out.println("initialize() старт.");
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
        System.out.println("initialize() финиш.");
    }// initialize ()


// Поток для чтения из входного потока
    private void treadDISListener ()
    {
        System.out.println("treadDISListener() старт.");
        String s = "";
        while (!appGettingOff)
        {
            try
            {
                if (dis.available() > 0) //< проверяем наличие данных в стриме. Цена за ожидание -- нагрузка на ЦП.
                {
                    s = dis.readUTF(); //< если стрим пуст, этот вызов застопорит выполнение потока (будет ждать данные)

                    if (s.equals (HWChatServer.msgEXIT))
                    {
                        System.out.println("treadDISListener получил /exit.");
                        appGettingOff = true;
                    //клиент и сервер обмениваются сообщениями /exit (возможно, не один раз)
                    //    dos.writeUTF (HWChatServer.msgEXIT); //< это решает проблему с исключением в потоке сервера, читающего наши сообщения
                    }
                    else
                        txtareaMessages.appendText (s +'\n');
                }
                else Thread.sleep(250); //< уменьшаем нагрузку на ЦП, вызванную применением available() (хорошо помогает).
            }
            catch (EOFException eofe)
            {
                //почему-то мы получаем это исключение, если стрим закрывается на стороне сервера
                System.err.println("ERROR @ treadDISListener() : treadDISListener -> EOFException");
                eofe.printStackTrace();
            }
            catch (IOException ioe)
            {
                System.err.println("ERROR @ treadDISListener() : treadDISListener -> IOException");
                ioe.printStackTrace();
            }
            catch (InterruptedException e){System.err.println("Thread.sleep() поймал эксепшн.");}
        }
        System.out.println("treadDISListener() финиш.");
        appExit();
    }// treadDISListener ()


// Кнопка «Отправить»
    @FXML public void onactionSendMessage ()
    {
        String s = txtfieldMessage.getText().trim();

        if (!s.isEmpty())
        if (s.equalsIgnoreCase (HWChatServer.msgEXIT))
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
        System.out.println("onactionSendExitMessage() старт.");
        sendMessage (HWChatServer.msgEXIT);
        txtfieldMessage.clear();
        appExit();
        System.out.println("onactionSendExitMessage() финиш.");
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
        System.out.println("appExit() старт.");
        appGettingOff = true;
        Platform.exit();  //< генерирует выход из метода launch() + «… This method may be called from any thread. …»
        System.out.println("appExit() финиш.");
    }// appExit ()

}// class HWChatClient
