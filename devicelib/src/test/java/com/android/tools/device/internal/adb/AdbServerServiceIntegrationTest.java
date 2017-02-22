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

import com.android.tools.device.internal.OsProcessRunner;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.Test;

public class AdbServerServiceIntegrationTest {
    @Test
    public void launchServer() throws IOException {
        ExecutorService executor = Executors.newCachedThreadPool();

        AdbServerOptions options = new AdbServerOptions(getFreePort(), AdbConstants.DEFAULT_HOST);
        Launcher launcher =
                new AdbServerLauncher(AdbTestUtils.getPathToAdb(), new OsProcessRunner(executor));
        AdbServerService service =
                new AdbServerService(options, launcher, new SocketProbe(), executor);

        service.startAsync().awaitRunning();
        service.stopAsync().awaitTerminated();

        executor.shutdownNow();
    }

    private static int getFreePort() throws IOException {
        // TODO https://code.google.com/p/android/issues/detail?id=221925#c5
        // TODO Two instances of this test may be run in parallel, and we need them to not interact
        // with each other's server instances. Picking a port this way is kludgy because there is
        // no guarantee that the port is still free when it is actually used. Eventually, we should
        // move to the correct fix which is to have the adb server automatically pick up a free
        // port when it starts up.
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }
}
