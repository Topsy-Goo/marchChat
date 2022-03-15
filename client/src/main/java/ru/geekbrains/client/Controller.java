package ru.geekbrains.client;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.geekbrains.server.Server;

import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static ru.geekbrains.client.Main.WNDTITLE_APPNAME;
import static ru.geekbrains.server.ServerApp.*;

public class Controller implements Initializable {
    public final static String TXT_INTRODUCE_YOURSELF = "Представьтесь:  ";
    public final static String TXT_YOU_LOGED_IN_AS = "Вы вошли в чат как: ";
    public final static String EMERGENCY_EXIT_FROM_CHAT = "аварийный выход из чата";
    public final static String WELCOME_TO_MARCHCHAT = "Добро пожаловать в March Chat!";
    public final static String FORMAT_CHATMSG = "\n%s:\n\t%s";
    public final static String FORMAT_PRIVATEMSG_TOYOU = "\n[приватно c %s] Вам:\n\t%s";
    public final static String FORMAT_PRIVATEMSG_FROMYOU = "\n[приватно c %s] от Вас:\n\t%s";
    public final static String PROMPT_CONNECTION_ESTABLISHED = "\nСоединение с сервером установлено.";
    public final static String PROMPT_TIPS_ON = "\nПодсказки включены.";
    public final static String PROMPT_YOU_ARE_LOGED_OFF = "Вы вышли из чата.";
    public final static String PROMPT_CONNECTION_LOST = "\nСоединение разорвано.";
    public final static String PROMPT_PRIVATE_MODE_IS_ON = "\n\nВы вошли в приватный режим. Ваши сообщения будут видны только выбранному собеседнику.";
    public final static String PROMPT_PRIVATE_MODE_IS_OFF = "\n\nВы вышли из приватного режима. Ваши сообщения будут видны всем участникам чата.";
    public final static String PROMPT_CONFIRM_NEW_NICKNAME = "Подтвердите смену вашего имени. Новое имя:\n%s";
    public final static String PROMPT_EMPTY_MESSAGE = "Введённое сообщение пустое или содержит только пробельные символы.";
    public final static String PROMPT_BAN_NICKNAME_SPECIFIED = "\nУказанное имя пользователя некорректно или уже используется.";
    public final static String PROMPT_ADDRESSEE_NOTSELECTED = "Выберите получателя сообщения в списке участников чата и попробуйте снова.";
    public final static String PROMPT_CHANGE_NICKNAME = "\n\nОправьте новое имя как сообщение. Чат-сервер присвоит его Вам, если это возможно.\n\nДля выхода из режима смены имени нажмите кнопку «Сменить ник» ещё раз.\n";
    public final static String PROMPT_MESSAGE_TO_YOURSELF = "Вы пытаетесь отправить сообщение себе.";
    public final static String ALERT_TITLE = WNDTITLE_APPNAME;
    public final static String ALERT_HEADER_BAD_NICKNAME = "Некорректное имя пользователя";
    public final static String ALERT_HEADER_LOGINERROR = "Ошибка авторизации.";
    public final static String ALERT_HEADER_ERROR = "Ошибка!";
    public final static String ALERT_HEADER_ADDRESSEE = "Не выбран получатель сообщения.";
    public final static String ALERT_HEADER_EMPTY_MESSAGE = "Пустое сообщение";
    public final static String ALERT_HEADER_RENAMING = "Смена имени в чате.";

    private final static boolean CAN_CHAT  = true, CANNOT_CHAT = !CAN_CHAT;
    private final static boolean LOGED_IN  = true, LOGED_OFF = !LOGED_IN;
    private final static boolean SEND_EXIT = true, DONTSEND_EXIT = !SEND_EXIT;
    private final static boolean TIPS_ON   = true, TIPS_OFF = !TIPS_ON;
    private final static boolean MODE_PRIVATE = true,  MODE_PUBLIC = !MODE_PRIVATE;
    public  final static boolean PRIVATE_MSG  = true,  PUBLIC_MSG = !PRIVATE_MSG;
    private final static boolean INPUT_MSG    = true,  OUTPUT_MSG = !INPUT_MSG;
    private final static boolean ANSWER_NO    = false, ANSWER_YES = !ANSWER_NO;
    private final static boolean MODE_CHANGE_NICKNAME = true, MODE_KEEP_NICKNAME = !MODE_CHANGE_NICKNAME;
    private static final Logger LOGGER = LogManager.getLogger(Controller.class);

