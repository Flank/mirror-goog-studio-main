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

    private static final String HANDSHAKE_STRING = "JDWP-Handshake";

    public JdwpCommandHandler() {
        super("jdwp");
    }

    @Override
    public void invoke(
            @NonNull FakeAdbServer server,
            @NonNull Socket socket,
            @NonNull DeviceState device,
            @NonNull String args) {
        OutputStream oStream;
        InputStream iStream;
        try {
            oStream = socket.getOutputStream();
            iStream = socket.getInputStream();
        } catch (IOException ignored) {
            return;
        }

        int pid;
        try {
            pid = Integer.parseInt(args);
        } catch (NumberFormatException ignored) {
            writeFailResponse(oStream, "Invalid pid specified: " + args);
            return;
        }

        ClientState client = device.getClient(pid);
        if (client == null) {
            writeFailResponse(oStream, "No client exists for pid: " + pid);
            return;
        }

        try {
            writeOkay(oStream);
        } catch (IOException ignored) {
            return;
        }

        byte[] handshake = new byte[14];
        try {
            int readCount = iStream.read(handshake);
            if (handshake.length != readCount) {
                writeFailResponse(oStream, "Could not read full handshake.");
                return;
            }
        } catch (IOException ignored) {
            writeFailResponse(oStream, "Could not read handshake.");
            return;
        }

        if (!HANDSHAKE_STRING.equals(new String(handshake, US_ASCII))) {
            return;
        }

        try {
            writeString(oStream, HANDSHAKE_STRING);
        } catch (IOException ignored) {
            return;
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
                writeFailResponse(oStream, "Could not read packet.");
                return;
            }
        }
    }
}
