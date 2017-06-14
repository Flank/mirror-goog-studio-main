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
import com.google.common.primitives.UnsignedInteger;
import java.io.IOException;

public class ServerVersion implements AdbCommand<UnsignedInteger> {
    private static final String COMMAND = "version";

    @NonNull
    @Override
    public String getName() {
        return COMMAND;
    }

    @NonNull
    @Override
    public String getQuery() {
        return HOST_COMMAND_PREFIX + COMMAND;
    }

    @NonNull
    @Override
    public UnsignedInteger execute(@NonNull Connection conn) throws IOException {
        CommandResult result = conn.executeCommand(this);

        if (result.isOk()) {
            UnsignedInteger len = conn.readUnsignedHexInt();
            assert len.equals(UnsignedInteger.valueOf(4)) : "Got length = " + len;
            return conn.readUnsignedHexInt();
        } else {
            String error = result.getError();
            if (error == null) {
                error = "Error obtaining version";
            }
            throw new IOException(error);
        }
    }
}