    @FXML VBox rootbox;
    @FXML TextArea txtareaMessages;
    @FXML TextField txtfieldUsernameField, txtfieldMessage;
    @FXML PasswordField txtfieldPassword;
    @FXML Button buttonLogin, buttonLogout;
    @FXML HBox hboxMessagePanel;
    @FXML HBox hboxPassword;
    @FXML HBox hboxToolbar;
    @FXML ToggleButton btnToolbarPrivate;
    @FXML ToggleButton btnToolbarChangeNickname;
    @FXML ToggleButton btnToolbarTips;
    @FXML VBox vboxClientsList;
    @FXML Text txtIntroduction;
    @FXML ListView<String> listviewClients;
    Network network;
    private String nickname, login; //< логин нужен для составления имени файла истории чата.
    private Thread treadInputStream;
    private Thread threadJfx;
    private MessageStenographer<ChatMessage> stenographer;
    private boolean privateMode = MODE_PUBLIC;
    private boolean changeNicknameMode = MODE_KEEP_NICKNAME;
    private boolean tipsMode = TIPS_ON;
    private boolean boolCloseRequest = false;


/** Инициализация контроллера. */
    @Override public void initialize (URL location, ResourceBundle resources) {
        LOGGER.fatal("initialize():начало ------------------------------------------");

        network = new Network();
        network.setOnConnectionFailed ((Object... objects)->closeSession((String) objects[0]));
        network.setOnSendMessageToServer ((Object... objects)->{
            //throw new RuntimeException (Network.PROMPT_UNABLE_TO_SEND_MESSAGE);
            ((Exception) objects[0]).printStackTrace(); // ничего не делаем (заготовка для заготовки)
        });
        threadJfx = Thread.currentThread();
        updateUserInterface(CANNOT_CHAT);
        txtareaMessages.appendText(WELCOME_TO_MARCHCHAT);

        LOGGER.info("initialize():конец");
    }

/** Стандартное окно сообщения с кнопкой Close. */
    public static void alertWarning (String header, String msg) {
        if (validateStrings (header, msg)) {
            Alert a = new Alert (Alert.AlertType.WARNING, msg, ButtonType.CLOSE);
            a.setTitle(ALERT_TITLE);
            a.setHeaderText(header);
            a.showAndWait();
        }
    }

/** Стандартное окно сообщения с кнопками Да и Нет.  */
    public static boolean alertConfirmationYesNo (String header, String message) {
        boolean boolYes = ANSWER_NO;
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        a.setHeaderText(header);
        a.setTitle(ALERT_TITLE);
        Optional<ButtonType> option = a.showAndWait();

        if (option.isPresent() && option.get() == ButtonType.YES) { boolYes = ANSWER_YES; }
        return boolYes;
    }

/** (Вспомогательная.) Проверяет строку на пригодность для использования в качестве логина, пароля, ника и т.п. */
    public static boolean validateStrings (String... lines) {
        boolean result = Server.validateStrings(lines);
        if (!result) { LOGGER.error("validateStrings() нашёл ошибку в следующих строках:\n" + Arrays.asList(lines).toString()); }
        return result;
    }

/** Изменяем атрибуты элементов управления так, чтобы пользователь мог пользоваться чатом, но не мог изменить своё имя. Или наоборот: чтобы мог ввести своё имя, но не мог пользоваться чатом.    */
    private void updateUserInterface (boolean canChat) {
        txtfieldUsernameField.setDisable(canChat == CAN_CHAT);
        buttonLogout.setVisible(canChat == CAN_CHAT);
        btnToolbarPrivate.setSelected(privateMode);
        btnToolbarChangeNickname.setSelected(changeNicknameMode);

        if (canChat == CAN_CHAT) {
            txtIntroduction.setText(TXT_YOU_LOGED_IN_AS);
            txtfieldUsernameField.setText(nickname);
            txtfieldMessage.requestFocus();
        }
        else {
            txtIntroduction.setText(TXT_INTRODUCE_YOURSELF);
            listviewClients.getItems().clear();
            txtfieldUsernameField.requestFocus();
        }
        hboxPassword.setManaged(canChat != CAN_CHAT);
        hboxPassword.setVisible(canChat != CAN_CHAT);
        hboxMessagePanel.setManaged(canChat == CAN_CHAT);
        hboxMessagePanel.setVisible(canChat == CAN_CHAT);
        hboxToolbar.setManaged(canChat == CAN_CHAT);
        hboxToolbar.setVisible(canChat == CAN_CHAT);
        vboxClientsList.setVisible(canChat == CAN_CHAT);
        vboxClientsList.setManaged(canChat == CAN_CHAT);
    }
//--------------------------------------------------- потоки и очередь

/** Соединение с сервером. */
    private boolean connect () {
        if (!boolCloseRequest) { //< на случай, если юзер выйдет из чата просто закрыв окно приложения.
            rootbox.getScene().getWindow().setOnCloseRequest((event)->closeSession(EMERGENCY_EXIT_FROM_CHAT));
        }
        boolCloseRequest = true;
        return network.connect();
    }

/** Закрытие сокета и обнуление связанных с ним переменных. */
    private void disconnect () { network.disconnect(); }

/** Run-метод потока threadListenServerCommands. Считываем сообщения из входного канала
соединения и помещаем их в очередь {@code inputqueue}. В очередь помещаем команду в том виде, в каком мы её получили, включая код команды. В этом нам помогает {@code queueOffer(…)}.<p>
   Работа методов {@code messageDispatcher()} и {@code runTreadInputStream()} организована так,
что они обрабатывают за цикл по одному сообщению, но на всякий случай доступ к СК (синхр.конт.)
помещён после вызова readUTF(), чтобы он, при некоторых изменениях кода, не заблокировал канал
во время захвата СК.  */
    private void runTreadInputStream () {
        LOGGER.info("runTreadInputStream() выполняется");
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            while (!Thread.currentThread().isInterrupted()) {
                String msgCode = network.readUTF().trim();
                String name, text;
                switch (msgCode) {
                    case CMD_CHAT_MSG:      //cmd + name + msg
                        name = network.readUTF();
                        text = network.readUTF();
                        executorService.execute (()->onCmdChatMsg (name, text));
                        break;
                    case CMD_PRIVATE_MSG:   //cmd + name + msg
                        name = network.readUTF();
                        text = network.readUTF();
                        executorService.execute (()->onCmdPrivatMsg (name, text));
                        break;
                    case CMD_LOGIN:         //cmd + nickname
                        name = network.readUTF();
                        executorService.execute (()->onCmdLogIn (name));
                        break;
                    case CMD_CHANGE_NICKNAME: //cmd + nickname
                        name = network.readUTF();
                        executorService.execute (()->onCmdChangeNickname (name));
                        break;
                    case CMD_CLIENTS_LIST: /* Нам передают сообщение со списком участников чата; список предваряет число-количество элементов списка (lstParts). */
                        int i = 0, listSize = Integer.parseInt (network.readUTF());
                        String[] names = new String [listSize];
                        while (i < listSize)
                            names[i++] = network.readUTF(); //строки
                        executorService.execute (()->onCmdClientsList (names));
                        break;
                    case CMD_CLIENTS_LIST_CHANGED:
                        executorService.execute (this::onCmdClientsListChanged);
                        break;
                    case CMD_BADLOGIN: //cmd + prompt
                        text = network.readUTF();
                        executorService.execute (()->onCmdBadLogin (text));
                        break;
                    case CMD_BADNICKNAME: //cmd + prompt
                        text = network.readUTF();
                        executorService.execute (()->onCmdBadNickname (text));
                        break;
                    case CMD_CONNECTED:
                        executorService.execute (this::onCmdConnected);
                        break;
                    case CMD_EXIT:
                        executorService.execute (()->onCmdExit (PROMPT_YOU_ARE_LOGED_OFF));
                        break;
                    default: {
                        LOGGER.error("runTreadInputStream() : незарегистрированное сообщение:\n\t"
                         + msgCode);
                        throw new UnsupportedOperationException (
                            "\nERROR @ runTreadInputStream() : незарегистрированное сообщение:\n\t"
                             + msgCode);
                    }
                }
            }
        }
        catch (IOException e) {     //< для DataInputStream.readUTF()
            LOGGER.error ("ERROR @ runTreadInputStream(): соединение оборвалось");
            LOGGER.throwing (Level.ERROR, e);
        }
        finally {
            LOGGER.info("runTreadInputStream() завершился (isInterrupted() == "
                        + Thread.currentThread().isInterrupted() + ")");
            //treadInputStream = null;
            executorService.shutdown();
            //executorService.awaitTermination ();
        }
    }
