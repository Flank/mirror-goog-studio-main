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

/**
 * Commands that can be sent to the adb server.
 *
 * <p>The list of commands and the protocol are described in adb's sources at
 * system/core/adb/OVERVIEW.TXT.
 */
class AdbCommands {
    public static final String GET_SERVER_VERSION = "host:server";

    @NonNull
    public static byte[] formatCommand(@NonNull String cmd) {
        String request = String.format("%04X%s", cmd.length(), cmd);
        return request.getBytes(Charsets.UTF_8);
    }
}
