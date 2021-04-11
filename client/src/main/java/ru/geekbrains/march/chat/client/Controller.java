package ru.geekbrains.march.chat.client;

import javafx.application.Platform;
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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.Socket;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;

import static ru.geekbrains.march.chat.client.Main.WNDTITLE_APPNAME;
import static ru.geekbrains.march.chat.server.ServerApp.*;

public class Controller implements Initializable
{
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
            PROMPT_UNABLE_TO_CONNECT = "Не удалось подключиться.",
            PROMPT_YOU_ARE_LOGED_OFF = "Вы вышли из чата.",
            PROMPT_CONNECTION_LOST = "\nСоединение разорвано.",
            PROMPT_PRIVATE_MODE_IS_ON = "\n\nВы вошли в приватный режим. Ваши сообщения будут видны только выбранному собеседнику.",
            PROMPT_PRIVATE_MODE_IS_OFF = "\n\nВы вышли из приватного режима. Ваши сообщения будут видны всем участникам чата.",
            //PROMPT_STATISTICS = "\nсообщений = ",
            PROMPT_CONFIRM_NEW_NICKNAME = "Подтвердите смену вашего имени. Новое имя:\n%s",
            PROMPT_EMPTY_MESSAGE = "Введённое сообщение пустое или содержит только пробельные символы.",
            PROMPT_BAN_NICKNAME_SPECIFIED = "\nУказанное имя пользователя некорректно или уже используется.",
            PROMPT_UNABLE_TO_SEND_MESSAGE = "не удадось отправить сообщение",
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
    private Thread
            threadListenServerCommands,
            threadJfx,
            threadCommandDispatcher
            ;
    private MessageStenographer<ChatMessage> stenographer;
    private Queue<String> inputqueue;
    private final Object syncQue = new Object();
    private boolean
            chatGettingClosed,    //< индикатор того, что сеанс завершается и что потокам пора «закругляться»
            privateMode = MODE_PUBLIC,
            changeNicknameMode = MODE_KEEP_NICKNAME,
            tipsMode = TIPS_ON
            ;
    private static final Logger LOGGER = LogManager.getLogger(Controller.class);

    public static class ChatMessage implements Serializable
    {
        private final String name, text;   //< Serializable
        private final boolean inputMsg, privateMsg;
        private final LocalDateTime ldt = LocalDateTime.now();      //< Serializable (сейчас не используется)

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
    {
        LOGGER.fatal("initialize():начало ------------------------------------------");
        //LOGGER.info("initialize():начало");
        threadJfx = Thread.currentThread();
        updateUserInterface (CANNOT_CHAT);
        txtareaMessages.appendText (WELCOME_TO_MARCHCHAT);
        LOGGER.info("initialize():конец");
    }// initialize ()

// Изменяем атрибуты элементов управления так, чтобы пользователь мог пользоваться чатом, но не мог
// изменить своё имя. Или наоборот: чтобы мог ввести своё имя, но не мог пользоваться чатом.
    private void updateUserInterface (boolean canChat)
    {
        txtfieldUsernameField.setDisable (canChat == CAN_CHAT);
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
            LOGGER.info("connect() подключен");
        }
        catch (IOException ioe)
        {   LOGGER.warn(PROMPT_UNABLE_TO_CONNECT);
            closeSession ("\n"+PROMPT_UNABLE_TO_CONNECT);
            LOGGER.throwing (Level.ERROR, ioe);
        }
        return boolOk;
    }// connect ()

//Закрытие сокета и обнуление связанных с ним переменных.
    private void disconnect ()
    {
        LOGGER.info("disconnect() - начало отключения");
        try
        {   if (clientSideSocket != null && !clientSideSocket.isClosed())
                clientSideSocket.close();
        }
        catch(IOException e) { LOGGER.throwing (Level.ERROR, e); }
        finally
        {   clientSideSocket = null;
            dis = null;
            dos = null;
            LOGGER.info("disconnect() звершился");
        }
    }// disconnect ()

//--------------------------------------------------- потоки и очередь

// Обрабатываем сообщения, находящиеся в очереди inputqueue. (Run-метод для threadCommandDispatcher.)
    private void messageDispatcher ()
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
        String prompt = PROMPT_YOU_ARE_LOGED_OFF;
        boolean closeSessionYourSelf = false; //< определяет, какой поток будет вызывать closeSession: этот или Jfx
        LOGGER.info("messageDispatcher() выполняется");
        synchronized (syncQue)
        {
            try
            {
//syncQue.wait(5000); //< threadListenServerCommands нас разбудит, когда положит что-то в inputqueue
                while (!chatGettingClosed && inputqueue != null)
                {
                //Такая структура блока while оказалась оптимальной для работы всех трёх потоков: threadJfx,
                // threadCommandDispatcher и threadListenServerCommands.
                // Главная его особенность, которой не рекомендуется принебрегать, это -- приостановка потока
                // threadCommandDispatcher сразу после вызова Platform.runLater(->). Если этого не делать, то
                // поток threadJfx начинает «ломиться» в queuePoll() в то время, когда inputqueue занят
                // преимущественно threadCommandDispatcher'ом. Серии холостых вызовов queuePoll() могут
                // достигать нескольких тысяч подряд.
                    if (threadJfx == null || !threadJfx.isAlive()) //< если клиент закрыл приложение, не выходя из чата
                    {   threadJfx = null;
                        prompt = "Кажется, приложение закрылось до выхода из чата…";
                        chatGettingClosed = true;
                        closeSessionYourSelf = true;
                    }
                    else
                    {   if (!inputqueue.isEmpty())
                            Platform.runLater(()->{  chatGettingClosed = !queuePoll();  });
                //Использование wait-notify сделало использование sleep() ненужным, но пришлось добавить в клиент
                // один вызов Platform.runLater -- в closeSession(). Теперь в клиенте два вызова Platform.runLater.
                        syncQue.notify();   //< будим поток threadListenServerCommands
                        syncQue.wait(5000); //< даём спокойно поработать threadJfx (на всякий случай укажим таймаут)
                    }
                }//while
            }//try
            catch (InterruptedException e) //< искл.бросается вызовом thread.interrupt();
            {   LOGGER.throwing(e);
                chatGettingClosed = true;
                prompt = EMERGENCY_EXIT_FROM_CHAT;
                LOGGER.info("messageDispatcher(): threadCommandDispatcher is interrupted");
                //break;
            }
            finally
            {   String finalPrompt = prompt;
                if (!closeSessionYourSelf)
                    Platform.runLater(()->{  closeSession (finalPrompt);  });
                else
                    closeSession (finalPrompt); //< если родительский поток закрыт, то всё закрываем сами
                LOGGER.info("messageDispatcher() завершился");
                //threadCommandDispatcher = null;
            }
        }//synchronized
    }// messageDispatcher ()

