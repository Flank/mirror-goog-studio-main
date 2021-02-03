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

import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.JdwpHandshake;
import com.android.ddmlib.internal.jdwp.chunkhandler.ChunkHandler;
import com.android.ddmlib.internal.jdwp.chunkhandler.JdwpPacket;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class JdwpConnectionReaderTest {

    @Before
    public void setup() {
        // Reset max packet size between test.
        DdmPreferences.setsJdwpMaxPacketSize(1024 * 1024);
    }

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
        JdwpConnectionReader reader =
                new JdwpConnectionReader(channel, JdwpPacket.JDWP_HEADER_LEN + 1);
        Thread serverThread = new Thread(server);
        serverThread.start();
        validatePackets(reader, packets);
    }

    @Test
    public void handshakePacketDoesNotOverflowBuffer() throws Exception {
        SocketChannel channel = SocketChannel.open();
        ByteBuffer[] packets = new ByteBuffer[5];
        int length = 0;
        for (int i = 0; i < packets.length - 1; i++) {
            packets[i] = JdwpTest.createPacketBuffer(JdwpTest.CHUNK_TEST, packets.length - i);
            length += packets[i].position();
        }
        packets[packets.length - 1] = ByteBuffer.allocate(JdwpHandshake.HANDSHAKE_LEN);
        JdwpHandshake.putHandshake(packets[packets.length - 1]);
        length += JdwpHandshake.HANDSHAKE_LEN;

        ByteBuffer finalSentData = ByteBuffer.allocate(length);
        for (ByteBuffer byteBuffer : packets) {
            finalSentData.put(byteBuffer.array());
        }
        SimpleServer server = new SimpleServer(finalSentData.array());
        channel.connect(
                new InetSocketAddress(InetAddress.getByName("localhost"), server.getPort()));
        JdwpConnectionReader reader =
                new JdwpConnectionReader(channel, JdwpPacket.JDWP_HEADER_LEN + 1);
        Thread serverThread = new Thread(server);
        serverThread.start();
        validatePackets(reader, packets);
    }

    @Test
    public void resizeProperlySetsBufferPosition() throws Exception {
        // This tests a subtle buffer resizing behavior. A bug was introduced due to the buffer
        // resizing and copying the contents of the previous buffer independent of how full the
        // buffer was. This means reading 1K in a 5K buffer then resizing the buffer to 10K would
        // copy the full 5K of memory to the new buffer.
        // One way to test this is to create a packet that expands the default buffer, then create
        // a packet that satisfies two conditions.
        // 1) Is larger than the current buffer
        // 2) When read does not fill the entire buffer.
        // Using the SlowSimpleServer this test meets those two requirements.
        SocketChannel channel = SocketChannel.open();
        ByteBuffer[] packets = new ByteBuffer[2];
        int length = 0;
        packets[0] = JdwpTest.createPacketBuffer(JdwpTest.CHUNK_TEST, 128);
        length += packets[0].position();
        packets[1] = JdwpTest.createPacketBuffer(ChunkHandler.type("PKET"), 128 * 5);
        length += packets[1].position();
        ByteBuffer finalSentData = ByteBuffer.allocate(length);
        for (ByteBuffer byteBuffer : packets) {
            finalSentData.put(byteBuffer.array());
        }
        SlowSimpleServer server = new SlowSimpleServer(finalSentData.array());
        channel.connect(
                new InetSocketAddress(InetAddress.getByName("localhost"), server.getPort()));
        JdwpConnectionReader reader =
                new JdwpConnectionReader(channel, JdwpPacket.JDWP_HEADER_LEN);
        Thread serverThread = new Thread(server);
        serverThread.start();
        validatePackets(reader, packets);
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
        assertThat(sentPacket.array()).isEqualTo(packetData.array());
    }

    @Test(expected = BufferOverflowException.class)
    public void invalidPacketSizeThrowsException() throws Exception {
        SocketChannel channel = SocketChannel.open();
        DdmPreferences.setsJdwpMaxPacketSize(JdwpPacket.JDWP_HEADER_LEN);
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
        Assert.fail();
    }

    private void validatePackets(JdwpConnectionReader reader, ByteBuffer[] packets)
            throws Exception {
        for (ByteBuffer packet : packets) {
            JdwpPacket readPacket = null;
            while (reader.read() != -1) {
                if (reader.isHandshake()) {
                    break;
                }
                readPacket = reader.readPacket();
                if (readPacket != null) {
                    break;
                }
            }
            if (readPacket == null) {
                assertThat(reader.isHandshake()).isTrue();
            } else {
                ByteBuffer packetData = ByteBuffer.allocate(readPacket.getLength());
                readPacket.copy(packetData);
                assertThat(packet.array()).isEqualTo(packetData.array());
                readPacket.consume();
            }
        }
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
            }
            catch (Exception ex) {
                // End thread.
            }
        }
    }
}