//------------------------- обработчики сетевых команд ----------------------------

/** Обработчик команды CMD_CHAT_MSG (здесь обрабатываются входящие и исходящие публичные сообщения). */
    boolean onCmdChatMsg (String name, String message) {
        if (!validateStrings (name, message)) {
            if (DEBUG)
                throw new RuntimeException ("ERROR @ onCmdChatMsg() : bad argument.");
            return false;
        }
        inlineAppendMessage (name, message, name.equals (nickname), PUBLIC_MSG);
        return true;
    }

    private void inlineAppendMessage (String name, String message, boolean input, boolean prv) {
        ChatMessage cm = new ChatMessage (name, message, input, prv);
        if (stenographer != null)
            stenographer.append (cm);

        if (Thread.currentThread().equals (threadJfx))
            txtareaMessages.appendText (cm.toString());
        else
            Platform.runLater(()->txtareaMessages.appendText (cm.toString()));
    }

/** Обработчик команды CMD_PRIVATE_MSG (здесь обрабатываются только входящие приватные сообщения).   */
    boolean onCmdPrivatMsg (String name, String message) {
        if (!validateStrings (name, message)) {
            if (DEBUG)
                throw new RuntimeException ("ERROR @ onCmdPrivatMsg() : bad argument.");
            return false;
        }
        inlineAppendMessage (name, message, INPUT_MSG, PRIVATE_MSG);
        return true;
    }

