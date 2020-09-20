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
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.JdwpHandshake;
import com.android.ddmlib.internal.FakeAdbTestRule;
import com.android.ddmlib.internal.jdwp.chunkhandler.ChunkHandler;
import com.android.ddmlib.internal.jdwp.chunkhandler.JdwpPacket;
import com.android.ddmlib.internal.jdwp.interceptor.Interceptor;
import com.android.fakeadbserver.DeviceState;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.mockito.Mockito;

public class JdwpClientManagerTest {

    // FakeAdbTestRule cannot be initialized as a rule in this class because some test need to have
    // a customized initialization order for FakeAdb

    @Test
    public void connectionThrowsErrorOnFiledToFindDevice() throws Throwable {
        Selector selector = Selector.open();
        FakeAdbTestRule fakeAdb = new FakeAdbTestRule();
        fakeAdb.before();
        byte[] buffer = new byte[1];
        try {
            JdwpClientManager ignored =
                    new JdwpClientManager(
                            new JdwpClientManagerId("BAD_DEVICE", FakeAdbTestRule.PID),
                            selector,
                            buffer);
            // Should never hit due to exception
            fail("Connection should throw exception");
        } catch (AdbCommandRejectedException ex) {
            assertThat(ex).hasMessageThat().contains("device");
        }
        fakeAdb.after();
    }

    @Test
    public void connectionThrowsErrorOnFiledToFindProcess() throws Throwable {
        FakeAdbTestRule fakeAdb = new FakeAdbTestRule();
        fakeAdb.before();
        Selector selector = Selector.open();
        DeviceState state = fakeAdb.connectAndWaitForDevice();
        assertThat(state.getDeviceStatus()).isEqualTo(DeviceState.DeviceStatus.ONLINE);
        byte[] buffer = new byte[1];
        try {
            JdwpClientManager ignored =
                    new JdwpClientManager(
                            new JdwpClientManagerId(FakeAdbTestRule.SERIAL, -1), selector, buffer);
            // Should never hit due to exception
            fail("Connection should throw exception");
        } catch (AdbCommandRejectedException ex) {
            assertThat(ex).hasMessageThat().contains("pid");
        }
        fakeAdb.after();
    }

    @Test
    public void connectionRegistorsSelector() throws Throwable {
        FakeAdbTestRule fakeAdb = new FakeAdbTestRule();
        fakeAdb.before();
        Selector selector = Selector.open();
        DeviceState state = fakeAdb.connectAndWaitForDevice();
        assertThat(state.getDeviceStatus()).isEqualTo(DeviceState.DeviceStatus.ONLINE);
        FakeAdbTestRule.launchAndWaitForProcess(state, true);
        byte[] buffer = new byte[1];
        assertThat(selector.keys()).isEmpty();
        JdwpClientManager connection =
                new JdwpClientManager(
                        new JdwpClientManagerId(FakeAdbTestRule.SERIAL, FakeAdbTestRule.PID),
                        selector,
                        buffer);
        assertThat(selector.keys()).isNotEmpty();
        SelectionKey key = selector.keys().iterator().next();
        assertThat(key.isAcceptable()).isFalse();
        assertThat(key.attachment()).isEqualTo(connection);
        assertThat(key.interestOps()).isEqualTo(SelectionKey.OP_READ);
        fakeAdb.after();
    }

    @Test
    public void proxyClientIsCalledWhenDataIsReceived() throws Throwable {
        // Need to start a server before FakeAdb so we have the actual server instead of the
        // fallback.
        JdwpProxyServer server =
                new JdwpProxyServer(DdmPreferences.DEFAULT_PROXY_SERVER_PORT, () -> {});
        server.start();
        FakeAdbTestRule fakeAdb = new FakeAdbTestRule();
        fakeAdb.before();
        DeviceState state = fakeAdb.connectAndWaitForDevice();
        assertThat(state.getDeviceStatus()).isEqualTo(DeviceState.DeviceStatus.ONLINE);
        FakeAdbTestRule.launchAndWaitForProcess(state, true);
        JdwpClientManager connection =
                server.getFactory()
                        .createConnection(
                                new JdwpClientManagerId(
                                        FakeAdbTestRule.SERIAL, FakeAdbTestRule.PID));
        JdwpProxyClient client = mock(JdwpProxyClient.class);
        when(client.isHandshakeComplete()).thenReturn(true);
        connection.addListener(client);
        connection.read(); // Read data from fake adb even if that data is 0, we should call write.
        verify(client, times(1)).write(any(), anyInt());
        fakeAdb.after();
        server.stop();
    }

