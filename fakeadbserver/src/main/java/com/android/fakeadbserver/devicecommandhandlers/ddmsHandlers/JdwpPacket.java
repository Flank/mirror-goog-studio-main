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

// Handle a JDWP packet.
public class JdwpPacket {

    private static final int JDWP_HEADER_LENGTH = 11;

    private static final byte IS_RESPONSE_FLAG = (byte)0x80;

    private final int myId;

    private final boolean myIsResponse;

    private final short myErrorCode;

    private final byte[] myPayload;

    private final int mCmdSet;

    private final int mCmd;

    protected JdwpPacket(
            int id, boolean isResponse, short errorCode, byte[] payload, int cmdSet, int cmd) {
        myId = id;
        myIsResponse = isResponse;
        myErrorCode = errorCode;
        myPayload = payload;
        mCmdSet = cmdSet;
        mCmd = cmd;
    }

    public byte[] getPayload() {
        return myPayload;
    }

    public int getCmdSet() {
        return mCmdSet;
    }

    public int getCmd() {
        return mCmd;
    }

    public boolean isResponse() {
        return myIsResponse;
    }

    public short getErrorCode() {
        return myErrorCode;
    }

    public int getId() {
        return myId;
    }

    public void write(OutputStream oStream) throws IOException {
        byte[] response = new byte[JDWP_HEADER_LENGTH + myPayload.length];
        ByteBuffer responseBuffer = ByteBuffer.wrap(response);
        responseBuffer.putInt(response.length);
        responseBuffer.putInt(myId);
        responseBuffer.put(myIsResponse ? IS_RESPONSE_FLAG : 0);
        if (myIsResponse) {
            responseBuffer.putShort(myErrorCode);
        } else {
            responseBuffer.put((byte) mCmdSet);
            responseBuffer.put((byte) mCmd);
        }
        responseBuffer.put(myPayload);

        oStream.write(response);
    }

    // Reads a packet from a stream
    public static JdwpPacket readFrom(InputStream iStream) throws IOException {
        byte[] packetHeader = new byte[JDWP_HEADER_LENGTH];
        ByteStreams.readFully(iStream, packetHeader);

        ByteBuffer headerBuffer = ByteBuffer.wrap(packetHeader);
        int length = headerBuffer.getInt();
        int id = headerBuffer.getInt();
        int flags = headerBuffer.get() & 0xff;
        int commandSet = headerBuffer.get() & 0xff;
        int command = headerBuffer.get() & 0xff;
        int readCount;

        int payloadLength = length - JDWP_HEADER_LENGTH;
        byte[] payload = new byte[payloadLength];
        if (payloadLength > 0) {
            readCount = iStream.read(payload);
            assert payload.length == readCount;
        }

        assert length >= JDWP_HEADER_LENGTH;
        assert (flags & ~IS_RESPONSE_FLAG) == 0;

        return new JdwpPacket(id, isResponse(flags), (short) 0, payload, commandSet, command);
    }

    // Create a response packet
    public static JdwpPacket createResponse(int id, byte[] payload, int cmdSet, int cmd) {
        return new JdwpPacket(id, true, (short) 0, payload, cmdSet, cmd);
    }

    // create a non-response packet
    public static JdwpPacket create(byte[] payload, int cmdSet, int cmd) {
        return new JdwpPacket(1234, false, (short) 0, payload, cmdSet, cmd);
    }

    private static boolean isResponse(int flags) {
        return (flags & IS_RESPONSE_FLAG) != 0;
    }
}
