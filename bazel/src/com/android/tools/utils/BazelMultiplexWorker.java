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

package com.android.tools.utils;

import com.google.devtools.build.lib.worker.WorkerProtocol.WorkRequest;
import com.google.devtools.build.lib.worker.WorkerProtocol.WorkResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implements support for running programs as persistent Bazel workers, which can improve build
 * times by reducing JVM and JIT startup overhead. For details see
 * <a href="https://bazel.build/docs/persistent-workers">docs/persistent-workers</a>.
 *
 * <p>This is a "multiplex" Bazel worker, which means that multiple jobs can run
 * in parallel in the same worker process. For details see
 * <a href="https://bazel.build/docs/multiplex-worker">docs/multiplex-worker</a>.
 *
 * <p>Note: if <a href="https://github.com/bazelbuild/bazel/issues/14556">bazel/issues/14556</a>
 * gets resolved, then we may be able to reuse the generic worker implementation in Bazel rather
 * than rolling our own implementation here.
 */
public final class BazelMultiplexWorker {

    /**
     * A program which can be invoked repeatedly and in parallel. Program output should be written
     * to {@code out} instead of stdout/stderr. Any output written to stdout/stderr will be directed
     * to a shared worker log, which is generally not presented to the user.
     */
    @FunctionalInterface
    public interface Program {
        int run(List<String> args, PrintStream out) throws Exception;
    }

    /**
     * Runs the given CLI program as a persistent Bazel worker when possible.
     *
     * <p>The corresponding Bazel action (in Starlark) should be tagged with
     * "supports-multiplex-workers". All action arguments should be passed via a single argfile.
     *
     * <p>The {@code rawArgs} array should come directly from main(). To ensure isolation between
     * jobs, {@code program} should be thread safe and should not leak state between invocations.
     *
     * <p>This method does not return: it either loops indefinitely as a persistent worker, or runs
     * {@code program} and exits.
     */
    public static void run(String[] rawArgs, Program program) throws Exception {
        // The last argument is either '--persistent_worker' or '@argfile', depending on
        // whether Bazel decided to make the worker persistent. Preceding arguments are
        // reserved for use by the persistent worker itself (currently unused).
        if (rawArgs.length != 1) {
            throw new IllegalArgumentException(
                    "Expected exactly one argument. If you are writing a Bazel rule, you " +
                    "should be passing an argfile.");
        }
        String lastArg = rawArgs[rawArgs.length - 1];
        if (lastArg.equals("--persistent_worker")) {
            runAsPersistentWorker(program);
        } else if (lastArg.startsWith("@")) {
            List<String> argfileLines = Files.readAllLines(Paths.get(lastArg.substring(1)));
            System.exit(program.run(argfileLines, System.err));
        } else {
            throw new IllegalArgumentException("Unexpected last argument: " + lastArg);
        }
    }

    private static void runAsPersistentWorker(Program program) throws Exception {
        // Persistent workers communicate with Bazel via standard input/output.
        // So, we replace System.in and System.out to ensure nothing else reads/writes there.
        InputStream realStdIn = System.in;
        PrintStream realStdOut = System.out;
        System.setIn(new ByteArrayInputStream(new byte[0]));
        System.setOut(System.err);
        try {
            dispatchWorkRequests(realStdIn, realStdOut, program);
        } finally {
            System.setIn(realStdIn);
            System.setOut(realStdOut);
        }
    }

    private static void dispatchWorkRequests(
            InputStream requestStream,
            OutputStream responseStream,
            Program program) throws Exception {
        // Since the jobs run in parallel, we must serialize responses.
        ThreadSafeResponseWriter responseWriter = new ThreadSafeResponseWriter(responseStream);
        while (true) {
            WorkRequest request = WorkRequest.parseDelimitedFrom(requestStream);
            if (request == null) {
                break;
            }
            Thread workThread = new Thread(() -> {
                WorkResponse workResponse = doWork(request, program);
                responseWriter.write(workResponse);
            });
            workThread.setUncaughtExceptionHandler((t, e) -> {
                // Internal error; shut down the entire worker.
                e.printStackTrace(System.err);
                System.exit(1);
            });
            workThread.start();
        }
    }

    private static WorkResponse doWork(WorkRequest request, Program program) {
        try (ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
             PrintStream out = new PrintStream(outBuffer)
        ) {
            List<String> args = request.getArgumentsList();
            int exitCode;
            try {
                exitCode = program.run(args, out);
            } catch (Throwable e) {
                e.printStackTrace(out);
                exitCode = 1;
            }
            return WorkResponse.newBuilder()
                    .setRequestId(request.getRequestId())
                    .setOutput(outBuffer.toString(Charset.defaultCharset()))
                    .setExitCode(exitCode)
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static class ThreadSafeResponseWriter {
        private final OutputStream responseStream;
        private final ReentrantLock lock = new ReentrantLock();

        private ThreadSafeResponseWriter(OutputStream responseStream) {
            this.responseStream = responseStream;
        }

        private void write(WorkResponse response) {
            lock.lock();
            try {
                response.writeDelimitedTo(responseStream);
                responseStream.flush();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                lock.unlock();
            }
        }
    }
}