/** Обработчик команды CMD_LOGIN (сообщение приходит в случае успешной авторизации). */
    boolean onCmdLogIn (String name) {
        if (!validateStrings (name))
            throw new RuntimeException("ERROR @ onCmdLogIn() : bad login.");
        nickname = name;
        readChatStorage();    //< Считываем историю чата из файла
        updateUserInterface (CAN_CHAT);
        LOGGER.info("onCmdLogIn()/nickname: " + nickname);
        return (network != null) && network.sendMessageToServer (CMD_LOGIN_READY); //< сообщаем о готовности войти в чат (теперь мы участники чата)
    }

/** Обработчик команды CMD_CHANGE_NICKNAME.  */
    boolean onCmdChangeNickname (String name) {
        if (!validateStrings (name))
            throw new RuntimeException("ERROR @ onCmdChangeNickname() : bad nickname.");
        nickname = name;

        Platform.runLater(()->{
            txtfieldUsernameField.setText (nickname);
            onactionChangeNickname();   //< Отщёлкиваем кнопку «Сменить имя» в исходное состояние.
            txtfieldMessage.clear();//< перед отправкой запроса на сервер мы оставили имя в поле ввода.
            // Теперь нужно его оттуда убрать.
            txtfieldMessage.requestFocus();
        });
        LOGGER.info("поменял имя на: " + nickname);
        return true;
    }

/** Обработчик команды CMD_CLIENTS_LIST. */
    boolean onCmdClientsList (String[] names) {
        if (names == null)
            throw new RuntimeException("ERROR @ onCmdClientsList() : queue polling error (number).");

        int size = names.length;
        Platform.runLater(()->{
            //Запоминаем выбранный пункт.
            ObservableList<String> olSelected = listviewClients.getSelectionModel().getSelectedItems();
            HashSet<String> set = new HashSet<>(olSelected);

            //Очищаем список и заново его наполняем.
            listviewClients.getItems().clear();
            int i = 0;
            for (String name : names) {
                if (!validateStrings (name)) {
                    if (DEBUG)
                        throw new RuntimeException("ERROR @ onCmdClientsList() : corrupted name : "+ name);
                    name = "errName_"+ i++;
                }
                String s = name;
                listviewClients.getItems().add(s);
            }
            //Выбираем тот пункт, который был выбран до начала обновления списка. (Это делается для того,
            // чтобы приход/уход участников чата не мешал приватному общению других участников, — в приватном
            // режиме общение происходит с тем участником чата, чьё имя выделено в списке.)
            if (!set.isEmpty()) {
                for (String s : set)
                    listviewClients.getSelectionModel().select (s);
            }
        });
        return true;
    }

