/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.ddmlib.adbserver.hostcommandhandlers;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.adbserver.DeviceState;
import com.android.ddmlib.adbserver.FakeAdbServer;

import java.io.IOException;
import java.net.Socket;

/**
 * host:kill terminates the server when the command is received.
 */
public class KillCommandHandler extends HostCommandHandler {

    @NonNull
    public static final String COMMAND = "kill";

    @Override
    public boolean invoke(@NonNull FakeAdbServer fakeAdbServer, @NonNull Socket responseSocket,
            @Nullable DeviceState device, @NonNull String args) {
        fakeAdbServer.stop();
        try {
            writeOkay(responseSocket.getOutputStream()); // Send ok.
        } catch (IOException ignored) {
        }

        return false;
    }
}
