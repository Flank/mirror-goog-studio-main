package com.android.tools.checker.agent;

public class TestClass {
    public static void staticMethodThrows() {
        // This will throw after instrumentation
    }

    public static void staticMethodNop() {}

    private void privateMethodThrows() {
        // This will throw after instrumentation
    }

    public void publicMethodThrows() {
        privateMethodThrows();
    }

    public void methodDoNotThrow() {
        // This should not throw
    }

    public void methodNop() {}
}
