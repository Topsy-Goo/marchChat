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
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static ru.geekbrains.march.chat.client.Main.WNDTITLE_APPNAME;
import static ru.geekbrains.march.chat.server.ClientHandler.THREAD_SLEEPINTERVAL_250;
import static ru.geekbrains.march.chat.server.ServerApp.*;

public class Controller implements Initializable
{
    private final static String
            TXT_INTRODUCE_YOURSELF = "Представьтесь:  ",
            TXT_YOU_LOGED_IN_AS = "Вы вошли в чат как: ",
            EMERGENCY_EXIT_FROM_CHAT = "аварийный выход из чата",
            WELCOME_TO_MARCHCHAT = "Добро пожаловать в March Chat!",
            FORMAT_CHATMSG = "\n%s:\n\t%s",
            FORMAT_PRIVATEMSG_TOYOU = "\n[приватно c %s] Вам:\n\t%s",
            FORMAT_PRIVATEMSG_FROMYOU = "\n[приватно c %s] от Вас:\n\t%s",
            PROMPT_CONNECTION_ESTABLISHED = "\nСоединение с сервером установлено.",
            PROMPT_TIPS_ON = "\nПодсказки включены.",
            PROMPT_UNABLE_TO_CONNECT = "\nНе удалось подключиться.",
            PROMPT_YOU_ARE_LOGED_OFF = "Вы вышли из чата.",
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
    private Socket clientSideSocket;
    private DataInputStream dis;
    private DataOutputStream dos;
    private String nickname, login; //< логин нужен для составления имени файла истории чата.
    private Thread threadIntputStream,
                   threadParent,
                   threadMainLoop
                   ;
    private Queue<String> inputqueue;
    private final Object syncQue = new Object();
    private boolean chatGettingClosed,    //< индикатор того, что сеанс завершается и что потокам пора «закругляться»
                    //loginState = LOGED_OFF,
                    privateMode = MODE_PUBLIC,
                    changeNicknameMode = MODE_KEEP_NICKNAME,
                    tipsMode = TIPS_ON
                    ;
    private MessageStenographer<ChatMessage> stenographer;

    @FXML TextArea txtareaMessages;
    @FXML TextField txtfieldUsernameField, txtfieldMessage;
    @FXML PasswordField txtfieldPassword;
    @FXML Button buttonLogin, buttonLogout;
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
        //private static final long serialVersiobUID = 1L;

        public ChatMessage (String name, String message, boolean input, boolean prv)
        {   if (!validateStrings (name, message))    throw new IllegalArgumentException();
            this.name = name;
            text = message;
            inputMsg = input;
            privateMsg = prv;
        }
        @Override public String toString ()
        {   String format = FORMAT_CHATMSG;
            if (privateMsg)
                format = inputMsg ? FORMAT_PRIVATEMSG_TOYOU : FORMAT_PRIVATEMSG_FROMYOU;
            return String.format (format, name, text);
        }
    }// class ChatMessage


    @Override public void initialize (URL location, ResourceBundle resources)
    {   threadParent = Thread.currentThread();
        updateUserInterface (CANNOT_CHAT);
        txtareaMessages.appendText (WELCOME_TO_MARCHCHAT);
    }// initialize ()

// Изменяем атрибуты элементов управления так, чтобы пользователь мог пользоваться чатом, но не мог
// изменить своё имя. Или наоборот: чтобы мог ввести своё имя, но не мог пользоваться чатом.
    private void updateUserInterface (boolean canChat)
    {
        //Platform.runLater(()->{
            txtfieldUsernameField.setDisable (canChat == CAN_CHAT);
            //buttonLogin.setText (canChat == CAN_CHAT ? "Выйти" : "Войти");
            //buttonLogin.setVisible (canChat != CAN_CHAT);
            buttonLogout.setVisible (canChat == CAN_CHAT);
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
            vboxClientsList.setVisible (canChat == CAN_CHAT);
            vboxClientsList.setManaged (canChat == CAN_CHAT);
        //});
    }// updateUserInterface ()

// Подключение к серверу.
    private boolean connect ()
    {
        boolean boolOk = false;
        if (clientSideSocket == null || clientSideSocket.isClosed())
        try
        {   clientSideSocket = new Socket (SERVER_ADDRESS, SERVER_PORT);
            dis = new DataInputStream (clientSideSocket.getInputStream());
            dos = new DataOutputStream (clientSideSocket.getOutputStream());
            boolOk = true;
            print("\nconnect() подключен.");
        }
        catch (IOException ioe)
        {   onCmdExit (PROMPT_UNABLE_TO_CONNECT);
            disconnect();
            ioe.printStackTrace();
        }
        return boolOk;
    }// connect ()

