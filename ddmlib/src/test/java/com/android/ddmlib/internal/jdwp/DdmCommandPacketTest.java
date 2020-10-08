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

import static com.google.common.truth.Truth.assertThat;

import java.nio.ByteBuffer;
import org.junit.Test;

public class DdmCommandPacketTest {
    @Test
    public void negativeLengthWhenBufferTooSmall() {
        ByteBuffer buffer = ByteBuffer.allocate(1);
        DdmCommandPacket packet = new DdmCommandPacket(buffer);
        assertThat(packet.getLength()).isEqualTo(-1);
    }

    @Test
    public void negativeLengthWhenBufferSmallerThanLength() {
        ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.put("000A".getBytes());
        DdmCommandPacket packet = new DdmCommandPacket(buffer);
        assertThat(packet.getLength()).isEqualTo(-1);
    }

    @Test
    public void commandLengthIsEqualToLength() {
        ByteBuffer buffer = ByteBuffer.allocate(64);
        buffer.put("0004".getBytes());
        buffer.put("TEST".getBytes());
        buffer.put("SHOULD NOT BE IN COMMAND".getBytes());
        DdmCommandPacket packet = new DdmCommandPacket(buffer);
        assertThat(packet.getLength()).isEqualTo(4);
        assertThat(packet.getCommand()).isEqualTo("TEST");
    }

    @Test
    public void totalSizeIsSizeOfDataAndLength() {
        ByteBuffer buffer = ByteBuffer.allocate(64);
        buffer.put("0004".getBytes());
        buffer.put("TEST".getBytes());
        DdmCommandPacket packet = new DdmCommandPacket(buffer);
        assertThat(packet.getTotalSize()).isEqualTo(8);
    }

    @Test
    public void invalidLength() {
        ByteBuffer buffer = ByteBuffer.allocate(64);
        buffer.put("ZZZZ".getBytes());
        buffer.put("TEST".getBytes());
        DdmCommandPacket packet = new DdmCommandPacket(buffer);
        assertThat(packet.getLength()).isEqualTo(-1);
    }
}
