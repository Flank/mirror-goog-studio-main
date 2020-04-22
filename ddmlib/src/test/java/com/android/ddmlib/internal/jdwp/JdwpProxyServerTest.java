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
import com.android.ddmlib.internal.FakeAdbTestRule;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;

public class JdwpProxyServerTest {

  // Creates and starts a default server
  public @Rule FakeAdbTestRule myFakeAdb = new FakeAdbTestRule();

  @Test
  public void serverStartsOnPortDefaultPort() throws Exception {
    SocketChannel channel = SocketChannel.open(new InetSocketAddress("localhost", DdmPreferences.DEFAULT_PROXY_SERVER_PORT));
    assertThat(channel.isConnected()).isTrue();
    channel.close();
  }

  @Test
  public void secondaryServerStartsInFallbackMode() throws Exception {
    JdwpProxyServer proxy = new JdwpProxyServer(DdmPreferences.DEFAULT_PROXY_SERVER_PORT, () -> {
    });
    proxy.start();
    assertThat(proxy.IsRunningAsServer()).isFalse();
    proxy.stop();
  }

  @Test
  public void stateChangeCallbackWhenServerStops() throws Exception {
    CountDownLatch stateChangedLatch = new CountDownLatch(1);
    JdwpProxyServer server = new JdwpProxyServer(0, () -> {
    });
    server.start();
    assertThat(server.IsRunningAsServer()).isTrue();
    assertThat(server.IsConnectedOrListening()).isTrue();
    waitForConnectionOrListening(server, true);

    JdwpProxyServer fallback = new JdwpProxyServer(server.getBindPort(), () -> stateChangedLatch.countDown());
    fallback.start();
    assertThat(fallback.IsRunningAsServer()).isFalse();
    // Connection happens on separate thread so we need to delay a little to see if we can actually establish a connection.
    waitForConnectionOrListening(fallback, false);

    // Kill server and expect fallback to become our primary server.
    server.stop();
    waitForConnectionOrListening(fallback, true);
    assertThat(stateChangedLatch.await(FakeAdbTestRule.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
    assertThat(fallback.IsRunningAsServer()).isTrue();
    fallback.stop();
  }

  private static void waitForConnectionOrListening(JdwpProxyServer server, boolean expectServer) throws InterruptedException {
    int retryCount = 10;
    for (int i = 0; i < retryCount; i++) {
      if (server.IsConnectedOrListening() && server.IsRunningAsServer() == expectServer) {
        break;
      }
      Thread.sleep(1000);
    }
    assertThat(server.IsConnectedOrListening()).isTrue();
  }
}
