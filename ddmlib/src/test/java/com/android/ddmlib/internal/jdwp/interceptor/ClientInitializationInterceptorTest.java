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

import static com.android.ddmlib.internal.jdwp.interceptor.InterceptorTest.makePacket;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.ddmlib.internal.jdwp.JdwpProxyClient;
import com.android.ddmlib.internal.jdwp.chunkhandler.HandleHello;
import org.junit.Test;

public class ClientInitializationInterceptorTest {

    @Test
    public void defaultsReturnFalse() throws Exception {
        ClientInitializationInterceptor interceptor = new ClientInitializationInterceptor();
        JdwpProxyClient mock = mock(JdwpProxyClient.class);
        assertThat(interceptor.filterToClient(mock, new byte[0], 0)).isFalse();
        assertThat(interceptor.filterToDevice(mock, new byte[0], 0)).isFalse();
    }

    @Test
    public void packetFilterIgnoresUnknownPackets() {
        ClientInitializationInterceptor interceptor = new ClientInitializationInterceptor();
        JdwpProxyClient mock = mock(JdwpProxyClient.class);
        assertThat(interceptor.filterToClient(mock, makePacket("TEST"))).isFalse();
    }

    @Test
    public void packetFilterDoesntSendFilteredPacketTwice() throws Exception {
        ClientInitializationInterceptor interceptor = new ClientInitializationInterceptor();
        JdwpProxyClient mock = mock(JdwpProxyClient.class);
        assertThat(interceptor.filterToDevice(mock, makePacket(HandleHello.CHUNK_HELO))).isFalse();
        assertThat(interceptor.filterToDevice(mock, makePacket(HandleHello.CHUNK_HELO))).isTrue();
    }

    @Test
    public void filterPacketsBeforeHandshake() throws Exception {
        ClientInitializationInterceptor interceptor = new ClientInitializationInterceptor();
        JdwpProxyClient mock = mock(JdwpProxyClient.class);
        when(mock.isHandshakeComplete()).thenReturn(false);
        // Make a round trip to put packet in cache.
        assertThat(interceptor.filterToDevice(mock, makePacket(HandleHello.CHUNK_HELO))).isFalse();
        assertThat(interceptor.filterToClient(mock, makePacket(HandleHello.CHUNK_HELO))).isTrue();
        assertThat(interceptor.filterToDevice(mock, makePacket(HandleHello.CHUNK_HELO))).isTrue();
    }

    @Test
    public void packetWithResponseAnsweredOnRequest() throws Exception {
        ClientInitializationInterceptor interceptor = new ClientInitializationInterceptor();
        JdwpProxyClient mock = mock(JdwpProxyClient.class);
        when(mock.isHandshakeComplete()).thenReturn(true);
        // Make a round trip to put packet in cache.
        assertThat(interceptor.filterToDevice(mock, makePacket(HandleHello.CHUNK_HELO))).isFalse();
        assertThat(interceptor.filterToClient(mock, makePacket(HandleHello.CHUNK_HELO))).isFalse();
        // All future request should be filtered.
        assertThat(interceptor.filterToDevice(mock, makePacket(HandleHello.CHUNK_HELO))).isTrue();
        assertThat(interceptor.filterToDevice(mock, makePacket(HandleHello.CHUNK_HELO))).isTrue();
        assertThat(interceptor.filterToDevice(mock, makePacket(HandleHello.CHUNK_HELO))).isTrue();
    }
}
