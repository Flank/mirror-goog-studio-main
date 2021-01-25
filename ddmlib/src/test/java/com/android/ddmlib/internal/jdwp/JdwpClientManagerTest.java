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
import org.junit.After;
import org.junit.Test;
import org.mockito.Mockito;

public class JdwpClientManagerTest {

    // FakeAdbTestRule cannot be initialized as a rule in this class because some test need to have
    // a customized initialization order for FakeAdb
    FakeAdbTestRule myFakeAdb = new FakeAdbTestRule();
    JdwpProxyServer myServer = new JdwpProxyServer(DdmPreferences.getJdwpProxyPort(), () -> {});

    @After
    public void shutdown() {
        // Attempt to stop server and fake adb after each test.
        myFakeAdb.after();
        myServer.stop();
    }

    @Test
    public void connectionThrowsErrorOnFiledToFindDevice() throws Throwable {
        Selector selector = Selector.open();
        myFakeAdb.before();
        try {
            JdwpClientManager ignored =
                    new JdwpClientManager(
                            new JdwpClientManagerId("BAD_DEVICE", FakeAdbTestRule.PID), selector);
            // Should never hit due to exception
            fail("Connection should throw exception");
        } catch (AdbCommandRejectedException ex) {
            assertThat(ex).hasMessageThat().contains("device");
        }
    }

    @Test
    public void connectionThrowsErrorOnFiledToFindProcess() throws Throwable {
        myFakeAdb.before();
        Selector selector = Selector.open();
        DeviceState state = myFakeAdb.connectAndWaitForDevice();
        assertThat(state.getDeviceStatus()).isEqualTo(DeviceState.DeviceStatus.ONLINE);
        try {
            JdwpClientManager ignored =
                    new JdwpClientManager(
                            new JdwpClientManagerId(FakeAdbTestRule.SERIAL, -1), selector);
            // Should never hit due to exception
            fail("Connection should throw exception");
        } catch (AdbCommandRejectedException ex) {
            assertThat(ex).hasMessageThat().contains("pid");
        }
    }

    @Test
    public void connectionRegistorsSelector() throws Throwable {
        myFakeAdb.before();
        Selector selector = Selector.open();
        DeviceState state = myFakeAdb.connectAndWaitForDevice();
        assertThat(state.getDeviceStatus()).isEqualTo(DeviceState.DeviceStatus.ONLINE);
        FakeAdbTestRule.launchAndWaitForProcess(state, true);
        assertThat(selector.keys()).isEmpty();
        JdwpClientManager connection =
                new JdwpClientManager(
                        new JdwpClientManagerId(FakeAdbTestRule.SERIAL, FakeAdbTestRule.PID),
                        selector);
        assertThat(selector.keys()).isNotEmpty();
        SelectionKey key = selector.keys().iterator().next();
        assertThat(key.isAcceptable()).isFalse();
        assertThat(key.attachment()).isEqualTo(connection);
        assertThat(key.interestOps()).isEqualTo(SelectionKey.OP_READ);
    }

