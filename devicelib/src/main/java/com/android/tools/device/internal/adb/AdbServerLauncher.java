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
package com.android.tools.device.internal.adb;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.tools.device.internal.ProcessRunner;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

class AdbServerLauncher implements Launcher {
    private final Path adb;
    private final ProcessRunner runner;
    private final Map<String, String> env;

    public AdbServerLauncher(@NonNull Path adb, @NonNull ProcessRunner runner) {
        this(adb, runner, ImmutableMap.of());
    }

    @VisibleForTesting
    public AdbServerLauncher(
            @NonNull Path adb, @NonNull ProcessRunner runner, @NonNull Map<String, String> env) {
        this.adb = adb;
        this.runner = runner;
        this.env = env;
    }

    @NonNull
    @Override
    public Endpoint launch(int port, boolean useLibUsb, long timeout, @NonNull TimeUnit unit)
            throws IOException, InterruptedException, TimeoutException {
        if (port == AdbConstants.ANY_PORT) {
            // TODO(b/35644544): Have adb pick a free port
            port = AdbConstants.DEFAULT_PORT;
        }

        List<String> cmd =
                Arrays.asList(adb.toString(), "-P", Integer.toString(port), "start-server");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("ADB_LIBUSB", useLibUsb ? "1" : "0");
        env.forEach((key, value) -> pb.environment().put(key, value));

        Logger logger = Logger.getLogger(getClass().getName());
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Launching adb: " + Joiner.on(" ").join(cmd));
            logger.fine("  ADB_LIBUSB=" + pb.environment().get("ADB_LIBUSB"));
            env.forEach((key, value) -> logger.fine(String.format("  %1$s=%2$s", key, value)));
        }

        Process process = runner.start(pb);
        if (!runner.waitFor(timeout, unit)) {
            runner.destroyForcibly();
            String msg =
                    String.format(
                            Locale.US,
                            "Timed out (%1$d seconds) starting adb server [%2$s]: %3$s",
                            TimeUnit.SECONDS.convert(timeout, unit),
                            Joiner.on(' ').join(cmd),
                            runner.getStdout() + runner.getStderr());
            throw new TimeoutException(msg);
        }

        if (process.exitValue() != 0) {
            String msg =
                    String.format(
                            Locale.US,
                            "Error starting adb server [%1$s]: %2$s",
                            Joiner.on(' ').join(cmd),
                            runner.getStdout() + runner.getStderr());
            throw new IOException(msg);
        }

        return new SocketEndpoint(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
    }
}