// Run-метод потока threadListenServerCommands. Считываем сообщения из входного канала соединения и помещаем их в очередь.
// Больше ничего не делаем.
    private void runTreadInputStream ()
    {
        String  msg;
        LOGGER.info("runTreadInputStream() выполняется");
        try
        {   while (!chatGettingClosed)
            {
                msg = dis.readUTF().trim();
            // работо потоков организована так, что они обрабатывают за цикл по одному сообщению, но на всякий случай
            // доступ к СК помещён после вызова readUTF(), который, при некоторых изменениях кода, может воткнуться в
            // канал во время захвате СК.
                synchronized (syncQue) //синхронизируем доступ к inputqueue
                {
                    switch (msg)
                    {
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
                        default:
                        {   LOGGER.error("runTreadInputStream() : незарегистрированное сообщение:\n\t"+msg);
                            throw new UnsupportedOperationException (
                                    "\nERROR @ runTreadInputStream() : незарегистрированное сообщение:\n\t" + msg);
                        }
                    }//switch
                    syncQue.notify();
                    syncQue.wait();
                }//synchronized
            }//while
        }
        catch (InterruptedException e) //< искл.бросается вызовом thread.interrupt();
        {   chatGettingClosed = true;
            synchronized (syncQue) { inputqueue.offer (CMD_EXIT);} //если не можем слушать канал, то всем отбой.
            LOGGER.info("runTreadInputStream(): threadListenServerCommands is interrupted");
            //e.printStackTrace();
            LOGGER.throwing(e);
        }
        catch (IOException e)
        {   chatGettingClosed = true;
            synchronized (syncQue)
            {
                if (inputqueue != null) inputqueue.offer (CMD_EXIT);
            }
            LOGGER.error("ERROR @ runTreadInputStream(): соединение оборвалось");
            //e.printStackTrace();
            LOGGER.throwing(Level.ERROR, e);
        }
        finally
        {   LOGGER.info("runTreadInputStream() завершился (chatGettingClosed == "+chatGettingClosed+")");
            threadListenServerCommands = null;
        }
    }// runTreadInputStream ()

