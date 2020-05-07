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
import java.net.InetSocketAddress;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import org.junit.Test;

public class JdwpProxyClientTest {
  @Test
  public void validateHandshakeSwitch() throws Exception {
    JdwpProxyServer server = new JdwpProxyServer(DdmPreferences.DEFAULT_PROXY_SERVER_PORT, () -> {
    });
    server.start();
    SocketChannel channel = SocketChannel.open(new InetSocketAddress("localhost", DdmPreferences.DEFAULT_PROXY_SERVER_PORT));
    Selector selector = Selector.open();
    JdwpProxyClient client = new JdwpProxyClient(channel, new JdwpClientManagerFactory(selector, new byte[1]), new byte[1]);
    assertThat(client.isHandshakeComplete()).isFalse();
    client.setHandshakeComplete();
    assertThat(client.isHandshakeComplete()).isTrue();
    server.stop();
  }

  @Test
  public void validateConnection() throws Exception {
    JdwpProxyServer server = new JdwpProxyServer(DdmPreferences.DEFAULT_PROXY_SERVER_PORT, () -> {
    });
    server.start();
    SocketChannel channel = SocketChannel.open(new InetSocketAddress("localhost", DdmPreferences.DEFAULT_PROXY_SERVER_PORT));
    Selector selector = Selector.open();
    JdwpProxyClient client = new JdwpProxyClient(channel, new JdwpClientManagerFactory(selector, new byte[1]), new byte[1]);
    assertThat(client.isConnected()).isTrue();
    client.shutdown();
    assertThat(client.isConnected()).isFalse();
    server.stop();
  }
}
