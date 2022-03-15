package ru.geekbrains.client;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class MessageStenographer<T extends Serializable> implements Stenographer<T> {
    public static final boolean APPEND = true, REPLACE = !APPEND;
    private String filename;
    private File file;
    private List<T> datalist; //< собственно история чата

    public MessageStenographer (String filename) {
        //LOGGER.fatal("------------------------------------------------------------------------------------------------");
        if (filename == null || (filename = filename.trim()).isEmpty()) {
            throw new IllegalArgumentException();
        }

        file = new File(filename);
        try {
            file.createNewFile();   //< создаёт файл, только если он не существует
        }
        catch (IOException ioe) {
            file = null;
            //close();
            ioe.printStackTrace();
            throw new RuntimeException("ERROR : cannot create file.");
        }
        if (!file.isFile()) { throw new IllegalArgumentException("ERROR : not a file."); }
        this.filename = filename;
    }

/** Сейчас метод только записывает историю чата в файл.  */
    @Override public void close () {
        if (datalist != null && file != null && file.canWrite()) {
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file, REPLACE))) // ObjectOutputStream в close() закрывает и FileOutputStream.
            {
                oos.writeObject(datalist);
            }
            catch (IOException ioe) {
                ioe.printStackTrace();
                print("\nERROR @ storeTextUTF(): unable write to file.");
            }
            finally {
                filename = null;
                file = null;
                datalist = null;
            }
        }
    }

/** Добавляем одно сообщение к истории чата. */
    @Override public void append (T t) {
        if (t != null)
            datalist.add(t);
    }

/** Перезаписываем всю историю чата в файл. (У меня не получилось последовательно читать из файла объекты, т.к. из ObjectInputStream почему-то извлекался только один (первый) объект. Пришлось выходить из положения.   */
    @Override public List<T> getData () {
        if (datalist == null) {
            if (file.canRead()) {
                // ObjectInputStream.close() закрывает и FileInputStream.
                try (ObjectInputStream ois = new ObjectInputStream (new FileInputStream (file)))
                {
                    datalist = (List<T>) ois.readObject();
                    System.out.printf("\n\tистория считана из <%s>", filename); //< для отладки
                }
                catch (EOFException e) { //< пустой файл?
                    datalist = new ArrayList<>(16);
                }
                catch (IOException | ClassNotFoundException e) { e.printStackTrace(); }
            }
        }
        return datalist;
    }

    public void print (String s) {System.out.print(s);}
}
