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

import static com.android.ddmlib.internal.jdwp.JdwpConnectionReader.JDWP_DISCONNECT;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.times;

import com.android.ddmlib.AdbHelper;
import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.JdwpHandshake;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import org.junit.Test;
import org.mockito.Mockito;

public class JdwpProxyClientTest {

    @Test
    public void validateHandshakeSwitch() throws Exception {
        JdwpProxyServer server = new JdwpProxyServer(DdmPreferences.getJdwpProxyPort(), () -> {});
        server.start();
        SocketChannel channel =
                SocketChannel.open(
                        new InetSocketAddress("localhost", DdmPreferences.getJdwpProxyPort()));
        Selector selector = Selector.open();
        JdwpProxyClient client =
                new JdwpProxyClient(channel, new JdwpClientManagerFactory(selector));
        assertThat(client.isHandshakeComplete()).isFalse();
        client.setHandshakeComplete();
        assertThat(client.isHandshakeComplete()).isTrue();
        server.stop();
    }

    @Test
    public void validateConnection() throws Exception {
        JdwpProxyServer server = new JdwpProxyServer(DdmPreferences.getJdwpProxyPort(), () -> {});
        server.start();
        SocketChannel channel =
                SocketChannel.open(
                        new InetSocketAddress("localhost", DdmPreferences.getJdwpProxyPort()));
        Selector selector = Selector.open();
        JdwpProxyClient client =
                new JdwpProxyClient(channel, new JdwpClientManagerFactory(selector));
        assertThat(client.isConnected()).isTrue();
        client.shutdown();
        assertThat(client.isConnected()).isFalse();
        server.stop();
    }

    @Test
    public void validateDisconnectCommandNoClients() throws Throwable {
        verifyCommand(
                new JdwpClientManagerFactory(null),
                AdbHelper.formAdbRequest(JDWP_DISCONNECT + "0:0"),
                "FAIL".getBytes());
    }

    @Test
    public void validateDisconnectCommandWithClient() throws Throwable {
        JdwpClientManagerFactory factory = Mockito.mock(JdwpClientManagerFactory.class);
        JdwpClientManager clientManager = Mockito.mock(JdwpClientManager.class);
        Mockito.when(factory.getConnection("DEVICEID", 1234)).thenReturn(clientManager);
        verifyCommand(
                factory,
                AdbHelper.formAdbRequest(JDWP_DISCONNECT + "DEVICEID:1234"),
                "OKAY".getBytes());
        Mockito.verify(clientManager, times(1)).shutdown();
    }

    @Test
    public void validateHandshakeResponseOnHandshake() throws Throwable {
        JdwpClientManagerFactory factory = Mockito.mock(JdwpClientManagerFactory.class);
        ByteBuffer handshake = ByteBuffer.allocate(JdwpHandshake.HANDSHAKE_LEN);
        JdwpHandshake.putHandshake(handshake);
        verifyCommand(factory, handshake.array(), handshake.array());
    }

    private void verifyCommand(JdwpClientManagerFactory factory, byte[] command, byte[] response)
            throws Throwable {
        SimpleServer server = new SimpleServer(command);
        Thread serverThread = new Thread(server);
        serverThread.start();
        SocketChannel channel = SocketChannel.open();
        channel.connect(
                new InetSocketAddress(InetAddress.getByName("localhost"), server.getPort()));
        JdwpProxyClient client = new JdwpProxyClient(channel, factory);
        client.read();
        serverThread.join();
        // If the client sends more than one bit of data back sometimes it can be appended to
        // original response. (eg FAIL [failure message]). To reduce the risk of flakes, only the
        // portion of the response that matches the expected response length is checked.
        assertThat(server.getData().position()).isAtLeast(response.length);
        for (int i = 0; i < response.length; i++) {
            assertThat(server.getData().get(i)).isEqualTo(response[i]);
        }
    }
}
