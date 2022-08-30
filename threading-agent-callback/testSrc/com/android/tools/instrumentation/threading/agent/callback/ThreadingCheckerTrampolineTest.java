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
import org.junit.Before;
import org.junit.Test;

public class ThreadingCheckerTrampolineTest {
    int verifyOnUiThreadCallCount = 0;
    int verifyOnWorkerThreadCallCount = 0;

    @Before
    public void setUp() {
        ThreadingCheckerTrampoline.clearHooks();
    }

    @Test
    public void installSingleHook() {
        ThreadingCheckerTrampoline.installHook(createThreadingCheckerHook());

        ThreadingCheckerTrampoline.verifyOnUiThread();
        assertThat(verifyOnUiThreadCallCount).isEqualTo(1);

        ThreadingCheckerTrampoline.verifyOnWorkerThread();
        assertThat(verifyOnWorkerThreadCallCount).isEqualTo(1);
    }

    @Test
    public void installMultipleHooks() {
        ThreadingCheckerTrampoline.installHook(createThreadingCheckerHook());
        ThreadingCheckerTrampoline.installHook(createThreadingCheckerHook());

        ThreadingCheckerTrampoline.verifyOnUiThread();
        assertThat(verifyOnUiThreadCallCount).isEqualTo(2);

        ThreadingCheckerTrampoline.verifyOnWorkerThread();
        assertThat(verifyOnWorkerThreadCallCount).isEqualTo(2);
    }

    @Test
    public void removeHook() {
        ThreadingCheckerHook hook1 = createThreadingCheckerHook();
        ThreadingCheckerHook hook2 = createThreadingCheckerHook();
        ThreadingCheckerTrampoline.installHook(hook1);
        ThreadingCheckerTrampoline.installHook(hook2);

        ThreadingCheckerTrampoline.verifyOnUiThread();
        assertThat(verifyOnUiThreadCallCount).isEqualTo(2);

        ThreadingCheckerTrampoline.removeHook(hook1);
        ThreadingCheckerTrampoline.verifyOnUiThread();
        assertThat(verifyOnUiThreadCallCount).isEqualTo(3);

        ThreadingCheckerTrampoline.removeHook(hook2);
        ThreadingCheckerTrampoline.verifyOnUiThread();
        assertThat(verifyOnUiThreadCallCount).isEqualTo(3);
    }

    @Test
    public void threadingViolationChecks_notEnforcedOnMethodInBaselineFile() {
        ThreadingCheckerTrampoline.installHook(createThreadingCheckerHook());

        String baselineMethod =
                "com.android.tools.instrumentation.threading.agent.callback.ThreadingCheckerTrampolineTest$InnerTestClass#method1";
        ThreadingCheckerTrampoline.BaselineViolationsHolder.baselineViolations =
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
    public void keepsTrackOfSkippedChecks_whenVerifyMethodsAreCalledBeforeHookIsInstalled() {
        ThreadingCheckerTrampoline.verifyOnUiThread();
        ThreadingCheckerTrampoline.verifyOnWorkerThread();

        assertThat(ThreadingCheckerTrampoline.skippedChecksCounter.get()).isEqualTo(2L);
        ThreadingCheckerTrampoline.installHook(createThreadingCheckerHook());

        assertThat(ThreadingCheckerTrampoline.skippedChecksCounter.get()).isEqualTo(0L);
        assertThat(verifyOnUiThreadCallCount).isEqualTo(0);
        assertThat(verifyOnWorkerThreadCallCount).isEqualTo(0);
    }

    private ThreadingCheckerHook createThreadingCheckerHook() {
        return new ThreadingCheckerHook() {
            @Override
            public void verifyOnUiThread() {
                ++verifyOnUiThreadCallCount;
            }

            @Override
            public void verifyOnWorkerThread() {
                ++verifyOnWorkerThreadCallCount;
            }
        };
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
