package ru.geekbrains.march.chat.server;

import java.security.InvalidParameterException;
import java.util.HashMap;
import java.util.Map;

import static java.lang.Integer.max;

public class AuthentificationProvider implements Authentificator
{
    public static boolean THROW_EXCEPTION = true, SOFT_MODE = !THROW_EXCEPTION;
    private final static int MAP_CAPACITY_MIN = 16;
    public static final String FORMAT_USER = "(lpn:%s,%s,%s)";

    private final Map<String, User> mapUsers;
    private final Map<String, String> mapNicknames;   //для быстрокго поиска ключа для mapUsers

    public class User
    {
        private String login, password, nickname;

        public User (String lgn, String psw, String nick)
        {
            if (!validateStrings (SOFT_MODE, lgn, psw, nick))
                throw new InvalidParameterException (
                    String.format("\nERROR @ AuthentificationProvider.add() : cannot create user with parameters " + FORMAT_USER, lgn, psw, nick));

            login = lgn.trim();        //< key для mapUsers
            password = psw;
            nickname = nick.trim();    //< key для mapNicknames
        }
        @Override public String toString () { return String.format (FORMAT_USER, login, password, nickname); }
    }// class User


    public AuthentificationProvider (int capacity)
    {
        capacity = max(capacity, MAP_CAPACITY_MIN);
        mapUsers = new HashMap<>(capacity);
        mapNicknames = new HashMap<>(capacity);
    }// конструктор

//(Вспомогательная.) Проверяет строку на пригодность для использования в качестве логина, пароля, ника.
    private static boolean validateStrings (boolean mode, String ... lines)
    {
        for (String s : lines)
        if (s == null || s.trim().isEmpty())
            if (mode == THROW_EXCEPTION)
                throw new InvalidParameterException (
                    "ERROR @ validateString() : String must not be null, empty or blank.");
            else
                return false;
        return true;
    }// validateString ()


//добавляем учётную запись для нового пользователя (типа регистрация).
    @Override public boolean add (String lgn, String psw, String nick)
    {
        boolean boolOk = false;
        User user = new User (lgn, psw, nick);

        if (!mapNicknames.containsKey (user.nickname))
        {
            mapNicknames.put (user.nickname, user.login);
            mapUsers.put (user.login, user);
            boolOk = true;
        }
        else System.out.printf("\nERROR @ AuthentificationProvider.add() :  nickname «%s» is busy.", nick);
        return boolOk;
    }// add ()


//проверяем учётные данные зарегистрированного ранее пользователя — по логину и паролю возвращаем ник.
    @Override public String authenticate (String lgn, String psw)
    {
        String nick = null;
        if (validateStrings (SOFT_MODE, lgn, psw))
        {
            User u = mapUsers.get (lgn.trim());
            if (u != null  &&  u.password.equals (psw))
                nick = u.nickname;
        }
        return nick;
    }// authenticateByLoginAndPassword ()


//меняем ник пользователя на указанный.
    @Override public String rename (String prevName, String newnickname)
    {
        //boolean boolOk = false;
        String result = null;
        String login;
        User u;

        if (validateStrings (SOFT_MODE, prevName, newnickname) &&
            !mapNicknames.containsKey (newnickname) &&
            (login = mapNicknames.remove (prevName)) != null &&
            (u = mapUsers.get(login)) != null)
        {
            mapNicknames.put (newnickname, login);
            u.nickname = newnickname;
            //boolOk = true;
            result = newnickname;
        }
        else System.out.print("\nНе удалось изменить ник пользователя : "+prevName+" -> "+newnickname+".");
        //return boolOk;
        return result;
    }// changeNickname ()


//удаляем учётную запись (эта функция пока не используется; добавлена для порядка).
    @Override public void remove (String nick)
    {
        boolean boolOk = false;
        String login;
        if (validateStrings (SOFT_MODE, nick)  &&  (login = mapNicknames.remove (nick)) != null)
        {
            boolOk = mapUsers.remove(login) != null;
        }
    }// remove ()

}// class AuthentificationProvider
