package com.android.tools.checker;

public class TestAssertions {
    public static int count = 0;

    public static void fail() {
        throw new RuntimeException("Fail");
    }

    public static void count() {
        count++;
    }
}
