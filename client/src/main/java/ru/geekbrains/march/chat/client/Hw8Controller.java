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
import java.util.Optional;
import java.util.ResourceBundle;

import static ru.geekbrains.march.chat.server.ServerApp.*;

public class Hw8Controller implements Initializable
{
    private final static String
            TXT_INTRODUCE_YOURSELF = "Введите логин:  ",
            TXT_YOU_LOGED_IN_AS = "Вы вошли в чат как: ",
            FORMAT_TOYOU_PRIVATE_FROM = "\n[Вам приватно от %s]:\n\t%s",
            FORMAT_YOU_PRIVATE_TO = "\n[Приватно от Вас к %s]:\n\t%s",
            ALERT_NICKNAME_BUSY = "\nУказанное имя пользователя уже используется.",
            ALERT_UNABLE_TO_SEND_MESSAGE = "Не удадось отправить сообщение.",
            ALERT_INCORRECT_LOGIN_PASSWORD = "Введены некорректные логин или пароль.",
            ALERT_ADDRESSEE_NOTSELECTED = "Не выбран получатель приватного сообщения.\nВыберите получателя сообщения в списке участников чата и попробуйте снова.",
            ALERT_EMPTY_MESSAGE = "Введённое сообщение пустое или содержит только пробельные символы.",
            ALERT_CONFIRM_NEW_NICKNAME = "Подтвердите смену вашего имени. Новое имя:\n%s",
            PROMPT_UNABLE_TO_CONNECT = "\nНе удалось подключиться.",
            PROMPT_YOU_ARE_LOGED_OFF = "\nВы вышли из чата.",
            PROMPT_CONNECTION_LOST = "\nСоединение разорвано.",
            PROMPT_PRIVATE_MODE_IS_ON = "\n\nВы вошли в приватный режим. Ваши сообщения будут видны только выбранному собеседнику.",
            PROMPT_PRIVATE_MODE_IS_OFF = "\n\nВы вышли из приватного режима. Ваши сообщения будут видны всем участникам чата.",
            PROMPT_STATISTICS = "\nсообщений = ",
            PROMPT_CHANGE_NICKNAME = "\n\nОправьте новое имя как сообщение. Чат-сервер присвоит его Вам, если это" +
                                     " возможно.\n\nДля выхода из режима смены имени нажмите кнопку «Сменить ник» ещё раз.\n",
            PROMP_TIPS_ON = "\nПодсказки включены."
            ;

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
    private String userName;
    private Thread threadIntputStream,
                   threadParent;
    private boolean appGettingOff = false,
                    loginState = LOGED_OFF,
                    privateMode = MODE_PUBLIC,
                    changeNicknameMode = MODE_KEEP_NICKNAME,
                    tipsMode = TIPS_ON;

    @FXML TextArea txtareaMessages;
    @FXML TextField txtfieldUsername,
                    txtfieldPassword,
                    txtfieldMessage;
    @FXML Button buttonLogin;
    @FXML HBox  hboxPassword,
                hboxMessagePanel,
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
    }// initialize ()


// Изменяем атрибуты элементов управления так, чтобы пользователь мог пользоваться чатом, но не мог
// изменить своё имя. Или наоборот: чтобы мог ввести своё имя, но не мог пользоваться чатом.
    private void updateUserInterface (boolean canChat)
    {
        Platform.runLater(()->{
            txtfieldUsername.setDisable(canChat == CAN_CHAT);
            buttonLogin.setText (canChat == CAN_CHAT ? "Выйти" : "Войти");
            btnToolbarPrivate.setSelected (privateMode);
            btnToolbarChangeNickname.setSelected (changeNicknameMode);

            if (canChat == CAN_CHAT)
            {
                txtIntroduction.setText (TXT_YOU_LOGED_IN_AS);
                txtfieldUsername.setText(userName);
                txtfieldMessage.requestFocus();
            }
            else
            {
                txtIntroduction.setText (TXT_INTRODUCE_YOURSELF);
                listviewClients.getItems().clear();
                txtfieldUsername.requestFocus();
            }
            hboxPassword.setManaged (canChat != CAN_CHAT);
            hboxPassword.setVisible (canChat != CAN_CHAT);
            hboxToolbar.setManaged (canChat == CAN_CHAT);
            hboxToolbar.setVisible (canChat == CAN_CHAT);
            hboxMessagePanel.setManaged (canChat == CAN_CHAT);
            hboxMessagePanel.setVisible (canChat == CAN_CHAT);
            vboxClientsList.setVisible(canChat == CAN_CHAT);
            vboxClientsList.setManaged(canChat == CAN_CHAT);
        });
    }// updateUserInterface ()


    private void connect ()
    {
        appGettingOff = false;

        if (clientSideSocket == null || clientSideSocket.isClosed())
        try
        {
            clientSideSocket = new Socket (SERVER_ADDRESS, SERVER_PORT);
            dis = new DataInputStream (clientSideSocket.getInputStream());
            dos = new DataOutputStream (clientSideSocket.getOutputStream());

            threadIntputStream = new Thread(() -> runTreadInputStream());
            threadIntputStream.start();
        }
        catch (IOException ioe)
        {
            txtareaMessages.appendText(PROMPT_UNABLE_TO_CONNECT);
            ioe.printStackTrace();
        }
        syncSendMessageToServer(CMD_CLIENTS_LIST);
    }// connect ()