//(Вспомогательная. Без проверок; вызывается только из runTreadInputStream() во время захвата из СК.)
// Добавляем в очередь одну или несколько строк, в зависимости от типа сообщения. ()
    private void queueOffer (String ... lines)
    {
        if (inputqueue != null)
        {
            for (String s : lines)
                if (!inputqueue.offer (s))
                {   LOGGER.error("queueOffer() is unable to offer message");
                    throw new RuntimeException ("ERROR @ queueOffer() : unable to offer message.");
                }
            if (DEBUG)
            {   StringBuilder sb = new StringBuilder ("queueOffer();input message:\n\t");
                for (int i=0,n=lines.length;   i<n;   i++)    sb.append (i>0?" | ":'«').append(lines[i]);
                LOGGER.debug(sb.append('»').toString());
            }
        }
        else LOGGER.error("queueOffer() call while inputqueue == null");
    }// queueOffer ()

//Извлекаем команды из очереди и обрабатываем их. Вызывается только из threadJfx (через
// threadCommandDispatcher.Platform.runLater(->)).
    private boolean queuePoll ()
    {
        synchronized (syncQue) //< синхронизируем доступ к inputqueue
        {
            boolean boolOk = false;
            String msg;
            if ((msg = inputqueue.poll()) != null)
            {
                switch (msg)
                {
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
                    default:
                        throw new UnsupportedOperationException (
                            "ERROR queuePoll(): незарегистрированное сообщение: "+ msg);
                }//switch
                LOGGER.debug(String.format("queuePoll() обработал msg «%s» с результатом: %b", msg, boolOk));
            }
            else LOGGER.error("queuePoll() считал null");
            return boolOk;
        }//synchronized
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

        txtareaMessages.appendText (cm.toString());
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

        txtareaMessages.appendText (cm.toString());
        return true;
    }// onCmdPrivateMsg ()

// Обработчик команды CMD_LOGIN (сообщение приходит в случае успешной авторизации).
    boolean onCmdLogIn ()
    {   nickname = inputqueue.poll();
        if (!validateStrings (nickname))
            throw new RuntimeException("ERROR @ onCmdLogIn() : queue polling error.");

        readChatStorage();    //< Считываем историю чата из файла
        updateUserInterface (CAN_CHAT);
        LOGGER.info("onCmdLogIn()/nickname: "+ nickname);
        return sendMessageToServer (CMD_LOGIN_READY); //< сообщаем о готовности войти в чат (теперь мы участники чата)
    }// onCmdLogIn ()

// Обработчик команды CMD_CHANGE_NICKNAME.
    boolean onCmdChangeNickname ()
    {
        nickname = inputqueue.poll();
        if (!validateStrings (nickname))
            throw new RuntimeException ("ERROR @ onCmdChangeNickname() : queue polling error.");

        txtfieldUsernameField.setText (nickname);
        onactionChangeNickname();   //< Отщёлкиваем кнопку «Сменить имя» в исходное состояние.
        txtfieldMessage.clear();//< перед отправкой запроса на сервер мы оставили имя в поле ввода.
                                // Теперь нужно его оттуда убрать.
        txtfieldMessage.requestFocus();
        LOGGER.info("поменял имя на: "+ nickname);
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

        listviewClients.getItems().clear();
        for (String s : tmplist)
            listviewClients.getItems().add(s);
        return true;
    }// onCmdClientsList ()

