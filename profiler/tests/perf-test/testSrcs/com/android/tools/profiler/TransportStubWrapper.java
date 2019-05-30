/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.profiler;

import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class TransportStubWrapper {
    private final TransportServiceGrpc.TransportServiceBlockingStub myTransportStub;

    public TransportStubWrapper(TransportServiceGrpc.TransportServiceBlockingStub transportStub) {
        myTransportStub = transportStub;
    }

    /**
     * Wraps {@link
     * TransportServiceGrpc.TransportServiceBlockingStub#getEvents(Transport.GetEventsRequest)}
     * streaming RPC.
     *
     * @param expectedEventCount stop streaming event after this many events.
     * @param eventFilter count event only when the predicate evaluates to true.
     * @param actionToTrigger call this function after streaming RPC has started.
     * @return group ID to event list mapping.
     */
    public Map<Long, List<Common.Event>> getEvents(
            int expectedEventCount,
            Predicate<Common.Event> eventFilter,
            Consumer<Void> actionToTrigger)
            throws Exception {
        Map<Long, List<Common.Event>> events = new LinkedHashMap<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch stopLatch = new CountDownLatch(1);
        new Thread(
                        () -> {
                            Iterator<Common.Event> iterator =
                                    myTransportStub.getEvents(
                                            Transport.GetEventsRequest.getDefaultInstance());
                            startLatch.countDown();
                            int eventCount = 0;
                            while (iterator.hasNext()) {
                                Common.Event event = iterator.next();
                                if (eventFilter.test(event)) {
                                    events.computeIfAbsent(
                                                    event.getGroupId(),
                                                    groupId -> new ArrayList<>())
                                            .add(event);
                                    ++eventCount;
                                    if (eventCount >= expectedEventCount) {
                                        break;
                                    }
                                }
                            }
                            stopLatch.countDown();
                        })
                .start();
        // Wait for the thread to start to make sure we catch the event in the streaming rpc.
        startLatch.await();

        actionToTrigger.accept(null);

        // Wait for the events to come through.
        stopLatch.await(30, TimeUnit.SECONDS);
        return events;
    }
}
