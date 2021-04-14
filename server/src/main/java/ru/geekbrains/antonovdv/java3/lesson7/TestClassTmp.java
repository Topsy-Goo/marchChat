package ru.geekbrains.antonovdv.java3.lesson7;

public class TestClassTmp
{
    public static final int MAX_ORDER = 10, MIN_ORDER = 1;

    @BeforeSuite public static void init ()  { println("\tinit()"); }
    @AfterSuite  public static void clear ()  { println("\tclear()"); }

//Варианты задания приоритета
    @Test @Order(order = 1) public void testMetod1  () { println("\ttestMetod1()"); }
    @Test @Order            public void testMetod10 () { println("\ttestMetod10()"); }
    @Test                   public void testMetod11 () { println("\ttestMetod11()"); }

//Различные модификаторы:
    @Test @Order(order = 2) protected void testMetod2 () { println("\ttestMetod2()"); }
    @Test @Order(order = 3)           void testMetod3 () { println("\ttestMetod3()"); }
    @Test @Order(order = 4) private   void testMetod4 () { println("\ttestMetod4()"); }
    @Test @Order(order = -1) public static void testMetod9 () { println("\ttestMetod9()"); }

//Сочетания аннотаций и недопустимые сигнатуры:
    @Test @AfterSuite @Order(order = 5) public void testMetod5 () { println("\ttestMetod5()"); }
    @Test             @Order(order = 6) public int  testMetod6 () { println("\ttestMetod6()"); return 0; }
                      @Order(order = 7) public void testMetod7 () { println("\ttestMetod7()"); }
    @Test             @Order(order = 7) public void testMetod8 (int i) { println("\ttestMetod8():"+ i); }


// Вспомогательные меотды:
    //public static void print (String s) { System.out.print (s); }
    public static void println (String s) { System.out.print ("\n"+s); }

}// class TestClassTmp
