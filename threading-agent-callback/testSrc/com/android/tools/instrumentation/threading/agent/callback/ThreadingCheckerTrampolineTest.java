/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.instrumentation.threading.agent.callback;

import static com.google.common.truth.Truth.assertThat;

import java.io.ByteArrayInputStream;
import org.junit.Test;

public class ThreadingCheckerTrampolineTest {
    int verifyOnUiThreadCallCount = 0;
    int verifyOnWorkerThreadCallCount = 0;

    @Test
    public void threadingViolationChecks_notEnforcedOnMethodInBaselineFile() {
        ThreadingCheckerTrampoline.installHook(
                new ThreadingCheckerHook() {
                    @Override
                    public void verifyOnUiThread() {
                        ++verifyOnUiThreadCallCount;
                    }

                    @Override
                    public void verifyOnWorkerThread() {
                        ++verifyOnWorkerThreadCallCount;
                    }
                });

        String baselineMethod =
                "com.android.tools.instrumentation.threading.agent.callback.ThreadingCheckerTrampolineTest$InnerTestClass#method1";
        ThreadingCheckerTrampoline.baselineViolations =
                BaselineViolations.fromStream(new ByteArrayInputStream(baselineMethod.getBytes()));

        // method1 is in the baseline
        InnerTestClass.method1();
        assertThat(verifyOnUiThreadCallCount).isEqualTo(0);
        assertThat(verifyOnWorkerThreadCallCount).isEqualTo(0);

        // method2 is not in the baseline
        InnerTestClass.method2();
        assertThat(verifyOnUiThreadCallCount).isEqualTo(1);
        assertThat(verifyOnWorkerThreadCallCount).isEqualTo(1);
    }

    @Test
    public void noop_whenInstallHookMethodIsNotCalled() {
        // Verifies that no exceptions are thrown by the ThreadingCheckerTrampoline#verifyOnUiThread
        // method if the ThreadingCheckerTrampoline#installHook has never been called.
        ThreadingCheckerTrampoline.verifyOnUiThread();
    }

    public static class InnerTestClass {

        public static void method1() {
            ThreadingCheckerTrampoline.verifyOnUiThread();
            ThreadingCheckerTrampoline.verifyOnWorkerThread();
        }

        public static void method2() {
            ThreadingCheckerTrampoline.verifyOnUiThread();
            ThreadingCheckerTrampoline.verifyOnWorkerThread();
        }
    }
}
