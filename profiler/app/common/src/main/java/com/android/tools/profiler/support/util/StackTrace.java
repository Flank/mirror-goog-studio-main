/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.profiler.support.util;

public final class StackTrace {

    /**
     * Returns a stacktrace where profiler wrapper methods are filtered out, so user code should be
     * the first line.
     *
     * @param offsetLevel Given offset number of levels to skip besides the profiler methods.
     */
    public static String getStackTrace(int offsetLevel) {
        StackTraceElement[] stackTraceElements = new Throwable().getStackTrace();
        int firstNonProfilerTraceIndex = 0;
        for (int i = 0; i < stackTraceElements.length; i++) {
            if (!stackTraceElements[i].getClassName().startsWith("com.android.tools.profiler")) {
                firstNonProfilerTraceIndex = i;
                break;
            }
        }
        StringBuilder s = new StringBuilder();
        for (int i = firstNonProfilerTraceIndex + offsetLevel; i < stackTraceElements.length; i++) {
            s.append(stackTraceElements[i]).append('\n');
        }
        return s.toString();
    }

    public static String getStackTrace() {
        return getStackTrace(0);
    }
}
