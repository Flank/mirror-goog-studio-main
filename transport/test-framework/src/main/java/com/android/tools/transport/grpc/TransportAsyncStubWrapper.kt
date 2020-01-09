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
package com.android.tools.transport.grpc

import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport
import com.android.tools.profiler.proto.TransportServiceGrpc
import com.android.tools.profiler.proto.TransportServiceGrpc.TransportServiceStub
import io.grpc.Context
import io.grpc.stub.StreamObserver
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Predicate

/**
 * A wrapping class that provides useful utility methods on top of [TransportServiceGrpc.TransportServiceStub]
 *
 * This wrapper is useful for testing against transport APIs that stream, where you have to wait
 * for multiple events to arrive. You may also want to see [TransportStubWrapper] if you are
 * testing blocking APIs.
 */
class TransportAsyncStubWrapper(val transportStub: TransportServiceStub) {
    companion object {
        /**
         * Convenience method for creating a wrapper when you don't need to create the underlying
         * stub yourself.
         */
        @JvmStatic
        fun create(grpc: Grpc): TransportAsyncStubWrapper {
            return TransportAsyncStubWrapper(TransportServiceGrpc.newStub(grpc.channel))
        }
    }

    /**
     * A simpler API for fetching transport events via callbacks.
     *
     * @param shouldStop whether streaming should stop after seeing an event that matches the filter.
     * @param accept count event only when the predicate evaluates to true.
     * @param onStarted call this function after streaming RPC has started.
     * @return group ID to event list mapping.
     *
     * See also: [Grpc.createTransportAsyncStub]
     */
    fun getEvents(
            shouldStop: Predicate<Common.Event>,
            accept: Predicate<Common.Event>,
            onStarted: Runnable): Map<Long, MutableList<Common.Event>> {
        val events: MutableMap<Long, MutableList<Common.Event>> = LinkedHashMap()
        val stopLatch = CountDownLatch(1)
        val observer: StreamObserver<Common.Event> = object : StreamObserver<Common.Event> {
            override fun onNext(event: Common.Event) {
                if (accept.test(event)) {
                    events.computeIfAbsent(event.groupId) { ArrayList() }.add(event)
                    if (shouldStop.test(event)) {
                        stopLatch.countDown()
                    }
                }
            }

            override fun onCompleted() {}
            override fun onError(throwable: Throwable) {}
        }

        val context = Context.current().withCancellation()
        context.run {
            transportStub.getEvents(Transport.GetEventsRequest.getDefaultInstance(), observer)
            onStarted.run()
        }

        // Wait for the events to come through.
        stopLatch.await(30, TimeUnit.SECONDS)
        context.cancel(null)
        return events
    }

    /**
     * Like the main [getEvents] method but also uses a condition to stop after an expected
     * number of matching events are received.
     *
     * @param expectedEventCount stop streaming event after receiving this many events that match the filter.
     * @param eventFilter count event only when the predicate evaluates to true.
     * @param onStarted call this function after streaming RPC has started.
     * @return group ID to event list mapping.
     *
     * See also: [Grpc.createTransportAsyncStub]
     */
    fun getEvents(
            expectedEventCount: Int,
            accept: Predicate<Common.Event>,
            onStarted: Runnable): Map<Long, MutableList<Common.Event>> {
        val matchedEventCount = AtomicInteger(0)
        return getEvents(
                Predicate { evt ->
                    if (accept.test(evt)) {
                        matchedEventCount.incrementAndGet()
                    }
                    matchedEventCount.get() >= expectedEventCount
                },
                accept,
                onStarted)
    }
}