    @Test
    public void inspectorIsRunOnWriteAndRead() throws Throwable {
        // Need to start a server before FakeAdb so we have the actual server instead of the
        // fallback.
        JdwpProxyServer server =
                new JdwpProxyServer(DdmPreferences.DEFAULT_PROXY_SERVER_PORT, () -> {});
        server.start();
        FakeAdbTestRule fakeAdb = new FakeAdbTestRule();
        fakeAdb.before();

        // Attach device and process
        DeviceState state = fakeAdb.connectAndWaitForDevice();
        assertThat(state.getDeviceStatus()).isEqualTo(DeviceState.DeviceStatus.ONLINE);
        FakeAdbTestRule.launchAndWaitForProcess(state, true);

        // Spy on the real connection
        JdwpClientManager connection =
                Mockito.spy(
                        server.getFactory()
                                .createConnection(
                                        new JdwpClientManagerId(
                                                FakeAdbTestRule.SERIAL, FakeAdbTestRule.PID)));

        // Add a mock interceptor to verify the functions we expect get called
        TestInterceptor testInterceptor = new TestInterceptor(false);
        connection.addInterceptor(testInterceptor);

        // Create a fake client that writes data to device
        JdwpProxyClient client = mock(JdwpProxyClient.class);
        connection.addListener(client);
        // Create a test packet.
        ByteBuffer data = ChunkHandler.allocBuffer(4);
        JdwpPacket packet = new JdwpPacket(data);
        ChunkHandler.getChunkDataBuf(data);
        data.putInt(1234);
        ChunkHandler.finishChunkPacket(packet, ChunkHandler.type("TEST"), data.position());

        // Write data to device from client.
        connection.write(client, data.array(), data.position());
        verify(connection, times(1)).writeRaw(any());

        testInterceptor.verifyFunctionCallCount(1, 1, 0, 0);
        testInterceptor.verifyArguments(
                new Object[] {client, data.array(), data.position(), client, packet});
        testInterceptor.reset();
        // Read data from device to client.
        // Read only read 0 bytes of data as such we don't expect to parse that as a packet.
        connection.read();
        // Two monitors are connected so we expect the filter to be called twice.
        testInterceptor.verifyFunctionCallCount(0, 0, 2, 0);
        // Shutdown everything
        fakeAdb.after();
        server.stop();
    }

    @Test
    public void dontWriteWhenFiltered() throws Throwable {
        // Need to start a server before FakeAdb so we have the actual server instead of the
        // fallback.
        JdwpProxyServer server =
                new JdwpProxyServer(DdmPreferences.DEFAULT_PROXY_SERVER_PORT, () -> {});
        server.start();
        FakeAdbTestRule fakeAdb = new FakeAdbTestRule();
        fakeAdb.before();

        // Attach device and process
        DeviceState state = fakeAdb.connectAndWaitForDevice();
        assertThat(state.getDeviceStatus()).isEqualTo(DeviceState.DeviceStatus.ONLINE);
        FakeAdbTestRule.launchAndWaitForProcess(state, true);

        // Spy on the real connection
        JdwpClientManager connection =
                Mockito.spy(
                        server.getFactory()
                                .createConnection(
                                        new JdwpClientManagerId(
                                                FakeAdbTestRule.SERIAL, FakeAdbTestRule.PID)));

        // Add a mock interceptor to verify the functions we expect get called
        TestInterceptor testInterceptor = new TestInterceptor(true);
        connection.addInterceptor(testInterceptor);

        // Create a fake client that writes data to device
        JdwpProxyClient client = mock(JdwpProxyClient.class);
        connection.addListener(client);
        // Create a test packet.
        ByteBuffer data = ChunkHandler.allocBuffer(4);
        JdwpPacket packet = new JdwpPacket(data);
        ChunkHandler.getChunkDataBuf(data);
        data.putInt(1234);
        ChunkHandler.finishChunkPacket(packet, ChunkHandler.type("TEST"), data.position());

        // Write data to device from client.
        connection.write(client, data.array(), data.position());
        verify(connection, times(0)).writeRaw(any());

        // Shutdown everything
        fakeAdb.after();
        server.stop();
    }

    @Test
    public void handshakeSentOnCreation() throws Exception {
        SimpleServer server = new SimpleServer();
        Thread serverThread = new Thread(server);
        serverThread.start();
        SocketChannel channel = SocketChannel.open();
        channel.connect(
                new InetSocketAddress(InetAddress.getByName("localhost"), server.getPort()));
        JdwpClientManager manager = new JdwpClientManager(channel, new byte[1024]);
        serverThread.join();
        assertThat(server.getData().position()).isEqualTo(JdwpHandshake.HANDSHAKE_LEN);
        assertThat(JdwpHandshake.findHandshake(server.getData()))
                .isEqualTo(JdwpHandshake.HANDSHAKE_GOOD);
    }