/** Обработчик команды CMD_CLIENTS_LIST_CHANGED    */
    boolean onCmdClientsListChanged () {
        return (network != null) && network.sendMessageToServer (CMD_CLIENTS_LIST);
    }

/** Обработчик команды CMD_CONNECTED. Информируем пользователя об устанке соединения с сервером. */
    boolean onCmdConnected () {
        txtareaMessages.appendText (PROMPT_CONNECTION_ESTABLISHED);
        return true;
    }

/** Обработчик команды CMD_BADLOGIN. Сообщаем пользователю о том, что введённые логин и пароль не подходят. (Установленное соединение не рвём.)  */
    boolean onCmdBadLogin (String prompt) {
        if (validateStrings (prompt))
            Platform.runLater(()->alertWarning (ALERT_HEADER_LOGINERROR, prompt));
        closeSession (PROMPT_CONNECTION_LOST + "\n" + prompt);
        return true;
    }

/** Обработчик команды CMD_BADNICKNAME. Сообщаем пользователю о том, что введённый ник не годится для смены ника.    */
    boolean onCmdBadNickname (String prompt) {
        if (validateStrings (prompt))
            Platform.runLater(()->alertWarning (ALERT_HEADER_RENAMING, prompt));
        return true;
    }

/** Обработчик команды CMD_EXIT. Должна вызываться из синхронизированного контекста, т.к. обращается к inputqueue. Также вызывается из: closeSession().  */
    boolean onCmdExit (String prompt) {
        LOGGER.info("onCmdExit() начало");

        interruptQueueThreads(); //< это заставит завершиться дополнительные потоки
        if (threadJfx != null && threadJfx.isAlive())
            Platform.runLater(()->updateUserInterface (CANNOT_CHAT));

        if (stenographer != null) {  //< если stenographer == null, то, скорее всего, уже не нужно что-либо сохранять или выводить
            if (!validateStrings (prompt))
                prompt = PROMPT_YOU_ARE_LOGED_OFF;

            if (nickname != null)
                inlineAppendMessage (nickname, prompt, INPUT_MSG, PUBLIC_MSG);

            LOGGER.info ("onCmdExit()/prompt: " + prompt);
            stenographer.close();
            stenographer = null;
        }

        try {
            if (treadInputStream != null) treadInputStream.join(1000);
        }
        catch (InterruptedException e) {
            LOGGER.throwing (Level.ERROR, e);
        }
        finally {
            treadInputStream = null;
            login = null;
            nickname = null;
            LOGGER.info("onCmdExit() конец");
        }
        return true;
    }

//------------------------- обработчики команд интерфейса ----------------------------

/** Обработка ввода пользователем логина и пароля для входа чат. */
    @FXML public void onactionLogin (ActionEvent actionEvent) {
        LOGGER.info("onactionLogin() начало");

        String login = txtfieldUsernameField.getText(), password = txtfieldPassword.getText();
        boolean badLogin = login == null || login.isEmpty();

        if (badLogin || password == null || password.isEmpty()) {
            alertWarning (ALERT_HEADER_BAD_NICKNAME, PROMPT_BAN_NICKNAME_SPECIFIED);
            if (badLogin)
                txtfieldUsernameField.requestFocus();
            else
                txtfieldPassword.requestFocus();
        }
        else if (connect()) {
            LOGGER.info(String.format("onactionLogin()/ login: %s; password: %s", login, password));

            treadInputStream = new Thread(this::runTreadInputStream);
            treadInputStream.start();

            this.login = login; //< запоминаем логин, под которым регистрируемся (для имени файла)
            network.sendMessageToServer(CMD_LOGIN, this.login, password);
        }
        else {
            txtareaMessages.setText(Network.PROMPT_UNABLE_TO_CONNECT);
            interruptQueueThreads();
        }
        LOGGER.info("onactionLogin() конец");
    }

/** Кнопка «Выход». Пришлось отказаться от использования одной кнопки для входа в чат и выхода из чата, т.к. JavaFX даже Platform.runLater нормально не может в очередь поставить и в результате нажатия на кнопку «Вход/Выход» обрабатывались беспорядочно. */
    @FXML public void onactionLogout () { closeSession (PROMPT_YOU_ARE_LOGED_OFF); }

