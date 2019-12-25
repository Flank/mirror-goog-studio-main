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
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class TransportStubWrapper {
    private final TransportServiceGrpc.TransportServiceStub myTransportAsyncStub;

    public TransportStubWrapper(TransportServiceGrpc.TransportServiceStub transportStub) {
        myTransportAsyncStub = transportStub;
    }

    /**
     * Wraps {@link TransportServiceGrpc.TransportServiceStub#getEvents(Transport.GetEventsRequest, StreamObserver)}.
     *
     * @param stopPredicate whether streaming should stop after seeing an event that matches the filter.
     * @param eventFilter count event only when the predicate evaluates to true.
     * @param actionToTrigger call this function after streaming RPC has started.
     * @return group ID to event list mapping.
     */
    public Map<Long, List<Common.Event>> getEvents(
            Predicate<Common.Event> stopPredicate,
            Predicate<Common.Event> eventFilter,
            Consumer<Void> actionToTrigger)
            throws Exception {
        Map<Long, List<Common.Event>> events = new LinkedHashMap<>();
        CountDownLatch stopLatch = new CountDownLatch(1);

        StreamObserver<Common.Event> observer =
                new StreamObserver<Common.Event>() {
                    @Override
                    public void onNext(Common.Event event) {
                        if (eventFilter.test(event)) {
                            events.computeIfAbsent(event.getGroupId(), groupId -> new ArrayList<>())
                                    .add(event);
                            if (stopPredicate.test(event)) {
                                stopLatch.countDown();
                            }
                        }
                    }

                    @Override
                    public void onCompleted() {
                        // No-op
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        // No-op
                    }
                };

        Runnable getEventsRunable =
                () -> {
                    myTransportAsyncStub.getEvents(
                            Transport.GetEventsRequest.getDefaultInstance(), observer);
                    actionToTrigger.accept(null);
                };
        Context.CancellableContext context = Context.current().withCancellation();
        context.run(getEventsRunable);

        // Wait for the events to come through.
        stopLatch.await(30, TimeUnit.SECONDS);
        context.cancel(null);

        return events;
    }

    /**
     * Wraps {@link TransportServiceGrpc.TransportServiceStub#getEvents(Transport.GetEventsRequest, StreamObserver)}.
     *
     * @param expectedEventCount stop streaming event after receiving this many events that match the filter.
     * @param eventFilter count event only when the predicate evaluates to true.
     * @param actionToTrigger call this function after streaming RPC has started.
     * @return group ID to event list mapping.
     */
    public Map<Long, List<Common.Event>> getEvents(
            int expectedEventCount,
            Predicate<Common.Event> eventFilter,
            Consumer<Void> actionToTrigger)
            throws Exception {
        AtomicInteger matchedEventCount = new AtomicInteger(0);
        return getEvents(
                evt -> {
                    if (eventFilter.test(evt)) {
                        matchedEventCount.incrementAndGet();
                    }
                    return matchedEventCount.get() >= expectedEventCount;
                },
                eventFilter,
                actionToTrigger);
    }
}
