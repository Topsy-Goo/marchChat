import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static ru.geekbrains.march.chat.server.ServerApp.TABLE_NAME;
import static ru.geekbrains.march.chat.server.JdbcAuthentificationProvider.*;

class Fragments
{
    private final Connection connection = null;



// Создаём в БД таблицу с логинами, паролями и именами пользователей.
    private static final String
        FRMT7_STMENT_INS_3FLD = "INSERT INTO [%s] (%s, %s, %s) VALUES (%s, %s, %s);"
        ;
    private static final String[][] usersdata =
        {{"1", "11", "u1111"}, {"2", "22", "u2222"}, {"3", "33", "u3333"}, {"4", "44", "u4444"}}
        ;
    private boolean tmpCreateUserSqlDB ()
    {
        boolean boolOk = false;
        //Savepoint sp1; //< для использования connection.rollback (Savepoint)

        try (Statement stnt = connection.createStatement();
             //PreparedStatement psInsert = connection.prepareStatement(
             //    String.format (FRMT_PREPSTMENT_INS_3FLD, TABLE_NAME, FLD_LOGIN, FLD_PASS, FLD_NICK));
            )
        {   connection.setAutoCommit (false); //< скорость увел. в 150 раз, и в случае ошибки выполнится rollback

            //sp1 = new Savepoint()
            //{   @Override public int getSavepointId () throws SQLException       {  return 1;  }
            //    @Override public String getSavepointName () throws SQLException  {  return "sp1";  }
            //};

            for (String[] as : usersdata)
            {
                stnt.addBatch(String.format(FRMT7_STMENT_INS_3FLD, TABLE_NAME, FLD_LOGIN, FLD_PASS, FLD_NICK, as[0], as[1], as[2]));
                //for (int i=1, n=as.length;   i <= n;   i++)
                //    psInsert.setString (i, as[i-1]);
                //psInsert.executeUpdate();
            }
            int[]ai = stnt.executeBatch();
            connection.setAutoCommit (true);    // исп. connection.commit(); для промежуточной отправки накопленных stnt
            boolOk = true;//
        }
        catch (SQLException throwables)
        {   try { connection.rollback(); } catch (SQLException e) { e.printStackTrace(); }
            throwables.printStackTrace();
            throw new RuntimeException();
        }
        return boolOk;
    }// tmpCreateUserSqlDB ()


/*/ ----------------- Считываем из входного потока сообщение с кодом команды (остальные части команды, если они есть, считаем
// в обработчике.)
    private String readInputStreamUTF ()
    {
        String msg = null;
        int timer = 0;

/*  Я попробовал исключить пару dis.available()—Thread.sleep() из этого метода, и потоки действительно
исправно останавливались при закрытии сокета на к.-л. конце соединения. Но, к сожалению, вылезла
другая проблема, которую я не смог решить иначе как вернув всё на место.

    Не могу пока себе это объяснить, но, если в dis.readUTF() затыкает собою канал связи,
то начинаются проблемы с синхронизацией команд (я думаю, что это именно рассинхронизация). Команда
у меня является последовательностью сообщений, а не единой строкой. В результате «недуга» части
команд начинают приходить не к тем обработчикам.
/        try
        {   while (!chatGettingClosed)
            if (dis.available() > 0)
            {
                msg = dis.readUTF();
                break;
            }
            else
            {   Thread.sleep(INPUT_THREAD_SLEEPINTERVAL);
                timer ++;
                if (timer > 5000 / INPUT_THREAD_SLEEPINTERVAL)
                {
                    if (!threadParent.isAlive())
                        break;
                    dos.writeUTF(CMD_ONLINE);
                    timer = 0;
                }
            }
        }
        catch (IOException | InterruptedException e)
        {   onCmdExit (PROMPT_CONNECTION_LOST);
            e.printStackTrace();
        }
        return msg;
    }// readInputStreamUTF ()   //*/

    void test ()
    {
//————————————————
        InputStream is;
            FileInputStream fis;
            ByteArrayInputStream bais;
            ObjectInputStream ois;
            FilterInputStream flis;
                DataInputStream dis;
                BufferedInputStream bis;
/*pr*/          PrintStream ps;
            PipedInputStream pis;
            SequenceInputStream sis;
        OutputStream os;
            FileOutputStream fos;
            ByteArrayOutputStream baos;
            ObjectOutputStream oos;
            FilterOutputStream flos;
                DataOutputStream dos;
                BufferedOutputStream bos;
            PipedOutputStream pos;
//————————————————
        Reader r;
            InputStreamReader isr;
                FileReader fr;
            BufferedReader br;
            CharArrayReader car;
            PipedReader ppr;
            FilterReader fltr;
                PushbackReader pbr;
        Writer w;
            OutputStreamWriter osw;
                FileWriter fw;
            BufferedWriter bw;
            CharArrayWriter caw;
/*pr*/      PrintWriter pw;
            PipedWriter ppw;
//————————————————
        StandardCharsets stcs;
        RandomAccessFile raf;
//————————————————

    }// test ()


    public synchronized void method1()
    {
        System.out.println("M1");
        for (int i = 0; i < 10; i++)
        {
            System.out.println(i);
            try
            {   Thread.sleep(100);
            }
            catch (InterruptedException e) {   e.printStackTrace();  }
        }
        System.out.println("M2");
    }

    public synchronized void method2()
    {
        System.out.println("M1");
        for (int i = 0; i < 10; i++)
        {
            System.out.println(i);
            try
            {   Thread.sleep(100);
            }
            catch (InterruptedException e) { e.printStackTrace(); }
        }
        System.out.println("M2");

        //Arguments.arguments();
    }

    //ArrayBlockingQueue<String> abq = new ArrayBlockingQueue<>();

}// class Fragments