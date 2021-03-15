package ru.geekbrains.march.chat.client;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URL;
import java.util.ResourceBundle;

import static ru.geekbrains.march.chat.server.Hw7ServerApp.*;
import static ru.geekbrains.march.chat.server.Hw7ServerApp.CMD_EXIT;

public class Hw7Controller implements Initializable
{
    private final static String txtIntroduceYourself = "Представьтесь:",
                          txtYouLogedInAs = "Вы вошли в чат как:";

    private final static boolean CAN_CHAT  = true, CANNOT_CHAT   = !CAN_CHAT,
                                 LOGED_IN  = true, LOGED_OFF     = !LOGED_IN,
                                 SEND_EXIT = true, DONTSEND_EXIT = !SEND_EXIT;

    private Socket socket;
    private DataInputStream dis;
    private DataOutputStream dos;
    private String userName;
    private Thread threadIntputStream,
                   threadMain;
    private boolean appGettingOff = false,
                    loginState = LOGED_OFF;

    @FXML TextArea txtareaMessages;
    @FXML TextField txtfieldUsernameField, txtfieldMessage;
    @FXML Button buttonLogin;
    @FXML HBox hboxMessagePanel;
    @FXML Text txtIntroduction;


    @Override public void initialize (URL location, ResourceBundle resources)
    {
        threadMain = Thread.currentThread();
        txtIntroduction.setText (txtIntroduceYourself);
        //txtareaMessages.setWrapText (true);
    }// initialize ()


    private void connect ()
    {
        appGettingOff = false;

    // создаём сокет для подключения к серверу по порт 8189 (сервер должен уже ждать нас на этом порте)
        if (socket == null/* || socket.isClosed()*/)
        try
        {
            socket = new Socket (SERVER_ADDRESS, SERVER_PORT);
            dis = new DataInputStream (socket.getInputStream());
            dos = new DataOutputStream (socket.getOutputStream());

            threadIntputStream = new Thread(() -> runTreadInputStream());
            threadIntputStream.start();
        }
        catch (IOException ioe)
        {
            txtareaMessages.appendText("\nНе удалось подключиться.");
            ioe.printStackTrace();
        }
    }// connect ()

//Закрытие сокета и обнуление связанных с ним переменных. + Внесение изменений в интерфейс (чтобы юзер
// не мог пользоваться чатом).
    private void disconnect ()
    {
        updateUserInterface(CANNOT_CHAT);

        if (socket != null)
            try{
                socket.close();
            }catch(IOException e) {e.printStackTrace();}

        socket = null;
        dis = null;
        dos = null;
        threadIntputStream = null;
    }// disconnect ()

