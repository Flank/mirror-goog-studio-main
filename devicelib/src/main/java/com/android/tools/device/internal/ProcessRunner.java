/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.device.internal;

import com.android.annotations.NonNull;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * A {@link ProcessRunner} provides the ability to launch an external process while capturing its
 * stdout and stderr streams. It also allows waiting for completion of the process.
 */
public interface ProcessRunner {
    @NonNull
    Process start(@NonNull ProcessBuilder pb) throws IOException;

    /**
     * Causes the current thread to wait until either the process exits, or the timeout occurs. If
     * the process exits before the timeout, then this method waits for stdout and stderr reader
     * threads to quit, but only until the given timeout elapses.
     *
     * @return true if the process exited, false if it timed out waiting for the process to exit.
     *     The completion of reader threads has no impact on the return value.
     */
    boolean waitFor(long timeout, @NonNull TimeUnit unit) throws InterruptedException;

    /**
     * Destroys the launched process if its still alive, and also cancels any active streams that
     * may be reading the process's output.
     *
     * @see Process#destroyForcibly()
     */
    @NonNull
    Process destroyForcibly() throws InterruptedException;

    @NonNull
    String getStdout();

    @NonNull
    String getStderr();
}
