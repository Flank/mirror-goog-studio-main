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

import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

// Handle a JDWP packet wrapping a DDMS packet
public class JdwpDdmsPacket {

    private static final int JDWP_DDMS_HEADER_LENGTH = 19; // 11 for jdwp header + 8 for ddms header
    private static final byte IS_RESPONSE_FLAG = (byte) 0x80;
    public static final byte DDMS_CMD_SET = (byte) 0xc7;
    public static final byte DDMS_CMD = (byte) 0x01;

    private final int myId;
    private final boolean myIsResponse;
    private final short myErrorCode;
    private final int myChunkType;
    private final byte[] myPayload;

    // Reads a packet from a stream
    public static JdwpDdmsPacket readFrom(InputStream iStream) throws IOException {
        byte[] packetHeader = new byte[JDWP_DDMS_HEADER_LENGTH];
        ByteStreams.readFully(iStream, packetHeader);

        ByteBuffer headerBuffer = ByteBuffer.wrap(packetHeader);
        int length = headerBuffer.getInt();
        int id = headerBuffer.getInt();
        byte flags = headerBuffer.get();
        byte commandSet = headerBuffer.get();
        byte command = headerBuffer.get();
        int chunkType = headerBuffer.getInt();
        int chunkLength = headerBuffer.getInt();
        int readCount;

        assert length >= JDWP_DDMS_HEADER_LENGTH;
        assert commandSet == DDMS_CMD_SET;
        assert command == DDMS_CMD;
        assert (flags & ~IS_RESPONSE_FLAG) == 0;
        assert chunkLength == length - JDWP_DDMS_HEADER_LENGTH;

        byte[] payload = new byte[chunkLength];
        if (chunkLength > 0) {
            readCount = iStream.read(payload);
            assert payload.length == readCount;
        }

        return new JdwpDdmsPacket(id, isResponse(flags), (short) 0, chunkType, payload);
    }

    // Create a response packet
    public static JdwpDdmsPacket createResponse(int id, int chunkType, byte[] payload) {
        return new JdwpDdmsPacket(id, true, (short) 0, chunkType, payload);
    }

    // create a non-response packet
    public static JdwpDdmsPacket create(int chunkType, byte[] payload) {
        return new JdwpDdmsPacket(1234, false, (short) 0, chunkType, payload);
    }

    private JdwpDdmsPacket(
            int id, boolean isResponse, short errorCode, int chunkType, byte[] payload) {
        myId = id;
        myIsResponse = isResponse;
        myErrorCode = errorCode;
        myChunkType = chunkType;
        myPayload = payload;
    }

    public int getChunkType() {
        return myChunkType;
    }

    public int getId() {
        return myId;
    }

    public void write(OutputStream oStream) throws IOException {
        byte[] response = new byte[JDWP_DDMS_HEADER_LENGTH + myPayload.length];
        ByteBuffer responseBuffer = ByteBuffer.wrap(response);
        responseBuffer.putInt(response.length);
        responseBuffer.putInt(myId);
        responseBuffer.put(myIsResponse ? IS_RESPONSE_FLAG : 0);
        if (myIsResponse) {
            responseBuffer.putShort(myErrorCode);
        } else {
            responseBuffer.put(DDMS_CMD_SET);
            responseBuffer.put(DDMS_CMD);
        }
        responseBuffer.putInt(myChunkType);
        responseBuffer.putInt(myPayload.length);
        responseBuffer.put(myPayload);

        oStream.write(response);
    }

    private static boolean isResponse(byte flags) {
        return (flags & IS_RESPONSE_FLAG) != 0;
    }

    protected static int encodeChunkType(String typeName) {
        assert typeName.length() == 4;

        int val = 0;
        for (int i = 0; i < 4; i++) {
            val <<= 8;
            val |= (byte) typeName.charAt(i);
        }

        return val;
    }
}