    private void runTreadInputStream ()
    {
        int timer = 0;
        try
        {
            while (!appGettingOff)
            {
                if (dis.available() > 0)
                {
                    String msg = dis.readUTF();

        //  Чтобы войти в чат, пользователь должен ввести непустое уникальное (для чата) имя.
        // (Именно клиентское приложение начинает процесс регистрации. Если регистрация не состоялась,
        // то пользователь не может возможности пользоваться чатом.)
        // Введённое имя отправляется на сервер в формате «/login userName» и проверяется сервером
        // на уникальность. Если сервер счёл имя подходящим, то ClientHandler возвращает клиентскому
        // приложению имя в том же формате — «/login userName». Если сервер счёл имя неподходящим, то
        // клиентскому приложению возвращается только запрос /login.

                    if (msg.equalsIgnoreCase(CMD_LOGIN))
                    {
                        txtareaMessages.appendText ("\nВведите другое имя пользователя.");
                        // Почему-то Alert, вызванный отсюда выдаёт исключение (что-то про поток, который
                        // не является javafx-потоком).
                    }
                    else //Сервер одобрил отправленное ему имя пользователя — в его сообщении что-то есть после /login.
                    if (msg.startsWith(LOGIN_PREFIX))
                    {
                        userName = msg.substring(LOGIN_PREFIX.length());
                        loginState = LOGED_IN;
                        updateUserInterface (CAN_CHAT);
                    }
                    else if (msg.equalsIgnoreCase(CMD_EXIT)) //< Нам от сервера пришло сообщение /exit
                    {
                        onactionLogout (DONTSEND_EXIT);
                    }
                    else if (msg.equalsIgnoreCase(CMD_ONLINE)) //< сервер проверяет, на связи ли мы
                    {
                        ; // (нет необходимости реагировать на это сообщение)
                    }
                    else txtareaMessages.appendText ('\n'+ msg);
                }
                else
                {   //Использование dis.available() нуждается в притормаживании…
                    Thread.sleep(SLEEP_INTERVAL);

                //…плюс это позволяет каждые 5 сек. проверять, не работает ли наш поток впустую.
                    timer ++;
                    if (timer > 5000 / SLEEP_INTERVAL)
                    {
                        if (!threadMain.isAlive())
                            break;
                        dos.writeUTF(CMD_ONLINE); //< «пингуем» сервер на случай, если он отключился без предупреждения
                        timer = 0;
                    }
                }
            }//while
        }
        catch (IOException e)
        {
            txtareaMessages.appendText("\nОШИБКА: соединение разорвано.");
            e.printStackTrace();
        }
        catch (InterruptedException e) {e.printStackTrace();}
        finally
        {
            disconnect();
        }
    }// runTreadInputStream ()


// Обработка ввода пользователем своего имени для чата.
    public void onactionLogin (ActionEvent actionEvent)
    {
        if (loginState == LOGED_OFF)
        {
            String name = txtfieldUsernameField.getText().trim();
            if (name.isEmpty())
            {
                alertWarning("\nВведите другое имя пользователя.");
                txtfieldUsernameField.requestFocus();
            }
            else
            {   userName = name;
                connect();  //< На данном этапе развития чата удобно сперва запрашивать
                            //  у пользователя его имя, а потом подключаться к серверу.
                sendMessageToServer(LOGIN_PREFIX + userName);
            }
        }
        else onactionLogout (SEND_EXIT); //Кнопка «Войти» используется и для выхода из чата.
    }// onactionLogin ()

//Обрабатываем команду выхода из чата (по кнопке, по команде и по приходу сообщения от сервера).
    private void onactionLogout (boolean sendExitMessage)
    {
    //Этот метод может быть вызван из runTreadInputStream() как реакция на приход сообщения /exit от сервера.
        if (sendExitMessage == SEND_EXIT)
            sendMessageToServer(CMD_EXIT);

        loginState = LOGED_OFF;
    //при нормальном течении событий disconnect() вызовется из потока threadIntputStream в самом конце.
        appGettingOff = true;
        if (threadIntputStream == null)     //а если потока нет, то disconnect() вызывается здесь.
            disconnect();
    }// onactionLogout ()


// Изменяем атрибуты элементов управления так, чтобы пользователь мог пользоваться чатом, но не мог
// изменить своё имя. Или наоборот: чтобы мог ввести своё имя, но не мог пользоваться чатом.
    private void updateUserInterface (boolean canChat)
    {
        txtfieldUsernameField.setDisable (canChat == CAN_CHAT);
        buttonLogin.setText (canChat == CAN_CHAT ? "Выйти" : "Войти"); //Текст кнопки не обновляется, пока на ней фокус.
        hboxMessagePanel.setManaged (canChat == CAN_CHAT);
        hboxMessagePanel.setVisible (canChat == CAN_CHAT);

        if (canChat == CAN_CHAT)
        {
            txtIntroduction.setText (txtYouLogedInAs);
            txtfieldUsernameField.setText (userName);
            //txtfieldMessage.requestFocus();           //Эта строка портит работу программы! Интересно, это нормально?
        }
        else
        {
            txtIntroduction.setText (txtIntroduceYourself);
            txtareaMessages.appendText("\nВы вышли из чата.");
            //txtfieldUsernameField.requestFocus();     //Эта строка портит работу программы! Интересно, это нормально?
        }
    }// updateUserInterface ()


//Обработка вводимых пользователем сообщений.
    @FXML public void onactionSendMessage ()
    {
        String msg = txtfieldMessage.getText();

        if (msg.trim().equalsIgnoreCase(CMD_EXIT))
        {
            onactionLogout (SEND_EXIT);
        }
        else if (sendMessageToServer (msg))
        {
            txtfieldMessage.clear();;
            txtfieldMessage.requestFocus();
        }
    }// onactionSendMessage ()

//(Вспомогательный метод.)
    private boolean sendMessageToServer (String msg)
    {
        boolean boolSent = false;
        if (msg != null  &&  !msg.trim().isEmpty()  &&  dos != null)
        {
            try
            {
                dos.writeUTF(msg);
                boolSent = true;
            }
            catch (IOException e) { alertWarning("Не удадось отправить сообщение."); }
        }
        return boolSent;
    }// sendMessageToServer ()


    public static void alertWarning (String msg)
    {
        if (msg != null)
            new Alert(Alert.AlertType.WARNING, msg, ButtonType.CLOSE).showAndWait();

        //Примечательно, что в API Windows уже ни один десяток лет присутствует функция
        // MessageBox ("", xx), которая и создаёт окно сообщения, и показывает его… Очень удобно… ))

    }// alertWarning ()


    @Override public String toString() {return "Controller : "+ userName;}

}// class Hw7Controller
