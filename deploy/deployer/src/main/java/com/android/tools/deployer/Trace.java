/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.deployer;

import com.android.tools.deploy.proto.Deploy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.TimeUnit;

// TODO: Instead of using synchronized method, use ThreadLocal
public class Trace implements AutoCloseable {

    private static Trace INSTANCE = new Trace();

    interface TraceConsumer {
        void onStart();

        void onBegin(Event event);

        void onEnd(Event event);

        void onInfo(Event event);

        void onFinish();
    }

    enum Type {
        BEGIN,
        END,
        INFO
    }

    private static long enabledUntil = System.nanoTime();
    private static boolean enabled() {
        return (enabledUntil - System.nanoTime()) > 0;
    }

    static class Event {
        public Type type;
        public long pid;
        public long tid;
        public long timestamp_ns;
        public String text;
    }

    private static List<Event> events = new ArrayList<>();
    private static HashMap<Long, Stack<Event>> threadBegins = new HashMap<>();

    public static synchronized void reset() {
        if (!enabled()) {
            return;
        }
        events.clear();
        threadBegins.clear();
    }

    private static synchronized void closeOutstandingPhases() {
        for (Long threadId : threadBegins.keySet()) {
            Stack<Event> stack = threadBegins.get(threadId);
            while (!stack.isEmpty()) {
                Event lastBegin = stack.pop();
                Event event = new Event();
                event.pid = lastBegin.pid;
                event.tid = lastBegin.tid;
                event.timestamp_ns = System.nanoTime();
                event.type = Type.END;
                events.add(event);
            }
        }
    }

    private static synchronized Stack<Event> getCurrentThreadBeginStack() {
        long tid = Thread.currentThread().getId();
        if (!threadBegins.containsKey(tid)) {
            threadBegins.put(tid, new Stack<>());
        }
        return threadBegins.get(tid);
    }

    public static synchronized Trace begin(String text) {
        if (!enabled()) {
            // Java try-with-resources allows null closeables.
            return null;
        }
        Event event = new Event();
        event.pid = 0;
        event.tid = Thread.currentThread().getId();
        event.text = text;
        event.timestamp_ns = System.nanoTime();
        event.type = Type.BEGIN;
        events.add(event);
        getCurrentThreadBeginStack().push(event);
        return INSTANCE;
    }

    public static synchronized void end() {
        if (!enabled()) {
            return;
        }
        if (getCurrentThreadBeginStack().isEmpty()) {
            // This is an error.
            return;
        }
        Event matchingBegin = getCurrentThreadBeginStack().pop();
        Event event = new Event();
        event.pid = matchingBegin.pid;
        event.tid = matchingBegin.tid;
        event.timestamp_ns = System.nanoTime();
        event.type = Type.END;
        events.add(event);
    }

    @Override
    public void close() {
        end();
    }

    public static synchronized void endtWithRemoteEvents(List<Deploy.Event> remoteEvents) {
        if (!enabled()) {
            return;
        }

        Stack<Event> eventsStack = getCurrentThreadBeginStack();
        if (eventsStack.isEmpty()) {
            // This is an error, this method was called on this Thread without a call to
            // begin() first.
            return;
        }

        // We need to rebase the remote timestamp on the current timeline. We will
        // use the first remote even timestamp as remote t=0 and generate deltas
        // from each remote timetamps. All event are added to the local timeline,
        // floated within the phase they were contained into.
        Event matchingBegin = eventsStack.peek();
        long duration = System.nanoTime() - matchingBegin.timestamp_ns;

        long startRemoteNs = Long.MAX_VALUE;
        long endRemoteNs = Long.MIN_VALUE;

        for (Deploy.Event remoteEvent : remoteEvents) {
            if (remoteEvent.getTimestampNs() > endRemoteNs) {
                endRemoteNs = remoteEvent.getTimestampNs();
            }
            if (remoteEvent.getTimestampNs() < startRemoteNs) {
                startRemoteNs = remoteEvent.getTimestampNs();
            }
        }

        long remoteDuration = endRemoteNs - startRemoteNs;
        if (remoteDuration > duration) {
            Event event = new Event();
            event.pid = 0;
            event.tid = Thread.currentThread().getId();
            event.text =
                    "Remote events could not be integrated (duration too long:"
                            + (remoteDuration - duration)
                            + ")";
            event.timestamp_ns = System.nanoTime();
            event.type = Type.INFO;
            events.add(event);
            return;
        }
        long floatOffset = (duration - remoteDuration) / 2;

        for (Deploy.Event remoteEvent : remoteEvents) {
            Event event = new Event();
            event.timestamp_ns =
                    matchingBegin.timestamp_ns
                            + floatOffset
                            + (remoteEvent.getTimestampNs() - startRemoteNs);
            event.text = remoteEvent.getText();
            event.tid = remoteEvent.getTid();
            event.pid = remoteEvent.getPid();
            if (remoteEvent.getType() == Deploy.Event.Type.TRC_BEG) {
                event.type = Type.BEGIN;
                events.add(event);
            }
            if (remoteEvent.getType() == Deploy.Event.Type.TRC_END) {
                event.type = Type.END;
                events.add(event);
            }
        }

        end();
    }

    public static synchronized void start() {
        // Record events for a maximum of two minutes, even if finish() is not called.
        enabledUntil = System.nanoTime() + TimeUnit.MINUTES.toNanos(2);
        reset();
    }

    public static synchronized void finish() {
        if (!enabled()) {
            return;
        }
        closeOutstandingPhases();
        enabledUntil = System.nanoTime();
    }

    public static synchronized void consume(TraceConsumer consumer) {
        if (!enabled()) {
            return;
        }
        finish();
        consumer.onStart();
        for (Event event : events) {
            switch (event.type) {
                case BEGIN:
                    consumer.onBegin(event);
                    break;
                case END:
                    consumer.onEnd(event);
                    break;
                case INFO:
                    consumer.onInfo(event);
                    break;
            }
        }
        consumer.onFinish();
        reset();
    }
}
