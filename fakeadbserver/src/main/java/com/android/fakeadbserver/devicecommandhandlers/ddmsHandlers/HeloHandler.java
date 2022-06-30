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

import com.android.annotations.NonNull;
import com.android.fakeadbserver.ClientState;
import com.android.fakeadbserver.DeviceState;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class HeloHandler implements DDMPacketHandler {

    public static final int CHUNK_TYPE = DdmPacket.encodeChunkType("HELO");

    private static final String VM_IDENTIFIER = "FakeVM";

    private static final String JVM_FLAGS = "-jvmflag=true";

    private static final int HELO_CHUNK_HEADER_LENGTH = 16;

    private static final int VERSION = 9999;

    @Override
    public boolean handlePacket(
            @NonNull DeviceState device,
            @NonNull ClientState client,
            @NonNull DdmPacket packet,
            @NonNull OutputStream oStream) {
        // ADB has an issue of reporting the process name instead of the real not reporting the real package name.
        String appName = client.getProcessName();

        int deviceApiLevel = device.getApiLevel();

        // UserID starts at API 18
        boolean writeUserId = deviceApiLevel >= 18;

        // ABI starts at API 21
        boolean writeAbi = deviceApiLevel >= 21;
        String abi = device.getCpuAbi();

        // JvmFlags starts at API 21
        boolean writeJvmFlags = deviceApiLevel >= 21;
        String jvmFlags = JVM_FLAGS;

        // native debuggable starts at API 24
        boolean writeNativeDebuggable = deviceApiLevel >= 24;

        // package name starts at API 30
        boolean writePackageName = deviceApiLevel >= 30;
        String packageName = client.getPackageName();

        int payloadLength =
                HELO_CHUNK_HEADER_LENGTH
                        + ((VM_IDENTIFIER.length() + appName.length()) * 2)
                        + (writeUserId ? 4 : 0)
                        + (writeAbi ? 4 + abi.length() * 2 : 0)
                        + (writeJvmFlags ? 4 + jvmFlags.length() * 2 : 0)
                        + (writeNativeDebuggable ? 1 : 0)
                        + (writePackageName ? 4 + packageName.length() * 2 : 0);
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
        if (writeUserId) {
            payloadBuffer.putInt(client.getUid());
        }
        if (writeAbi) {
            payloadBuffer.putInt(abi.length());
            for (char c : abi.toCharArray()) {
                payloadBuffer.putChar(c);
            }
        }
        if (writeJvmFlags) {
            payloadBuffer.putInt(jvmFlags.length());
            for (char c : jvmFlags.toCharArray()) {
                payloadBuffer.putChar(c);
            }
        }
        if (writeNativeDebuggable) {
            payloadBuffer.put((byte) 0);
        }
        if (writePackageName) {
            payloadBuffer.putInt(packageName.length());
            for (char c : packageName.toCharArray()) {
                payloadBuffer.putChar(c);
            }
        }

        DdmPacket responsePacket = DdmPacket.createResponse(packet.getId(), CHUNK_TYPE, payload);

        try {
            responsePacket.write(oStream);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        if (client.getIsWaiting()) {

            byte[] waitPayload = new byte[1];
            DdmPacket waitPacket = DdmPacket.create(DdmPacket.encodeChunkType("WAIT"), waitPayload);
            try {
                waitPacket.write(oStream);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }
}