//Закрытие сокета и обнуление связанных с ним переменных.
    private void disconnect ()
    {
        if (clientSideSocket != null && !clientSideSocket.isClosed())
        {   try { clientSideSocket.close(); }
            catch(IOException e) {e.printStackTrace();}
            print("\ndisconnect() - соединение разорвано.");
        }
        clientSideSocket = null;
        dis = null;
        dos = null;
    }// disconnect ()

//--------------------------------------------------- потоки и очередь
// Обрабатываем сообщения, находящиеся в очереди inputqueue.
    private void messageDispatcher () //Main Loop.
    {
/* (машинный перевод фрагмента комментария к методу Platform.runLater()):

    … запускает Runnable в потоке JavaFX в неопределенное время в будущем. Этот метод может быть вызван
     из любого потока. Он отправит Runnable в очередь событий, а затем немедленно вернется к вызывающему.
     Runnables выполняются в том порядке, в котором они размещены.
!!!! Runnable, переданный в метод runLater, будет выполнен до того, как любой Runnable будет передан в
     последующий вызов runLater. …

!!!! … ПРИМЕЧАНИЕ: приложениям следует избегать переполнения JavaFX слишком большим количеством ожидающих
     выполнения Runnables. В противном случае приложение может перестать отвечать. Приложениям рекомендуется
     объединять несколько операций в меньшее количество вызовов runLater. По возможности, длительные операции
     должны выполняться в фоновом потоке, освобождая поток приложения JavaFX для операций с графическим
     интерфейсом пользователя.
    Этот метод нельзя вызывать до инициализации среды выполнения FX.
 */
//Platform.runLater(()->{   System.out.print("\n\tmessageDispatcher() выполняется.");  });
        String prompt = PROMPT_YOU_ARE_LOGED_OFF;
        while (!chatGettingClosed)
        {
            if (!inputqueue.isEmpty())
            {
                Platform.runLater(()->{
                    queuePoll();
                    //chatGettingClosed = !queuePoll(); < здесь оказывается нельзя делать так (вызов
                    // перестаёт работать корректно -- обрабатывает пару сообщений и перестаёт работать)
                });
            }
            else
            {
                try
                {   if (!threadParent.isAlive())
                    {
                        throw new InterruptedException ("Кажется, родительский поток завершился…");
                    }
            //экспериментальным путём установлено, что приложение не может нормально работать без пауз
            //(Прошу обратить внимание, что доп.потоки максимально, насколько это возможно, изолированы от
            // потока JavaFX, а используемый здесь вызов «Platform.runLater» единственный на всё приложение.)
                    Thread.sleep (THREAD_SLEEPINTERVAL_250);
                }
                catch (InterruptedException e)
                {   e.printStackTrace();
                    chatGettingClosed = true;
                    System.out.print("\n\tmessageDispatcher() : "+EMERGENCY_EXIT_FROM_CHAT);
                    prompt = EMERGENCY_EXIT_FROM_CHAT;
                    break;
                }
                //Platform.runLater(()->{  System.out.print("_"); });
            }
        }
        closeSession (prompt);
        //Platform.runLater(()->{
        //    print("\n\tmessageDispatcher() звершился.");
        //});
        //print("\n\tвыход из messageDispatcher().");
    }// messageDispatcher ()

