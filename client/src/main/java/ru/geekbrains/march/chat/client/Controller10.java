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
import java.io.Serializable;
import java.net.Socket;
import java.net.URL;
import java.security.InvalidParameterException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

import static ru.geekbrains.march.chat.client.Main10.WNDTITLE_APPNAME;
import static ru.geekbrains.march.chat.server.ServerApp10.*;

public class Controller10 implements Initializable
{
    private final static String
            TXT_INTRODUCE_YOURSELF = "Представьтесь:  ",
            TXT_YOU_LOGED_IN_AS = "Вы вошли в чат как: ",
            WELCOME_TO_MARCHCHAT = "Добро пожаловать в March Chat!",
            FORMAT_CHATMSG = "\n%s:\n\t%s",
            FORMAT_PRIVATEMSG_TOYOU = "\n[приватно c %s] Вам:\n\t%s",
            FORMAT_PRIVATEMSG_FROMYOU = "\n[приватно c %s] от Вас:\n\t%s",
            PROMPT_CONNECTION_ESTABLISHED = "\nСоединение с сервером установлено.",
            PROMPT_TIPS_ON = "\nПодсказки включены.",
            PROMPT_UNABLE_TO_CONNECT = "\nНе удалось подключиться.",
            PROMPT_YOU_ARE_LOGED_OFF = "\nВы вышли из чата.",
            PROMPT_CONNECTION_LOST = "\nСоединение разорвано.",
            PROMPT_PRIVATE_MODE_IS_ON = "\n\nВы вошли в приватный режим. Ваши сообщения будут видны только выбранному собеседнику.",
            PROMPT_PRIVATE_MODE_IS_OFF = "\n\nВы вышли из приватного режима. Ваши сообщения будут видны всем участникам чата.",
            //PROMPT_STATISTICS = "\nсообщений = ",
            PROMPT_CONFIRM_NEW_NICKNAME = "Подтвердите смену вашего имени. Новое имя:\n%s",
            PROMPT_EMPTY_MESSAGE = "Введённое сообщение пустое или содержит только пробельные символы.",
            PROMPT_BAN_NICKNAME_SPECIFIED = "\nУказанное имя пользователя некорректно или уже используется.",
            PROMPT_UNABLE_TO_SEND_MESSAGE = "Не удадось отправить сообщение.",
            PROMPT_ADDRESSEE_NOTSELECTED = "Выберите получателя сообщения в списке участников чата и попробуйте снова.",
            PROMPT_CHANGE_NICKNAME = "\n\nОправьте новое имя как сообщение. Чат-сервер присвоит его Вам, если это" +
                                     " возможно.\n\nДля выхода из режима смены имени нажмите кнопку «Сменить ник» ещё раз.\n",
            ALERT_TITLE = WNDTITLE_APPNAME,
            ALERT_HEADER_BAD_NICKNAME = "Некорректное имя пользователя",
            ALERT_HEADER_LOGINERROR = "Ошибка авторизации.",
            ALERT_HEADER_ERROR = "Ошибка!",
            ALERT_HEADER_ADDRESSEE = "Не выбран получатель сообщения.",
            ALERT_HEADER_EMPTY_MESSAGE = "Пустое сообщение",
            ALERT_HEADER_RENAMING = "Смена имени в чате."
            ;
    private final static boolean
                CAN_CHAT  = true, CANNOT_CHAT   = !CAN_CHAT,
                LOGED_IN  = true, LOGED_OFF     = !LOGED_IN,
                SEND_EXIT = true, DONTSEND_EXIT = !SEND_EXIT,
                MODE_PRIVATE = true, MODE_PUBLIC = !MODE_PRIVATE,
                PRIVATE_MSG = true, PUBLIC_MSG = !PRIVATE_MSG,
                INPUT_MSG = true, OUTPUT_MSG = !INPUT_MSG,
                MODE_CHANGE_NICKNAME = true, MODE_KEEP_NICKNAME = !MODE_CHANGE_NICKNAME,
                ANSWER_NO = false, ANSWER_YES = !ANSWER_NO,
                TIPS_ON = true, TIPS_OFF = !TIPS_ON
                ;
    private final int INPUT_THREAD_SLEEPINTERVAL = 250;