/** Обработка вводимых пользователем сообщений. (У пользователя нет возможности вводить команды руками, — для управления приложением предусмотрены кнопки.)   */
    @FXML public void onactionSendMessage () {

        LOGGER.info ("onactionSendMessage() начало");
        String message = txtfieldMessage.getText();
        boolean boolSent = false;
        LOGGER.info ("onactionSendMessage()/message: " + message);

        if (message == null || message.trim().isEmpty()) {
            if (tipsMode == TIPS_ON)
                alertWarning (ALERT_HEADER_EMPTY_MESSAGE, PROMPT_EMPTY_MESSAGE);
        }
        else if (changeNicknameMode == MODE_CHANGE_NICKNAME) { //< включен режим смены имени
            boolean b = alertConfirmationYesNo (ALERT_HEADER_RENAMING,
                                                String.format (PROMPT_CONFIRM_NEW_NICKNAME, message));
            if (b == ANSWER_YES)
                boolSent = network.sendMessageToServer(CMD_CHANGE_NICKNAME, message);
        }
        else if (privateMode == MODE_PRIVATE) { // исходящие приватные сообщения

            String addressee = listviewClients.getSelectionModel().getSelectedItem();

            if (addressee == null || addressee.isEmpty()) {
                alertWarning (ALERT_HEADER_ADDRESSEE, PROMPT_ADDRESSEE_NOTSELECTED);
                LOGGER.warn ("Имя адресата пустое или отсутствует.");
            }
            else if (addressee.equals (nickname)) {
                alertWarning (ALERT_TITLE, PROMPT_MESSAGE_TO_YOURSELF);
                LOGGER.info ("Попытка отправить сообщения себе");
            }
            else if (boolSent = network.sendMessageToServer (CMD_PRIVATE_MSG, addressee, message)) {
                inlineAppendMessage (addressee, message, OUTPUT_MSG, PRIVATE_MSG);
                LOGGER.info ("Отправка приватного сообщения.");
            }
        }
        else boolSent = network.sendMessageToServer(CMD_CHAT_MSG, message); // обычный режим (публичные сообщения)

        if (boolSent) {
            txtfieldMessage.clear();
            txtfieldMessage.requestFocus();
            LOGGER.info("onactionSendMessage() конец");
        }
        else  LOGGER.warn("onactionSendMessage() не справился");
    }

/** Обработка нажатия на кнопку «Приватно» (переключение приват. режима).<p>
    В обычном (публичном) режиме не имеет значения, какое имя выбрано в списке участноков чата, — вводимые
    сообщения получают все. Но в приватном режиме сообщения получает только тот участник, чьё имя выбрано в
    списке участников чата.  */
    @FXML public void onactionTogglePrivateMode () {
        LOGGER.info("onactionTogglePrivateMode() call");
        privateMode = !privateMode;
        btnToolbarPrivate.setSelected(privateMode);
        if (tipsMode == TIPS_ON)
            txtareaMessages.appendText (
                privateMode ? PROMPT_PRIVATE_MODE_IS_ON : PROMPT_PRIVATE_MODE_IS_OFF);
    }

//------------------------- вспомогательные методы ----------------------------

/** Обработка нажатия на кнопку «Сменить ник» (переключение режима смены ника).  */
    @FXML public void onactionChangeNickname () {
        LOGGER.info("onactionChangeNickname() call");

        changeNicknameMode = !changeNicknameMode;
        btnToolbarChangeNickname.setSelected(changeNicknameMode);

        if (changeNicknameMode == MODE_CHANGE_NICKNAME && tipsMode == TIPS_ON)
            txtareaMessages.appendText(PROMPT_CHANGE_NICKNAME);
    }

/** Обработка нажатия на кнопку «Подсказки» (переключение режима показа подсказок к интерфейсу). */
    @FXML public void onactionTips () {
        LOGGER.info ("onactionTips() call");
        tipsMode = !tipsMode;
        if (tipsMode == TIPS_ON)    txtareaMessages.appendText (PROMPT_TIPS_ON);
    }

