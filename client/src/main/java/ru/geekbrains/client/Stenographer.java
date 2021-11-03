package ru.geekbrains.client;

import java.io.Closeable;
import java.io.Serializable;
import java.util.List;

public interface Stenographer<T extends Serializable> extends Closeable {
    List<T> getData ();
    void append (T t);
}