    private Socket clientSideSocket;
    private DataInputStream dis;
    private DataOutputStream dos;
    private String nickname, login; //< логин нужен для составления имени файла истории чата.
    private Thread threadIntputStream,
                   threadParent;
    private boolean chatGettingClosed,
                    loginState = LOGED_OFF,
                    privateMode = MODE_PUBLIC,
                    changeNicknameMode = MODE_KEEP_NICKNAME,
                    tipsMode = TIPS_ON
                    ;
    private MessageStenographer<ChatMessage> stenographer;

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


    public static class ChatMessage implements Serializable
    {
        private final String name, text;   //< Serializable
        private final boolean inputMsg, privateMsg;
        private final LocalDateTime ldt = LocalDateTime.now();      //< Serializable (сейчас не используется)

        public ChatMessage (String name, String message, boolean input, boolean prv)
        {
            if (!validateStrings (name, message))    throw new InvalidParameterException();
            this.name = name;
            text = message;
            inputMsg = input;
            privateMsg = prv;
        }

        @Override public String toString ()
        {
            String format = FORMAT_CHATMSG;
            if (privateMsg)
                format = inputMsg ? FORMAT_PRIVATEMSG_TOYOU : FORMAT_PRIVATEMSG_FROMYOU;
            return String.format (format, name, text);
        }
    }// class ChatMessage


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
            } else
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

// Подключение к серверу + создание и запуск потока для обработки сообщений от сервера.
    private void connect ()
    {
        if (clientSideSocket == null || clientSideSocket.isClosed())
        try
        {   clientSideSocket = new Socket (SERVER_ADDRESS, SERVER_PORT);
            dis = new DataInputStream (clientSideSocket.getInputStream());
            dos = new DataOutputStream (clientSideSocket.getOutputStream());
        }
        catch (IOException ioe)
        {   onCmdExit (PROMPT_UNABLE_TO_CONNECT);
            ioe.printStackTrace();
        }
        chatGettingClosed = false;
        threadIntputStream = new Thread(() -> runTreadInputStream());
        threadIntputStream.start();
    }// connect ()

//Закрытие сокета и обнуление связанных с ним переменных. + Внесение изменений в
// интерфейс (чтобы юзер не мог пользоваться чатом).
    private void disconnect ()
    {
        if (clientSideSocket != null && !clientSideSocket.isClosed())
            try{
                clientSideSocket.close();
            }catch(IOException e) {e.printStackTrace();}

        clientSideSocket = null;
        dis = null;
        dos = null;
        threadIntputStream = null;

        if (stenographer != null)  stenographer.close();
        stenographer = null;
        login = null;
    }// disconnect ()

// Считываем из входного потока сообщение с кодом команды (остальные части команды, если они есть, считаем
// в обработчике.)
    private String readInputStreamUTF ()
    {
        String msg = null;
        int timer = 0;

/*  Я попробовал исключить пару dis.available()—Thread.sleep() из этого метода, и потоки действительно
исправно останавливались при закрытии сокета на к.-л. конце соединения. Но, к сожалению, вылезла
другая проблема, которую я не смог решить иначе как вернув всё на место.

    Не могу пока себе это объяснить, но, если в dis.readUTF() затыкает собою канал связи,
то начинаются проблемы с синхронизацией команд (я думаю, что это именно рассинхронизация). Команда
у меня является последовательностью сообщений, а не единой строкой. В результате «недуга» части
команд начинают приходить не к тем обработчикам.
*/        try
        {   while (!chatGettingClosed)
            if (dis.available() > 0)
            {
                msg = dis.readUTF();
                break;
            }
            else
            {   Thread.sleep(INPUT_THREAD_SLEEPINTERVAL);
                timer ++;
                if (timer > 5000 / INPUT_THREAD_SLEEPINTERVAL)
                {
                    if (!threadParent.isAlive())
                        break;
                    dos.writeUTF(CMD_ONLINE);
                    timer = 0;
                }
            }
        }
        catch (IOException | InterruptedException e)
        {   onCmdExit (PROMPT_CONNECTION_LOST);
            e.printStackTrace();
        }
        return msg;
    }// readInputStreamUTF ()

