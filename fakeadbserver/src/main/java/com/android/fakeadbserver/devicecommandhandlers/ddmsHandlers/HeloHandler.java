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
package com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers;

import static com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.JdwpDdmsPacket.encodeChunkType;

import com.android.annotations.NonNull;
import com.android.fakeadbserver.ClientState;
import com.android.fakeadbserver.CommandHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class HeloHandler extends CommandHandler implements JdwpDdmsPacketHandler {

    public static final int CHUNK_TYPE = encodeChunkType("HELO");

    private static final String VM_IDENTIFIER = "FakeVM";
    private static final int HELO_CHUNK_HEADER_LENGTH = 16;
    private static final int VERSION = 9999;

    @Override
    public boolean handlePacket(
            @NonNull JdwpDdmsPacket packet,
            @NonNull ClientState client,
            @NonNull OutputStream oStream) {
        // ADB has an issue of reporting the process name instead of the real not reporting the real package name.
        String appName = client.getProcessName();

        int payloadLength =
                HELO_CHUNK_HEADER_LENGTH + ((VM_IDENTIFIER.length() + appName.length()) * 2);
        byte[] payload = new byte[payloadLength];
        ByteBuffer payloadBuffer = ByteBuffer.wrap(payload);
        payloadBuffer.putInt(VERSION);
        payloadBuffer.putInt(client.getPid());
        payloadBuffer.putInt(VM_IDENTIFIER.length());
        payloadBuffer.putInt(appName.length());
        for (char c : VM_IDENTIFIER.toCharArray()) {
            payloadBuffer.putChar(c);
        }
        for (char c : appName.toCharArray()) {
            payloadBuffer.putChar(c);
        }

        JdwpDdmsPacket responsePacket =
                JdwpDdmsPacket.createResponse(packet.getId(), CHUNK_TYPE, payload);

        try {
            responsePacket.write(oStream);
        } catch (IOException e) {
            writeFailResponse(oStream, "Could not write HELO response packet");
            return false;
        }

        if (client.getIsWaiting()) {

            byte[] waitPayload = new byte[1];
            JdwpDdmsPacket waitPacket = JdwpDdmsPacket.create(encodeChunkType("WAIT"), waitPayload);
            try {
                waitPacket.write(oStream);
            } catch (IOException e) {
                writeFailResponse(oStream, "Could not write WAIT packet");
                return false;
            }
        }
        return true;
    }
}
