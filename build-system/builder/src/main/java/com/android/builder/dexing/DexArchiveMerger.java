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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.dex.Dex;
import com.android.dex.DexIndexOverflowException;
import com.android.dx.merge.DexMerger;
import com.android.ide.common.blame.parser.DexParser;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.utils.FileUtils;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Merges DEX files found in {@link DexArchive}s, and produces the final DEX file(s). Inputs can
 * come from one or more dex archives. In order to process the dex archives, one should invoke
 * {@link #merge(Collection, Path, Set)} method.
 *
 * <p>In order to merge individual DEX files, we are using {@link DexMergingStrategy} to determine
 * how many input DEX files can fit into a single output DEX.
 *
 * <p>Merging is actually executed by invoking the {@link DexMerger}, which is part of dx.
 */
public class DexArchiveMerger {

    /** Exception thrown when dex archive merging goes wrong. */
    public static class DexArchiveMergerException extends RuntimeException {

        public DexArchiveMergerException(String message, Throwable cause) {
            super(message, cause);
        }

        public DexArchiveMergerException(String message) {
            super(message);
        }
    }

    @NonNull private final DexMergerConfig config;
    @NonNull private final DexMergingStrategy mergingStrategy;
    @NonNull private final WaitableExecutor<Void> executor;

    public DexArchiveMerger(@NonNull DexMergerConfig config) {
        // merging with a simple merging strategy by default
        this(config, new NaiveDexMergingStrategy());
    }

    public DexArchiveMerger(
            @NonNull DexMergerConfig config, @NonNull DexMergingStrategy mergingStrategy) {
        this.config = config;
        this.mergingStrategy = mergingStrategy;
        this.executor = WaitableExecutor.useGlobalSharedThreadPool();
    }

    /**
     * Triggers the actual merging by processing all DEX files from dex archives, and outputting the
     * DEX file(s) in the specified directory. DEX files will have names classes.dex, classes2.dex
     * etc. It is the responsibility of the invoker to make sure the output directory is empty and
     * writable.
     *
     * <p>In case of mono-dex, it is expected that all DEX files will fit into a single output DEX
     * file. If this is not possible, {@link DexIndexOverflowException} exception will be thrown.
     *
     * <p>When merging native multidex, multiple DEX files might be created.
     *
     * <p>In case of legacy multidex, list of classes in the main dex should be specified as well.
     * When using legacy multidex mode, only classes explicitly specified in this list will be in
     * the main dex file. This is equivalent of invoking dx with --minimal-main-dex option.
     */
    public void merge(
            @NonNull Collection<Path> inputs,
            @NonNull Path outputDir,
            @NonNull Set<String> mainDexClasses)
            throws IOException {
        try {
            if (config.getDexingMode() == DexingMode.MONO_DEX) {
                mergeMonoDex(inputs, outputDir);
            } else {
                mergeMultidex(inputs, outputDir, mainDexClasses);
            }

            executor.waitForTasksWithQuickFail(true);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            config.getDxContext().err.println(DexParser.DX_UNEXPECTED_EXCEPTION);
            config.getDxContext().err.println(Throwables.getRootCause(e));
            config.getDxContext().err.print(Throwables.getStackTraceAsString(e));
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new DexArchiveMergerException("Unable to merge dex", e.getCause());
        }
    }

    /**
     * Merge all DEX files from the dex archives. They need to fit into a single DEX file.
     *
     * <p>In this dexing mode, we actually read DEX files from different archives in parallel.
     * Because we are reading disjoint sets of DEX files, we can benefit from parallelism. After the
     * reading stage, we invoke the merger.
     */
    private void mergeMonoDex(@NonNull Collection<Path> inputs, @NonNull Path output)
            throws IOException, InterruptedException {
        WaitableExecutor<Set<Dex>> readersExecutor = WaitableExecutor.useGlobalSharedThreadPool();
        for (Path archivePath : inputs) {
            readersExecutor.execute(
                    () -> {
                        try (DexArchive dexArchive = DexArchives.fromInput(archivePath)) {

                            Set<DexArchiveEntry> entries = dexArchive.getFiles();
                            Set<Dex> result = new HashSet<>(entries.size());
                            for (DexArchiveEntry e : entries) {
                                result.add(new Dex(e.getDexFileContent()));
                            }
                            return result;
                        }
                    });
        }

        Set<Dex> readDexes =
                readersExecutor
                        .waitForTasksWithQuickFail(true)
                        .stream()
                        .flatMap(Set::stream)
                        .collect(Collectors.toSet());
        submitForMerging(readDexes, output.resolve(getDexFileName(0)));
    }

    /**
     * Merges all DEX files from the dex archives into DEX file(s). It does so by using {@link
     * DexMergingStrategy} which specifies when a DEX file should be started.
     *
     * <p>For {@link DexingMode#LEGACY_MULTIDEX} mode, only classes specified in the main dex
     * classes list will be packaged in the classes.dex, thus creating a minimal main DEX. Remaining
     * DEX classes will be placed in other DEX files.
     *
     * @throws IOException if dex archive cannot be read, or merged DEX file(s) cannot be written
     */
    private void mergeMultidex(
            @NonNull Collection<Path> inputs,
            @NonNull Path output,
            @NonNull Set<String> mainDexClasses)
            throws IOException {
        Iterator<DexArchiveEntry> entries = getAllEntries(inputs);
        if (!entries.hasNext()) {
            // nothing to do
            return;
        }

        int classesDexSuffix;
        if (config.getDexingMode() == DexingMode.LEGACY_MULTIDEX) {
            // if we are in native multidex, we should leave classes.dex for the main dex
            classesDexSuffix = 1;
        } else {
            classesDexSuffix = 0;
        }

        List<Dex> toMergeInMain = Lists.newArrayList();
        mergingStrategy.startNewDex();

        while (entries.hasNext()) {
            DexArchiveEntry entry = entries.next();
            Dex dex = new Dex(entry.getDexFileContent());

            if (config.getDexingMode() == DexingMode.LEGACY_MULTIDEX) {
                // check if this should go to the main dex
                Path classFile =
                        DexArchiveEntry.withClassExtension(entry.getRelativePathInArchive());
                String unixClassFile = FileUtils.toSystemIndependentPath(classFile);
                if (mainDexClasses.contains(unixClassFile)) {
                    toMergeInMain.add(dex);
                    continue;
                }
            }

            if (!mergingStrategy.tryToAddForMerging(dex)) {
                Path dexOutput = output.resolve(getDexFileName(classesDexSuffix++));
                submitForMerging(mergingStrategy.getAllDexToMerge(), dexOutput);
                mergingStrategy.startNewDex();

                // adding now should succeed
                if (!mergingStrategy.tryToAddForMerging(dex)) {
                    throw new DexArchiveMergerException(
                            "A single DEX file from a dex archive has more than 64K references.");
                }
            }
        }

        if (!toMergeInMain.isEmpty()) {
            // write the main dex file
            submitForMerging(toMergeInMain, output.resolve(getDexFileName(0)));
        }

        // if there are some remaining unprocessed dex files, merge them
        if (!mergingStrategy.getAllDexToMerge().isEmpty()) {
            Path dexOutput = output.resolve(getDexFileName(classesDexSuffix));
            submitForMerging(mergingStrategy.getAllDexToMerge(), dexOutput);
        }
    }

    @NonNull
    private Iterator<DexArchiveEntry> getAllEntries(@NonNull Collection<Path> inputs)
            throws IOException {
        return inputs.stream()
                .map(this::getEntriesFromSingleArchive)
                .flatMap(Set::stream)
                .iterator();
    }

    @NonNull
    private Set<DexArchiveEntry> getEntriesFromSingleArchive(@NonNull Path archivePath) {
        try (DexArchive archive = DexArchives.fromInput(archivePath)) {
            return archive.getFiles();
        } catch (IOException e) {
            throw new DexArchiveMergerException(
                    "Unable to read DEX files from dex archive " + archivePath.toString(), e);
        }
    }

    private void submitForMerging(@NonNull Collection<Dex> dexes, @NonNull Path dexOutputPath) {
        executor.execute(new DexArchiveMergerCallable(dexes, dexOutputPath, config.getDxContext()));
    }

    @NonNull
    private String getDexFileName(int classesDexIndex) {
        if (classesDexIndex == 0) {
            return SdkConstants.FN_APK_CLASSES_DEX;
        } else {
            return String.format(SdkConstants.FN_APK_CLASSES_N_DEX, (classesDexIndex + 1));
        }
    }
}
