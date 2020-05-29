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

package com.android.ddmlib.internal.jdwp.interceptor;

import static com.android.ddmlib.internal.jdwp.chunkhandler.ChunkHandler.allocBuffer;

import com.android.ddmlib.internal.jdwp.chunkhandler.ChunkHandler;
import com.android.ddmlib.internal.jdwp.chunkhandler.JdwpPacket;
import java.nio.ByteBuffer;

public class InterceptorTest {
    private static final byte REPLY_PACKET = (byte) 0x80;
    private static final int REPLY_PACKET_OFFSET = 0x08;
    private static final int CMD_OFFSET = 0x09;
    private static final int CMD_SET_OFFSET = 0x0A;

    public static JdwpPacket makePacket(int tag, boolean replyPacket, int payload) {
        ByteBuffer data = allocBuffer(4);
    JdwpPacket packet = new JdwpPacket(data);
        ByteBuffer payloadBuffer = ChunkHandler.getChunkDataBuf(data);
        payloadBuffer.putInt(payload); // payload
        ChunkHandler.finishChunkPacket(packet, tag, payloadBuffer.position());
        if (replyPacket) {
            data.put(REPLY_PACKET_OFFSET, REPLY_PACKET);
            data.put(CMD_OFFSET, (byte) 0);
            data.put(CMD_SET_OFFSET, (byte) 0);
            packet = JdwpPacket.findPacket(data);
        }
    return packet;
  }

    public static JdwpPacket makePacket(int tag) {
        return makePacket(tag, false, 1);
    }

  public static JdwpPacket makePacket(String tag) {
    return makePacket(ChunkHandler.type(tag));
  }
}
