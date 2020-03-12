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
package com.android.ddmlib;

import static com.android.ddmlib.internal.jdwp.chunkhandler.JdwpPacket.JDWP_HEADER_LEN;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.android.ddmlib.internal.ClientImpl;
import com.android.ddmlib.internal.jdwp.chunkhandler.ChunkHandler;
import com.android.ddmlib.internal.jdwp.chunkhandler.HandleViewDebug;
import com.android.ddmlib.internal.jdwp.chunkhandler.JdwpPacket;
import com.android.ddmlib.jdwp.JdwpInterceptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.mockito.ArgumentMatcher;

public class FakeClientBuilder {
    ClientImpl client = mock(ClientImpl.class);

    public FakeClientBuilder registerResponse(
            ArgumentMatcher<JdwpPacket> request, int responseType, ByteBuffer payload)
            throws IOException {
        payload.rewind();
        int size = payload.remaining();
        ByteBuffer rawBuf = ChunkHandler.allocBuffer(size + JDWP_HEADER_LEN);
        JdwpPacket returnPacket = new JdwpPacket(rawBuf);
        ByteBuffer buf = ChunkHandler.getChunkDataBuf(rawBuf);
        buf.put(payload);
        ChunkHandler.finishChunkPacket(returnPacket, responseType, size);

        doAnswer(
                        invocation ->
                                invocation
                                        .<JdwpInterceptor>getArgument(1)
                                        .intercept(client, returnPacket))
                .when(client)
                .send(argThat(request), any());
        return this;
    }

    public Client build() throws IOException {
        // Mock listViewRoots to call static function directly since we are using a mock client but
        // expecting HandleViewDebug functionality
        // from various test.
        doAnswer(
                        invocation -> {
                            HandleViewDebug.listViewRoots(client, invocation.getArgument(0));
                            return null;
                        })
                .when(client)
                .listViewRoots(any(DebugViewDumpHandler.class));
        return client;
    }
}
