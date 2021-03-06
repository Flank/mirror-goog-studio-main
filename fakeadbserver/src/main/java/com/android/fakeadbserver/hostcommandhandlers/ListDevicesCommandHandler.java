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

package com.android.fakeadbserver.hostcommandhandlers;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * host:devices is a one-shot command to list devices and their states that are presently connected
 * to the server.
 */
public class ListDevicesCommandHandler extends HostCommandHandler {

    @NonNull public static final String COMMAND = "devices";

    @NonNull public static final String LONG_COMMAND = "devices-l";

    private final boolean longFormat;

    public ListDevicesCommandHandler() {
        this(false);
    }

    public ListDevicesCommandHandler(boolean longFormat) {
        this.longFormat = longFormat;
    }

    @Override
    public boolean invoke(
            @NonNull FakeAdbServer fakeAdbServer,
            @NonNull Socket responseSocket,
            @Nullable DeviceState device,
            @NonNull String args) {
        OutputStream stream;
        try {
            stream = responseSocket.getOutputStream();
        }
        catch (IOException ignored) {
            return false;
        }
        try {
            String deviceListString = formatDeviceList(fakeAdbServer.getDeviceListCopy().get(),
                                                       longFormat);

            try {
                writeOkay(stream); // Send ok first.
                write4ByteHexIntString(stream, deviceListString.length());
                stream.write(deviceListString.getBytes(StandardCharsets.US_ASCII));
            }
            catch (IOException ignored) {
                return false;
            }
        }
        catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        catch (ExecutionException e) {
            writeFailResponse(stream, "Failed to retrieve the list of devices from the server.");
            return false;
        }

        return true;
    }

    @NonNull
    static String formatDeviceList(@NonNull List<DeviceState> deviceList, boolean longFormat) {
        StringBuilder builder = new StringBuilder();
        for (DeviceState deviceState : deviceList) {
            builder.append(deviceState.getDeviceId());
            builder.append("\t");
            builder.append(deviceState.getDeviceStatus().getState());
            if (longFormat) {
                if (deviceState.getDeviceStatus() == DeviceState.DeviceStatus.ONLINE) {
                    builder.append(" ");
                    builder.append("product:");
                    builder.append(deviceState.getManufacturer());

                    builder.append(" ");
                    builder.append("model:");
                    builder.append(deviceState.getModel());

                    builder.append(" ");
                    builder.append("device:");
                    builder.append(deviceState.getBuildVersionRelease());
                }
                builder.append(" ");
                builder.append("transport_id:");
                builder.append("1");
            }
            builder.append("\n");
        }

        // Remove trailing '\n' to match adb server behavior
        if (!deviceList.isEmpty()) {
            builder.deleteCharAt(builder.length() - 1);
        }

        return builder.toString();
    }
}
