package ru.geekbrains.march.chat.server;

import java.io.Closeable;

public interface Authentificator extends Closeable
{
    String authenticate (String login, String password);
    boolean add (String lgn, String psw, String nick);
    String rename (String prevName, String newName);
    void remove (String nick);

}// interface Authentificator
