package ru.geekbrains.march.chat.server;

public class WaitNotifyApp
{
    private final Object o = new Object();
    private char letter = 'A';

    public void treadWaitNotifyTest ()
    {   new Thread(()->runThreadA()).start();
        new Thread(()->runThreadB()).start();
        new Thread(()->runThreadC()).start();
    }

    void runThreadA ()
    {   synchronized (o)
        {   try
            {   for (int i=0; i<5; i++)
                {
                    while (letter != 'A')
                        o.wait();
                    System.out.print (letter);
                    letter = 'B';
                    o.notifyAll();
                }
            } catch (InterruptedException e)  {  e.printStackTrace();  }
        }
    }// runThreadA ()

    void runThreadB ()
    {   synchronized (o)
        {   try
            {   for (int i=0; i<5; i++)
                {
                    while (letter != 'B')
                        o.wait();
                    System.out.print (letter);
                    letter = 'C';
                    o.notifyAll();
                }
            } catch (InterruptedException e)  {  e.printStackTrace();  }
        }
    }// runThreadB ()

    void runThreadC ()
    {   synchronized (o)
        {   try
            {   for (int i=0; i<5; i++)
                {
                    while (letter != 'C')
                        o.wait();
                    System.out.print (letter);
                    letter = 'A';
                    o.notifyAll();
                }
            } catch (InterruptedException e)  {  e.printStackTrace();  }
        }
    }// runThreadC ()

}// class WaitNotifyApp
