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

package com.android.ddmlib.adbserver.devicecommandhandlers;

import static java.nio.charset.StandardCharsets.US_ASCII;

import com.android.annotations.NonNull;
import com.android.ddmlib.adbserver.ClientState;
import com.android.ddmlib.adbserver.DeviceState;
import com.android.ddmlib.adbserver.FakeAdbServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * jdwp:pid changes the connection to communicate with the pid's (required: Client) JDWP interface.
 */
public class JdwpCommandHandler extends DeviceCommandHandler {

    public static final String COMMAND = "jdwp";

    private static final String HANDSHAKE_STRING = "JDWP-Handshake";

    private static void readFully(@NonNull InputStream stream, @NonNull byte[] buffer)
            throws IOException {
        int bytesRead = 0;
        while (bytesRead < buffer.length) {
            bytesRead += stream.read(buffer, bytesRead, buffer.length - bytesRead);
        }
    }

    @Override
    public boolean invoke(@NonNull FakeAdbServer fakeAdbServer, @NonNull Socket responseSocket,
            @NonNull DeviceState device, @NonNull String args) {
        OutputStream oStream;
        InputStream iStream;
        try {
            oStream = responseSocket.getOutputStream();
            iStream = responseSocket.getInputStream();
        } catch (IOException ignored) {
            return false;
        }

        int pid;
        try {
            pid = Integer.parseInt(args);
        } catch (NumberFormatException ignored) {
            return writeFailResponse(oStream, "Invalid pid specified: " + args);
        }

        ClientState client = device.getClient(pid);
        if (client == null) {
            return writeFailResponse(oStream, "No client exists for pid: " + pid);
        }

        byte[] handshake = new byte[14];
        try {
            readFully(iStream, handshake);
        } catch (IOException ignored) {
            return writeFailResponse(oStream, "Could not read handshake.");
        }

        if (!HANDSHAKE_STRING.equals(new String(handshake, US_ASCII))) {
            return false;
        }

        try {
            writeString(oStream, HANDSHAKE_STRING);
        } catch (IOException ignored) {
            return false;
        }

        // TODO WIP -- spawn JDWP fake and hand off

        return false;
    }
}
