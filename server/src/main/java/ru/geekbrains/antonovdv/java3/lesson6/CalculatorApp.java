package ru.geekbrains.antonovdv.java3.lesson6;

import java.util.Arrays;

public class CalculatorApp
{
/** <p>Написать метод, которому в качестве аргумента передается не пустой одномерный целочисленный массив.
    Метод должен вернуть новый массив, который получен путем вытаскивания из исходного массива элементов,
    идущих после последней четверки. Входной массив должен содержать хотя бы одну четверку, иначе в методе
    необходимо выбросить RuntimeException.</p>
    <p>Написать набор тестов для этого метода (по 3-4 варианта входных данных).<pre>
    Вх: [ 1 2 4 4 2 3 4 1 7 ] -> вых: [ 1 7 ].</pre>
    @param arri непустой одномерный массив int[].
    @param number число int (используемое вместо четвёрки).
    @throws RuntimeException, если в массиве нет ни одной четвёрки.
    @return часть исходного массива, расположенную справа от последней четвёрки.
    @author Дмитрий Антонов
    @version 1.2.0000 (Компиялтор считает, что этому тэгу здесь не место.)
*/
    public int[] cutArrayOffByNumber (int[] arri, int number)
    {
        int[] result = {};
        if (arri != null && arri.length > 0)
        {
            int index = arri.length;
            while (--index >= 0 && arri[index] != number) ; //< ищем «четвёрку»

            if (index >= 0)   result = Arrays.copyOfRange (arri, index+1, arri.length);
            else
            throw new RuntimeException(); //< эту строку дописанли прямо в уд.репо.
        }
        else
        throw new RuntimeException(); //< эту строку дописанли прямо в уд.репо.
        return result;
    }// cutArrayOffByNumber ()



/**      Написать метод, который проверяет состав массива из чисел 1 и 4. Если в нем нет хоть одной четверки
    или единицы, то метод вернет false; Написать набор тестов для этого метода (по 3-4 варианта входных данных).
    <pre>
        [ 1 1 1 4 4 1 4 4 ] -> true
        [ 4 4 4 4 ] -> false
        [ 1 4 4 1 1 4 3 ] -> false</pre>
    <ol><li>Нельзя создавать заголовки h1 и h2, т.к. javadoc вставляет свои заголовки этих уровней, и они могут
        пересекаться с вашими.</li>
    <li>тэг @see используется для создания ссылки на другие классы: через пробел нужно указать имя_класса,
        или полное_имя_класса, или полное_имя_класса#имя_метода. В тексте такая ссылка будет иметь префикс
        «See Also».</li>
    <li>тэг { @link пакет.класс#член_класса метка } похож на предыдущий, но может использоваться как встроенный.
        (нужно убрать пробелы после { и перед }, чтобы тэг заработал. У работающего тэга  в тексте отображается
        только поле {@link пакет.класс#член_класса метка})</li>
    <li>тэг { @docRoot } показывает относительный путь к корневой папке, в которой нах.докум-я. Он выглядит в
        тексте как {@docRoot} .</li>
    <li>тэг { @inheritDoc } наследует док-ю ближ.базов.класса. В тексте выглядит как: {@inheritDoc}.</li>
    <li>тэг @author несёт инф-ю об авторе, если в команд.строке к javadoc есть ключ -author. Можно сделать
        несколько таких полей, но они должны располагаться друг за другом без пропусков.</li>
    <li>тэг @since несёт инф-ю о версии ч.-л., с которой начлось использование некой возможности. Например,
        версию JDK.</li>
    <li>тэг @param описывает название и назначение параметра в описании метода. Текст от этого тэга до след.тэга
        будет ассоцирован с этим тэгом. Допускается множестенное использование этого тэга.</li>
    <li>тэг @return описывает значение, возвращаемое методом.</li>
    <li>тэг @throws содержит полное имя класса исключения и описание ситуации, в которой бросается искл-е.</li>
    <li>тэг @deprecated исп-ся в описании устаревших методов и описывает ситуацию: причины устаревания, возможные
        замены, дату устаревания, и др. До Jse5 тэг использовался компилятором для выдачи предупреждения об использовании
        устаревшего метода. С jse5 для этой цели используется аннот-я @Deprecared в объявлении метода.</li>
    <li>При компиляции с исп-ем javadoc ни одна русская буква не отображалась правильно, а файлов было создано столько,
        сколько не всякий проект в состоянии сгенерить за месяц.</li>
    <li>Джавадок — говно!</li>
    </ol>

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


/** javadoc обрабатывает документацию только для public- и protected-членов класса. Для членов класса
    private и package-private требуется использование флага -private. */
    public static void main (String[] args)
    {
        //CalculatorApp calc = new CalculatorApp();

        //int[] arri = {1, 2, 4, 4, 2, 3, 4, 1, 7};// {1, 2, 2, 3, 1, 7};// {};// {4};// {4,4,4,4};// {1};//
        //arri = calc.cutArrayOffByNumber(arri, 4);
        //System.out.println (Arrays.toString (arri));

        //arri = new int[]{1, 1, 1, 4, 4, 1, 4, 4};
        //System.out.println (calc.checkForOneAndFourPresence (arri));
        //arri = new int[]{1, 1, 1, 1, 1, 1};
        //System.out.println (calc.checkForOneAndFourPresence (arri));
        //arri = new int[]{4, 4, 4, 4};
        //System.out.println (calc.checkForOneAndFourPresence (arri));
        //arri = new int[]{1, 4, 4, 1, 1, 4, 3};
        //System.out.println (calc.checkForOneAndFourPresence (arri));
/*
        System.getProperties().list(System.out);
        System.out.println("---------------------------------");
        System.out.println ("user.name = "+ System.getProperty("user.name"));
        System.out.println ("user.language = "+ System.getProperty("user.language"));
        System.out.println ("user.country = "+ System.getProperty("user.country"));
        System.out.println ("file.separator = "+ System.getProperty("file.separator"));
        System.out.println ("line.separator = ["+ System.getProperty("line.separator")+']');
        System.out.println ("user.home = "+ System.getProperty("user.home"));
        System.out.println ("os.version = "+ System.getProperty("os.version"));
        System.out.println ("os.name = "+ System.getProperty("os.name"));
        System.out.println ("sun.desktop = "+ System.getProperty("sun.desktop"));
        System.out.println ("user.dir = "+ System.getProperty("user.dir"));
        System.out.println ("java.specification.version = "+ System.getProperty("java.specification.version"));
        System.out.println ("file.encoding = "+ System.getProperty("file.encoding"));
        System.out.println ("sun.cpu.endian = "+ System.getProperty("sun.cpu.endian"));
        System.out.println ("path.separator = "+ System.getProperty("path.separator"));
        System.out.println ("java.library.path = "+ System.getProperty("java.library.path"));
        //System.out.println (" = "+ System.getProperty(""));   */

        ;
    }// main()

}// class CalculatorApp

