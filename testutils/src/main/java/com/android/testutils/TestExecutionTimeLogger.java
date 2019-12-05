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

package com.android.testutils;

import com.google.common.base.Stopwatch;
import java.time.temporal.ChronoUnit;

public class TestExecutionTimeLogger {
    private static final Stopwatch stopwatch = Stopwatch.createStarted();

    public static void log() {
        StackTraceElement caller = new Throwable().fillInStackTrace().getStackTrace()[1];
        System.out.format(
                "TestExecutionTimeLogger %1$ds: %2$s.%3$s:%4$d%n",
                stopwatch.elapsed().get(ChronoUnit.SECONDS),
                caller.getClassName(),
                caller.getMethodName(),
                caller.getLineNumber());
    }

    private static void logJvmShutdown() {
        log();
    }

    public static void addRuntimeHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(TestExecutionTimeLogger::logJvmShutdown));
    }
}
