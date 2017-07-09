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
import com.android.builder.dexing.DexMergerTool;
import com.android.builder.dexing.DexingType;
import com.android.dx.command.dexer.DxContext;
import com.android.ide.common.process.ProcessOutput;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;

/**
 * Helper class to invoke the {@link com.android.builder.dexing.DexArchiveMerger} used to merge dex
 * archives.
 */
class DexMergerTransformCallable implements Callable<Void> {

    @NonNull private final DexingType dexingType;
    @NonNull private final ProcessOutput processOutput;
    @NonNull private final File dexOutputDir;
    @NonNull private final Iterable<Path> dexArchives;
    @NonNull private final ForkJoinPool forkJoinPool;
    @Nullable private final Path mainDexList;
    @NonNull private final DexMergerTool dexMerger;

    public DexMergerTransformCallable(
            @NonNull DexingType dexingType,
            @NonNull ProcessOutput processOutput,
            @NonNull File dexOutputDir,
            @NonNull Iterable<Path> dexArchives,
            @Nullable Path mainDexList,
            @NonNull ForkJoinPool forkJoinPool,
            @NonNull DexMergerTool dexMerger) {
        this.dexingType = dexingType;
        this.processOutput = processOutput;
        this.dexOutputDir = dexOutputDir;
        this.dexArchives = dexArchives;
        this.mainDexList = mainDexList;
        this.forkJoinPool = forkJoinPool;
        this.dexMerger = dexMerger;
    }

    @Override
    public Void call() throws Exception {
        DexArchiveMerger merger;
        switch (dexMerger) {
            case DX:
                {
                    DxContext dxContext =
                            new DxContext(
                                    processOutput.getStandardOutput(),
                                    processOutput.getErrorOutput());
                    merger = DexArchiveMerger.createDxDexMerger(dxContext, forkJoinPool);
                    break;
                }
            default:
                throw new AssertionError("Unknown dex merger " + dexMerger.name());
        }

        merger.mergeDexArchives(dexArchives, dexOutputDir.toPath(), mainDexList, dexingType);
        return null;
    }
}