//Закрытие сокета и обнуление связанных с ним переменных. + Внесение изменений в
// интерфейс (чтобы юзер не мог пользоваться чатом).
    private void disconnect ()
    {
        updateUserInterface (CANNOT_CHAT);
        txtareaMessages.appendText (PROMPT_YOU_ARE_LOGED_OFF);

        if (clientSideSocket != null && !clientSideSocket.isClosed())
            try{
                clientSideSocket.close();
            }catch(IOException e) {e.printStackTrace();}

        clientSideSocket = null;
        dis = null;
        dos = null;
        threadIntputStream = null;
        System.out.println ("\n\t"+userName+" отключен"); //< для отладки
    }// disconnect ()


    private String readInputStreamUTF ()
    {
        String msg = null;
        int timer = 0;
        try
        {
            while (!appGettingOff)
            if (dis.available() > 0)
            {
                msg = dis.readUTF();
                break;
            }
            else
    //По поводу использования следующего блока (в паре с available()) я хочу заметить, что метод readUTF()
    // блокирует мой поток так, что тот не может освободиться БЕЗ ПОМОЩИ ДРУГОГО ПРИЛОЖЕНИЯ. Мне кажется
    // это настолько ненормальным, что я решил оставить нижеследующий блок как минимум до тех пор, пока
    // не обнаружу в Java аналог метода TerminateThread().
            {
                Thread.sleep (INPUT_THREAD_SLEEPINTERVAL); //< притормаживаем available()

                if (timer ++ > 5000 / INPUT_THREAD_SLEEPINTERVAL)
                {
                    if (!threadParent.isAlive())    //< проверяем родительский поток
                        break;
                    dos.writeUTF(CMD_ONLINE);       //< «пингуем» сервер
                    timer = 0;
                }
            }
        }
        catch (IOException e)
        {
            txtareaMessages.appendText(PROMPT_CONNECTION_LOST);
            e.printStackTrace();
        }
        catch (InterruptedException e) {e.printStackTrace();}

        return msg;
    }// readInputStreamUTF ()


    private void runTreadInputStream ()
    {
        String msg;
        while (!appGettingOff && (msg = readInputStreamUTF()) != null)
        {
    // Клиент и сервер теперь общаются при помощи последовательностей сообщений, первым сообщением в
    // которых всегда выступает команда.

    //  Чтобы войти в чат, пользователь должен ввести непустое уникальное (для чата) имя.
    // (Именно клиентское приложение начинает процесс регистрации. Если регистрация не состоялась,
    // то пользователь не может возможности пользоваться чатом.)

    // Введённое имя отправляется на сервер и проверяется сервером на уникальность. Если сервер
    // счёл имя подходящим, то ClientHandler возвращает Controller'у имя.

            msg = msg.trim().toLowerCase();

            if (msg.isEmpty() || msg.equalsIgnoreCase (CMD_ONLINE))
                continue;

            switch (msg.toLowerCase())
            {
                case CMD_CHAT_MSG:    txtareaMessages.appendText ('\n'+ readInputStreamUTF());
                    break;
                case CMD_CLIENTS_LIST_CHANGED:  syncSendMessageToServer (CMD_CLIENTS_LIST);
                    break;
                case CMD_CLIENTS_LIST:  onCmdClientsList();
                    break;
                case CMD_LOGIN:   onCmdLogIn();  //Сервер одобрил отправленное ему имя пользователя.
                    break;
                case CMD_BADLOGIN:    alertWarning (ALERT_INCORRECT_LOGIN_PASSWORD);
                    break;
                case CMD_CHANGE_NICKNAME:  onCmdChangeNickname();
                    break;
                case CMD_NICKNAME_BUSY:    alertWarning (ALERT_NICKNAME_BUSY);
                    break;
                case CMD_EXIT:  onactionLogout (DONTSEND_EXIT);
                    break;
                case CMD_STAT:  txtareaMessages.appendText(PROMPT_STATISTICS + readInputStreamUTF());
                    break;
                case CMD_PRIVATE_MSG:
                    txtareaMessages.appendText (String.format (FORMAT_TOYOU_PRIVATE_FROM,
                            readInputStreamUTF(),
                            readInputStreamUTF()));
                    break;
                case CMD_WHOAMI:  txtareaMessages.appendText ("\n "+ TXT_YOU_LOGED_IN_AS + readInputStreamUTF());
                    break;
                case CMD_ERROR:   txtareaMessages.appendText ("\nОшибка: " + readInputStreamUTF());
                default:
                    throw new UnsupportedOperationException (
                            "ERROR @ runTreadInputStream() : незарегистрированное сообщение:\n-\t" + msg);
            }//switch
        }//while
        disconnect();
    }// runTreadInputStream ()


