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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import com.android.ddmlib.internal.jdwp.JdwpProxyClient;
import org.junit.Test;

public class NoReplyPacketInterceptorTest {

    @Test
    public void packetsCachedBeforeHandshakeComplete() throws Exception {
        NoReplyPacketInterceptor interceptor = new NoReplyPacketInterceptor();
        JdwpProxyClient mock = mock(JdwpProxyClient.class);
        assertThat(interceptor.filterToClient(mock, makePacket("TEST"))).isTrue();
        assertThat(interceptor.filterToClient(mock, makePacket("TST1"))).isTrue();
        assertThat(interceptor.getCachedPackets()).hasSize(2);
        verify(mock, times(0)).write(any(), anyInt());
    }

    @Test
    public void cacheSentWhenHandshakeComplete() throws Exception {
        NoReplyPacketInterceptor interceptor = new NoReplyPacketInterceptor();
        JdwpProxyClient mock = mock(JdwpProxyClient.class);
        when(mock.isConnected()).thenReturn(true);
        assertThat(interceptor.filterToClient(mock, makePacket("TEST"))).isTrue();
        assertThat(interceptor.filterToClient(mock, makePacket("TST1"))).isTrue();
        assertThat(interceptor.getClientsSentCacheTo()).hasSize(0);
        verify(mock, times(0)).write(any(), anyInt());
        when(mock.isHandshakeComplete()).thenReturn(true);
        assertThat(interceptor.filterToClient(mock, makePacket("TST1"))).isFalse();
        assertThat(interceptor.getClientsSentCacheTo()).hasSize(1);
        verify(mock, times(2)).write(any(), anyInt());
    }
}
