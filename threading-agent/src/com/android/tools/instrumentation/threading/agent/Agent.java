/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tools.instrumentation.threading.agent;

import java.lang.instrument.Instrumentation;
import java.util.logging.Logger;

/**
 * The main entry point of the threading annotations agent.
 *
 * <p>Instruments code by installing the {@link Transformer} that inject runtime checks for
 * threading annotations.
 */
public class Agent {

    private static final Logger LOGGER = Logger.getLogger(Agent.class.getName());

    static Instrumentation instrumentation;

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        Agent.instrumentation = instrumentation;
        instrumentation.addTransformer(new Transformer(AnnotationMappings.create()));
        LOGGER.info("Threading agent has been loaded.");
    }
}
