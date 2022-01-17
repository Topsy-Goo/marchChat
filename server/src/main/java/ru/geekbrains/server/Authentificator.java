package ru.geekbrains.server;

import ru.geekbrains.server.errorhandlers.UserNotFoundException;

import java.sql.SQLException;

public interface Authentificator {

/** Проверяем, зарегистрирован ли пользователь. Все недоразумения, связанные с авторизацией, возвращаются в виде исключений.
    @return ник пользователя, которому в БД соответствуют введённые им логин и пароль.
    @throws UserNotFoundException логин и/или пароль не подошли;
    @throws SQLException какая-то ошибка БД (пробрасывается).  */
    String authenticate (String login, String password) throws SQLException;

    boolean add (String lgn, String psw, String nick);

/** В БД изменяем поле nickname == prevName на newName. (У поля nickname есть атрибут UNIQUE.). В случае
    кл. недоразумений метод бросает исключение.
    @return новое имя пользователя или NULL.
    @throws SQLException если произошла ошибка при работе с базой.  */
    String rename (String prevName, String newName) throws SQLException;

    void remove (String nick);

    Authentificator close (); //< чтобы close() мог вернуть null, чтобы одной строчкой закрыть и приравнять нулю (см.Server)
}
