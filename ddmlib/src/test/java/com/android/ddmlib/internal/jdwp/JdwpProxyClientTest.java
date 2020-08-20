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
import static org.mockito.Mockito.times;

import com.android.ddmlib.DdmPreferences;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.mockito.Mockito;

public class JdwpProxyClientTest {

    @Test
    public void validateHandshakeSwitch() throws Exception {
        JdwpProxyServer server =
                new JdwpProxyServer(DdmPreferences.DEFAULT_PROXY_SERVER_PORT, () -> {});
        server.start();
        SocketChannel channel =
                SocketChannel.open(
                        new InetSocketAddress(
                                "localhost", DdmPreferences.DEFAULT_PROXY_SERVER_PORT));
        Selector selector = Selector.open();
        JdwpProxyClient client =
                new JdwpProxyClient(
                        channel, new JdwpClientManagerFactory(selector, new byte[1]), new byte[1]);
        assertThat(client.isHandshakeComplete()).isFalse();
        client.setHandshakeComplete();
        assertThat(client.isHandshakeComplete()).isTrue();
        server.stop();
    }

    @Test
    public void validateConnection() throws Exception {
        JdwpProxyServer server =
                new JdwpProxyServer(DdmPreferences.DEFAULT_PROXY_SERVER_PORT, () -> {});
        server.start();
        SocketChannel channel =
                SocketChannel.open(
                        new InetSocketAddress(
                                "localhost", DdmPreferences.DEFAULT_PROXY_SERVER_PORT));
        Selector selector = Selector.open();
        JdwpProxyClient client =
                new JdwpProxyClient(
                        channel, new JdwpClientManagerFactory(selector, new byte[1]), new byte[1]);
        assertThat(client.isConnected()).isTrue();
        client.shutdown();
        assertThat(client.isConnected()).isFalse();
        server.stop();
    }

    @Test
    public void validateDisconnectCommandNoClients() throws Throwable {
        SimpleServer server = new SimpleServer("0000" + JdwpProxyClient.JDWP_DISCONNECT + "0:0");
        byte[] buffer = new byte[1024];
        Thread serverThread = new Thread(server);
        serverThread.start();
        SocketChannel channel = SocketChannel.open();
        channel.connect(
                new InetSocketAddress(InetAddress.getByName("localhost"), server.getPort()));
        JdwpProxyClient client =
                new JdwpProxyClient(channel, new JdwpClientManagerFactory(null, buffer), buffer);
        client.read();
        serverThread.join();
        assertThat(server.getData()).hasSize(1);
        assertThat(server.getData().get(0)).startsWith("FAIL");
    }

    @Test
    public void validateDisconnectCommandWithClient() throws Throwable {
        JdwpClientManagerFactory factory = Mockito.mock(JdwpClientManagerFactory.class);
        JdwpClientManager clientManager = Mockito.mock(JdwpClientManager.class);
        Mockito.when(factory.getConnection("DEVICEID", 1234)).thenReturn(clientManager);
        SimpleServer server =
                new SimpleServer("0000" + JdwpProxyClient.JDWP_DISCONNECT + "DEVICEID:1234");
        byte[] buffer = new byte[1024];
        Thread serverThread = new Thread(server);
        serverThread.start();
        SocketChannel channel = SocketChannel.open();
        channel.connect(
                new InetSocketAddress(InetAddress.getByName("localhost"), server.getPort()));
        JdwpProxyClient client = new JdwpProxyClient(channel, factory, buffer);
        client.read();
        serverThread.join();
        assertThat(server.getData()).hasSize(1);
        assertThat(server.getData().get(0)).startsWith("OKAY");
        Mockito.verify(clientManager, times(1)).shutdown();
    }

    private class SimpleServer implements Runnable {

        private ServerSocketChannel mListenChannel;

        private int mListenPort = 0;

        private List<String> mData = new ArrayList<>();

        private SocketChannel mClient = null;

        private String mConnectMessage;

        SimpleServer(String onConnectMessage) throws IOException {
            mConnectMessage = onConnectMessage;
            mListenChannel = ServerSocketChannel.open();
            InetSocketAddress addr =
                    new InetSocketAddress(
                            InetAddress.getByName("localhost"), // $NON-NLS-1$
                            0);
            mListenChannel.socket().setReuseAddress(true); // enable SO_REUSEADDR
            mListenChannel.socket().bind(addr);
            mListenPort = mListenChannel.socket().getLocalPort();
        }

        public ServerSocketChannel getServerSocket() {
            return mListenChannel;
        }

        public int getPort() {
            return mListenPort;
        }

        public List<String> getData() {
            return mData;
        }

        public SocketChannel getClient() {
            return mClient;
        }

        @Override
        public void run() {
            try {
                mClient = mListenChannel.accept();
                mClient.write(ByteBuffer.wrap(mConnectMessage.getBytes()));
                ByteBuffer buffer = ByteBuffer.allocate(1024);
                mClient.read(buffer);
                mData.add(new String(buffer.array()).trim());
            } catch (Exception ex) {
                // End thread.
            }
        }
    }
}