/** (Вспомогательная.) Считываем лог чата из файла при помощи объекта MessageStenographer<ChatMessage>. Сейчас имя файла состоит из логина пользователя и расширения chat.   */
    void readChatStorage () {
        if (validateStrings (login)) {
            stenographer = new MessageStenographer<> (login + ".chat");
            List<ChatMessage> cmlist = stenographer.getData();

            Platform.runLater(()->{
                txtareaMessages.clear(); //< очищаем окно чата (чтобы не мучаться, т.к. юзер может и под другой
                for (Object cm : cmlist) //           учёткой перезайти, для которой есть другой файл истории…)
                    txtareaMessages.appendText (cm.toString());
            });
        }
        else  LOGGER.error("readChatStorage() вызван при login = " + login);
    }

    @Override public String toString () { return "Controller:" + nickname; }

/** Выполняем действия, полагающиеся при выходе из чата. Вызывается потоком threadJfx из:<p>
          connect()   - в блоке catch(){}<br>
          messageDispatcher() - в блоке finally при пом. Platform.runLater(->)<br>
          onCmdBadLogin()     - через messageDispatcher > Platform.runLater(queuePoll())<br>
          onactionLogout()    - через messageDispatcher > Platform.runLater(queuePoll())  */
    private void closeSession (String prompt) {
/*  Всесь процесс выхода и чата (не из приложения) заключается в трёх действиях:
    - отправка сообщения CMD_EXIT на сервер (если выход инициализировал клиент);
    - вызов onCmdExit для изменения некоторых переменных и остановки доп.потоков;
    - вызов disconnect().
*/
        LOGGER.debug(String.format("closeSession() вызван с параметром: %s", prompt));
        network.sendMessageToServer(CMD_EXIT);   //< выполняется при необходимости
        onCmdExit (prompt); //< модно не синхронизировать, т.к. в этот блок есть доступ только у threadJfx
        network.disconnect();
        LOGGER.debug("closeSession() завершился");
    }

    public void print (String s) {System.out.print(s);}

    public void println (String s) {System.out.print("\n" + s);}

    public static class ChatMessage implements Serializable {
        private final String name, text;   //< Serializable
        private final boolean inputMsg, privateMsg;
        private final LocalDateTime ldt = LocalDateTime.now(); //< Serializable (сейчас не используется)

        public ChatMessage (String name, String message, boolean input, boolean prv) {
            if (validateStrings (name, message)) {
                this.name = name;
                text = message;
                inputMsg = input;
                privateMsg = prv;
            }
            else if (DEBUG) throw new IllegalArgumentException();
        }

        @Override public String toString () {
            String format = FORMAT_CHATMSG;
            if (privateMsg)
                format = inputMsg ? FORMAT_PRIVATEMSG_TOYOU : FORMAT_PRIVATEMSG_FROMYOU;
            return String.format (format, name, text);
        }
    }

/** Вызываем {@code interrupt()} для потоков {@code threadListenServerCommands} и {@code threadCommandDispatcher}. */
    private void interruptQueueThreads () {
        if (treadInputStream != null)
            treadInputStream.interrupt();
        //threadCommandDispatcher.interrupt();
    }
}
/*  TODO : Преподаватель «анонсировал» короткое чтение истории чата из файла (применительно к его версии чата): Files.lines(Paths.get("log.txt"))
              .collect(Collectors.joining("\n"));
*/
// (машинный перевод фрагмента комментария к методу Platform.runLater()):
//
//    … запускает Runnable в потоке JavaFX в неопределенное время в будущем. Этот метод может быть вызван из любого потока. Он отправит Runnable в очередь событий, а затем немедленно вернется к вызывающему.
//     Runnables выполняются в том порядке, в котором они размещены.
//!!!! Runnable, переданный в метод runLater, будет выполнен до того, как любой Runnable будет передан в
//     последующий вызов runLater. …
//
//!!!! … ПРИМЕЧАНИЕ: приложениям следует избегать переполнения JavaFX слишком большим количеством ожидающих выполнения Runnables. В противном случае приложение может перестать отвечать. Приложениям рекомендуется объединять несколько операций в меньшее количество вызовов runLater. По возможности, длительные операции должны выполняться в фоновом потоке, освобождая поток приложения JavaFX для операций с графическим интерфейсом пользователя.
