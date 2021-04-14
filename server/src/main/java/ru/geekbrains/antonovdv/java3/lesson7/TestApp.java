package ru.geekbrains.antonovdv.java3.lesson7;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.function.Predicate;

import static java.lang.Integer.max;
import static ru.geekbrains.antonovdv.java3.lesson7.TestClassTmp.MAX_ORDER;
import static ru.geekbrains.antonovdv.java3.lesson7.TestClassTmp.MIN_ORDER;

public class TestApp
{
    public static void main (String[] args)
    {
        start (TestClassTmp.class);
    }// main()

/*  Условие ДЗ:
        Создать класс, который может выполнять «тесты».
        В качестве тестов выступают классы с наборами методов, снабженных аннотациями @Test. Класс, запускающий
    тесты, должен иметь статический метод start(Class testClass), которому в качестве аргумента передается
    объект типа Class. Из «класса-теста» вначале должен быть запущен метод с аннотацией @BeforeSuite, если
    он присутствует. Далее запускаются методы с аннотациями @Test, а по завершении всех тестов – метод с
    аннотацией @AfterSuite.
        К каждому тесту необходимо добавить приоритеты (int-числа от 1 до 10), в соответствии с которыми будет
    выбираться порядок их выполнения. Если приоритет одинаковый, то порядок не имеет значения. Методы с
    аннотациями @BeforeSuite и @AfterSuite должны присутствовать в единственном экземпляре. Если это не так –
    необходимо бросить RuntimeException при запуске «тестирования».
*/
    public static void start (Class<?> testClass)
    {
        if (testClass == null || testClass.isInterface() ||
            testClass.isAnnotation() || testClass.isArray() || testClass.isPrimitive())
          throw new IllegalArgumentException();

    //Сздаём экземпляр класса testClass, чтобы иметь возможность запускать его нестатические методы:
        Object instance = null;
        try
        {   instance = testClass.newInstance();
        }
        catch (InstantiationException | IllegalAccessException e)
        {   e.printStackTrace();
            throw new RuntimeException("ERROR @ start(Class<?>) : не удалось создать экземпляр класса : "+ testClass);
        }
    //Составляем список методов сласса, которые будут участвовать в тесте. (Для простоты отладки не бросаем
    // исключения при встрече с недопустимыми сочетаниями атрибутов методов, а выводим сообщения, имитирующие
    // такие исключения.)

        Method[] methods = testClass.getDeclaredMethods();
        Method methodBefore = null,
               methodAfter = null;
        PriorityQueue<Method> methodsToTest = null;
        int size = methods.length;

        if (size > 0)
        {
            methodsToTest = new PriorityQueue<> (size, comparatorForMethodPriority());
            for (Method m : methods) //< Проверяем аннотации каждого метода класса testClass.
            {
                Method before = m.isAnnotationPresent (BeforeSuite.class) ? m : null,
                       after  = m.isAnnotationPresent (AfterSuite.class)  ? m : null,
                       test   = m.isAnnotationPresent (Test.class)        ? m : null;

                if (before == null && after == null && test == null)
                {   println ("ПРЕДУПРЕЖДЕНИЕ: "+m+"\n\t\tметод не будет участвовать в тесте.");
                    continue;
                }
        //Считаем, что следующие аннотации являются несовместимыми между собою: @BeforeSuite, @AfterSuite и @Test.
                if ((before != null && after != null) ||
                    (before != null && test  != null) ||
                    (after  != null && test  != null))
                {  printlnerr ("ОШИБКА (имитация RuntimeException) : "+m+"\n\t\tнедопустимое сочетание аннотаций.");
                   continue;
                }
        // Также считаем, что наличие аннотаций @Test или @BeforeSuite, или @AfterSuite подразумевает отсутствие
        // у метода m параметров и возвращаемого значения (по аналогии с подходом, принятым в log4j2).
                if (!m.getGenericReturnType().getTypeName().equals("void") || m.getParameterCount() > 0)
                {
                    printlnerr ("ОШИБКА (имитация RuntimeException) : "+m+"\n\t\tнедопустимая сигнатура;\n\t\t" +
                                "@BeforeSuite-, @AfterSuite- и @Test-методы должны возвращать void и не должны иметь аргументы.");
                    continue;
                }
        //Добавляем метод в список:
                if (before != null)
                    if (methodBefore == null) methodBefore = before;
                    else printlnerr ("ОШИБКА (имитация RuntimeException) : "+m+"\n\t\t" +
                                     "@BeforeSuite-метод должен быть единственным в классе.");
                else
                if (after != null)
                    if (methodAfter == null) methodAfter = after;
                    else printlnerr ("ОШИБКА (имитация RuntimeException : "+m+"\n\t\t" +
                                     "@AfterSuite-метод должен быть единственным в классе.");
                else
                methodsToTest.offer (test);
            }//for

    //Выполняем все методы, признанные годными для участия в тесте. (Не проверяем количество элементов
    // в methodsToTest, оставляя пользователю решать, как организовывать класс-тест.)

            invokeMethod (methodBefore, instance);

            while (!methodsToTest.isEmpty())
                invokeMethod (methodsToTest.poll(), instance);

            invokeMethod (methodAfter, instance);
        }
    }// start ()

//Выполнение методов класса-теста вынесено в отдельный метод.
    protected static void invokeMethod (Method m, Object instance)
    {
        if (m != null)
        try
        {   if (Modifier.isStatic (m.getModifiers()))    instance = null;
            else
            if (instance == null)
            {   printlnerr ("ОШИБКА (имитация NullPointerException) : "+m+"\n\t\t" +
                                        "«… the specified object is null and the method is an instance method».");
                return;
            }
            if (!m.isAccessible()) //< вместо проверки на private
                m.setAccessible(true);
            m.invoke (instance);
        }
        catch (InvocationTargetException | IllegalAccessException e) { e.printStackTrace(); }
    }// invokeMethod ()

//Создание анонимного класса-компаратора вынесено в отдельный метод.
    protected static Comparator<Method> comparatorForMethodPriority ()
    {
        return new Comparator<Method>() {
            @Override public int compare (Method m1, Method m2)
            {
                //(Если @Order не указан или указан неверно, то назначаем приоритет «по смыслу».)
                int priority1 = m1.isAnnotationPresent (Order.class) ? m1.getAnnotation(Order.class).order()
                                                                     : MAX_ORDER,
                    priority2 = m2.isAnnotationPresent (Order.class) ? m2.getAnnotation(Order.class).order()
                                                                     : MAX_ORDER;
                priority1 = min(MAX_ORDER, priority1);
                priority2 = min(MAX_ORDER, priority2);
                priority1 = max(MIN_ORDER, priority1);
                priority2 = max(MIN_ORDER, priority2);
                return priority1 - priority2; // (1 соотв-т макс. приоритету, 10 -- минимальному.)
            }
        };
    }// comparatorForMethodPriority ()

    public static void printlnerr (String s) { System.err.print ("\n"+ s +"\n"); }
    public static void println (String s) { System.out.print ("\n"+s); }

}// class TestApp