// Обработчик команды CMD_LOGIN
    void onCmdLogIn ()
    {
        userName = readInputStreamUTF();
        System.out.println ("\n\t"+userName+" подключен"); //< для отладки
        loginState = LOGED_IN;
        updateUserInterface (CAN_CHAT);
    }// onCmdLogIn ()

// Обработчик команды CMD_CHANGE_NICKNAME.
    private void onCmdChangeNickname ()
    {
        userName = readInputStreamUTF();
        System.out.println ("\n\tсменил ник на "+userName); //< для отладки
        txtfieldUsername.setText(userName);
        onactionChangeNickname();
        syncSendMessageToServer (CMD_CLIENTS_LIST);
    }// onCmdChangeNickname ()

// Обработчик команды CMD_CLIENTS_LIST.
    private void onCmdClientsList ()
    {
        int size = Integer.parseInt (readInputStreamUTF());
        String[] list = new String[size];
        for (int i=0; i<size; i++)
        {
            list[i] = readInputStreamUTF();
        }
        Platform.runLater(()->{
            listviewClients.getItems().clear();
            for (String s : list)
                listviewClients.getItems().add(s);
        });
    }// onCmdClientsList ()


// Обработка ввода пользователем своего имени для чата.
    public void onactionLogin (ActionEvent actionEvent)
    {
        Platform.runLater(()->{
            if (loginState == LOGED_OFF)
            {
                String  login = txtfieldUsername.getText(),
                        password = txtfieldPassword.getText();

                if (login == null || password == null || login.isEmpty() || password.isEmpty())
                {
                    alertWarning (ALERT_INCORRECT_LOGIN_PASSWORD);
                    txtfieldUsername.requestFocus();
                }
                else
                {   connect();
                    syncSendMessageToServer (CMD_LOGIN, login, password);
                }
            }
            else onactionLogout (SEND_EXIT); //Кнопка «Войти» используется и для выхода из чата.
        });
    }// onactionLogin ()


//Обрабатываем команду выхода из чата (по кнопке, по команде и по приходу сообщения от сервера).
    private void onactionLogout (boolean sendExitMessage)
    {
        //Этот метод может быть вызван из runTreadInputStream() как реакция на приход сообщения /exit от сервера.
        if (sendExitMessage == SEND_EXIT)
            syncSendMessageToServer (CMD_EXIT);

        loginState = LOGED_OFF;
    //при нормальном течении событий disconnect() вызовется из потока threadIntputStream в самом конце, …
        appGettingOff = true;
        if (threadIntputStream == null)     //… а если потока нет, то disconnect() вызывается здесь.
            disconnect();
    }// onactionLogout ()


