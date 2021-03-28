package ru.geekbrains.march.chat.client;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URL;
import java.util.Arrays;
import java.util.Optional;
import java.util.ResourceBundle;

import static ru.geekbrains.march.chat.client.Main9.WNDTITLE_APPNAME;
import static ru.geekbrains.march.chat.server.ServerApp9.*;

public class Controller9 implements Initializable
{
    private final static String
            TXT_INTRODUCE_YOURSELF = "Представьтесь:  ",
            TXT_YOU_LOGED_IN_AS = "Вы вошли в чат как: ",
            WELCOME_TO_MARCHCHAT = "Добро пожаловать в March Chat!",
            FORMAT_TOYOU_PRIVATE_FROM = "\n[%s приватно Вам]:\n\t%s",
            FORMAT_YOU_PRIVATE_TO = "\n[Вы приватно для %s]:\n\t%s",
            PROMPT_TIPS_ON = "\nПодсказки включены.",
            PROMPT_UNABLE_TO_CONNECT = "\nНе удалось подключиться.",
            PROMPT_YOU_ARE_LOGED_OFF = "\nВы вышли из чата.",
            PROMPT_CONNECTION_LOST = "\nСоединение разорвано.",
            PROMPT_PRIVATE_MODE_IS_ON = "\n\nВы вошли в приватный режим. Ваши сообщения будут видны только выбранному собеседнику.",
            PROMPT_PRIVATE_MODE_IS_OFF = "\n\nВы вышли из приватного режима. Ваши сообщения будут видны всем участникам чата.",
            PROMPT_STATISTICS = "\nсообщений = ",
            PROMPT_CHANGE_NICKNAME = "\n\nОправьте новое имя как сообщение. Чат-сервер присвоит его Вам, если это" +
                                     " возможно.\n\nДля выхода из режима смены имени нажмите кнопку «Сменить ник» ещё раз.\n",
            ALERT_TITLE = WNDTITLE_APPNAME,
            ALERT_HEADER_BAD_NICKNAME = "Некорректное имя пользователя",
            ALERT_HEADER_LOGINERROR = "Ошибка авторизации.",
            ALERT_HEADER_ERROR = "Ошибка!",
            ALERT_HEADER_ADDRESSEE = "Не выбран получатель сообщения.",
            ALERT_HEADER_EMPTY_MESSAGE = "Пустое сообщение",
            ALERT_HEADER_RENAMING = "Смена имени в чате.",
            ALERT_BAN_NICKNAME_SPECIFIED = "\nУказанное имя пользователя некорректно или уже используется.",
            ALERT_UNABLE_TO_SEND_MESSAGE = "Не удадось отправить сообщение.",
            ALERT_ADDRESSEE_NOTSELECTED = "Выберите получателя сообщения в списке участников чата и попробуйте снова.",
            ALERT_EMPTY_MESSAGE = "Введённое сообщение пустое или содержит только пробельные символы.",
            ALERT_CONFIRM_NEW_NICKNAME = "Подтвердите смену вашего имени. Новое имя:\n%s"            ;

    private final static boolean
                CAN_CHAT  = true, CANNOT_CHAT   = !CAN_CHAT,
                LOGED_IN  = true, LOGED_OFF     = !LOGED_IN,
                SEND_EXIT = true, DONTSEND_EXIT = !SEND_EXIT,
                MODE_PRIVATE = true, MODE_PUBLIC = !MODE_PRIVATE,
                MODE_CHANGE_NICKNAME = true, MODE_KEEP_NICKNAME = !MODE_CHANGE_NICKNAME,
                ANSWER_NO = false, ANSWER_YES = !ANSWER_NO,
                TIPS_ON = true, TIPS_OFF = !TIPS_ON;

    private final int INPUT_THREAD_SLEEPINTERVAL = 250;

    private Socket clientSideSocket;
    private DataInputStream dis;
    private DataOutputStream dos;
    private String nickname;
    private Thread threadIntputStream,
                   threadParent;
    private boolean appGettingOff = false,
                    loginState = LOGED_OFF,
                    privateMode = MODE_PUBLIC,
                    changeNicknameMode = MODE_KEEP_NICKNAME,
                    tipsMode = TIPS_ON;

    @FXML TextArea txtareaMessages;
    @FXML TextField txtfieldUsernameField, txtfieldMessage;
    @FXML PasswordField txtfieldPassword;
    @FXML Button buttonLogin;
    @FXML HBox hboxMessagePanel,
               hboxPassword,
               hboxToolbar;
    @FXML ToggleButton btnToolbarPrivate,
                       btnToolbarChangeNickname,
                       btnToolbarTips;
    @FXML VBox vboxClientsList;
    @FXML Text txtIntroduction;
    @FXML ListView<String> listviewClients;


