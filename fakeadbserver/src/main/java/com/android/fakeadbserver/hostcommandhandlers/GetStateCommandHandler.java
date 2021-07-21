/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.fakeadbserver.ClientState;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.fakeadbserver.devicecommandhandlers.DeviceCommandHandler;
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.ExitHandler;
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.HeloHandler;
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.JdwpDdmsPacket;
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.JdwpDdmsPacketHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import static com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.JdwpDdmsPacket.readFrom;
import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * host-prefix:get-state return the last known state of the device
 */
public class GetStateCommandHandler extends HostCommandHandler {

    @NonNull public static final String COMMAND = "get-state";

    @Override
    public boolean invoke(
            @NonNull FakeAdbServer fakeAdbServer,
            @NonNull Socket responseSocket,
            @Nullable DeviceState device,
            @NonNull String args) {
        assert device != null;
        try {
            writeOkayResponse(responseSocket.getOutputStream(), device.getDeviceStatus().getState());
        } catch (IOException ignored) {
        }
        return false;
    }
}