//Обработка вводимых пользователем сообщений. (У пользователя нет возможности вводить команды руками, —
// для управления приложением предусмотрены кнопки.)
    @FXML public void onactionSendMessage ()
    {
        //Platform.runLater(()->{
            String msg = txtfieldMessage.getText();
            boolean boolSent = false;

            if (msg == null || msg.trim().isEmpty())
            {
                if (tipsMode == TIPS_ON)   alertWarning (ALERT_EMPTY_MESSAGE);
            }
            else if (changeNicknameMode == MODE_CHANGE_NICKNAME)
            {
                String message = String.format (ALERT_CONFIRM_NEW_NICKNAME, msg);
                if (alertConfirmationYesNo (message) == ANSWER_YES)
                    boolSent = syncSendMessageToServer (CMD_CHANGE_NICKNAME, msg);
            }
            else if (privateMode == MODE_PRIVATE)
            {
            //Если мы в приватном режиме, то для отправки сообщения нужно выбрать получателя с списке
            //участников чата.
                String name = listviewClients.getSelectionModel().getSelectedItem();

                if (name == null || name.isEmpty())
                    alertWarning (ALERT_ADDRESSEE_NOTSELECTED); //< если получатель не выбран
                else
                {
                    boolSent = syncSendMessageToServer(CMD_PRIVATE_MSG, name, msg);
                    txtareaMessages.appendText (String.format (FORMAT_YOU_PRIVATE_TO, name, msg));
                }
            }
            else boolSent = syncSendMessageToServer(CMD_CHAT_MSG, msg);

            if (boolSent)
            {
                txtfieldMessage.clear();;
                txtfieldMessage.requestFocus();
            }
        //});
    }// onactionSendMessage ()


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
        catch (IOException e) { alertWarning (ALERT_UNABLE_TO_SEND_MESSAGE); }
        return boolSent;
    }// syncSendMessageToServer ()


    @Override public String toString() { return "Controller : "+ userName; }

//Переключение режима чата приватный/публичный.
    public void onactionTogglePrivateMode ()
    {
        privateMode = !privateMode;
        btnToolbarPrivate.setSelected (privateMode);
        if (tipsMode == TIPS_ON)
            txtareaMessages.appendText (privateMode ? PROMPT_PRIVATE_MODE_IS_ON : PROMPT_PRIVATE_MODE_IS_OFF);
    }

    public void onactionStat () {   syncSendMessageToServer (CMD_STAT);   }
    public void onactionWhoAmI ()   {   syncSendMessageToServer (CMD_WHOAMI);   }

//Включение/выключение режима смены имени.
    public void onactionChangeNickname ()
    {
        changeNicknameMode = !changeNicknameMode;
        btnToolbarChangeNickname.setSelected (changeNicknameMode);
        if (changeNicknameMode == MODE_CHANGE_NICKNAME && tipsMode == TIPS_ON)
            txtareaMessages.appendText (PROMPT_CHANGE_NICKNAME);
    }

//Вывод предупреждения в отдельном окне.
    public static void alertWarning (String msg)
    {
        if (msg != null)
            Platform.runLater(()->{
                new Alert (Alert.AlertType.WARNING, msg, ButtonType.CLOSE).showAndWait();
            });
    }// alertWarning ()

//Запрос подтверждения в отдельном окне.
    public static boolean alertConfirmationYesNo (String message)
    {
        boolean boolYes = ANSWER_NO;
        Optional<ButtonType> option =
            new Alert (Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO)
            .showAndWait();

        if (option.isPresent() && option.get() == ButtonType.YES)
            boolYes = ANSWER_YES;
    //Боже! Как всё непросто!.. Или я что-то не так делаю?…

        return boolYes;
    }// alertConfirmationYesNo ()

//Включение/выключение некоторых сообщений и предупреждений, чтобы они не замусоривали окно чата,
// когда юзер разобрался с интерфейсом.
    public void onactionTips ()
    {
        tipsMode = !tipsMode;
        if (tipsMode == TIPS_ON)
            txtareaMessages.appendText (PROMP_TIPS_ON);
    }


}// class Hw8Controller