// Run-метод потока threadIntputStream. Считываем сообщения из входного канала соединения и помещаем их в очередь.
// Больше ничего не делаем.
    private void runTreadInputStream ()
    {
        String  msg, prompt = PROMPT_YOU_ARE_LOGED_OFF;
        print("\n\trunTreadInputStream() выполняется.");
        try
        {   while (!chatGettingClosed)
            switch (msg = dis.readUTF().trim())
            {
                //case CMD_ONLINE:       queueOffer (CMD_ONLINE);
                //    break;
                case CMD_CHAT_MSG:     queueOffer (CMD_CHAT_MSG, dis.readUTF(), dis.readUTF()); //cmd + name + msg
                    break;
                case CMD_PRIVATE_MSG:  queueOffer (CMD_PRIVATE_MSG, dis.readUTF(), dis.readUTF()); //cmd + name + msg
                    break;
                case CMD_CLIENTS_LIST_CHANGED:  queueOffer (CMD_CLIENTS_LIST_CHANGED);
                    break;
                case CMD_LOGIN:    queueOffer (CMD_LOGIN, dis.readUTF()); //cmd + nickname
                    break;
                case CMD_BADLOGIN: queueOffer (CMD_BADLOGIN, dis.readUTF()); //cmd + prompt
                    break;
                case CMD_CHANGE_NICKNAME:  queueOffer (CMD_CHANGE_NICKNAME, dis.readUTF()); //cmd + nickname
                    break;
                case CMD_BADNICKNAME:      queueOffer (CMD_BADNICKNAME, dis.readUTF()); //cmd + prompt
                    break;
                case CMD_CONNECTED: queueOffer (CMD_CONNECTED);
                    break;
                case CMD_EXIT:      queueOffer (CMD_EXIT);
                    break;
                case CMD_CLIENTS_LIST:
                    int i=0, size = 2+ Integer.parseInt (msg = dis.readUTF()); //количество строк
                    String[] as = new String[size];
                    as[i++] = CMD_CLIENTS_LIST; // cmd
                    as[i++] = msg;              // count
                    while (i < size)  as[i++] = dis.readUTF(); //строки
                    queueOffer (as);
                    break;
                default:  throw new UnsupportedOperationException (
                            "\nERROR @ runTreadInputStream() : незарегистрированное сообщение:\n\t" + msg);
            }
        }
        catch (IOException e)
        {   prompt = EMERGENCY_EXIT_FROM_CHAT;
            e.printStackTrace();
        }
        finally
        {   inputqueue.offer (CMD_EXIT); //если не можем слушать канал, то всем отбой.
            closeSession (prompt);
            print("\n\trunTreadInputStream() завершился.");
        }
//print("\n\tвыход из runTreadInputStream().");
    }// runTreadInputStream ()

//(Вспомогательная.) Добавляем в очередь одну или несколько строк, в зависимости от типа сообщения. (Без проверок.)
    private void queueOffer (String ... lines)
    {
        synchronized (syncQue)
        {
            for (String s : lines)
                if (!inputqueue.offer (s))
                    throw new RuntimeException ("ERROR : unable to offer message.");
            if (DEBUG)
            {   print("\n\tin«");
                for (int i=0,n=lines.length;   i<n;   i++)    print(((i>0)?" | ":"")+lines[i]);
                print("»");
            }
        }
    }// queueOffer ()

//Извлекаем команды из очереди и обрабатываем их.
    private boolean queuePoll ()
    {
        boolean boolOk = false;
        String msg;
        synchronized (syncQue)
        {
            if ((msg = inputqueue.poll()) != null)
            {
                switch (msg)
                {   //case CMD_ONLINE:    sendMessageToServer (CMD_ONLINE);
                    //    break;
                    case CMD_CHAT_MSG:   boolOk = onCmdChatMsg();
                        break;
                    case CMD_CLIENTS_LIST_CHANGED:  boolOk = sendMessageToServer (CMD_CLIENTS_LIST);
                        break;
                    case CMD_CLIENTS_LIST:  boolOk = onCmdClientsList();
                        break;
                    case CMD_LOGIN:    boolOk = onCmdLogIn ();
                        break;
                    case CMD_BADLOGIN:  boolOk = onCmdBadLogin();
                        break;
                    case CMD_CHANGE_NICKNAME:   boolOk = onCmdChangeNickname();
                        break;
                    case CMD_BADNICKNAME:    boolOk = onCmdBadNickname();
                        break;
                    case CMD_EXIT:    boolOk = onCmdExit (PROMPT_YOU_ARE_LOGED_OFF);
                        break;
                    case CMD_PRIVATE_MSG:   boolOk = onCmdPrivateMsg();
                        break;
                    case CMD_CONNECTED:   boolOk = onCmdConnected();
                        break;
                    default:   throw new UnsupportedOperationException ("ERROR queuePoll(): незарегистрированное сообщение: "+ msg);
                }//switch
                if (DEBUG) print ("\n\tpoll : "+ msg);
            }
        }
        if (DEBUG && !boolOk) print ("ERROR @ queuePoll(): boolOk = false.");
        return boolOk;
    }// queuePoll ()
//------------------------- обработчики сетевых команд ----------------------------