    @Test
    public void inspectorIsRunOnWriteAndRead() throws Throwable {
        ByteBuffer data = ChunkHandler.allocBuffer(4);
        ByteBuffer handshake = ByteBuffer.allocate(JdwpHandshake.HANDSHAKE_LEN);
        JdwpHandshake.putHandshake(handshake);
        JdwpPacket packet = new JdwpPacket(data);
        ChunkHandler.getChunkDataBuf(data);
        data.putInt(1234);
        ChunkHandler.finishChunkPacket(packet, JdwpTest.CHUNK_TEST, data.position());
        byte[] serverData = new byte[data.position() + JdwpHandshake.HANDSHAKE_LEN];
        System.arraycopy(handshake.array(), 0, serverData, 0, handshake.position());
        System.arraycopy(data.array(), 0, serverData, handshake.position(), data.position());
        SimpleServer server = new SimpleServer(serverData);
        Thread serverThread = new Thread(server);
        serverThread.start();
        SocketChannel channel = SocketChannel.open();
        channel.connect(
                new InetSocketAddress(InetAddress.getByName("localhost"), server.getPort()));
        JdwpClientManager manager = Mockito.spy(new JdwpClientManager(channel));
        serverThread.join();
        assertThat(server.getData().position()).isEqualTo(JdwpHandshake.HANDSHAKE_LEN);
        assertThat(JdwpHandshake.findHandshake(server.getData()))
                .isEqualTo(JdwpHandshake.HANDSHAKE_GOOD);

        // Add a mock interceptor to verify the functions we expect get called
        TestInterceptor testInterceptor = new TestInterceptor(false);
        manager.addInterceptor(testInterceptor);

        // Create a fake client that writes data to device
        JdwpProxyClient client = mock(JdwpProxyClient.class);
        manager.addListener(client);
        // Create a test packet.

        // Write data to device from client.
        manager.write(client, packet);
        verify(manager, times(1)).writeRaw(any());

        testInterceptor.verifyFunctionCallCount(1, 0);
        testInterceptor.verifyArguments(new Object[] {client, packet});
        testInterceptor.reset();

        manager.read();
        // One monitor connected the filter should be called once.
        testInterceptor.verifyFunctionCallCount(0, 1);
    }

