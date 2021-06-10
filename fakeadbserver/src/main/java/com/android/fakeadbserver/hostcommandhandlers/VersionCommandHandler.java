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
import com.android.fakeadbserver.CommandHandler;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;
import java.io.IOException;
import java.net.Socket;

/** host:version returns the internal ADB server version. */
public class VersionCommandHandler extends HostCommandHandler {

    @NonNull public static final String COMMAND = "version";

    public static final int ADB_INTERNAL_VERSION = 40;

    @Override
    public boolean invoke(
            @NonNull FakeAdbServer fakeAdbServer,
            @NonNull Socket responseSocket,
            @Nullable DeviceState device,
            @NonNull String args) {
        try {
            String version = getVersionString();
            CommandHandler.writeOkayResponse(responseSocket.getOutputStream(), version);
        } catch (IOException ignored) {
        }

        return false;
    }

    protected String getVersionString() {
        return String.format("%04X", ADB_INTERNAL_VERSION);
    }
}
