package ru.geekbrains.march.chat.server;

public interface Authentificator
{
    String authenticate (String login, String password);
    boolean add (String lgn, String psw, String nick);
    String rename (String prevName, String newName);
    void remove (String nick);

}// interface Authentificator
