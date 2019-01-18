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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Stopwatch;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OsProcessRunner implements ProcessRunner {
    private final ExecutorService executor;

    private Process process;

    @SuppressWarnings("StringBufferField")
    private StringBuffer stdoutBuffer = new StringBuffer(128);

    @SuppressWarnings("StringBufferField")
    private StringBuffer stderrBuffer = new StringBuffer(128);

    private Future<?> stdoutReader;
    private Future<?> stderrReader;

    public OsProcessRunner() {
        this(ForkJoinPool.commonPool());
    }

    public OsProcessRunner(@NonNull ExecutorService executorService) {
        executor = executorService;
    }

    @NonNull
    @Override
    public Process start(@NonNull ProcessBuilder pb) throws IOException {
        String cmd = getBaseName(pb.command());
        process = pb.start();

        stdoutReader =
                executor.submit(
                        ScopedThreadNameRunnable.wrap(
                                () -> redirectStream(process.getInputStream(), stdoutBuffer),
                                cmd + ":stdout"));
        stderrReader =
                executor.submit(
                        ScopedThreadNameRunnable.wrap(
                                () -> redirectStream(process.getErrorStream(), stderrBuffer),
                                cmd + ":stderr"));
        return process;
    }

    private static void redirectStream(
            @NonNull InputStream inputStream, @NonNull StringBuffer buffer) {
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(inputStream, Charsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line);
                buffer.append('\n');
            }
        } catch (IOException e) {
            // ignored, process likely quit at this point
        }
    }

    @VisibleForTesting
    @NonNull
    static String getBaseName(@NonNull List<String> command) {
        if (command.isEmpty()) {
            return "unknown";
        }

        return Paths.get(command.get(0)).getFileName().toString();
    }

    @Override
    public boolean waitFor(long timeout, @NonNull TimeUnit unit) throws InterruptedException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        boolean processExited = process.waitFor(timeout, unit);

        if (processExited) {
            // if the process has quit, then wait to make sure that the readers are done
            try {
                stdoutReader.get(timeout - stopwatch.elapsed(unit), unit);
                stderrReader.get(timeout - stopwatch.elapsed(unit), unit);
            } catch (ExecutionException e) {
                // this would be a programming error that can be ignored here
                String msg =
                        "Unexpected error while waiting for stdout/stderr stream readers to finish";
                Logger.getLogger(OsProcessRunner.class.getName())
                        .log(Level.WARNING, msg, e.getCause());
            } catch (TimeoutException ignored) {
            }
        }

        return processExited;
    }

    @NonNull
    @Override
    public Process destroyForcibly() throws InterruptedException {
        if (process.isAlive()) {
            process.destroyForcibly();
            stdoutReader.cancel(true);
            stderrReader.cancel(true);
        }

        try {
            // wait until the stdout and stderr readers are done
            stdoutReader.get();
            stderrReader.get();
        } catch (ExecutionException | CancellationException ignored) {
        }

        return process;
    }

    @Override
    @NonNull
    public String getStdout() {
        return stdoutBuffer.toString();
    }

    @Override
    @NonNull
    public String getStderr() {
        return stderrBuffer.toString();
    }
}
