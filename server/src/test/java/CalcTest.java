import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.geekbrains.antonovdv.java3.lesson6.CalculatorApp;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class CalcTest
{
    private static CalculatorApp calculator;


    @BeforeAll static void initAll ()    {   calculator = new CalculatorApp();   }

/*  Тестирование метода CalculatorApp.cutArrayOffByNumber(). Тест разделён на две части: первая часть
    проверяет штатную работу метода, а вторая -- выбрасывание исключения. Это сделано для того, чтобы
    пройденные тесты были зелёными, в том числе и в случаях выбрасывания исключения.

    Метод …_exception добавлен по результатам разбора ДЗ: на разборе было сказано, что все пройденные тесты должны
    давать «зелёный» результат. В предыдущем варианте все тесты были собраны в одном методе, и тесты, бросающие
    исключение, давали «красный» результат, т.е. как бы не проходились. Теперь тесты, которые должны бросать исключение,
    тестируются в методе testCutArrayOffByNumber_exception().
*/
    public static Stream<Arguments> generatorCutArrayOffByNumber ()
    {
        List<Arguments> list = new ArrayList<>();
        list.add (Arguments.arguments (new int[]{1},   new int[]{4,1}));
        list.add (Arguments.arguments (new int[]{},    new int[]{4,4,4,4}));
        list.add (Arguments.arguments (new int[]{},    new int[]{1,1,1,1,1,1,4}));
        list.add (Arguments.arguments (new int[]{},    new int[]{4}));
        list.add (Arguments.arguments (new int[]{1,7}, new int[]{1,2,4,4,2,3,4,1,7}));
        return list.stream();
    }
    @Order(1) //< указываем порядок выполнения тестов. Т.к. тесты должны быть независимы, то этот порядок носит
              //  характер скорее декоративный, позволяющий упорядочить тесты, но не повлиять на их результат(ы).
    @ParameterizedTest
    @MethodSource("generatorCutArrayOffByNumber")
    public void testCutArrayOffByNumber (int[] result, int[] arri)
    {
        Assertions.assertArrayEquals (result, calculator.cutArrayOffByNumber (arri, 4));
    }

    public static Stream<Arguments> generatorCutArrayOffByNumber_exception ()
    {
        List<Arguments> list = new ArrayList<>();
        list.add (Arguments.arguments (new int[]{}, new int[]{1}));
        list.add (Arguments.arguments (new int[]{}, new int[]{1,2,2,3,1,7}));
        list.add (Arguments.arguments (new int[]{}, new int[]{}));
        return list.stream();
    }
    @Order(2)
    @ParameterizedTest
    @MethodSource("generatorCutArrayOffByNumber_exception")
    public void testCutArrayOffByNumber_exception (int[] result, int[] arri)
    {
        Assertions.assertThrows (RuntimeException.class, ()->calculator.cutArrayOffByNumber(arri, 4));
        //Assertions.assertArrayEquals (result, calculator.cutArrayOffByNumber (arri, 4));
    }


/*  Тестирование метода CalculatorApp.checkForOneAndFourPresence().
*/
    public static Stream<Arguments> generatorCheckForOneAndFourPresence ()
    {
        List<Arguments> list = new ArrayList<>();
        list.add (Arguments.arguments (true,  new int[]{1,1,1,4,4,1,4,4}));
        list.add (Arguments.arguments (false, new int[]{1,1,1,1,1,1}));
        list.add (Arguments.arguments (false, new int[]{4,4,4,4}));
        list.add (Arguments.arguments (false, new int[]{4}));
        list.add (Arguments.arguments (false, new int[]{1,4,4,1,1,4,3}));
        list.add (Arguments.arguments (false, new int[]{1}));
        list.add (Arguments.arguments (true,  new int[]{4,1}));
        list.add (Arguments.arguments (true,  new int[]{1,4}));
        list.add (Arguments.arguments (false, new int[]{}));
        return list.stream();
    }
    @Order(3)
    @ParameterizedTest
    @MethodSource("generatorCheckForOneAndFourPresence")
    public void testCheckForOneAndFourPresence (boolean result, int[] arri)
    {
        Assertions.assertEquals(result, calculator.checkForOneAndFourPresence(arri));
    }

    public void m1()
    {
    // Inconvertible types; cannot cast '
    //      java.lang.Class
    //          <capture<? extends
    //                             ru.geekbrains.antonovdv.java3.lesson6.CalculatorApp >>
    // ' to '
    //                             ru.geekbrains.antonovdv.java3.lesson6.CalculatorApp
    // '
        ;
        try
        {
            CalculatorApp calc = new CalculatorApp();
            Class c = calc.getClass();
            Class<CalculatorApp> clas = CalculatorApp.class;
            Constructor<CalculatorApp> constructor = clas.getConstructor();
        }
        catch (NoSuchMethodException e)
        {
            e.printStackTrace();
        }
    }

}// class CalcTest
