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

package com.android.tools.tracer.agent;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Tracer {
    private static final int SAVE_BATCH_SIZE = 40;

    public static TraceProfile profile;

    enum Type {
        BEGIN,
        END,
    }

    static class Event {
        public Type type;
        public long pid;
        public long tid;
        public long timestamp_ns;
        public String text;
    }

    private static ArrayList<Event> events;
    private static final ExecutorService writer;
    public static final int pid;

    static {
        events = new ArrayList<>();
        // When the VM terminates we do not want to block it waiting for our thread to terminate, so
        // we mark it as a daemon thread. If the events need to be flushed, then a flush marker should
        // be used instead.
        writer =
                Executors.newSingleThreadExecutor(
                        runnable -> {
                            Thread thread = new Thread(runnable);
                            thread.setDaemon(true);
                            return thread;
                        });
        String name = ManagementFactory.getRuntimeMXBean().getName();
        int ix = name.indexOf('@');
        int candidatePid = 0;
        if (ix != -1) {
            try {
                candidatePid = Integer.valueOf(name.substring(0, ix));
            } catch (NumberFormatException ignored) {
            }
        }
        pid = candidatePid;
    }

    private static synchronized void add(Event event) {
        events.add(event);
        // If this was the first event make sure the exector thread will try to write
        if (events.size() == 1) {
            writer.submit(Tracer::drain);
        }
    }

    private static synchronized ArrayList<Event> consumeEvents() {
        ArrayList<Event> old = events;
        events = new ArrayList<>(events.size());
        return old;
    }

    public static void add(Type type, String text) {
        add(type, pid, Thread.currentThread().getId(), System.nanoTime(), text);
    }

    public static void add(Type type, long pid, long tid, long ns, String text) {
        Event event = new Event();
        event.pid = pid;
        event.tid = tid;
        event.text = text;
        event.timestamp_ns = ns;
        event.type = type;
        add(event);
    }

    private static void drain() {
        ArrayList<Event> events = consumeEvents();
        writer.submit(() -> save(events));
    }

    /**
     * Saves all the events in the list to the output json file. This method will lock the file
     * while performing this operation.
     */
    private static void save(ArrayList<Event> events) {
        try (FileChannel fd =
                FileChannel.open(
                        Paths.get(profile.getOutputFile()),
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND,
                        StandardOpenOption.CREATE)) {
            fd.lock();
            long size = fd.size();
            if (size == 0) {
                fd.write(ByteBuffer.wrap(new byte[] {'[', '\n'}));
            }
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < events.size(); i++) {
                Event event = events.get(i);

                String s =
                        String.format(
                                "{\"ts\" : \"%d\", \"ph\" : \"%s\" , \"pid\" : \"%s\" , \"tid\" : \"%d\", \"name\" : \"%s\"},\n",
                                event.timestamp_ns / 1000,
                                event.type == Type.BEGIN ? "B" : "E",
                                event.pid,
                                event.tid,
                                event.text);
                builder.append(s);
                if ((i + 1) % SAVE_BATCH_SIZE == 0 || i == events.size() - 1) {
                    byte[] bytes = builder.toString().getBytes(UTF_8);
                    fd.write(ByteBuffer.wrap(bytes));
                    builder = new StringBuilder();
                }
            }
            fd.force(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Truncates the output file back to zero. */
    private static void truncate() {
        try (FileChannel fd =
                FileChannel.open(
                        Paths.get(profile.getOutputFile()),
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE)) {
            fd.lock();
            fd.position(0);
            fd.truncate(0);
            fd.force(true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unused") // Added via instrumentation
    public static void begin(String text) {
        add(Type.BEGIN, text);
    }

    @SuppressWarnings("unused") // Added via instrumentation
    public static void end() {
        add(Type.END, "");
    }

    @SuppressWarnings("unused") // Added via instrumentation
    public static void flush() {
        CountDownLatch latch = new CountDownLatch(1);
        drain();
        writer.submit(latch::countDown);
        try {
            latch.await();
        } catch (InterruptedException ignored) {
        }
    }

    @SuppressWarnings("unused") // Added via instrumentation
    public static void begin(long pid, long tid, long ns, String text) {
        add(Type.BEGIN, pid, tid, ns, text);
    }

    @SuppressWarnings("unused") // Added via instrumentation
    public static void end(long pid, long tid, long ns) {
        add(Type.END, pid, tid, ns, "");
    }

    @SuppressWarnings("unused") // Added via instrumentation
    public static void start() {
        drain();
        writer.submit(Tracer::truncate);
    }

    @SuppressWarnings("unused") // Added via instrumentation
    public static void addVmArgs(List<String> args) {
        String jvmArgs = profile.getJvmArgs();
        if (!jvmArgs.isEmpty()) {
            args.add(jvmArgs);
        }
    }
}
