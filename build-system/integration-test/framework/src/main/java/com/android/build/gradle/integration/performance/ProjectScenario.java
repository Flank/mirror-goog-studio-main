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

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor;
import com.android.build.gradle.options.BooleanOption;
import com.google.wireless.android.sdk.gradlelogging.proto.Logging.GradleBenchmarkResult.Flags;
import java.util.function.Consumer;

/**
 * Project scenarios for performance tests set-up.
 *
 * <p>This is a flat list to explicitly include all the combinations that are
 * actually tested.
 */
public enum ProjectScenario {
    NORMAL(flags -> {}),
    DEX_ARCHIVE_MONODEX(
            flags -> {
                flags.setCompiler(Flags.Compiler.DEX_ARCHIVE);
                flags.setMultiDex(Flags.MultiDexMode.NO_MULTIDEX);
            }),
    DEX_ARCHIVE_NATIVE_MULTIDEX(
            flags -> {
                flags.setCompiler(Flags.Compiler.DEX_ARCHIVE);
                flags.setMultiDex(Flags.MultiDexMode.NATIVE);
            }),
    DEX_ARCHIVE_LEGACY_MULTIDEX(
            flags -> {
                flags.setCompiler(Flags.Compiler.DEX_ARCHIVE);
                flags.setMultiDex(Flags.MultiDexMode.LEGACY);
            }),
    DEX_ARCHIVE_MONODEX_J8(
            flags -> {
                flags.setCompiler(Flags.Compiler.DEX_ARCHIVE);
                flags.setMultiDex(Flags.MultiDexMode.NO_MULTIDEX);
                flags.setJava8LangSupport(Flags.Java8LangSupport.DESUGAR_TOOL);
            }),
    D8_MONODEX_J8(
            flags -> {
                flags.setCompiler(Flags.Compiler.D8);
                flags.setJava8LangSupport(Flags.Java8LangSupport.DESUGAR_TOOL);
            }),
    D8_NATIVE_MULTIDEX(
            flags -> {
                flags.setCompiler(Flags.Compiler.D8);
                flags.setMultiDex(Flags.MultiDexMode.NATIVE);
            }),
    D8_LEGACY_MULTIDEX(
            flags -> {
                flags.setCompiler(Flags.Compiler.D8);
                flags.setMultiDex(Flags.MultiDexMode.LEGACY);
            }),
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

    public boolean useD8() {
        return flags.getCompiler() == Flags.Compiler.D8;
    }

    /**
     * The responsibility of this method is to ensure that a Gradle executor is configured in a way
     * that accurately represents the flags set on the current ProjectScenario.
     */
    public GradleTaskExecutor configureExecutor(GradleTaskExecutor executor) {
        return executor.withEnableInfoLogging(false)
                .with(BooleanOption.ENABLE_INTERMEDIATE_ARTIFACTS_CACHE, false)
                .with(BooleanOption.ENABLE_D8, useD8())
                .with(BooleanOption.ENABLE_D8_DESUGARING, useD8());
    }
}
