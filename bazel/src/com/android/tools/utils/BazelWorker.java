/*
 * Copyright (C) 2020 The Android Open Source Project
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
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Implements support for running programs as persistent Bazel workers, which can improve build
 * times by reducing JVM and JIT startup overhead. For details see
 * https://docs.bazel.build/versions/master/persistent-workers.html
 *
 * <p>Future work: we might be able to take advantage of Multiplex workers someday. See
 * https://docs.bazel.build/versions/master/multiplex-worker.html
 */
public class BazelWorker {

    /** A CLI program which leaks no state between repeated invocations. */
    @FunctionalInterface
    public interface StatelessCliProgram {
        int run(List<String> args) throws Exception;
    }

    /**
     * Runs the given CLI program as a persistent Bazel worker when possible.
     *
     * <p>The corresponding Bazel action (in Starlark) should be tagged with "supports-workers", and
     * its arguments should be passed via a single argfile.
     *
     * <p>The {@code rawArgs} array should come directly from main(). To ensure isolation between
     * jobs, {@code program} must be stateless.
     *
     * <p>This method does not return: it either loops indefinitely as a persistent worker, or runs
     * {@code program} and exits. If {@code program} detects an internal error that might taint
     * subsequent invocations, then it should thrown an exception to ensure that the persistent
     * worker gets shut down.
     */
    public static void run(String[] rawArgs, StatelessCliProgram program) throws Exception {
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
            System.exit(program.run(argfileLines));
        } else {
            throw new IllegalArgumentException("Unexpected last argument: " + lastArg);
        }
    }

    private static void runAsPersistentWorker(StatelessCliProgram program) throws Exception {
        // Persistent Bazel workers communicate with Bazel via standard input/output,
        // so we must redirect those streams while running jobs.
        InputStream realStdIn = System.in;
        PrintStream realStdOut = System.out;
        PrintStream realStdErr = System.err; // realStdErr feeds into the persistent worker log.

        try (ByteArrayOutputStream fakeStdOutBuffer = new ByteArrayOutputStream();
                PrintStream fakeStdOut = new PrintStream(fakeStdOutBuffer)) {

            System.setIn(new ByteArrayInputStream(new byte[0]));
            System.setOut(fakeStdOut);
            System.setErr(fakeStdOut);

            while (true) {
                WorkRequest request = WorkRequest.parseDelimitedFrom(realStdIn);
                if (request == null) {
                    break;
                }
                List<String> args = request.getArgumentsList();
                int exitCode;
                boolean destroyWorker = false;
                try {
                    exitCode = program.run(args);
                } catch (Throwable e) {
                    // The program could be in a bad state. Shut down the worker.
                    e.printStackTrace(fakeStdOut); // For the WorkResponse.
                    e.printStackTrace(realStdErr); // For the local worker log.
                    exitCode = 1;
                    destroyWorker = true;
                }

                WorkResponse.newBuilder()
                        .setOutput(fakeStdOutBuffer.toString())
                        .setExitCode(exitCode)
                        .build()
                        .writeDelimitedTo(realStdOut);
                realStdOut.flush();
                fakeStdOutBuffer.reset();

                if (destroyWorker) {
                    System.exit(1);
                }
            }
        } finally {
            System.setIn(realStdIn);
            System.setOut(realStdOut);
            System.setErr(realStdErr);
        }
    }
}
