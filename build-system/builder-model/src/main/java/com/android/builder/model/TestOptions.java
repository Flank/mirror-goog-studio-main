package com.android.builder.model;

/** Test options for running tests - e.g. instrumented or not. */
public interface TestOptions {
    enum Execution {
        /** On device orchestration is not used in this case */
        HOST,
        /** On device orchestration is now used */
        ANDROID_TEST_ORCHESTRATOR
    }

    public boolean getAnimationsDisabled();

    public Execution getExecutionEnum();
}
