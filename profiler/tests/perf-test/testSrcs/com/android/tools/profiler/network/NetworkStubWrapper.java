/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.profiler.network;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.profiler.PerfDriver;
import com.android.tools.profiler.TestUtils;
import com.android.tools.profiler.proto.*;
import com.android.tools.profiler.proto.Common.Session;
import com.android.tools.profiler.proto.NetworkProfiler.HttpDetailsRequest.Type;
import com.android.tools.profiler.proto.NetworkServiceGrpc.NetworkServiceBlockingStub;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Wrapper of stub calls that is shared among tests. */
final class NetworkStubWrapper {
    private final NetworkServiceBlockingStub myNetworkStub;

    NetworkStubWrapper(NetworkServiceBlockingStub networkStub) {
        myNetworkStub = networkStub;
    }

    /**
     * Fetch an http range that have at least one element in it. This method will block until a
     * non-empty range is returned.
     */
    NetworkProfiler.HttpRangeResponse getNonEmptyHttpRange(Session session) {
        return TestUtils.waitForAndReturn(
                getHttpRangeSupplier(session), res -> res.getDataList().size() > 0);
    }

    Supplier<NetworkProfiler.HttpRangeResponse> getHttpRangeSupplier(Session session) {
        return () ->
                myNetworkStub.getHttpRange(
                        NetworkProfiler.HttpRangeRequest.newBuilder()
                                .setSession(session)
                                .setStartTimestamp(0L)
                                .setEndTimestamp(Long.MAX_VALUE)
                                .build());
    }

    NetworkProfiler.HttpDetailsResponse getHttpDetails(long connId, Type type) {
        return myNetworkStub.getHttpDetails(
                NetworkProfiler.HttpDetailsRequest.newBuilder()
                        .setConnId(connId)
                        .setType(type)
                        .build());
    }

    /**
     * Returns payload id after repeatedly fetch it until it is non-empty or time out, otherwise,
     * returns empty string. Because there is some time gap between connection tracking and payload
     * track complete, it need some time to set the payload id.
     */
    String getPayloadId(final long connectionId, final Type type) {
        final Supplier<String> getPayloadId =
                () -> {
                    NetworkProfiler.HttpDetailsResponse result = getHttpDetails(connectionId, type);
                    return type == Type.RESPONSE_BODY
                            ? result.getResponseBody().getPayloadId()
                            : result.getRequestBody().getPayloadId();
                };
        TestUtils.waitFor(() -> !getPayloadId.get().isEmpty());
        return getPayloadId.get();
    }

    static Map<Long, List<Common.Event>> getHttpEvents(
            TransportServiceGrpc.TransportServiceBlockingStub stub,
            PerfDriver perfDriver,
            String activityName,
            String methodName,
            String expectedResponse,
            int expectedConnections)
            throws Exception {
        Map<Long, List<Common.Event>> httpEvents = new LinkedHashMap<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch stopLatch = new CountDownLatch(1);
        new Thread(
                        () -> {
                            Iterator<Common.Event> events =
                                    stub.getEvents(Transport.GetEventsRequest.getDefaultInstance());
                            startLatch.countDown();
                            long connectionCount = 0;
                            while (events.hasNext()) {
                                Common.Event event = events.next();
                                // capture the first event.
                                if (event.getKind() == Common.Event.Kind.NETWORK_HTTP_CONNECTION
                                        || event.getKind()
                                                == Common.Event.Kind.NETWORK_HTTP_THREAD) {
                                    List<Common.Event> connectionEvents =
                                            httpEvents.get(event.getGroupId());
                                    if (connectionEvents == null) {
                                        connectionEvents = new ArrayList<>();
                                        httpEvents.put(event.getGroupId(), connectionEvents);
                                    }
                                    connectionEvents.add(event);

                                    if (event.getIsEnded()) {
                                        connectionCount++;
                                        if (connectionCount >= expectedConnections) {
                                            break;
                                        }
                                    }
                                }
                            }
                            stopLatch.countDown();
                        })
                .start();
        // Wait for the thread to start to make sure we catch the event in the streaming rpc.
        startLatch.await(30, TimeUnit.SECONDS);

        perfDriver.getFakeAndroidDriver().triggerMethod(activityName, methodName);
        assertThat(perfDriver.getFakeAndroidDriver().waitForInput(expectedResponse)).isTrue();
        // Wait for the connection event to come through.
        stopLatch.await();

        return httpEvents;
    }
}