// Run-метод потока threadIntputStream. Считываем сообщения из входного канала соединения и обрабатываем их.
    private void runTreadInputStream ()
    {
        String  msg;
        while (!chatGettingClosed && (msg = readInputStreamUTF()) != null)
        {
            msg = msg.trim().toLowerCase();

            if (msg.isEmpty() || msg.equalsIgnoreCase (CMD_ONLINE))
                continue;

            switch (msg.toLowerCase())
            {
                case CMD_CHAT_MSG:   onCmdChatMsg();
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
                case CMD_PRIVATE_MSG:   onCmdPrivateMsg();
                    break;
                case CMD_CONNECTED:   onCmdConnected();
                    break;
                default:   throw new UnsupportedOperationException (
                              "ERROR @ runTreadInputStream() : незарегистрированное сообщение:\n\t" + msg);
            }
        }//while
        disconnect();
    }// runTreadInputStream ()

// Обработчик команды CMD_CONNECTED. Информируем пользователя об устанке соединения с сервером.
    void onCmdConnected ()
    {   Platform.runLater(()->{
            txtareaMessages.appendText (PROMPT_CONNECTION_ESTABLISHED);
        });
    }// onCmdConnected ()

// Обработчик команды CMD_BADLOGIN (установленное соединение не рвём).
    void onCmdBadLogin ()
    {   alertWarning (ALERT_HEADER_LOGINERROR, readInputStreamUTF());
        onCmdExit (PROMPT_CONNECTION_LOST);
    }// onCmdBadLogin ()

// Обработчик команды CMD_BADNICKNAME.
    void onCmdBadNickname ()  {  alertWarning (ALERT_HEADER_RENAMING, readInputStreamUTF());  }// onCmdBadNickname ()

// Обработчик команды CMD_PRIVATE_MSG (здесь обрабатываются только входящие приватные сообщения).
    void onCmdPrivateMsg ()
    {
        String name = readInputStreamUTF(),
               message = readInputStreamUTF();
        ChatMessage cm = new ChatMessage (name, message, INPUT_MSG, PRIVATE_MSG);
        if (stenographer != null) stenographer.append (cm);

        Platform.runLater(()->{
            txtareaMessages.appendText (cm.toString());
        });
    }// onCmdPrivateMsg ()

// Обработчик команды CMD_CHAT_MSG (здесь обрабатываются входящие и исходящие публичные сообщения).
    void onCmdChatMsg ()
    {
        String name = readInputStreamUTF(),
               message = readInputStreamUTF();
        ChatMessage cm = new ChatMessage (name, message, name.equals(nickname), PUBLIC_MSG);
        if (stenographer != null) stenographer.append (cm);

        Platform.runLater(()->{
            txtareaMessages.appendText (cm.toString());
        });
    }// onCmdChatMsg ()

// Обработчик команды CMD_EXIT (также вызывается из onactionLogin() при нажатии кнопки Вход/Выход, и из onCmdBadLogin()).
    private void onCmdExit (String prompt)
    {
        chatGettingClosed = true;   //< это заставит runTreadInputStream() завершиться вызовом disconnect().
        loginState = LOGED_OFF;

        String name = nickname != null ? nickname : "(???)";
        ChatMessage cm = new ChatMessage (name, prompt, INPUT_MSG, PUBLIC_MSG);
        if (stenographer != null) stenographer.append (cm);

        Platform.runLater(()->{
            txtareaMessages.appendText (cm.toString());
        });
        updateUserInterface (CANNOT_CHAT);
/*  Всесь процесс выхода и чата (не из приложения) заключается в трёх действиях:
    - отправка сообщения CMD_EXIT на сервер (если выход инициализировал клиент);
    - вызов onCmdExit для изменения некоторых переменных;
    - вызов disconnect().
*/  }// onCmdExit ()


