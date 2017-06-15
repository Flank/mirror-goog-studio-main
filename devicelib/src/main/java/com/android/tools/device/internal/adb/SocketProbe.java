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
import com.android.annotations.Nullable;
import com.android.tools.device.internal.adb.commands.ServerVersion;
import com.google.common.base.Charsets;
import com.google.common.primitives.Ints;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

class SocketProbe implements Probe {
    @Nullable
    @Override
    public Endpoint probe(@NonNull InetSocketAddress addr, long timeout, @NonNull TimeUnit unit) {
        long timeoutMs = TimeUnit.MILLISECONDS.convert(timeout, unit);
        return isAdbServerRunning(addr, timeoutMs) ? new SocketEndpoint(addr) : null;
    }

    private static boolean isAdbServerRunning(@NonNull SocketAddress addr, long connectTimeoutMs) {
        try (Socket s = new Socket()) {
            // Note that Windows and Linux behave differently when there is nothing running at
            // the given address: Linux seems to fail immediately, whereas Windows is likely to
            // timeout trying to establish a connection.
            s.connect(addr, Ints.checkedCast(connectTimeoutMs));

            try (OutputStream out = new BufferedOutputStream(s.getOutputStream());
                    BufferedReader r =
                            new BufferedReader(
                                    new InputStreamReader(s.getInputStream(), Charsets.UTF_8))) {
                byte[] cmd = new ServerVersion().getQuery().getBytes(Charsets.UTF_8);
                out.write(String.format(Locale.US, "%04X", cmd.length).getBytes(Charsets.UTF_8));
                out.write(cmd);
                out.flush();
                String line = r.readLine();
                return line != null && line.startsWith("OKAY");
            }
        } catch (Exception e) {
            return false;
        }
    }
}
