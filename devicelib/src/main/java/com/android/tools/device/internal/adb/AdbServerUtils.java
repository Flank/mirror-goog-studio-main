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
import com.google.common.base.Charsets;
import com.google.common.primitives.Ints;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class AdbServerUtils {
    /**
     * Returns whether adb server is running at the given address, or times out if it can't be
     * determined within the given deadline. It does this by attempting to connect to the server at
     * that port and trying to obtain the version.
     *
     * <p>Note that Windows and Linux behave differently when there is nothing running at the given
     * address: On Linux, it immediately returns, whereas Windows is likely to timeout while trying
     * to establish a connection..
     */
    static boolean isAdbServerRunning(
            @NonNull SocketAddress address, long timeout, @NonNull TimeUnit unit)
            throws InterruptedException, TimeoutException {
        int timeoutMs = Ints.checkedCast(TimeUnit.MILLISECONDS.convert(timeout, unit));
        CompletableFuture<Boolean> future =
                CompletableFuture.supplyAsync(() -> isAdbServerRunning(address, timeoutMs));
        try {
            return future.get(timeout, unit);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    private static boolean isAdbServerRunning(@NonNull SocketAddress addr, int connectTimeoutMs) {
        try (Socket s = new Socket()) {
            s.connect(addr, connectTimeoutMs);

            try (OutputStream out = new BufferedOutputStream(s.getOutputStream());
                    BufferedReader r =
                            new BufferedReader(
                                    new InputStreamReader(s.getInputStream(), Charsets.UTF_8))) {
                out.write(AdbCommands.formatCommand(AdbCommands.GET_SERVER_VERSION));
                out.flush();
                String line = r.readLine();
                return line != null && line.startsWith("OKAY");
            }
        } catch (Exception e) {
            return false;
        }
    }
}
