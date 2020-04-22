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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.internal.FakeAdbTestRule;
import com.android.ddmlib.internal.jdwp.chunkhandler.ChunkHandler;
import com.android.ddmlib.internal.jdwp.chunkhandler.JdwpPacket;
import com.android.ddmlib.internal.jdwp.interceptor.Interceptor;
import com.android.fakeadbserver.DeviceState;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
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
      JdwpClientManager ignored = new JdwpClientManager(new JdwpClientManagerId("BAD_DEVICE", FakeAdbTestRule.PID), selector, buffer);
      // Should never hit due to exception
      fail("Connection should throw exception");
    }
    catch (AdbCommandRejectedException ex) {
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
      JdwpClientManager ignored = new JdwpClientManager(new JdwpClientManagerId(FakeAdbTestRule.SERIAL, -1), selector, buffer);
      // Should never hit due to exception
      fail("Connection should throw exception");
    }
    catch (AdbCommandRejectedException ex) {
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
      new JdwpClientManager(new JdwpClientManagerId(FakeAdbTestRule.SERIAL, FakeAdbTestRule.PID), selector, buffer);
    assertThat(selector.keys()).isNotEmpty();
    SelectionKey key = selector.keys().iterator().next();
    assertThat(key.isAcceptable()).isFalse();
    assertThat(key.attachment()).isEqualTo(connection);
    assertThat(key.interestOps()).isEqualTo(SelectionKey.OP_READ);
    fakeAdb.after();
  }

  @Test
  public void proxyClientIsCalledWhenDataIsReceived() throws Throwable {
    // Need to start a server before FakeAdb so we have the actual server instead of the fallback.
    JdwpProxyServer server = new JdwpProxyServer(DdmPreferences.DEFAULT_PROXY_SERVER_PORT, () -> {
    });
    server.start();
    FakeAdbTestRule fakeAdb = new FakeAdbTestRule();
    fakeAdb.before();
    DeviceState state = fakeAdb.connectAndWaitForDevice();
    assertThat(state.getDeviceStatus()).isEqualTo(DeviceState.DeviceStatus.ONLINE);
    FakeAdbTestRule.launchAndWaitForProcess(state, true);
    JdwpClientManager connection =
      server.getFactory().createConnection(new JdwpClientManagerId(FakeAdbTestRule.SERIAL, FakeAdbTestRule.PID));
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
    // Need to start a server before FakeAdb so we have the actual server instead of the fallback.
    JdwpProxyServer server = new JdwpProxyServer(DdmPreferences.DEFAULT_PROXY_SERVER_PORT, () -> {
    });
    server.start();
    FakeAdbTestRule fakeAdb = new FakeAdbTestRule();
    fakeAdb.before();

    // Attach device and process
    DeviceState state = fakeAdb.connectAndWaitForDevice();
    assertThat(state.getDeviceStatus()).isEqualTo(DeviceState.DeviceStatus.ONLINE);
    FakeAdbTestRule.launchAndWaitForProcess(state, true);

    // Spy on the real connection
    JdwpClientManager connection =
      Mockito.spy(server.getFactory().createConnection(new JdwpClientManagerId(FakeAdbTestRule.SERIAL, FakeAdbTestRule.PID)));

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
    testInterceptor.verifyArguments(new Object[]{
      client,
      data.array(),
      data.position(),
      client,
      packet
    });
    testInterceptor.reset();
    // Read data from device to client.
    // Read only read 0 bytes of data as such we don't expect to parse that as a packet.
    connection.read();
    // Two monitors are connected so we expect the filter to be called twice.
    testInterceptor.verifyFunctionCallCount(0, 0, 2, 0);
    //Shutdown everything
    fakeAdb.after();
    server.stop();
  }

  @Test
  public void dontWriteWhenFiltered() throws Throwable {
    // Need to start a server before FakeAdb so we have the actual server instead of the fallback.
    JdwpProxyServer server = new JdwpProxyServer(DdmPreferences.DEFAULT_PROXY_SERVER_PORT, () -> {
    });
    server.start();
    FakeAdbTestRule fakeAdb = new FakeAdbTestRule();
    fakeAdb.before();

    // Attach device and process
    DeviceState state = fakeAdb.connectAndWaitForDevice();
    assertThat(state.getDeviceStatus()).isEqualTo(DeviceState.DeviceStatus.ONLINE);
    FakeAdbTestRule.launchAndWaitForProcess(state, true);

    // Spy on the real connection
    JdwpClientManager connection =
      Mockito.spy(server.getFactory().createConnection(new JdwpClientManagerId(FakeAdbTestRule.SERIAL, FakeAdbTestRule.PID)));

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

    //Shutdown everything
    fakeAdb.after();
    server.stop();
  }

  private static class TestInterceptor implements Interceptor {
    int[] functionCallCount = new int[4];
    List<Object> capturedData = new ArrayList<>();
    boolean defaultReturnValue;

    TestInterceptor(boolean defaultReturnValue) {
      this.defaultReturnValue = defaultReturnValue;
    }

    void verifyFunctionCallCount(int deviceBytes, int devicePackets, int clientBytes, int clientPackets) {
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
          JdwpPacket captured = (JdwpPacket)capturedData.get(i);
          JdwpPacket expected = (JdwpPacket)data[i];
          assertThat(captured.getId()).isEqualTo(expected.getId());
          assertThat(captured.getPayload()).isEqualTo(expected.getPayload());
        }
        else {
          assertThat(capturedData.get(i)).isEqualTo(data[i]);
        }
      }
    }

    void reset() {
      Arrays.fill(functionCallCount, 0);
      capturedData.clear();
    }

    @Override
    public boolean filterToDevice(@NonNull JdwpProxyClient from, @NonNull byte[] bufferToSend, int length) {
      functionCallCount[0]++;
      capturedData.add(from);
      capturedData.add(bufferToSend);
      capturedData.add(length);
      return defaultReturnValue;
    }

    @Override
    public boolean filterToDevice(@NonNull JdwpProxyClient from, @NonNull JdwpPacket packetToSend) {
      functionCallCount[1]++;
      capturedData.add(from);
      capturedData.add(packetToSend);
      return defaultReturnValue;
    }

    @Override
    public boolean filterToClient(@NonNull JdwpProxyClient to, @NonNull byte[] bufferToSend, int length) {
      functionCallCount[2]++;
      capturedData.add(to);
      capturedData.add(bufferToSend);
      capturedData.add(length);
      return defaultReturnValue;
    }

    @Override
    public boolean filterToClient(@NonNull JdwpProxyClient to, @NonNull JdwpPacket packetToSend) {
      functionCallCount[3]++;
      capturedData.add(to);
      capturedData.add(packetToSend);
      return defaultReturnValue;
    }
  }
}