// Обработчик команды CMD_LOGIN (сообщение приходит в случае успешной авторизации).
    void onCmdLogIn ()
    {   nickname = readInputStreamUTF();
        loginState = LOGED_IN;
        readChatStorage();    //< Считываем историю чата из файла
        updateUserInterface (CAN_CHAT);
        syncSendMessageToServer (CMD_CLIENTS_LIST);
        System.out.print("\n\tподключен "+ nickname); //< для отладки
    }// onCmdLogIn ()

// Обработчик команды CMD_CHANGE_NICKNAME.
    private void onCmdChangeNickname ()
    {
        nickname = readInputStreamUTF();
        Platform.runLater(()->{
            txtfieldUsernameField.setText (nickname);
            onactionChangeNickname();   //< Отщёлкиваем кнопку «Сменить имя» в исходное состояние.
            txtfieldMessage.clear();//< перед отправкой запроса на сервер мы оставили имя в поле ввода.
                                    // Теперь нужно его оттуда убрать.
            txtfieldMessage.requestFocus();
        });
        System.out.printf ("\n\tпоменял имя на %s", nickname); //< для отладки
    }// onCmdChangeNickname ()

// Обработчик команды CMD_CLIENTS_LIST.
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
    }// onCmdClientsList ()


// Обработка ввода пользователем логина и пароля для входа чат, или выход из чата (метод вызывается по
// нажатию кнопки «Вход/Выход».
    public void onactionLogin (ActionEvent actionEvent)
    {
        //Platform.runLater(()->{
            if (loginState == LOGED_OFF)
            {
                String login = txtfieldUsernameField.getText(),
                       password = txtfieldPassword.getText();
                boolean badLogin = login == null || login.isEmpty();

                if (badLogin || password == null || password.isEmpty())
                {
                    alertWarning (ALERT_HEADER_BAD_NICKNAME, PROMPT_BAN_NICKNAME_SPECIFIED);
                    if (badLogin)   txtfieldUsernameField.requestFocus();
                    else            txtfieldPassword.requestFocus();
                }
                else
                {   connect();
                    this.login = login; //< запоминаем логин, под которым регистрируемся (для имени файла)
                    syncSendMessageToServer (CMD_LOGIN, login, password);
                }
            }
            else // Выход из чата производится по такой трёхэтапной схеме (где-то эти этапы могут быть
            {    // разбросаны по методам или отсутстовать, но последовательность должна сохраняться):
                syncSendMessageToServer (CMD_EXIT);   //< выполняется при необходимости
                onCmdExit (PROMPT_YOU_ARE_LOGED_OFF);
                disconnect ();
            }
        //});
    }// onactionLogin ()


