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

package com.android.ddmlib.internal.jdwp.interceptor;

import static com.android.ddmlib.internal.jdwp.JdwpTest.makePacket;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.ddmlib.internal.jdwp.JdwpProxyClient;
import com.android.ddmlib.internal.jdwp.chunkhandler.ChunkHandler;
import com.android.ddmlib.internal.jdwp.chunkhandler.JdwpPacket;
import com.android.ddmlib.jdwp.JdwpCommands;
import java.nio.ByteBuffer;
import org.junit.Test;

public class DebuggerInterceptorTest {

  @Test
  public void oneClientGetsAllDataWhenFiltered() {
    DebuggerInterceptor interceptor = new DebuggerInterceptor();
    JdwpProxyClient mock = mock(JdwpProxyClient.class);
    when(mock.isConnected()).thenReturn(true);
    ByteBuffer data = ByteBuffer.allocate(128);
    JdwpPacket packet = new JdwpPacket(data);
    ChunkHandler.getChunkDataBuf(data);
    data.putInt(1234); // Some version
    packet.finishPacket(JdwpCommands.SET_VM, JdwpCommands.CMD_VM_IDSIZES, 0);
    assertThat(interceptor.filterToDevice(mock, packet)).isFalse();
    assertThat(interceptor.filterToDevice(mock, makePacket("HELO"))).isFalse();
    assertThat(interceptor.filterToDevice(mock, makePacket("HELO"))).isFalse();
    assertThat(interceptor.filterToClient(mock, makePacket("HELO"))).isFalse();

    JdwpProxyClient mock2 = mock(JdwpProxyClient.class);
    assertThat(interceptor.filterToDevice(mock2, makePacket("HELO"))).isTrue();
  }
}
