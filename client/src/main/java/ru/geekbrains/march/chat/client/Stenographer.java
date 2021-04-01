package ru.geekbrains.march.chat.client;

import java.io.Closeable;
import java.io.Serializable;
import java.util.List;

public interface Stenographer<T extends Serializable> extends Closeable
{
    List<T> read ();

    void append (T t);

}// interface Stenographer