//Обработка вводимых пользователем сообщений. (У пользователя нет возможности вводить команды руками, —
// для управления приложением предусмотрены кнопки.)
    @FXML public void onactionSendMessage ()
    {
        //Platform.runLater(()->{
            String message = txtfieldMessage.getText();
            boolean boolSent = false;

            if (message == null || message.trim().isEmpty())
            {
                if (tipsMode == TIPS_ON)   alertWarning (ALERT_HEADER_EMPTY_MESSAGE, PROMPT_EMPTY_MESSAGE);
            }
            else if (changeNicknameMode == MODE_CHANGE_NICKNAME) //< включен режим смены имени
            {
                if (alertConfirmationYesNo (ALERT_HEADER_RENAMING, String.format(PROMPT_CONFIRM_NEW_NICKNAME, message)) == ANSWER_YES)
                    syncSendMessageToServer (CMD_CHANGE_NICKNAME, message);
            }
            else if (privateMode == MODE_PRIVATE) // исходящие приватные сообщения
            {
                String name = listviewClients.getSelectionModel().getSelectedItem();

                if (name == null || name.isEmpty())
                    alertWarning(ALERT_HEADER_ADDRESSEE, PROMPT_ADDRESSEE_NOTSELECTED); //< если получатель не выбран
                else
                if (boolSent = syncSendMessageToServer (CMD_PRIVATE_MSG, name, message))
                {
                    ChatMessage cm = new ChatMessage (name, message, OUTPUT_MSG, PRIVATE_MSG);
                    if (stenographer != null) stenographer.append (cm);
                    txtareaMessages.appendText (cm.toString());
                }
            }
            else boolSent = syncSendMessageToServer (CMD_CHAT_MSG, message); // обычный режим (публичные сообщения)

            if (boolSent)
            {   txtfieldMessage.clear();
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
        catch (IOException e) { alertWarning (ALERT_HEADER_ERROR, PROMPT_UNABLE_TO_SEND_MESSAGE); }
        return boolSent;
    }// syncSendMessageToServer ()

// Обработка нажатия на кнопку «Приватно» (переключение приват. режима).
    public void onactionTogglePrivateMode ()
    {   privateMode = !privateMode;
        btnToolbarPrivate.setSelected (privateMode);
        if (tipsMode == TIPS_ON)
            txtareaMessages.appendText (privateMode ? PROMPT_PRIVATE_MODE_IS_ON : PROMPT_PRIVATE_MODE_IS_OFF);
    }

// Обработка нажатия на кнопку «Сменить ник» (переключение режима смены ника).
    public void onactionChangeNickname ()
    {   changeNicknameMode = !changeNicknameMode;
        btnToolbarChangeNickname.setSelected (changeNicknameMode);
        if (changeNicknameMode == MODE_CHANGE_NICKNAME && tipsMode == TIPS_ON)
            txtareaMessages.appendText (PROMPT_CHANGE_NICKNAME);
    }// onactionChangeNickname ()

// Обработка нажатия на кнопку «Подсказки» (переключение режима показа подсказок к интерфейсу).
    public void onactionTips ()
    {   tipsMode = !tipsMode;
        if (tipsMode == TIPS_ON)
            txtareaMessages.appendText (PROMPT_TIPS_ON);
    }// onactionTips ()

// Стандартное окно сообщения с кнопкой Close.
    public static void alertWarning (String header, String msg)
    {
        if (msg != null)
        Platform.runLater(()->{
            Alert a = new Alert (Alert.AlertType.WARNING, msg, ButtonType.CLOSE);
            a.setTitle (ALERT_TITLE);
            a.setHeaderText (header);
            a.showAndWait();
        });
    }// alertWarning ()

// Стандартное окно сообщения с кнопками Да и Нет.
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
    }// alertConfirmationYesNo ()

// (Вспомогательная.) Считываем лог чата из файла при помощи объекта MessageStenographer<ChatMessage>.
// Сейчас имя файла состоит из логина пользователя и расширения chat.
    void readChatStorage ()
    {
        stenographer = new MessageStenographer<> (login +".chat");
        List<ChatMessage> cmlist = stenographer.read();

        Platform.runLater(()->{
            txtareaMessages.clear(); //< очищаем окно чата (чтобы не мучаться, т.к. юзер может и под другой
            for (Object cm : cmlist) //           учёткой перезайти, для которой есть другой файл истории…)
                txtareaMessages.appendText (cm.toString());
        });
    }// readChatStorage ()

    @Override public String toString() { return "Controller9:"+ nickname; }

//(Вспомогательная.) Проверяет строку на пригодность для использования в качестве логина, пароля, ника и т.п.
    public static boolean validateStrings (String ... lines)
    {
        if (lines != null)
        for (String s : lines)
            if (s == null || s.trim().isEmpty())
                return false;
        return true;
    }// validateString ()


}// class Controller9
