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

package com.android.tools.profiler.support.cpu;

import android.system.Os;

/** A set of helpers for Android {@link Debug} instrumentation, used by the CPU Profiler. */
@SuppressWarnings("unused") // Used by native instrumentation code.
public final class TraceOperationTracker {
    /**
     * Entry hook for {@link Debug#startMethodTracing(String)}.
     *
     * @param tracePath the path to the file containing method tracing data.
     */
    public static void onStartMethodTracing(String tracePath) {
        sendStartOperation(Os.gettid(), tracePath);
    }

    /** Exit hook for {@link Debug#stopMethodTracing()}. */
    public static void onStopMethodTracing() {
        sendStopOperation(Os.gettid());
    }

    /**
     * Entry hook for {@link String Debug#fixTracePath(String)}.
     *
     * @param tracePath the path passed in, potentially a relative path.
     */
    public static void onFixTracePathEntry(String tracePath) {
        recordInputPath(Os.gettid(), tracePath);
    }

    /**
     * Exit hook for {@link String Debug#fixTracePath(String)}.
     *
     * @param fixedPath return value of fixTracePath(). It is an absolute path with the right extension.
     */
    public static String onFixTracePathExit(String fixedPath) {
        recordOutputPath(Os.gettid(), fixedPath);
        return fixedPath;
    }

    // Native functions to send trace operations to perfd.
    // Note Android NDK doesn't support Linux's gettid(); therefore, we need to obtain it from
    // the platform's Java API.
    private static native void sendStartOperation(int thread_id, String tracePath);

    private static native void sendStopOperation(int thread_id);

    private static native void recordInputPath(int thread_id, String input_path);

    private static native void recordOutputPath(int thread_id, String output_path);
}
