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

package com.android.builder.dexing;

import com.android.annotations.NonNull;
import com.android.dx.cf.direct.DirectClassFile;
import com.android.dx.cf.direct.StdAttributeFactory;
import com.android.dx.dex.cf.CfTranslator;
import com.android.dx.dex.file.ClassDefItem;
import com.android.dx.dex.file.DexFile;
import com.android.ide.common.blame.parser.DexParser;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.utils.PathUtils;
import com.google.common.base.MoreObjects;
import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Dex archive builder that is using dx to convert class files to dex files. */
final class DxDexArchiveBuilder extends DexArchiveBuilder {

    @NonNull private final DexArchiveBuilderConfig config;
    @NonNull private final WaitableExecutor executor;

    /**
     * Creates instance that is configured for processing .class input files, and producing a dex
     * archive. For configuring it, please take a look at {@link DexArchiveBuilderConfig} which
     * contains relevant options.
     *
     * @param config contains setup for this builder
     */
    public DxDexArchiveBuilder(@NonNull DexArchiveBuilderConfig config) {
        this.config = config;
        this.executor = WaitableExecutor.useGlobalSharedThreadPool();
    }

    @NonNull
    @Override
    protected List<DexArchiveEntry> convertClassFileInput(@NonNull ClassFileInput input) {
        Map<Integer, Map<Path, byte[]>> bucketizedJobs = Maps.newHashMap();
        int i = 0;
        for (ClassFileEntry classFileEntry : input.allEntries()) {
            int bucketId = (i++) % executor.getParallelism();

            Map<Path, byte[]> jobs = bucketizedJobs.getOrDefault(bucketId, Maps.newHashMap());
            jobs.put(classFileEntry.relativePath, classFileEntry.classFileContent);
            bucketizedJobs.put(bucketId, jobs);
        }

        for (Map<Path, byte[]> job : bucketizedJobs.values()) {
            processJobChunk(job);
        }
        try {
            List<List<DexArchiveEntry>> res = executor.waitForTasksWithQuickFail(true);
            return res.stream().flatMap(Collection::stream).collect(Collectors.toList());
        } catch (Exception e) {
            config.getErrorOut().println(DexParser.DX_UNEXPECTED_EXCEPTION);
            config.getErrorOut().println(Throwables.getRootCause(e).getMessage());
            config.getErrorOut().print(Throwables.getStackTraceAsString(e));
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new DexArchiveBuilderException(
                    "Unable to convert input to dex archive.",
                    MoreObjects.firstNonNull(e.getCause(), e));
        }
    }

    private void processJobChunk(@NonNull Map<Path, byte[]> pathToContent) {
        executor.execute(
                () -> {
                    List<DexArchiveEntry> res = new ArrayList<>(pathToContent.size());
                    for (Map.Entry<Path, byte[]> e : pathToContent.entrySet()) {
                        Path relativePath = e.getKey();
                        byte[] fileBytes = e.getValue();
                        // parses the class file
                        String unixClassFile = PathUtils.toSystemIndependentPath(relativePath);

                        // Copied from dx, from com.android.dx.command.dexer.Main
                        DirectClassFile cf = new DirectClassFile(fileBytes, unixClassFile, true);
                        cf.setAttributeFactory(StdAttributeFactory.THE_ONE);
                        cf.getMagic(); // triggers the actual parsing

                        // starts the actual translation and writes the content to the dex file
                        // specified
                        DexFile dexFile = new DexFile(config.getDexOptions());

                        // Copied from dx, from com.android.dx.command.dexer.Main
                        ClassDefItem classDefItem =
                                CfTranslator.translate(
                                        config.getDxContext(),
                                        cf,
                                        fileBytes,
                                        config.getCfOptions(),
                                        config.getDexOptions(),
                                        dexFile);
                        dexFile.add(classDefItem);

                        byte[] dexClassContent = dexFile.toDex(null, false);
                        res.add(new DexArchiveEntry(dexClassContent, relativePath));
                    }
                    return res;
                });
    }
}
