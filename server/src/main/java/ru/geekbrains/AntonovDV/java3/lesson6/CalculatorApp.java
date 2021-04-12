package ru.geekbrains.AntonovDV.java3.lesson6;

import java.util.Arrays;

public class CalculatorApp
{
/*      Написать метод, которому в качестве аргумента передается не пустой одномерный целочисленный массив.
    Метод должен вернуть новый массив, который получен путем вытаскивания из исходного массива элементов,
    идущих после последней четверки. Входной массив должен содержать хотя бы одну четверку, иначе в методе
    необходимо выбросить RuntimeException.
        Написать набор тестов для этого метода (по 3-4 варианта входных данных).
    Вх: [ 1 2 4 4 2 3 4 1 7 ] -> вых: [ 1 7 ].
*/
    public int[] cutArrayOffByNumber (int[] arri, int number)
    {
        int[] result = {};
        if (arri != null && arri.length > 0)
        {
            int index = arri.length;
            while (--index >= 0 && arri[index] != number) //< ищем «четвёрку»
                ;
            if (index >= 0)  result = Arrays.copyOfRange (arri, index+1, arri.length);
            else
            throw new RuntimeException();
        }
        return result;
    }// cutArrayOffByNumber ()



/*      Написать метод, который проверяет состав массива из чисел 1 и 4. Если в нем нет хоть одной четверки
    или единицы, то метод вернет false; Написать набор тестов для этого метода (по 3-4 варианта входных данных).
        [ 1 1 1 4 4 1 4 4 ] -> true
        [ 1 1 1 1 1 1 ] -> false
        [ 4 4 4 4 ] -> false
        [ 1 4 4 1 1 4 3 ] -> false
*/
    public boolean checkForOneAndFourPresence (int[] arri)
    {
        boolean one = false, four = false;

        if (arri != null && arri.length > 0)
        for (int i : arri)
        {
            if (i == 1)   one = true;
            else
            if (i == 4)  four = true;
            else
            return false;
        }
        return one && four;
    }// checkForOneAndFourPresence ()



    public static void main (String[] args)
    {
        CalculatorApp calc = new CalculatorApp();

        int[] arri = {1, 2, 4, 4, 2, 3, 4, 1, 7};// {1, 2, 2, 3, 1, 7};// {};// {4};// {4,4,4,4};// {1};//
        arri = calc.cutArrayOffByNumber(arri, 4);
        System.out.println (Arrays.toString (arri));

        //arri = new int[]{1, 1, 1, 4, 4, 1, 4, 4};
        //System.out.println (calc.checkForOneAndFourPresence (arri));
        //arri = new int[]{1, 1, 1, 1, 1, 1};
        //System.out.println (calc.checkForOneAndFourPresence (arri));
        //arri = new int[]{4, 4, 4, 4};
        //System.out.println (calc.checkForOneAndFourPresence (arri));
        //arri = new int[]{1, 4, 4, 1, 1, 4, 3};
        //System.out.println (calc.checkForOneAndFourPresence (arri));
    }// main()

}// class CalculatorApp

