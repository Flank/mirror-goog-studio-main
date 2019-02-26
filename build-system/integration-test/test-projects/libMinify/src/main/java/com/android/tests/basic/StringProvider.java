package com.android.tests.basic;

public class StringProvider {
    private static int obfuscatedInt = 4;

    public static String getString(int foo) {
        // R8 removes the field if it is constant. Therefore,
        // write a new value to it in order for tests that check
        // obfuscation to pass.
        obfuscatedInt = 5;
        return Integer.toString(foo + obfuscatedInt);
    }
}
