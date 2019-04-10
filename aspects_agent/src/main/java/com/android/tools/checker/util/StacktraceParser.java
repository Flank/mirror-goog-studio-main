/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.checker.util;

import com.android.annotations.NonNull;

/**
 * Converts {@link StackTraceElement[]} to strings that can be parsed and read by {@link
 * com.android.tools.checker.agent.Baseline}. These strings are in the format:
 * method1|method2|...|methodN, where N is defined by {@link #SUBSTACK_SIZE}
 *
 * <p>For N = 2, we could have the following lines as example:
 * com.example.LaunchCompatibilityCheckerImpl.validate|com.other.pkg.Device.<init>
 * sun.configurations.Configuration.computeBestDevice|com.tools.Configuration.getDevice
 * com.example.Configuration.getDevice|com.nele.NlPreviewForm.lambda$initNeleModelWhenSmart$8
 */
public class StacktraceParser {

    private static final String SEPARATOR = "|";

    private static final String SEPARATOR_REGEX = "\\|";

    /**
     * Size of the subcallstack relevant to the whitelist. Generally, we don't need to whitelist the
     * full callstack as it would be a waste of resources. The top couple/few calls should be enough
     * to make each whitelisted flow unique.
     */
    private static final int SUBSTACK_SIZE = 2;

    /**
     * Index of a {@link StackTraceElement} array representing the current thread's callstack. This
     * index should skip the first element (Thread.currentThread().getStackTrace()) and the
     * following methods irrelevant to the callstack (e.g. the method called when intercepting an
     * instrumented call).
     */
    private static final int STACKTRACE_START_INDEX = 2;

    /**
     * Converts the already parsed stack trace to a printable format, with one method per line and
     * an extra indentation level per line.
     */
    @NonNull
    public static String formattedCallstack(@NonNull String stackTrace) {
        String[] toFormat = stackTrace.split(SEPARATOR_REGEX);
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < toFormat.length; i++) {
            for (int j = 0; j < i; j++) {
                // Indentation
                output.append("  ");
            }
            output.append(toFormat[i]).append("\n");
        }
        return output.toString();
    }

    public static String stackTraceToString(@NonNull StackTraceElement[] stackTrace) {
        StringBuilder callstack = new StringBuilder();
        for (int i = 0; i < SUBSTACK_SIZE; i++) {
            if (i > 0) {
                callstack.append(SEPARATOR);
            }
            callstack.append(
                    String.format(
                            "%s.%s",
                            stackTrace[STACKTRACE_START_INDEX + i].getClassName(),
                            stackTrace[STACKTRACE_START_INDEX + i].getMethodName()));
        }
        return callstack.toString();
    }
}