// Обработчик команды CMD_CONNECTED. Информируем пользователя об устанке соединения с сервером.
    boolean onCmdConnected ()
    {
        txtareaMessages.appendText (PROMPT_CONNECTION_ESTABLISHED);
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
        closeSession (PROMPT_CONNECTION_LOST +"\n"+ prompt);
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

// Обработчик команды CMD_EXIT. Должна вызываться из синхронизированного контекста, т.к. обращается к inputqueue.
// Также вызывается из: closeSession().
    boolean onCmdExit (String prompt)
    {
        LOGGER.info("onCmdExit() начало");
        chatGettingClosed = true; //< это заставит звершиться дополнительные потоки
        if (threadJfx != null)  updateUserInterface (CANNOT_CHAT);

        if (stenographer != null) //< если stenographer == 0, то, скорее всего, ничего сохранять или выводить уже не нужно
        {
            if (!validateStrings (prompt))
                prompt = PROMPT_YOU_ARE_LOGED_OFF;

            if (nickname != null)
            {   ChatMessage cm = new ChatMessage (nickname, prompt, INPUT_MSG, PUBLIC_MSG);
                if (stenographer != null) stenographer.append (cm);
                prompt = cm.toString();
            }
            txtareaMessages.appendText (prompt);
            LOGGER.info("onCmdExit()/prompt: "+prompt);
            stenographer.close();
        }
        stenographer = null;
        login = null;
        nickname = null;

        synchronized (syncQue) {syncQue.notifyAll();}
        try
        {   if (threadListenServerCommands != null) threadListenServerCommands.join(1000);
            if (threadCommandDispatcher != null) threadCommandDispatcher.join(1000);
        }
        catch (InterruptedException e) { LOGGER.throwing(Level.ERROR,e); }
        finally
        {   threadListenServerCommands = null;
            threadCommandDispatcher = null;
            if (inputqueue != null) inputqueue.clear();
            inputqueue = null;
        }
        LOGGER.info("onCmdExit() конец");
        return true;
    }// onCmdExit ()

//------------------------- обработчики команд интерфейса ----------------------------

// Обработка ввода пользователем логина и пароля для входа чат.
    @FXML public void onactionLogin (ActionEvent actionEvent)
    {
        LOGGER.info("onactionLogin() начало");
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
            LOGGER.info(String.format("onactionLogin()/ login: %s; password: %s", login, password));
            inputqueue = new LinkedList<>(); //< других потоков нет (можно не синхронизировать доступ)

            threadListenServerCommands = new Thread(() -> runTreadInputStream());
            threadListenServerCommands.start();
            threadCommandDispatcher = new Thread(() -> messageDispatcher());
            threadCommandDispatcher.start(); //< входим в Main Loop.

            this.login = login; //< запоминаем логин, под которым регистрируемся (для имени файла)
            sendMessageToServer (CMD_LOGIN, this.login, password);
        }
        else txtareaMessages.setText (PROMPT_UNABLE_TO_CONNECT);
        LOGGER.info("onactionLogin() конец");
    }// onactionLogin ()

// Кнопка «Выход». Пришлось отказаться от использования одной кнопки для входа в чат и выхода из чата, т.к.
// JavaFX даже Platform.runLater нормально не может в очередь поставить и в результате нажатия на кнопку
// «Вход/Выход» обрабатывались беспорядочно.
    @FXML public void onactionLogout () {   closeSession(PROMPT_YOU_ARE_LOGED_OFF);  }// onactionLogout ()

//Обработка вводимых пользователем сообщений. (У пользователя нет возможности вводить команды руками, —
// для управления приложением предусмотрены кнопки.)
    @FXML public void onactionSendMessage ()
    {
        LOGGER.info("onactionSendMessage() начало");
        String message = txtfieldMessage.getText();
        boolean boolSent = false;
        LOGGER.info("onactionSendMessage()/message: "+message);

        if (message == null || message.trim().isEmpty())
        {
            if (tipsMode == TIPS_ON)   alertWarning (ALERT_HEADER_EMPTY_MESSAGE, PROMPT_EMPTY_MESSAGE);
        }
        else if (changeNicknameMode == MODE_CHANGE_NICKNAME) //< включен режим смены имени
        {
            if (alertConfirmationYesNo (ALERT_HEADER_RENAMING, String.format(PROMPT_CONFIRM_NEW_NICKNAME, message)) == ANSWER_YES)
                boolSent = sendMessageToServer(CMD_CHANGE_NICKNAME, message);
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
            LOGGER.info("onactionSendMessage() конец");
        }
        else LOGGER.warn("onactionSendMessage() не справился");
    }// onactionSendMessage ()

// Обработка нажатия на кнопку «Приватно» (переключение приват. режима).
    @FXML public void onactionTogglePrivateMode ()
    {   LOGGER.info("onactionTogglePrivateMode() call");
        privateMode = !privateMode;
        btnToolbarPrivate.setSelected (privateMode);
        if (tipsMode == TIPS_ON)
            txtareaMessages.appendText (privateMode ? PROMPT_PRIVATE_MODE_IS_ON : PROMPT_PRIVATE_MODE_IS_OFF);
    }

// Обработка нажатия на кнопку «Сменить ник» (переключение режима смены ника).
    @FXML public void onactionChangeNickname ()
    {   LOGGER.info("onactionChangeNickname() call");
        changeNicknameMode = !changeNicknameMode;
        btnToolbarChangeNickname.setSelected (changeNicknameMode);
        if (changeNicknameMode == MODE_CHANGE_NICKNAME && tipsMode == TIPS_ON)
            txtareaMessages.appendText (PROMPT_CHANGE_NICKNAME);
    }// onactionChangeNickname ()