// Обработчик команды CMD_CHAT_MSG (здесь обрабатываются входящие и исходящие публичные сообщения).
    boolean onCmdChatMsg ()
    {
        String name = inputqueue.poll(),
               message = inputqueue.poll();
        if (!validateStrings (message))
            throw new RuntimeException("ERROR @ onCmdChatMsg() : queue polling error.");

        ChatMessage cm = new ChatMessage (name, message, name.equals(nickname), PUBLIC_MSG);
        if (stenographer != null) stenographer.append (cm);

        //Platform.runLater(()->{
            txtareaMessages.appendText (cm.toString());
        //});
        return true;
    }// onCmdChatMsg ()

// Обработчик команды CMD_PRIVATE_MSG (здесь обрабатываются только входящие приватные сообщения).
    boolean onCmdPrivateMsg ()
    {
        String name = inputqueue.poll(),
               message = inputqueue.poll();
        if (!validateStrings (message))
            throw new RuntimeException("ERROR @ () : queue polling error.");

        ChatMessage cm = new ChatMessage (name, message, INPUT_MSG, PRIVATE_MSG);
        if (stenographer != null) stenographer.append (cm);

        //Platform.runLater(()->{
            txtareaMessages.appendText (cm.toString());
        //});
        return true;
    }// onCmdPrivateMsg ()

// Обработчик команды CMD_LOGIN (сообщение приходит в случае успешной авторизации).
    boolean onCmdLogIn ()
    {   nickname = inputqueue.poll();
        if (!validateStrings (nickname))
            throw new RuntimeException("ERROR @ onCmdLogIn() : queue polling error.");
        //loginState = LOGED_IN;
        readChatStorage();    //< Считываем историю чата из файла
        updateUserInterface (CAN_CHAT);

        print("\n\tonCmdLogIn() : "+ nickname); //< для отладки
        return sendMessageToServer (CMD_LOGIN_READY); //< сообщаем о готовности войти в чат (теперь мы участники чата)
    }// onCmdLogIn ()

// Обработчик команды CMD_CHANGE_NICKNAME.
    boolean onCmdChangeNickname ()
    {
        nickname = inputqueue.poll();
        if (!validateStrings (nickname))
            throw new RuntimeException ("ERROR @ onCmdChangeNickname() : queue polling error.");

        //Platform.runLater(()->{
            txtfieldUsernameField.setText (nickname);
            onactionChangeNickname();   //< Отщёлкиваем кнопку «Сменить имя» в исходное состояние.
            txtfieldMessage.clear();//< перед отправкой запроса на сервер мы оставили имя в поле ввода.
                                    // Теперь нужно его оттуда убрать.
            txtfieldMessage.requestFocus();
        //});
        System.out.printf ("\n\tпоменял имя на %s", nickname); //< для отладки
        return true;
    }// onCmdChangeNickname ()

// Обработчик команды CMD_CLIENTS_LIST.
    boolean onCmdClientsList ()
    {
        String number = inputqueue.poll();
        if (!validateStrings (number))
            throw new RuntimeException("ERROR @ onCmdClientsList() : queue polling error (number).");

        int size = Integer.parseInt (number);
        String[] tmplist = new String[size];

        for (int i=0; i<size; i++)
            tmplist[i] = inputqueue.poll();
        if (!validateStrings (tmplist))
            throw new RuntimeException("ERROR @ onCmdClientsList() : queue polling error (array).");

        //Platform.runLater(()->{
            listviewClients.getItems().clear();
            for (String s : tmplist)
                listviewClients.getItems().add(s);
        //});
        return true;
    }// onCmdClientsList ()

// Обработчик команды CMD_CONNECTED. Информируем пользователя об устанке соединения с сервером.
    boolean onCmdConnected ()
    {
        //Platform.runLater(()->{
            txtareaMessages.appendText (PROMPT_CONNECTION_ESTABLISHED);
        //});
        return true;
    }// onCmdConnected ()

// Обработчик команды CMD_BADLOGIN. Сообщаем пользователю о том, что введённые логин и пароль не подходят.
// (Установленное соединение не рвём.)
    boolean onCmdBadLogin ()
    {
        String prompt = inputqueue.poll();
        if (!validateStrings (prompt))
            throw new RuntimeException("ERROR @ onCmdBadLogin() : queue polling error.");

        alertWarning (ALERT_HEADER_LOGINERROR, prompt);
    //выполняем завершение сессии:
        sendMessageToServer (CMD_EXIT);
        onCmdExit (PROMPT_CONNECTION_LOST);
        disconnect();
        return true;
    }// onCmdBadLogin ()

