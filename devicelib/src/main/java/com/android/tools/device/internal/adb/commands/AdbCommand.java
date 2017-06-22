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
import java.io.IOException;

/**
 * {@link AdbCommand} represents any command that can be issued to an adb server. It is executed in
 * the context of a connection to a server. Simple commands may return a single result value when
 * execution is complete, while more complex/time consuming commands may specify their own callbacks
 * that will be invoked while the command is executing.
 */
public interface AdbCommand<T> {
    /** Prefix for all queries to the host adb server. */
    String HOST_COMMAND_PREFIX = "host:";

    /** Prefix for all queries to a particular device. */
    String DEVICE_COMMAND_PREFIX = "host-serial:";

    /** Delimiter used between various sections of a command to adb server. */
    String ADB_COMMAND_DELIMITER = ":";

    /** Returns a short name for this command. */
    @NonNull
    String getName();

    /** Returns the adb query string for this command. */
    @NonNull
    String getQuery();

    /** Executes this command using the given connection to the adb server. */
    @NonNull
    T execute(@NonNull Connection conn) throws IOException;
}
