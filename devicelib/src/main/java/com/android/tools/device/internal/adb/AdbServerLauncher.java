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
import com.android.tools.device.internal.ProcessRunner;
import com.google.common.base.Joiner;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class AdbServerLauncher implements Launcher {
    private final Path adb;
    private final ProcessRunner runner;

    public AdbServerLauncher(@NonNull Path adb, @NonNull ProcessRunner runner) {
        this.adb = adb;
        this.runner = runner;
    }

    @NonNull
    @Override
    public Endpoint launch(int port, long timeout, @NonNull TimeUnit unit)
            throws IOException, InterruptedException, TimeoutException {
        if (port == AdbConstants.ANY_PORT) {
            // TODO(b/35644544): Have adb pick a free port
            port = AdbConstants.DEFAULT_PORT;
        }

        List<String> cmd =
                Arrays.asList(adb.toString(), "-P", Integer.toString(port), "start-server");
        Process process = runner.start(new ProcessBuilder(cmd));
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