// Обработчик команды CMD_BADNICKNAME. Сообщаем пользователю о том, что введённый ник не годится для смены ника.
    boolean onCmdBadNickname ()
    {
        String prompt = inputqueue.poll();
        if (!validateStrings (prompt))
            throw new RuntimeException("ERROR @ onCmdBadNickname() : queue polling error.");

        alertWarning (ALERT_HEADER_RENAMING, prompt);
        return true;
    }// onCmdBadNickname ()

// Обработчик команды CMD_EXIT (также вызывается из onactionLogin() при нажатии кнопки Вход/Выход, и из onCmdBadLogin()).
    boolean onCmdExit (String prompt)
    {
        chatGettingClosed = true; //< это заставит звершиться дополнительные потоки
        //loginState = LOGED_OFF;
        updateUserInterface (CANNOT_CHAT);

        if (stenographer != null) //< если stenographer == 0, то, скорее всего, ничего сохранять или выводить уже не нужно
        {
            if (!validateStrings (prompt))
                prompt = PROMPT_YOU_ARE_LOGED_OFF;

            if (nickname != null)
            {   ChatMessage cm = new ChatMessage (nickname, prompt, INPUT_MSG, PUBLIC_MSG);
                if (stenographer != null) stenographer.append (cm);
                prompt = cm.toString();
            }
            //Platform.runLater(()->{
                txtareaMessages.appendText (prompt);
            //});
            stenographer.close();
        }
        stenographer = null;
        login = null;
        nickname = null;
        try
        {   if (threadIntputStream != null) threadIntputStream.join(1000);
            if (threadMainLoop != null) threadMainLoop.join(1000);
            //(Ожидание нужно на случай аварийного выхода, т.к. метод вызывается одним из потоков.)
        }
        catch (InterruptedException e){e.printStackTrace();}
        finally
        {   threadIntputStream = null;
            threadMainLoop = null;
            if (inputqueue != null) inputqueue.clear();
            inputqueue = null;
        }
        return true;
    }// onCmdExit ()

//------------------------- обработчики команд интерфейса ----------------------------

// Обработка ввода пользователем логина и пароля для входа чат.
    @FXML public void onactionLogin (ActionEvent actionEvent)
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
        else if (!(chatGettingClosed = !connect()))
        {
            inputqueue = new LinkedList<>();

            threadIntputStream = new Thread(() -> runTreadInputStream());
            threadIntputStream.start();

            threadMainLoop = new Thread(() -> messageDispatcher());
            threadMainLoop.start(); //< входим в Main Loop.

            this.login = login; //< запоминаем логин, под которым регистрируемся (для имени файла)
            sendMessageToServer (CMD_LOGIN, this.login, password);
            //chatGettingClosed = false;
            //threadMainLoop = new Thread(this::messageDispatcher);
            //threadMainLoop.start(); //< входим в Main Loop.
        }
        else txtareaMessages.setText (PROMPT_UNABLE_TO_CONNECT);
    }// onactionLogin ()

// Кнопка «Выход». Пришлось отказаться от использования одной кнопки для входа в чат и выхода из чата, т.к.
// JavaFX даже Platform.runLater нормально не может в очередь поставить и в результате нажатия на кнопку
// «Вход/Выход» обрабатывались беспорядочно.
    @FXML public void onactionLoginout (ActionEvent actionEvent)
    {
        closeSession (PROMPT_YOU_ARE_LOGED_OFF);
    }// onactionLoginout ()

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
                    sendMessageToServer(CMD_CHANGE_NICKNAME, message);
            }
            else if (privateMode == MODE_PRIVATE) // исходящие приватные сообщения
            {
                String name = listviewClients.getSelectionModel().getSelectedItem();

                if (name == null || name.isEmpty())
                    alertWarning(ALERT_HEADER_ADDRESSEE, PROMPT_ADDRESSEE_NOTSELECTED); //< если получатель не выбран
                else
                if (boolSent = sendMessageToServer(CMD_PRIVATE_MSG, name, message))
                {
                    ChatMessage cm = new ChatMessage (name, message, OUTPUT_MSG, PRIVATE_MSG);
                    if (stenographer != null) stenographer.append (cm);
                    txtareaMessages.appendText (cm.toString());
                }
            }
            else boolSent = sendMessageToServer(CMD_CHAT_MSG, message); // обычный режим (публичные сообщения)

            if (boolSent)
            {   txtfieldMessage.clear();
                txtfieldMessage.requestFocus();
            }
        //});
    }// onactionSendMessage ()