// Обработка нажатия на кнопку «Подсказки» (переключение режима показа подсказок к интерфейсу).
    @FXML public void onactionTips ()
    {   LOGGER.info("onactionTips() call");
        tipsMode = !tipsMode;
        if (tipsMode == TIPS_ON)
            txtareaMessages.appendText (PROMPT_TIPS_ON);
    }// onactionTips ()

//------------------------- вспомогательные методы ----------------------------

//(Вспомогательный метод.) Шлём на сервер строки отдельными сообщениями.
    private boolean sendMessageToServer (String ... lines)
    {
        boolean boolSent = false;
        StringBuilder sb;
        if (lines != null  &&  lines.length > 0  &&  dos != null)
        try
        {   if (DEBUG) sb = new StringBuilder("sendMessageToServer() call on :\n\t«");
            for (String msg : lines)
            {
                dos.writeUTF(msg);
                if (DEBUG) sb.append(msg).append(" | ");
            }
            if (DEBUG) LOGGER.debug(sb.append('»').toString());
            boolSent = true;
        }
        catch (IOException e)
        {   LOGGER.info ("ERROR @ sendMessageToServer() - "+ PROMPT_UNABLE_TO_SEND_MESSAGE);
            LOGGER.throwing (Level.ERROR, e);
        }
        return boolSent;
    }// sendMessageToServer ()

// Стандартное окно сообщения с кнопкой Close.
    public static void alertWarning (String header, String msg)
    {
        if (validateStrings (header, msg))
        {   Alert a = new Alert (Alert.AlertType.WARNING, msg, ButtonType.CLOSE);
            a.setTitle (ALERT_TITLE);
            a.setHeaderText (header);
            a.showAndWait();
        }
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
        if (validateStrings(login))
        {   stenographer = new MessageStenographer<> (login +".chat");
            List<ChatMessage> cmlist = stenographer.getData();
            txtareaMessages.clear(); //< очищаем окно чата (чтобы не мучаться, т.к. юзер может и под другой
            for (Object cm : cmlist) //           учёткой перезайти, для которой есть другой файл истории…)
                txtareaMessages.appendText (cm.toString());
        }
        else LOGGER.error("readChatStorage() вызван при login = "+login);
    }// readChatStorage ()

    @Override public String toString() { return "Controller:"+ nickname; }

//(Вспомогательная.) Проверяет строку на пригодность для использования в качестве логина, пароля, ника и т.п.
    public static boolean validateStrings (String ... lines)
    {
        boolean result = lines != null;
        if (result)
        for (String ln : lines)
        if (ln == null || ln.trim().isEmpty())
        {
            result = false;
            LOGGER.error(String.format("validateStrings() обнаружил: %s", ln));
            break;
        }
        else LOGGER.info(String.format("в validateStrings() передано %s", ln));
        return result;
    }// validateString ()

//Выполняем действия, полагающиеся при выходе из чата. Вызывается потоком threadJfx из:
//      connect()   - в блоке catch(){}
//      messageDispatcher() - в блоке finally при пом. Platform.runLater(->)
//      onCmdBadLogin()     - через messageDispatcher > Platform.runLater(queuePoll())
//      onactionLogout()    - через messageDispatcher > Platform.runLater(queuePoll())
    private void closeSession (String prompt)
    {
/*  Всесь процесс выхода и чата (не из приложения) заключается в трёх действиях:
    - отправка сообщения CMD_EXIT на сервер (если выход инициализировал клиент);
    - вызов onCmdExit для изменения некоторых переменных и остановки доп.потоков;
    - вызов disconnect().
*/      LOGGER.debug(String.format("closeSession() вызван с параметром: %s", prompt));
        sendMessageToServer (CMD_EXIT);   //< выполняется при необходимости
        onCmdExit (prompt); //< модно не синхронизировать, т.к. в этот блок есть доступ только у threadJfx
        disconnect();
        LOGGER.debug("closeSession() завершился");
    }// closeSession ()

    public void print (String s) {System.out.print(s);}
    public void println (String s) {System.out.print("\n"+s);}

}// class Controller

/*  TODO * сейчас при отправке личного сообщения оно сразу записывается в историю. Для таких исх.сообщений можно
            сделать подтверждение от сервера, по получении которого сообщение и будет записываться в историю.

    TODO : Преподаватель «анонсировал» короткое чтение истории чата из файла (применительно к его версии чата):
            Files.lines(Paths.get("log.txt")).collect(Collectors.joining("\n"));

    TODO * у нас получается захватить чужой ник, даже если хозяин вошёл в чат (если хозяин в чате, то ещё и
            список отображается неверно).
*/
