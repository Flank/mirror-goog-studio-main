/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.ddmlib.internal.jdwp;

import static com.android.ddmlib.internal.jdwp.chunkhandler.ChunkHandler.CHUNK_HEADER_LEN;
import static com.android.ddmlib.internal.jdwp.chunkhandler.ChunkHandler.CHUNK_ORDER;
import static com.android.ddmlib.internal.jdwp.chunkhandler.ChunkHandler.DDMS_CMD;
import static com.android.ddmlib.internal.jdwp.chunkhandler.ChunkHandler.DDMS_CMD_SET;

import com.android.ddmlib.internal.jdwp.chunkhandler.ChunkHandler;
import com.android.ddmlib.internal.jdwp.chunkhandler.JdwpPacket;
import java.nio.ByteBuffer;
import java.util.Random;

public class JdwpTest {

    private static final byte REPLY_PACKET = (byte) 0x80;

    private static final int REPLY_PACKET_OFFSET = 0x08;

    private static final int CMD_OFFSET = 0x09;

    private static final int CMD_SET_OFFSET = 0x0A;

    public static final int CHUNK_TEST = ChunkHandler.type("TEST");

    public static ByteBuffer createPacketBuffer(int tag, int dataLength) {
        return createPacketBuffer(tag, dataLength, true);
    }

    public static ByteBuffer createPacketBuffer(int tag, int dataLength, boolean replyFlag) {
        Random rnd = new Random(1234);
        ByteBuffer rawBuf =
                ByteBuffer.allocate(JdwpPacket.JDWP_HEADER_LEN + CHUNK_HEADER_LEN + dataLength);
        rawBuf.order(CHUNK_ORDER);
        JdwpPacket packet = new JdwpPacket(rawBuf);
        ByteBuffer buf = ChunkHandler.getChunkDataBuf(rawBuf);
        for (int i = 0; i < dataLength; i++) {
            buf.put((byte) rnd.nextInt());
        }
        int chunkLen = buf.position();
        ByteBuffer payload = packet.getPayload();
        payload.putInt(0x00, tag);
        payload.putInt(0x04, chunkLen);
        packet.finishPacket(DDMS_CMD_SET, DDMS_CMD, CHUNK_HEADER_LEN + chunkLen);
        // Adding the reply flag allows test to skip the noreply packet interceptor.
        if (replyFlag) {
            rawBuf.put(REPLY_PACKET_OFFSET, REPLY_PACKET);
        }
        return rawBuf;
    }

    public static JdwpPacket makePacket(int tag, boolean replyPacket, int payload) {
        ByteBuffer rawBuf = ByteBuffer.allocate(JdwpPacket.JDWP_HEADER_LEN + CHUNK_HEADER_LEN + 4);
        JdwpPacket packet = new JdwpPacket(rawBuf);
        ByteBuffer payloadBuffer = ChunkHandler.getChunkDataBuf(rawBuf);
        payloadBuffer.putInt(payload);
        ChunkHandler.finishChunkPacket(packet, tag, payloadBuffer.position());
        if (replyPacket) {
            rawBuf.put(REPLY_PACKET_OFFSET, REPLY_PACKET);
        }
        rawBuf.put(CMD_OFFSET, (byte) 0);
        rawBuf.put(CMD_SET_OFFSET, (byte) 0);
        return JdwpPacket.findPacket(rawBuf);
    }

    public static JdwpPacket makePacket(int tag) {
        return makePacket(tag, false, 1);
    }

    public static JdwpPacket makePacket(String tag) {
        return makePacket(ChunkHandler.type(tag));
    }
}