    @Override public void initialize (URL location, ResourceBundle resources)
    {
        threadParent = Thread.currentThread();
        updateUserInterface (CANNOT_CHAT);
        txtareaMessages.appendText (WELCOME_TO_MARCHCHAT);
    }// initialize ()


// Изменяем атрибуты элементов управления так, чтобы пользователь мог пользоваться чатом, но не мог
// изменить своё имя. Или наоборот: чтобы мог ввести своё имя, но не мог пользоваться чатом.
    private void updateUserInterface (boolean canChat)
    {
        Platform.runLater(()->{
            txtfieldUsernameField.setDisable (canChat == CAN_CHAT);
            buttonLogin.setText (canChat == CAN_CHAT ? "Выйти" : "Войти");
            btnToolbarPrivate.setSelected (privateMode);
            btnToolbarChangeNickname.setSelected (changeNicknameMode);

            if (canChat == CAN_CHAT)
            {   txtIntroduction.setText (TXT_YOU_LOGED_IN_AS);
                txtfieldUsernameField.setText(nickname);
                txtfieldMessage.requestFocus();
            }
            else
            {   txtIntroduction.setText (TXT_INTRODUCE_YOURSELF);
                listviewClients.getItems().clear();
                txtfieldUsernameField.requestFocus();
            }
            hboxPassword.setManaged (canChat != CAN_CHAT);
            hboxPassword.setVisible (canChat != CAN_CHAT);
            hboxMessagePanel.setManaged (canChat == CAN_CHAT);
            hboxMessagePanel.setVisible (canChat == CAN_CHAT);
            hboxToolbar.setManaged (canChat == CAN_CHAT);
            hboxToolbar.setVisible (canChat == CAN_CHAT);
            vboxClientsList.setVisible(canChat == CAN_CHAT);
            vboxClientsList.setManaged(canChat == CAN_CHAT);
        });
    }// updateUserInterface ()


    private void connect ()
    {
        appGettingOff = false;
        if (clientSideSocket == null || clientSideSocket.isClosed())
        try
        {   clientSideSocket = new Socket (SERVER_ADDRESS, SERVER_PORT);
            dis = new DataInputStream (clientSideSocket.getInputStream());
            dos = new DataOutputStream (clientSideSocket.getOutputStream());

            threadIntputStream = new Thread(() -> runTreadInputStream());
            threadIntputStream.start();
        }
        catch (IOException ioe)
        {   onCmdExit (PROMPT_UNABLE_TO_CONNECT);
            ioe.printStackTrace();
        }
    }// connect ()

//Закрытие сокета и обнуление связанных с ним переменных. + Внесение изменений в
// интерфейс (чтобы юзер не мог пользоваться чатом).
    private void disconnect ()
    {
        if (clientSideSocket != null && !clientSideSocket.isClosed())
        try
        {   clientSideSocket.close();
        }
        catch(IOException e) {e.printStackTrace();}

        clientSideSocket = null;
        dis = null;
        dos = null;
        threadIntputStream = null;
    }// disconnect ()


/*  Я попробовал исключить пару dis.available()—Thread.sleep() из этого метода, и потоки действительно
исправно останавливались при закрытии сокета на к.-л. конце соединения. Но, к сожалению, вылезла
другая проблема, которую я не смог решить иначе как вернув всё на место.

    Не могу пока себе это объяснить, но, если в dis.readUTF() затыкает собою канал связи,
то начинаются проблемы с синхронизацией команд (я думаю, что это именно рассинхронизация). Команда
у меня является последовательностью сообщений, а не единой строкой. В результате «недуга» части
команд начинают приходить не к тем обработчикам.
*/
    private String readInputStreamUTF ()
    {
        String msg = null;
        int timer = 0;
        try
        {   while (!appGettingOff)
            if (dis.available() > 0)
            {
                msg = dis.readUTF();
                break;
            }
            else
            {   Thread.sleep (INPUT_THREAD_SLEEPINTERVAL); //< Использование dis.available() нуждается в притормаживании…

                timer ++;   //< каждые 5 сек. проверяем, не работает ли наш поток впустую
                if (timer > 5000 / INPUT_THREAD_SLEEPINTERVAL)
                {
                    if (!threadParent.isAlive())    //< проверяем родительский поток
                        break;
                    dos.writeUTF(CMD_ONLINE);       //< «пингуем» сервер
                    timer = 0;
                }
            }
        }
        catch (IOException | InterruptedException e)  {  onCmdExit (PROMPT_CONNECTION_LOST);  }
        return msg;
    }

