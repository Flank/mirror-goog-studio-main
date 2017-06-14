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
import com.android.tools.device.internal.adb.DeviceHandle;

/**
 * A command to retrieve the list of features supported by the daemon.
 *
 * <p>Even though this command applies to a specific device, it is handled internally by the host
 * server itself. The server already knows the feature set supported by each transport, and it just
 * returns that. Older devices return an empty set.
 */
public class DaemonFeatures extends HostFeatures {
    private static final String COMMAND = "features";
    private final DeviceHandle handle;

    public DaemonFeatures(@NonNull DeviceHandle handle) {
        this.handle = handle;
    }

    @NonNull
    @Override
    public String getName() {
        return COMMAND;
    }

    @NonNull
    @Override
    public String getQuery() {
        return DEVICE_COMMAND_PREFIX + handle.getSerial() + ADB_COMMAND_DELIMITER + COMMAND;
    }
}
