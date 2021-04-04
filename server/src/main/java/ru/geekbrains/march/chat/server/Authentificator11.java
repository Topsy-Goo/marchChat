package ru.geekbrains.march.chat.server;

public interface Authentificator11
{
    String authenticate (String login, String password);

    boolean add (String lgn, String psw, String nick);

    String rename (String prevName, String newName);

    void remove (String nick);

    Authentificator11 close (); //< чтобы close() мог вернуть null, чтобы одной строчкой закрыть и приравнять нулю (см.Server)

}// interface Authentificator
