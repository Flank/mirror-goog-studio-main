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
import static com.android.ddmlib.internal.jdwp.chunkhandler.ChunkHandler.CHUNK_ORDER;
import static com.android.ddmlib.internal.jdwp.chunkhandler.HandleProfiling.CHUNK_MPRQ;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.internal.jdwp.JdwpProxyClient;
import com.android.ddmlib.internal.jdwp.chunkhandler.HandleHello;
import com.android.ddmlib.internal.jdwp.chunkhandler.HandleProfiling;
import com.android.ddmlib.internal.jdwp.chunkhandler.JdwpPacket;
import java.nio.ByteBuffer;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class ClientInitializationInterceptorTest {

    @Test
    public void packetFilterIgnoresUnknownPackets() throws Exception {
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
    public void cachedPacketsReplyWithCachedData() throws Exception {
        ClientInitializationInterceptor interceptor = new ClientInitializationInterceptor();
        JdwpProxyClient mock = mock(JdwpProxyClient.class);
        ArgumentCaptor<byte[]> writeData = ArgumentCaptor.forClass(byte[].class);
        int payload = 0xBEEF;
        JdwpPacket requestA = makePacket(HandleHello.CHUNK_HELO);
        JdwpPacket requestB = makePacket(HandleHello.CHUNK_HELO, false, 1234);
        // Round trip and cache HELO Packet
        assertThat(interceptor.filterToDevice(mock, requestA)).isFalse();
        assertThat(
                        interceptor.filterToClient(
                                mock, makePacket(HandleHello.CHUNK_HELO, true, payload)))
                .isTrue();
        verify(mock, times(1)).write(writeData.capture(), anyInt());
        verifyDataPayload(writeData.getValue(), payload, requestA.getId());

        // Request a 2nd packet expecting back the cached value.
        assertThat(interceptor.filterToDevice(mock, requestB)).isTrue();
        verify(mock, times(2)).write(writeData.capture(), anyInt());
        verifyDataPayload(writeData.getAllValues().get(1), payload, requestB.getId());
    }

    @Test
    public void nonCachedPacketsReplyFromDeviceOnly() throws Exception {
        ClientInitializationInterceptor interceptor = new ClientInitializationInterceptor();
        JdwpProxyClient mock = mock(JdwpProxyClient.class);
        ArgumentCaptor<byte[]> writeData = ArgumentCaptor.forClass(byte[].class);
        int payload = 0xBEEF;
        JdwpPacket requestA = makePacket(CHUNK_MPRQ);
        JdwpPacket requestB = makePacket(CHUNK_MPRQ, false, 1234);
        // Round trip MPRQ expecting it to not be cached.
        assertThat(interceptor.filterToDevice(mock, requestA)).isFalse();
        assertThat(interceptor.filterToClient(mock, makePacket(CHUNK_MPRQ, true, payload)))
                .isTrue();
        verify(mock, times(1)).write(writeData.capture(), anyInt());
        verifyDataPayload(writeData.getValue(), payload, requestA.getId());

        // Request a 2nd packet expecting it to be sent to the device.
        assertThat(interceptor.filterToDevice(mock, requestB)).isFalse();
    }

    @Test
    public void replyPacketIdMatchesRequestPacketId() throws Exception {
        ClientInitializationInterceptor interceptor = new ClientInitializationInterceptor();
        JdwpProxyClient mock = mock(JdwpProxyClient.class);
        ArgumentCaptor<byte[]> writeData = ArgumentCaptor.forClass(byte[].class);
        int payload = 0xBEEF;
        JdwpPacket requestPacket = makePacket(HandleHello.CHUNK_HELO);
        JdwpPacket replyPacket = makePacket(HandleHello.CHUNK_HELO, true, payload);
        assertThat(requestPacket.getId()).isNotEqualTo(replyPacket.getId());
        // Round trip HELO Packet.
        assertThat(interceptor.filterToDevice(mock, requestPacket)).isFalse();
        assertThat(interceptor.filterToClient(mock, replyPacket)).isTrue();
        // Capture reply packet and validate Id
        verify(mock, times(1)).write(writeData.capture(), anyInt());
        verifyDataPayload(writeData.getValue(), payload, requestPacket.getId());
    }

    private void verifyDataPayload(byte[] writeData, int payload, int replyId) {
        ByteBuffer buffer = ByteBuffer.wrap(writeData);
        buffer.order(CHUNK_ORDER);
        buffer.position(buffer.limit()); // need to set position so find packet has proper size.
        JdwpPacket sentPacket = JdwpPacket.findPacket(buffer);
        assertThat(sentPacket.getId()).isEqualTo(replyId);
        ByteBuffer bufferPayload = sentPacket.getPayload();
        // 0 = type
        // 4 = length of payload
        assertThat(bufferPayload.getInt(0x08)).isEqualTo(payload);
    }

    @Test
    public void replyPacketsAreFiltered() throws Exception {
        ClientInitializationInterceptor interceptor = new ClientInitializationInterceptor();
        JdwpProxyClient mock = mock(JdwpProxyClient.class);
        int payload = 0xBEEF;
        // HELO is filtered on the round trip
        assertThat(interceptor.filterToDevice(mock, makePacket(HandleHello.CHUNK_HELO))).isFalse();
        assertThat(
                        interceptor.filterToClient(
                                mock, makePacket(HandleHello.CHUNK_HELO, true, payload)))
                .isTrue();
        // FEAT is filtered on the round trip.
        assertThat(interceptor.filterToDevice(mock, makePacket(HandleHello.CHUNK_FEAT))).isFalse();
        assertThat(
                        interceptor.filterToClient(
                                mock, makePacket(HandleHello.CHUNK_FEAT, true, payload)))
                .isTrue();
        // MPRQ is filtered on the round trip.
        assertThat(interceptor.filterToDevice(mock, makePacket(HandleProfiling.CHUNK_MPRQ)))
                .isFalse();
        assertThat(
                        interceptor.filterToClient(
                                mock, makePacket(HandleProfiling.CHUNK_MPRQ, true, payload)))
                .isTrue();
        // Non reply packets are not filtered.
        assertThat(interceptor.filterToClient(mock, makePacket(HandleHello.CHUNK_HELO))).isFalse();
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
