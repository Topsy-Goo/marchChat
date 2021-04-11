import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.geekbrains.antonovdv.java3.lesson6.CalculatorApp;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class CalcTest
{
    private static CalculatorApp calculator;


    @BeforeAll static void initAll ()    {   calculator = new CalculatorApp();   }

    public static Stream<Arguments> generatorCutArrayOffByNumber ()
    {
        List<Arguments> list = new ArrayList<>();
        list.add (Arguments.arguments (new int[]{1},   new int[]{4,1}));
        list.add (Arguments.arguments (new int[]{},    new int[]{1}));
        list.add (Arguments.arguments (new int[]{},    new int[]{4,4,4,4}));
        list.add (Arguments.arguments (new int[]{},    new int[]{4}));
        list.add (Arguments.arguments (new int[]{},    new int[]{1,2,2,3,1,7}));
        list.add (Arguments.arguments (new int[]{1,7}, new int[]{1,2,4,4,2,3,4,1,7}));
        list.add (Arguments.arguments (new int[]{},    new int[]{}));
        return list.stream();
    }
    @ParameterizedTest
    @MethodSource("generatorCutArrayOffByNumber")
    public void testCutArrayOffByNumber (int[] result, int[] arri)
    {
        Assertions.assertArrayEquals (result, calculator.cutArrayOffByNumber (arri, 4));
    }


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
    @ParameterizedTest
    @MethodSource("generatorCheckForOneAndFourPresence")
    public void testCheckForOneAndFourPresence (boolean result, int[] arri)
    {
        Assertions.assertEquals(result, calculator.checkForOneAndFourPresence(arri));
    }

}// class CalcTest
