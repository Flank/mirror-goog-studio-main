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

package com.android.tools.device.internal.adb.commands;

import com.android.annotations.NonNull;
import com.android.tools.device.internal.adb.Connection;
import com.android.tools.device.internal.adb.DeviceHandle;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Track devices issues "host:track-devices" command to the adb server, and invokes a callback
 * whenever there is output for this command. This command needs to be canceled via {@link #cancel}.
 */
public class TrackDevices implements AdbCommand<Void> {
    private final DeviceListChangeListener listener;
    private final ExecutorService executor;

    private volatile Connection connection;

    public TrackDevices(
            @NonNull DeviceListChangeListener listener, @NonNull ExecutorService executor) {
        this.listener = listener;
        this.executor = executor;
    }

    @NonNull
    @Override
    public String getName() {
        return HostService.TRACK_DEVICES.toString();
    }

    @NonNull
    @Override
    public Void execute(@NonNull Connection conn) throws IOException {
        connection = conn;

        CommandBuffer buffer = new CommandBuffer().writeHostCommand(HostService.TRACK_DEVICES);
        CommandResult result = conn.executeCommand(buffer);

        if (!result.isOk()) {
            String msg = "Error connecting to adb server to track devices";
            String error = result.getError();
            if (error != null) {
                msg += ": " + error;
            }

            Logger.getLogger(TrackDevices.class.getName()).severe(msg);
            throw new IOException(msg);
        }

        //noinspection InfiniteLoopStatement (quits when the conn is closed by calling #cancel)
        while (true) {
            String devices = conn.readString(conn.readUnsignedHexInt().intValue());
            List<DeviceHandle> deviceHandles =
                    Arrays.stream(devices.split("\n"))
                            .filter(s -> !s.isEmpty())
                            .map(DeviceHandle::create)
                            .collect(Collectors.toList());
            executor.submit(
                    () -> {
                        listener.deviceListChanged(deviceHandles);
                    });
        }
    }

    public void cancel() {
        try {
            connection.close();
        } catch (IOException ignored) {
        }
    }

    public interface DeviceListChangeListener {
        void deviceListChanged(@NonNull List<DeviceHandle> handles);
    }
}
