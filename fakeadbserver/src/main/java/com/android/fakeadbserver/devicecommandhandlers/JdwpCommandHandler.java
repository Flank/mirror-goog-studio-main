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

package com.android.fakeadbserver.devicecommandhandlers;

import static com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.JdwpDdmsPacket.readFrom;
import static java.nio.charset.StandardCharsets.US_ASCII;

import com.android.annotations.NonNull;
import com.android.fakeadbserver.ClientState;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.ExitHandler;
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.HeloHandler;
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.JdwpDdmsPacket;
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.JdwpDdmsPacketHandler;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Map;

/**
 * jdwp:pid changes the connection to communicate with the pid's (required: Client) JDWP interface.
 */
public class JdwpCommandHandler extends DeviceCommandHandler {

    public static final String COMMAND = "jdwp";

    private static final String HANDSHAKE_STRING = "JDWP-Handshake";

    @Override
    public boolean invoke(
            @NonNull FakeAdbServer fakeAdbServer,
            @NonNull Socket responseSocket,
            @NonNull DeviceState device,
            @NonNull String args) {
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

        try {
            writeOkay(oStream);
        } catch (IOException ignored) {
            return false;
        }

        byte[] handshake = new byte[14];
        try {
            int readCount = iStream.read(handshake);
            if (handshake.length != readCount) {
                return writeFailResponse(oStream, "Could not read full handshake.");
            }
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

        Map<Integer, JdwpDdmsPacketHandler> packetHandlers =
                ImmutableMap.of(
                        HeloHandler.CHUNK_TYPE, new HeloHandler(),
                        ExitHandler.CHUNK_TYPE, new ExitHandler());

        // default - ignore the packet and keep listening
        JdwpDdmsPacketHandler defaultHandler = (unused, unused2, unused3) -> true;

        boolean running = true;

        while (running) {
            try {
                JdwpDdmsPacket packet = readFrom(iStream);
                running =
                        packetHandlers
                                .getOrDefault(packet.getChunkType(), defaultHandler)
                                .handlePacket(packet, client, oStream);
            } catch (IOException e) {
                return writeFailResponse(oStream, "Could not read packet.");
            }
        }

        return false;
    }
}
