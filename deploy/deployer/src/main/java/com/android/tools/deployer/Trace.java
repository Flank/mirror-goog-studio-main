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

// TODO: Instead of using synchronized method, use ThreadLocal
public class Trace {

    interface TraceConsumer {
        void onStart();

        void onBegin(Event event);

        void onEnd(Event event);

        void onFinish();
    }

    enum Type {
        BEGIN,
        END
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

    public static synchronized void begin(String text) {
        Event event = new Event();
        event.pid = 0;
        event.tid = Thread.currentThread().getId();
        event.text = text;
        event.timestamp_ns = System.nanoTime();
        event.type = Type.BEGIN;
        events.add(event);
        getCurrentThreadBeginStack().push(event);
    }

    public static synchronized void end() {
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

    public static synchronized void endtWithRemoteEvents(List<Deploy.Event> remoteEvents) {
        Event matchingBegin = getCurrentThreadBeginStack().peek();

        if (matchingBegin == null) {
            // This is an error.
            return;
        }

        // We need to rebase the remote timestamp on the current timeline. We will
        // use the first remote even timestamp as remote t=0 and generate deltas
        // from each remote timetamps. All event are added to the local timeline,
        // floated within the phase they were contained into.
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
            throw new DeployerException(
                    "Remote duration longer than local ("
                            + (remoteDuration - duration) / 1000000
                            + "ms).");
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

    public static synchronized void finish() {
        closeOutstandingPhases();
    }

    public static synchronized void consume(TraceConsumer consumer) {
        finish();
        consumer.onStart();
        for (Event event : events) {
            if (event.type == Type.BEGIN) {
                consumer.onBegin(event);
            }
            if (event.type == Type.END) {
                consumer.onEnd(event);
            }
        }
        consumer.onFinish();
        reset();
    }
}