// Обработка нажатия на кнопку «Приватно» (переключение приват. режима).
    @FXML public void onactionTogglePrivateMode ()
    {   privateMode = !privateMode;
        btnToolbarPrivate.setSelected (privateMode);
        if (tipsMode == TIPS_ON)
            txtareaMessages.appendText (privateMode ? PROMPT_PRIVATE_MODE_IS_ON : PROMPT_PRIVATE_MODE_IS_OFF);
    }

// Обработка нажатия на кнопку «Сменить ник» (переключение режима смены ника).
    @FXML public void onactionChangeNickname ()
    {   changeNicknameMode = !changeNicknameMode;
        btnToolbarChangeNickname.setSelected (changeNicknameMode);
        if (changeNicknameMode == MODE_CHANGE_NICKNAME && tipsMode == TIPS_ON)
            txtareaMessages.appendText (PROMPT_CHANGE_NICKNAME);
    }// onactionChangeNickname ()

// Обработка нажатия на кнопку «Подсказки» (переключение режима показа подсказок к интерфейсу).
    @FXML public void onactionTips ()
    {   tipsMode = !tipsMode;
        if (tipsMode == TIPS_ON)
            txtareaMessages.appendText (PROMPT_TIPS_ON);
    }// onactionTips ()

//------------------------- вспомогательные методы ----------------------------

//(Вспомогательный метод.) Шлём на сервер строки отдельными сообщениями.
    private boolean sendMessageToServer (String ... lines)
    {
        boolean boolSent = false;
        if (lines != null  &&  lines.length > 0  &&  dos != null)
        try
        {   if (DEBUG) print("\n\tout: ");
            for (String msg : lines)
            {
                dos.writeUTF(msg);
                if (DEBUG) print(msg+";\t");
            }
            boolSent = true;
        }
        catch (IOException e) { alertWarning (ALERT_HEADER_ERROR, PROMPT_UNABLE_TO_SEND_MESSAGE); }
        return boolSent;
    }// sendMessageToServer ()

// Стандартное окно сообщения с кнопкой Close.
    public static void alertWarning (String header, String msg)
    {
        //Platform.runLater(()->{
        if (validateStrings (header, msg))
        {   Alert a = new Alert (Alert.AlertType.WARNING, msg, ButtonType.CLOSE);
            a.setTitle (ALERT_TITLE);
            a.setHeaderText (header);
            a.showAndWait();
        }//});
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
        List<ChatMessage> cmlist = stenographer.getData();

        //Platform.runLater(()->{
            txtareaMessages.clear(); //< очищаем окно чата (чтобы не мучаться, т.к. юзер может и под другой
            for (Object cm : cmlist) //           учёткой перезайти, для которой есть другой файл истории…)
                txtareaMessages.appendText (cm.toString());
        //});
    }// readChatStorage ()

    @Override public String toString() { return "Controller:"+ nickname; }

//(Вспомогательная.) Проверяет строку на пригодность для использования в качестве логина, пароля, ника и т.п.
    public static boolean validateStrings (String ... lines)
    {
        if (lines != null)
        {   int i = -1;
            while (++i < lines.length)
                if (lines[i] == null || lines[i].trim().isEmpty())
                    break;
            return i == lines.length;
        }
        return false;
    }// validateString ()

//Выполняем действия, полагающиеся при выходе из чата.
    private void closeSession (String prompt)
    {
/*  Всесь процесс выхода и чата (не из приложения) заключается в трёх действиях:
    - отправка сообщения CMD_EXIT на сервер (если выход инициализировал клиент);
    - вызов onCmdExit для изменения некоторых переменных и остановки доп.потоков;
    - вызов disconnect().
*/
        sendMessageToServer (CMD_EXIT);   //< выполняется при необходимости
        onCmdExit (prompt);
        disconnect ();
    }// closeSession ()

    public void print (String s) {System.out.print(s);}
}// class Controller

// TODO • если попробовать войти вчат при выключенном сервере, то потом не получается в него войти до перезапуска клиента.