    private void runTreadInputStream ()
    {
        String  msg;
        while (!appGettingOff && (msg = readInputStreamUTF()) != null)
        {
            msg = msg.trim().toLowerCase();

            if (msg.isEmpty() || msg.equalsIgnoreCase (CMD_ONLINE))
                continue;

            switch (msg.toLowerCase())
            {
                case CMD_CHAT_MSG:  onCmdChatMsg(readInputStreamUTF());
                    break;
                case CMD_CLIENTS_LIST_CHANGED:  syncSendMessageToServer (CMD_CLIENTS_LIST);
                    break;
                case CMD_CLIENTS_LIST:  onCmdClientsList();
                    break;
                case CMD_LOGIN:    onCmdLogIn ();
                    break;
                case CMD_BADLOGIN:  onCmdBadLogin();
                    break;
                case CMD_CHANGE_NICKNAME:   onCmdChangeNickname();
                    break;
                case CMD_BADNICKNAME:    onCmdBadNickname();
                    break;
                case CMD_EXIT:    onCmdExit (PROMPT_YOU_ARE_LOGED_OFF);
                    break;
                case CMD_PRIVATE_MSG:   onCmdPrivateMsg(readInputStreamUTF(), readInputStreamUTF());
                    break;
                default:   throw new UnsupportedOperationException (
                              "ERROR @ runTreadInputStream() : незарегистрированное сообщение:\n\t" + msg);
            }
        }//while
        disconnect();
    }

// Обработчик команды CMD_BADLOGIN.
    void onCmdBadLogin ()
    {
        alertWarning (ALERT_HEADER_LOGINERROR, readInputStreamUTF());
        onCmdExit (PROMPT_CONNECTION_LOST);
    }

// Обработчик команды CMD_BADNICKNAME.
    void onCmdBadNickname ()
    {
        alertWarning (ALERT_HEADER_RENAMING, readInputStreamUTF());
    }

// Обработчик команды CMD_PRIVATE_MSG.
    void onCmdPrivateMsg (String from, String msg)
    {
        Platform.runLater(()->{
            txtareaMessages.appendText (String.format (FORMAT_TOYOU_PRIVATE_FROM, from, msg));
        });
    }

// Обработчик команды CMD_CHAT_MSG.
    void onCmdChatMsg (String msg)
    {
        Platform.runLater(()->{
            txtareaMessages.appendText ('\n'+ msg);
        });
    }

// Обработчик команды CMD_EXIT (также вызывается из onactionLogin() при нажатии кнопки Вход/Выход).
    private void onCmdExit (String prompt)
    {
        appGettingOff = true;
        loginState = LOGED_OFF;
        Platform.runLater(()->{
            txtareaMessages.appendText (prompt);
        });
        updateUserInterface (CANNOT_CHAT);
    }

// Обработчик команды CMD_LOGIN (сообщение приходит в случае успешной авторизации).
    void onCmdLogIn ()
    {
        nickname = readInputStreamUTF();
        loginState = LOGED_IN;
        updateUserInterface (CAN_CHAT);
        syncSendMessageToServer (CMD_CLIENTS_LIST);
    }

// Обработчик команды CMD_CHANGE_NICKNAME
    private void onCmdChangeNickname ()
    {
        nickname = readInputStreamUTF();
        Platform.runLater(()->{
            txtfieldUsernameField.setText (nickname);
            onactionChangeNickname();
            txtfieldMessage.clear();
            txtfieldMessage.requestFocus();
        });
    }

// Обработчик команды CMD_CLIENTS_LIST
    private void onCmdClientsList ()
    {
        int size = Integer.parseInt(readInputStreamUTF());
        String[] tmplist = new String[size];

        for (int i=0; i<size; i++)
            tmplist[i] = readInputStreamUTF();

        Platform.runLater(()->{
            listviewClients.getItems().clear();
            for (String s : tmplist)
                listviewClients.getItems().add(s);
        });
    }


// Обработка ввода пользователем своего имени для чата (метод вызывается по нажатию кнопки «Вход/Выход»).
    public void onactionLogin (ActionEvent actionEvent)
    {
        if (loginState == LOGED_OFF)
        {
            String login = txtfieldUsernameField.getText(),
                   password = txtfieldPassword.getText();
            boolean badLogin = login == null || login.isEmpty();

            if (badLogin || password == null || password.isEmpty())
            {
                alertWarning(ALERT_HEADER_BAD_NICKNAME, ALERT_BAN_NICKNAME_SPECIFIED);
                if (badLogin)   txtfieldUsernameField.requestFocus();
                else            txtfieldPassword.requestFocus();
            }
            else
            {   connect();
                syncSendMessageToServer (CMD_LOGIN, login, password);
            }
        }
        else //Кнопка «Войти» используется и для выхода из чата.
        {   syncSendMessageToServer (CMD_EXIT);
            onCmdExit (PROMPT_YOU_ARE_LOGED_OFF);
            disconnect ();
        }
    }


//Обработка вводимых пользователем сообщений. (У пользователя нет возможности вводить команды руками, —
// для управления приложением предусмотрены кнопки.)
    @FXML public void onactionSendMessage ()
    {
        String msg = txtfieldMessage.getText();
        boolean boolSent = false;

// Три режима обработки сообщений:
//    простой чат, приватный чат, смена ника (отправка нового ника как сообщение на сервер).
        if (msg == null || msg.trim().isEmpty())
        {
            if (tipsMode == TIPS_ON)   alertWarning (ALERT_HEADER_EMPTY_MESSAGE, ALERT_EMPTY_MESSAGE);
        }
        else if (changeNicknameMode == MODE_CHANGE_NICKNAME) //------------------- смена ника
        {
            String message = String.format (ALERT_CONFIRM_NEW_NICKNAME, msg);
            if (alertConfirmationYesNo (ALERT_HEADER_RENAMING, message) == ANSWER_YES)
                syncSendMessageToServer (CMD_CHANGE_NICKNAME, msg);
        }
        else if (privateMode == MODE_PRIVATE) //--------------- приватный чат
        {
            String name = listviewClients.getSelectionModel().getSelectedItem();

            if (name == null || name.isEmpty())
                alertWarning (ALERT_HEADER_ADDRESSEE, ALERT_ADDRESSEE_NOTSELECTED); //< если получатель не выбран
            else
            {   boolSent = syncSendMessageToServer (CMD_PRIVATE_MSG, name, msg);
                txtareaMessages.appendText (String.format (FORMAT_YOU_PRIVATE_TO, name, msg));
            }
        }
        else boolSent = syncSendMessageToServer (CMD_CHAT_MSG, msg); // --------------- простой чат

        if (boolSent)
        {   txtfieldMessage.clear();
            txtfieldMessage.requestFocus();
        }
    }


//(Вспомогательный метод.) Шлём на сервер строки отдельными сообщениями.
    private synchronized boolean syncSendMessageToServer (String ... lines)
    {
        boolean boolSent = false;
        if (lines != null  &&  lines.length > 0  &&  dos != null)
        try
        {   for (String msg : lines)
                dos.writeUTF(msg);
            boolSent = true;
        }
        catch (IOException e) { alertWarning (ALERT_HEADER_ERROR, ALERT_UNABLE_TO_SEND_MESSAGE); }
        return boolSent;
    }


