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
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link ListDevices} is equivalent to "adb devices -l" command. It issues the "devices -l"
 * command to the adb server and returns the data returned parsed into {@link DeviceHandle}s.
 */
public class ListDevices implements AdbCommand<List<DeviceHandle>> {
    @NonNull
    @Override
    public String getName() {
        return HostService.DEVICES.toString();
    }

    @NonNull
    @Override
    public List<DeviceHandle> execute(@NonNull Connection conn) throws IOException {
        CommandBuffer buffer = new CommandBuffer().writeHostCommand(HostService.DEVICES);
        CommandResult result = conn.executeCommand(buffer);

        if (!result.isOk()) {
            String msg = "Error retrieving device list";
            String error = result.getError();
            if (error != null) {
                msg += ": " + error;
            }
            throw new IOException(msg);
        }

        int len = conn.readUnsignedHexInt().intValue();
        if (len == 0) {
            return ImmutableList.of();
        }

        String response = conn.readString(len);

        return Arrays.stream(response.split("\n"))
                .map(DeviceHandle::create)
                .collect(Collectors.toList());
    }
}
