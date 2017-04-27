/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.transforms;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.dexing.DexArchiveMerger;
import com.android.builder.dexing.DexMergerConfig;
import com.android.builder.dexing.DexingMode;
import com.android.dx.command.dexer.DxContext;
import com.android.ide.common.process.ProcessOutput;
import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;

/**
 * Helper class to invoke the {@link com.android.builder.dexing.DexArchiveMerger} used to merge dex
 * archives.
 */
class DexMergerTransformCallable implements Callable<Void> {

    @NonNull private final DexingMode dexingMode;
    @NonNull private final ProcessOutput processOutput;
    @NonNull private final File dexOutputDir;
    @NonNull private final Collection<Path> dexArchives;
    @Nullable private final Set<String> mainDexList;
    @NonNull private final ForkJoinPool forkJoinPool;

    public DexMergerTransformCallable(
            @NonNull DexingMode dexingMode,
            @NonNull ProcessOutput processOutput,
            @NonNull File dexOutputDir,
            @NonNull Collection<Path> dexArchives,
            @Nullable Set<String> mainDexList,
            @NonNull ForkJoinPool forkJoinPool) {
        this.dexingMode = dexingMode;
        this.processOutput = processOutput;
        this.dexOutputDir = dexOutputDir;
        this.dexArchives = dexArchives;
        this.mainDexList = mainDexList;
        this.forkJoinPool = forkJoinPool;
    }

    @Override
    public Void call() throws Exception {
        DxContext dxContext =
                new DxContext(processOutput.getStandardOutput(), processOutput.getErrorOutput());
        DexMergerConfig config = new DexMergerConfig(dexingMode, dxContext);
        DexArchiveMerger merger = new DexArchiveMerger(config, forkJoinPool);
        merger.merge(dexArchives, dexOutputDir.toPath(), mainDexList);
        return null;
    }
}