    @Test
    public void dontWriteWhenFiltered() throws Throwable {
        // Need to start a server before FakeAdb so we have the actual server instead of the
        // fallback.

        myServer.start();
        myFakeAdb.before();

        // Attach device and process
        DeviceState state = myFakeAdb.connectAndWaitForDevice();
        assertThat(state.getDeviceStatus()).isEqualTo(DeviceState.DeviceStatus.ONLINE);
        FakeAdbTestRule.launchAndWaitForProcess(state, true);

        // Spy on the real connection
        JdwpClientManager connection =
                Mockito.spy(
                        myServer.getFactory()
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
        ChunkHandler.finishChunkPacket(packet, JdwpTest.CHUNK_TEST, data.position());

        // Write data to device from client.
        connection.write(client, packet);
        verify(connection, times(0)).writeRaw(any());
    }

    @Test
    public void handshakeSentOnCreation() throws Exception {
        SimpleServer server = new SimpleServer();
        Thread serverThread = new Thread(server);
        serverThread.start();
        SocketChannel channel = SocketChannel.open();
        channel.connect(
                new InetSocketAddress(InetAddress.getByName("localhost"), server.getPort()));
        JdwpClientManager manager = new JdwpClientManager(channel);
        serverThread.join();
        assertThat(server.getData().position()).isEqualTo(JdwpHandshake.HANDSHAKE_LEN);
        assertThat(JdwpHandshake.findHandshake(server.getData()))
                .isEqualTo(JdwpHandshake.HANDSHAKE_GOOD);
    }

    @Test
    public void eachClientGetsSameData() throws Exception {
        ByteBuffer handshake = ByteBuffer.allocate(JdwpHandshake.HANDSHAKE_LEN);
        JdwpHandshake.putHandshake(handshake);
        ByteBuffer packetA = JdwpTest.createPacketBuffer(JdwpTest.CHUNK_TEST, 0);
        ByteBuffer packetB = JdwpTest.createPacketBuffer(JdwpTest.CHUNK_TEST, 0);
        byte[] data = new byte[handshake.position() + packetA.position() + packetB.position()];
        System.arraycopy(handshake.array(), 0, data, 0, handshake.position());
        System.arraycopy(packetA.array(), 0, data, handshake.position(), packetA.position());
        System.arraycopy(
                packetB.array(),
                0,
                data,
                handshake.position() + packetA.position(),
                packetB.position());
        SimpleServer server = new SimpleServer(data);
        Thread serverThread = new Thread(server);
        serverThread.start();
        SocketChannel channel = SocketChannel.open();
        channel.connect(
                new InetSocketAddress(InetAddress.getByName("localhost"), server.getPort()));
        JdwpClientManager manager = new JdwpClientManager(channel);
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
                                                    invocation.getArgument(1)))))
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

    @Test
    public void packetsBeforeHandshakeAreIgnored() throws Exception {
        ByteBuffer packetA = JdwpTest.createPacketBuffer(JdwpTest.CHUNK_TEST, 4);
        ByteBuffer handshake =
                ByteBuffer.allocate(JdwpHandshake.HANDSHAKE_LEN + packetA.position());
        JdwpHandshake.putHandshake(handshake);
        byte[] data = new byte[packetA.position() + handshake.position()];
        System.arraycopy(packetA.array(), 0, data, 0, packetA.position());
        System.arraycopy(handshake.array(), 0, data, packetA.position(), handshake.position());
        SimpleServer server = new SimpleServer(data);
        Thread serverThread = new Thread(server);
        serverThread.start();
        SocketChannel channel = SocketChannel.open();
        channel.connect(
                new InetSocketAddress(InetAddress.getByName("localhost"), server.getPort()));
        JdwpClientManager manager = new JdwpClientManager(channel);
        List<byte[]> dataCapture = new ArrayList<>();
        JdwpProxyClient clients = Mockito.mock(JdwpProxyClient.class);
        when(clients.isHandshakeComplete()).thenReturn(true);
        // Due to https://code.google.com/archive/p/mockito/issues/126 we are unable to use
        // an ArgumentCapture. Instead we grab a copy of the arguments using an Answer.
        doAnswer(
                        (invocation ->
                                dataCapture.add(
                                        Arrays.copyOf(
                                                (byte[]) invocation.getArgument(0),
                                                invocation.getArgument(1)))))
                .when(clients)
                .write(any(), anyInt());
        manager.addListener(clients);
        // Read handshake data from SimpleServer
        manager.read();
        assertThat(dataCapture).isEmpty();
        serverThread.interrupt();
        serverThread.join();
    }

    @Test
    public void largePacket() throws Exception {
        int largePacketSize = 1024 * 1024;
        ByteBuffer packetA = JdwpTest.createPacketBuffer(JdwpTest.CHUNK_TEST, largePacketSize);
        ByteBuffer handshake =
                ByteBuffer.allocate(JdwpHandshake.HANDSHAKE_LEN + packetA.position());
        JdwpHandshake.putHandshake(handshake);
        byte[] data = new byte[packetA.position() + handshake.position()];
        System.arraycopy(handshake.array(), 0, data, 0, handshake.position());
        System.arraycopy(packetA.array(), 0, data, handshake.position(), packetA.position());
        SimpleServer server = new SimpleServer(data);
        Thread serverThread = new Thread(server);
        serverThread.start();
        SocketChannel channel = SocketChannel.open();
        channel.connect(
                new InetSocketAddress(InetAddress.getByName("localhost"), server.getPort()));
        JdwpClientManager manager = new JdwpClientManager(channel);
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
        // Read handshake data from SimpleServer
        manager.read();
        for (int i = 0; i < clients.length; i++) {
            int packetAIndex = i;
            assertThat(dataCapture.get(packetAIndex)).hasLength(packetA.position());
            assertThat(dataCapture.get(packetAIndex)).isEqualTo(packetA.array());
        }
        serverThread.interrupt();
        serverThread.join();
    }

    private static class TestInterceptor implements Interceptor {

        int[] functionCallCount = new int[4];

        List<Object> capturedData = new ArrayList<>();

        boolean defaultReturnValue;

        TestInterceptor(boolean defaultReturnValue) {
            this.defaultReturnValue = defaultReturnValue;
        }

        void verifyFunctionCallCount(int devicePackets, int clientPackets) {
            assertThat(functionCallCount[0]).isEqualTo(devicePackets);
            assertThat(functionCallCount[1]).isEqualTo(clientPackets);
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
                @NonNull JdwpProxyClient from, @NonNull JdwpPacket packetToSend) {
            functionCallCount[0]++;
            capturedData.add(from);
            capturedData.add(packetToSend);
            return defaultReturnValue;
        }

        @Override
        public boolean filterToClient(
                @NonNull JdwpProxyClient to, @NonNull JdwpPacket packetToSend) {
            functionCallCount[1]++;
            capturedData.add(to);
            capturedData.add(packetToSend);
            return defaultReturnValue;
        }
    }
}
