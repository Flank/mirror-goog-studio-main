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
import com.google.common.annotations.VisibleForTesting;
import java.util.concurrent.TimeUnit;

class AdbServerOptions {
    private static final long START_TIMEOUT_MS = 10 * 1000;

    // Note: Windows will try to connect to a non existent server for as long as requested,
    // so it is better to be conservative here, otherwise there will be unnecessary delay included
    // in every launch if the server isn't running.
    private static final long PROBE_TIMEOUT_MS = 250;

    private final int port;
    private final String hostName;
    private final boolean libUsb;

    private final long probeTimeoutMs;
    private final long startTimeoutMs;

    public AdbServerOptions(int port, @Nullable String hostName) {
        this(port, hostName, false);
    }

    public AdbServerOptions(int port, @Nullable String hostName, boolean useLibUsbBackend) {
        this(port, hostName, useLibUsbBackend, PROBE_TIMEOUT_MS);
    }

    @VisibleForTesting
    AdbServerOptions(int port, @Nullable String hostName, boolean useLibUsb, long probeTimeoutMs) {
        this.port = port;
        this.hostName = hostName;
        this.libUsb = useLibUsb;
        this.probeTimeoutMs = probeTimeoutMs;
        this.startTimeoutMs = START_TIMEOUT_MS;
    }

    @Nullable
    public String getHostName() {
        return hostName;
    }

    public int getPort() {
        return port;
    }

    public boolean shouldUseLibUsb() {
        return libUsb;
    }

    public long getProbeTimeout(@NonNull TimeUnit unit) {
        return unit.convert(probeTimeoutMs, TimeUnit.MILLISECONDS);
    }

    public long getStartTimeout(@NonNull TimeUnit unit) {
        return unit.convert(startTimeoutMs, TimeUnit.MILLISECONDS);
    }
}
