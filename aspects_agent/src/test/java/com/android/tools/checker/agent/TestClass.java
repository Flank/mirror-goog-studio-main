package com.android.tools.checker.agent;

import com.android.tools.checker.AnotherTestAnnotation;
import com.android.tools.checker.BlockingTest;
import com.android.tools.checker.ConflictingAnnotation;

@AnotherTestAnnotation
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

    @AnotherTestAnnotation
    public void methodNop() {}

    @BlockingTest
    public void blockingMethod() {}

    @BlockingTest
    public void blockingMethod2() {}

    @ConflictingAnnotation
    public void conflictingMethod() {}
}