    @Test
    public void eachClientGetsSameData() throws Exception {
        ByteBuffer packetA = createPacket(ChunkHandler.type("TEST"));
        ByteBuffer packetB = createPacket(ChunkHandler.type("TSET"));
        byte[] data = new byte[packetA.position() + packetB.position()];
        System.arraycopy(packetA.array(), 0, data, 0, packetA.position());
        System.arraycopy(packetB.array(), 0, data, packetA.position(), packetB.position());
        SimpleServer server = new SimpleServer(data);
        Thread serverThread = new Thread(server);
        serverThread.start();
        SocketChannel channel = SocketChannel.open();
        channel.connect(
                new InetSocketAddress(InetAddress.getByName("localhost"), server.getPort()));
        JdwpClientManager manager = new JdwpClientManager(channel, new byte[1024]);
        List<byte[]> dataCapture = new ArrayList<>();

        // Create multiple clients
        JdwpProxyClient[] clients = new JdwpProxyClient[3];
        for (int i = 0; i < clients.length; i++) {
            clients[i] = Mockito.mock(JdwpProxyClient.class);
            when(clients[i].isHandshakeComplete()).thenReturn(true);
            // Due to https://code.google.com/archive/p/mockito/issues/126 we are unable to use
            // an ArgumentCapture. Instead we grab a copy of the arguments using an Answer.
            doAnswer(
                            (invocation ->
                                    dataCapture.add(
                                            Arrays.copyOf(
                                                    (byte[]) invocation.getArgument(0),
                                                    (int) invocation.getArgument(1)))))
                    .when(clients[i])
                    .write(any(), anyInt());
            manager.addListener(clients[i]);
        }
        // Read initial data from SimpleServer
        manager.read();
        // Verify each client was written to 2 times.
        for (JdwpProxyClient client : clients) {
            verify(client, times(2)).write(any(), anyInt());
        }

        for (int i = 0; i < clients.length; i++) {
            int packetAIndex = i;
            int packetBIndex = i + clients.length;
            assertThat(dataCapture.get(packetAIndex)).hasLength(packetA.position());
            assertThat(dataCapture.get(packetBIndex)).hasLength(packetB.position());
            assertThat(dataCapture.get(packetAIndex)).isEqualTo(packetA.array());
            assertThat(dataCapture.get(packetBIndex)).isEqualTo(packetB.array());
        }
    }

    private static ByteBuffer createPacket(int type) {
        ByteBuffer rawBuf = ByteBuffer.allocate(JdwpPacket.JDWP_HEADER_LEN + 8);
        rawBuf.order(CHUNK_ORDER);
        JdwpPacket packet = new JdwpPacket(rawBuf);
        ByteBuffer buf = ChunkHandler.getChunkDataBuf(rawBuf);
        int chunkLen = buf.position();
        // no data
        ByteBuffer payload = packet.getPayload();
        payload.putInt(0x00, type);
        payload.putInt(0x04, chunkLen);
        packet.finishPacket(DDMS_CMD_SET, DDMS_CMD, CHUNK_HEADER_LEN + chunkLen);
        rawBuf.put(0x08, (byte) 0x80 /* reply packet */);
        return rawBuf;
    }

    private static class TestInterceptor implements Interceptor {

        int[] functionCallCount = new int[4];

        List<Object> capturedData = new ArrayList<>();

        boolean defaultReturnValue;

        TestInterceptor(boolean defaultReturnValue) {
            this.defaultReturnValue = defaultReturnValue;
        }

        void verifyFunctionCallCount(
                int deviceBytes, int devicePackets, int clientBytes, int clientPackets) {
            assertThat(functionCallCount[0]).isEqualTo(deviceBytes);
            assertThat(functionCallCount[1]).isEqualTo(devicePackets);
            assertThat(functionCallCount[2]).isEqualTo(clientBytes);
            assertThat(functionCallCount[3]).isEqualTo(clientPackets);
        }

        void verifyArguments(Object[] data) {
            assertThat(capturedData).hasSize(data.length);
            for (int i = 0; i < capturedData.size(); i++) {
                if (capturedData.get(i) instanceof JdwpPacket) {
                    assertThat(data[i]).isInstanceOf(JdwpPacket.class);
                    JdwpPacket captured = (JdwpPacket) capturedData.get(i);
                    JdwpPacket expected = (JdwpPacket) data[i];
                    assertThat(captured.getId()).isEqualTo(expected.getId());
                    assertThat(captured.getPayload()).isEqualTo(expected.getPayload());
                } else {
                    assertThat(capturedData.get(i)).isEqualTo(data[i]);
                }
            }
        }

        void reset() {
            Arrays.fill(functionCallCount, 0);
            capturedData.clear();
        }

        @Override
        public boolean filterToDevice(
                @NonNull JdwpProxyClient from, @NonNull byte[] bufferToSend, int length) {
            functionCallCount[0]++;
            capturedData.add(from);
            capturedData.add(bufferToSend);
            capturedData.add(length);
            return defaultReturnValue;
        }

        @Override
        public boolean filterToDevice(
                @NonNull JdwpProxyClient from, @NonNull JdwpPacket packetToSend) {
            functionCallCount[1]++;
            capturedData.add(from);
            capturedData.add(packetToSend);
            return defaultReturnValue;
        }

        @Override
        public boolean filterToClient(
                @NonNull JdwpProxyClient to, @NonNull byte[] bufferToSend, int length) {
            functionCallCount[2]++;
            capturedData.add(to);
            capturedData.add(bufferToSend);
            capturedData.add(length);
            return defaultReturnValue;
        }

        @Override
        public boolean filterToClient(
                @NonNull JdwpProxyClient to, @NonNull JdwpPacket packetToSend) {
            functionCallCount[3]++;
            capturedData.add(to);
            capturedData.add(packetToSend);
            return defaultReturnValue;
        }
    }
}
