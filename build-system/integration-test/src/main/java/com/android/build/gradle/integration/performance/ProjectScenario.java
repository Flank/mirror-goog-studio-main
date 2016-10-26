/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.performance;

import com.google.wireless.android.sdk.gradlelogging.proto.Logging.GradleBenchmarkResult.Flags;
import java.util.function.Consumer;

/**
 * Project scenarios for performance tests set-up.
 */
public enum ProjectScenario {
    NORMAL(flags -> {}),
    DEX_OUT_OF_PROCESS(flags -> flags.setDexInProcess(Flags.DexInProcess.DEX_OUT_OF_PROCESS)),
    NATIVE_MULTIDEX(flags -> flags.setMultiDex(Flags.MultiDexMode.NATIVE)),
    LEGACY_MULTIDEX(flags -> flags.setMultiDex(Flags.MultiDexMode.LEGACY)),
    JACK_ON(flags -> flags.setCompiler(Flags.Compiler.JACK)),
    JACK_OUT_OF_PROCESS(flags -> {
        flags.setCompiler(Flags.Compiler.JACK);
        // Flag will be renamed to "InProcess" once data is migrated.
        flags.setDexInProcess(Flags.DexInProcess.DEX_OUT_OF_PROCESS);})
    ;

    private final Flags flags;

    ProjectScenario(Consumer<Flags.Builder> flagsAction) {
        Flags.Builder builder = Flags.newBuilder();
        // Default configuration goes here.
        // Note: Because of conflict in renaming fields in the logging.proto (old name in old data,
        // new name in new data), Dremel cannot read renamed fields. "DexInProcess" flag will be
        // changed to "InProcess" and the old data will be migrated to match the new schema, but it
        // will have to be done in a later CL.
        // Both Jack and Dex run in process by default, if they have enough memory available.
        builder.setDexInProcess(Flags.DexInProcess.DEX_IN_PROCESS);
        flagsAction.accept(builder);
        this.flags = builder.build();
    }

    public Flags getFlags() {
        return flags;
    }
}
