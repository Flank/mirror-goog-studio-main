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

/**
 * Java agent is loaded by the bootstrap class loader, and we cannot emit bytecode that calls into
 * the core Android Studio code which is loaded by the system classloader.
 *
 * <p>So, we install a layer of indirection between these two worlds.
 */
public class ThreadingCheckerTrampoline {
    private static ThreadingCheckerHook hook = null;

    static BaselineViolations baselineViolations;

    // This method should be called from Android Studio startup code.
    public static void installHook(ThreadingCheckerHook newHook) {
        hook = newHook;
        baselineViolations = BaselineViolations.fromResource();
    }

    // This method is called from instrumented bytecode.
    public static void verifyOnUiThread() {
        if (hook == null) {
            return;
        }
        if (baselineViolations.isIgnored(getInstrumentedMethodSignature())) {
            return;
        }
        hook.verifyOnUiThread();
    }

    private static String getInstrumentedMethodSignature() {
        // Stack trace here will look like
        // Thread#getStackTrace
        // ThreadingCheckerTrampoline#getInstrumentedMethodSignature
        // ThreadingCheckerTrampoline.verifyOnUiThread
        // [method-of-interest]
        // ...
        //
        // And so we are interested in the fourth frame. If this changes please update the frame
        // index below
        StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[3];
        return stackTraceElement.getClassName() + "#" + stackTraceElement.getMethodName();
    }
}