    @Override public String toString() { return "Controller:"+ nickname; }

// Вкл/Выкл приватного режима.
    public void onactionTogglePrivateMode ()
    {
        privateMode = !privateMode;
        btnToolbarPrivate.setSelected (privateMode);
        if (tipsMode == TIPS_ON)
            txtareaMessages.appendText (privateMode ? PROMPT_PRIVATE_MODE_IS_ON : PROMPT_PRIVATE_MODE_IS_OFF);
    }

// Вкл/Выкл режима смены имени.
    public void onactionChangeNickname ()
    {
        changeNicknameMode = !changeNicknameMode;
        btnToolbarChangeNickname.setSelected (changeNicknameMode);
        if (changeNicknameMode == MODE_CHANGE_NICKNAME && tipsMode == TIPS_ON)
            txtareaMessages.appendText (PROMPT_CHANGE_NICKNAME);
    }

    public static void alertWarning (String header, String msg)
    {
        if (msg != null)
        {   Platform.runLater(()->{
                Alert a = new Alert (Alert.AlertType.WARNING, msg, ButtonType.CLOSE);
                a.setTitle (ALERT_TITLE);
                a.setHeaderText (header);
                a.showAndWait();
            });
        }
    }

    public static boolean alertConfirmationYesNo (String header, String message)
    {
        boolean boolYes = ANSWER_NO;
        Alert a = new Alert (Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        a.setHeaderText (header);
        a.setTitle (ALERT_TITLE);
        Optional<ButtonType> option = a.showAndWait();

        if (option.isPresent() && option.get() == ButtonType.YES)
            boolYes = ANSWER_YES;
        return boolYes;
    }

// Вкл/Выкл подсказок.
    public void onactionTips ()
    {
        tipsMode = !tipsMode;
        if (tipsMode == TIPS_ON)
            txtareaMessages.appendText (PROMPT_TIPS_ON);
    }
}
