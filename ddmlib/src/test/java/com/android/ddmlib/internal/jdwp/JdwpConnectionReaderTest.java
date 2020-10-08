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

import com.android.ddmlib.internal.jdwp.chunkhandler.JdwpPacket;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import org.junit.Test;

public class JdwpConnectionReaderTest {

    @Test
    public void multiplePacketsSpanningBufferSize() throws Exception {
        SocketChannel channel = SocketChannel.open();
        ByteBuffer[] packets = new ByteBuffer[5];
        int length = 0;
        for (int i = 0; i < packets.length; i++) {
            packets[i] = JdwpTest.createPacketBuffer(JdwpTest.CHUNK_TEST, packets.length - i);
            length += packets[i].position();
        }
        ByteBuffer finalSentData = ByteBuffer.allocate(length);
        for (ByteBuffer byteBuffer : packets) {
            finalSentData.put(byteBuffer.array());
        }
        SlowSimpleServer server = new SlowSimpleServer(finalSentData.array());
        channel.connect(
                new InetSocketAddress(InetAddress.getByName("localhost"), server.getPort()));
        JdwpConnectionReader mReader =
                new JdwpConnectionReader(channel, JdwpPacket.JDWP_HEADER_LEN + 1);
        Thread serverThread = new Thread(server);
        serverThread.start();
        for (ByteBuffer packet : packets) {
            JdwpPacket readPacket = null;
            while (mReader.read() != -1 && readPacket == null) {
                readPacket = mReader.readPacket();
            }
            ByteBuffer packetData = ByteBuffer.allocate(readPacket.getLength());
            readPacket.copy(packetData);
            assertThat(packet).isEqualTo(packetData);
        }
    }

    @Test
    public void oneByteAtATimePacket() throws Exception {
        SocketChannel channel = SocketChannel.open();
        ByteBuffer sentPacket = JdwpTest.createPacketBuffer(JdwpTest.CHUNK_TEST, 10);
        SlowSimpleServer server = new SlowSimpleServer(sentPacket.array());
        channel.connect(
                new InetSocketAddress(InetAddress.getByName("localhost"), server.getPort()));
        JdwpConnectionReader mReader =
                new JdwpConnectionReader(channel, JdwpPacket.JDWP_HEADER_LEN);
        Thread serverThread = new Thread(server);
        serverThread.start();
        JdwpPacket readPacket = null;
        while (mReader.read() != -1 && readPacket == null) {
            readPacket = mReader.readPacket();
        }
        ByteBuffer packetData = ByteBuffer.allocate(readPacket.getLength());
        readPacket.copy(packetData);
        assertThat(sentPacket).isEqualTo(packetData);
    }

    class SlowSimpleServer extends SimpleServer {
        public SlowSimpleServer(byte[] message) throws IOException {
            super(message);
        }

        @Override
        public void run() {
            try {
                SocketChannel client = getServerSocket().accept();
                for (int i = 0; i < mConnectMessage.length; i++) {
                    client.write(ByteBuffer.wrap(mConnectMessage, i, 1));
                    Thread.sleep(10);
                }
            } catch (Exception ex) {
                // End thread.
            }
        }
    }
}
