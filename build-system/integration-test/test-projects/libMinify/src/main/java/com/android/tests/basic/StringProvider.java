package com.android.tests.basic;

public class StringProvider {

    // R8 removes the field if it is constant. Therefore, make it seem as if the field is being
    // initialized with a non-constant value to allow testing that the field name is minified.
    private static int obfuscatedInt = System.currentTimeMillis() > 0 ? 4 : 5;

    public static String getString(int foo) {
        return Integer.toString(foo + obfuscatedInt);
    }
}
