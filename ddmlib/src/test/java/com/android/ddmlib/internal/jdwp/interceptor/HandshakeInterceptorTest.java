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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import com.android.ddmlib.JdwpHandshake;
import com.android.ddmlib.internal.jdwp.JdwpProxyClient;
import java.nio.ByteBuffer;
import org.junit.Test;

public class HandshakeInterceptorTest {

  @Test
  public void handshakeFilterToDevice() throws Exception {
    HandshakeInterceptor interceptor = new HandshakeInterceptor();
    ByteBuffer handshake = ByteBuffer.allocate(JdwpHandshake.HANDSHAKE_LEN);
    JdwpHandshake.putHandshake(handshake);
    JdwpProxyClient mock = mock(JdwpProxyClient.class);
    assertThat(interceptor.filterToDevice(mock, handshake.array(), handshake.position())).isFalse();
    assertThat(interceptor.filterToDevice(mock, handshake.array(), handshake.position())).isTrue();
  }

  @Test
  public void handshakeFilterToClient() throws Exception {
    HandshakeInterceptor interceptor = new HandshakeInterceptor();
    ByteBuffer handshake = ByteBuffer.allocate(JdwpHandshake.HANDSHAKE_LEN);
    JdwpHandshake.putHandshake(handshake);
    JdwpProxyClient mock = mock(JdwpProxyClient.class);
    assertThat(interceptor.filterToClient(mock, handshake.array(), handshake.position())).isTrue();
    assertThat(interceptor.filterToDevice(mock, handshake.array(), handshake.position())).isFalse();
    assertThat(interceptor.filterToClient(mock, handshake.array(), handshake.position())).isFalse();
  }

  @Test
  public void handshakeSentWhenCachedFromDevice() throws Exception {
    HandshakeInterceptor interceptor = new HandshakeInterceptor();
    ByteBuffer handshake = ByteBuffer.allocate(JdwpHandshake.HANDSHAKE_LEN);
    JdwpHandshake.putHandshake(handshake);
    JdwpProxyClient mock = mock(JdwpProxyClient.class);
    assertThat(interceptor.filterToDevice(mock, handshake.array(), handshake.position())).isFalse();
    assertThat(interceptor.filterToClient(mock, handshake.array(), handshake.position())).isFalse();
    verify(mock, times(0)).write(any(), anyInt());
    assertThat(interceptor.filterToDevice(mock, handshake.array(), handshake.position())).isTrue();
    verify(mock, times(1)).write(any(), anyInt());
  }
}
