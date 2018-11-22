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

import java.lang.instrument.Instrumentation;

public class TraceAgent {
    public static void premain(String agentArgs, Instrumentation inst) {
        TraceProfile profile = new TraceProfile(agentArgs);
        inst.addTransformer(new TraceTransformer(profile));
        Tracer.profile = profile;
        if (profile.traceAgent()) {
            traceVMLifetime();
        }
    }

    private static void traceVMLifetime() {
        Tracer.begin(Tracer.pid, 0, System.nanoTime(), "TraceAgent");
        Runtime.getRuntime()
                .addShutdownHook(new Thread(() -> Tracer.end(Tracer.pid, 0, System.nanoTime())));
    }
}
