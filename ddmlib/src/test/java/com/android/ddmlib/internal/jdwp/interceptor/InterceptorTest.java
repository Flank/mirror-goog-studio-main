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

import com.android.ddmlib.internal.jdwp.chunkhandler.ChunkHandler;
import com.android.ddmlib.internal.jdwp.chunkhandler.JdwpPacket;
import java.nio.ByteBuffer;

public class InterceptorTest {

  public static JdwpPacket makePacket(int tag) {
    ByteBuffer data = ByteBuffer.allocate(128);
    JdwpPacket packet = new JdwpPacket(data);
    ChunkHandler.getChunkDataBuf(data);
    data.putInt(1234); // Some version
    ChunkHandler.finishChunkPacket(packet, tag, data.position());
    return packet;
  }

  public static JdwpPacket makePacket(String tag) {
    return makePacket(ChunkHandler.type(tag));
  }
}
