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
package com.android.tools.tracer;

import java.util.List;

/**
 * Public API to trace events. Unless the application is instrumented, these calls are all no-ops.
 * To enable tracing add:
 *
 * <p>-javaagent=-javaagent:$WORKSPACE/bazel-genfiles/tools/base/tracer/trace_agent.jar
 *
 * <p>to your start up options. Tracing can be performed via this API, via the @Trace annotation, or
 * via a profile file. See tools/base/tracer/README.md for more information.
 */
public class Trace implements AutoCloseable {

    private static Trace INSTANCE = new Trace();

    /**
     * Begins a trace event with the given text.
     *
     * @return a closable object to ease ending the event.
     */
    public static Trace begin(String text) {
        return INSTANCE;
    }

    /** Ends the currently open event by this pid/tid. */
    public static void end() {}

    /** Makes sure all the events are written to the backing file. */
    public static void flush() {}

    /** Truncates the backing file to zero and starts afresh. */
    public static void start() {}

    /** Begins a custom event with a fixed time. */
    public static void begin(long pid, long tid, long ns, String text) {}

    /** Ends a custom event with a fixed time. */
    public static void end(long pid, long tid, long ns) {}

    /**
     * Adds the needed arguments to attach the same agent that is being used to trace. If no agent
     * is attached it does nothing.
     *
     * <p>This is useful to propagate the agent down to spawned VMs.
     */
    public static void addVmArgs(List<String> args) {}

    @Override
    public void close() {
        end();
    }
}
