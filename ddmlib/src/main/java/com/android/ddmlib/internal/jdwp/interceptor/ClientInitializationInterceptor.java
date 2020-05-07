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

import static com.android.ddmlib.internal.jdwp.chunkhandler.ChunkHandler.CHUNK_ORDER;
import static com.android.ddmlib.internal.jdwp.chunkhandler.HandleHeap.CHUNK_HPIF;
import static com.android.ddmlib.internal.jdwp.chunkhandler.HandleHeap.CHUNK_REAQ;
import static com.android.ddmlib.internal.jdwp.chunkhandler.HandleHello.CHUNK_FEAT;
import static com.android.ddmlib.internal.jdwp.chunkhandler.HandleHello.CHUNK_HELO;
import static com.android.ddmlib.internal.jdwp.chunkhandler.HandleProfiling.CHUNK_MPRQ;
import static com.android.ddmlib.internal.jdwp.chunkhandler.HandleWait.CHUNK_WAIT;

import com.android.annotations.NonNull;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.internal.jdwp.JdwpProxyClient;
import com.android.ddmlib.internal.jdwp.chunkhandler.JdwpPacket;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Interceptor that is responsible for caching specific request/reply packets.
 *
 * Because the device manages internal state specific packets being sent at an unexpected time may cause undefined behavior. This interceptor
 * listens for various request from clients and either forwards the request on to the device or replies with a cached response previously
 * captured from device.
 *
 * This allows the device to talk to the proxy as a single client. While at the same time allowing multiple instances of DDMLIB to treat the proxy
 * as a single device client. Example
 * DDMLIB_1 -> HELO -> ClientInterceptor (First time seeing HELO) -> HELO -> DEVICE
 * DDMLIB_2 -> HELO -> ClientInterceptor (I am waiting on a response do nothing)
 * DEVICE -> HELO -> ClientInterceptor (2 instances are waiting for a response)
 *                                          -> HELO -> DDMLIB_1
 *                                          -> HELO -> DDMLIB_2
 * ... Some time later ...
 * DDMLIB_3 -> HELO -> ClientInterceptor (I have a response already send cache) -> HELO -> DDMLIB_3
 *
 * Some things to note in the example above, the device only sees HELO 1 time no matter how many instances connect.
 * Responses to all request tracked by this interceptor are only sent back as replies to instances that request them.
 */
public class ClientInitializationInterceptor implements Interceptor {
    private final Set<Integer> mPacketFilter = new HashSet<>();
    private final Map<Integer, byte[]> cachedPackets = new HashMap<>();
    private final HashMap<Integer, Set<JdwpProxyClient>> pendingPackets = new HashMap<>();

    public ClientInitializationInterceptor() {
        mPacketFilter.add(CHUNK_HELO);
        mPacketFilter.add(CHUNK_FEAT);
        mPacketFilter.add(CHUNK_MPRQ);
        mPacketFilter.add(CHUNK_HPIF);
        mPacketFilter.add(CHUNK_REAQ);
        mPacketFilter.add(CHUNK_WAIT);
    }
    @Override
    public boolean filterToDevice(@NonNull JdwpProxyClient from, @NonNull JdwpPacket packet) throws IOException, TimeoutException {
        if (packet.isEmpty() || packet.isError() || packet.getLength() < JdwpPacket.JDWP_HEADER_LEN + 4) {
            return false;
        }
        ByteBuffer payload = packet.getPayload();
        int type = payload.getInt();
        if (!mPacketFilter.contains(type)) {
            return false;
        }
        if (cachedPackets.containsKey(type)) {
            byte[] send = cachedPackets.get(type);
            from.write(send, send.length);
            return true;
        }
        boolean alreadyPending = pendingPackets.containsKey(type);
        pendingPackets.computeIfAbsent(type, (key) -> new HashSet<>()).add(from);
        return alreadyPending;
    }

    @Override
    public boolean filterToClient(@NonNull JdwpProxyClient to, @NonNull JdwpPacket packet) {
        // Can't filter on DMS packets since REAQ is not one of them.
        if (packet.isEmpty() || packet.isError() || packet.getLength() < JdwpPacket.JDWP_HEADER_LEN + 4) {
            return false;
        }

        ByteBuffer payload = packet.getPayload();
        int type = payload.getInt();
        if (!pendingPackets.containsKey(type)) {
            return false;
        }
        cachedPackets.computeIfAbsent(type, key ->
        {
            ByteBuffer buffer = ByteBuffer.allocate(packet.getLength());
            buffer.order(CHUNK_ORDER);
            packet.move(buffer);
            return buffer.array();
        });
        boolean filterPacket = !pendingPackets.get(type).remove(to) || !to.isHandshakeComplete();
        if (pendingPackets.get(type).isEmpty()) {
            pendingPackets.remove(type);
        }
        return filterPacket;
    }
}
