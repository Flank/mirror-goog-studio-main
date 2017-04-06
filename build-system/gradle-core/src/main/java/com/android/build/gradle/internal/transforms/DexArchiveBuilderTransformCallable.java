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
import com.android.build.gradle.internal.BuildCacheUtils;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.builder.core.DexOptions;
import com.android.builder.dexing.ClassFileInput;
import com.android.builder.dexing.ClassFileInputs;
import com.android.builder.dexing.DexArchive;
import com.android.builder.dexing.DexArchiveBuilder;
import com.android.builder.dexing.DexArchiveBuilderConfig;
import com.android.builder.dexing.DexArchiveEntry;
import com.android.builder.dexing.DexArchives;
import com.android.builder.utils.ExceptionRunnable;
import com.android.builder.utils.FileCache;
import com.android.builder.utils.PerformanceUtils;
import com.android.dx.Version;
import com.android.dx.command.dexer.DxContext;
import com.android.ide.common.process.ProcessOutput;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.base.Verify;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

/** Callable helper class used to invoke conversion of single jar or directory to a dex archive. */
class DexArchiveBuilderTransformCallable implements Callable<Void> {

    /**
     * Input parameters to be provided by the client when using {@link FileCache}.
     *
     * <p>The clients of {@link FileCache} need to exhaustively specify all the inputs that affect
     * the creation of an output file/directory. This enum class lists the input parameters that are
     * used in {@link DexArchiveBuilderTransformCallable}.
     */
    private enum FileCacheInputParams {

        /** Path of an input file. */
        FILE_PATH,

        /** Relative path of an input file exploded from an aar file. */
        EXPLODED_AAR_FILE_PATH,

        /** Name of an input file that is an instant-run.jar file. */
        INSTANT_RUN_JAR_FILE_NAME,

        /** Hash of an input file. */
        FILE_HASH,

        /** Dx version used to create the dex archive. */
        DX_VERSION,

        /** Whether jumbo mode is enabled. */
        JUMBO_MODE,

        /** Whether optimize is enabled. */
        OPTIMIZE
    }

    private static final LoggerWrapper logger =
            LoggerWrapper.getLogger(DexArchiveBuilderTransformCallable.class);

    private static final int NUM_THREADS = PerformanceUtils.getNumThreadsForDexArchives();

    @NonNull private final Path rootPath;
    @NonNull private final Predicate<Path> toProcess;
    @NonNull private final Predicate<Path> toRemove;
    @NonNull private final File to;
    @NonNull private final Set<String> hashes;
    @NonNull private final ProcessOutput processOutput;
    @Nullable private final FileCache userLevelCache;
    @Nullable private final FileCache projectLevelCache;
    @NonNull private final DexOptions dexOptions;
    private final int minSdkVersion;

    public DexArchiveBuilderTransformCallable(
            @NonNull Path rootPath,
            @NonNull Predicate<Path> toProcess,
            @NonNull Predicate<Path> toRemove,
            @NonNull File to,
            @NonNull Set<String> hashes,
            @NonNull ProcessOutput processOutput,
            @Nullable FileCache userLevelCache,
            @Nullable FileCache projectLevelCache,
            @NonNull DexOptions dexOptions,
            int minSdkVersion) {
        this.rootPath = rootPath;
        this.toProcess = toProcess;
        this.toRemove = toRemove;
        this.to = to;
        this.hashes = hashes;
        this.processOutput = processOutput;
        this.userLevelCache = userLevelCache;
        this.projectLevelCache = projectLevelCache;
        this.dexOptions = dexOptions;
        this.minSdkVersion = minSdkVersion;
    }

    @Override
    public Void call() throws Exception {
        logger.verbose("predex will process %s", rootPath.toString());
        // TODO remove once we can properly add a library as a dependency of its test.
        String hash = getFileHash(rootPath.toFile());

        synchronized (hashes) {
            if (hashes.contains(hash)) {
                logger.verbose("Input with the same hash exists. Pre-dexing skipped.");
                return null;
            }

            hashes.add(hash);
        }

        ExceptionRunnable cacheMissAction = cacheMissAction();

        FileCache cache = cacheToUse();
        if (cache != null) {
            FileCache.Inputs buildCacheInputs = getBuildCacheInputs(rootPath.toFile(), dexOptions);
            String actionableMessage =
                    userLevelCache == cache
                            ? BuildCacheUtils.BUILD_CACHE_TROUBLESHOOTING_MESSAGE
                            : "";
            getFromCacheAndCreateIfMissing(
                    cache, buildCacheInputs, cacheMissAction, actionableMessage);
        } else {
            cacheMissAction.run();
        }

        try (DexArchive outputArchive = DexArchives.fromInput(to.toPath())) {
            for (DexArchiveEntry entry : outputArchive.getFiles()) {
                Path dexPath = entry.getRelativePathInArchive();
                Path withClassExt = DexArchiveEntry.withClassExtension(dexPath);
                if (toRemove.test(withClassExt)) {
                    outputArchive.removeFile(dexPath);
                }
            }
        }

        return null;
    }

    /** Returns cache to be used, or {@code null} if none should be used. */
    @Nullable
    private FileCache cacheToUse() {
        if (userLevelCache != null) {
            return userLevelCache;
        } else if (projectLevelCache != null) {
            return projectLevelCache;
        } else {
            return null;
        }
    }

    private void getFromCacheAndCreateIfMissing(
            @NonNull FileCache cache,
            @NonNull FileCache.Inputs key,
            @NonNull ExceptionRunnable cacheMissAction,
            @NonNull String actionableMessage) {
        FileCache.QueryResult result;
        try {
            result = cache.createFile(to, key, cacheMissAction);
        } catch (ExecutionException exception) {
            logger.error(
                    null,
                    String.format(
                            "Unable to pre-dex '%1$s' to '%2$s'",
                            rootPath.toString(), to.getAbsolutePath()));
            throw new RuntimeException(exception);
        } catch (Exception exception) {
            logger.error(
                    null,
                    String.format(
                            "Unable to pre-dex '%1$s' to '%2$s' using the build cache at"
                                    + " '%3$s'.\n"
                                    + "%4$s",
                            rootPath.toString(),
                            to.getAbsolutePath(),
                            cache.getCacheDirectory().getAbsolutePath(),
                            actionableMessage));
            throw new RuntimeException(exception);
        }
        if (result.getQueryEvent().equals(FileCache.QueryEvent.CORRUPTED)) {
            Verify.verifyNotNull(result.getCauseOfCorruption());
            logger.verbose(
                    "The build cache at '%1$s' contained an invalid cache entry.\n"
                            + "Cause: %2$s\n"
                            + "We have recreated the cache entry.\n"
                            + "%3$s",
                    cache.getCacheDirectory().getAbsolutePath(),
                    Throwables.getStackTraceAsString(result.getCauseOfCorruption()),
                    actionableMessage);
        }
    }

    @NonNull
    private ExceptionRunnable cacheMissAction() {
        return () -> {
            try (ClassFileInput input = ClassFileInputs.fromPath(rootPath, toProcess);
                    DexArchive outputArchive = DexArchives.fromInput(to.toPath())) {
                boolean optimizedDex =
                        !dexOptions.getAdditionalParameters().contains("--no-optimize");
                DxContext dxContext =
                        new DxContext(
                                processOutput.getStandardOutput(), processOutput.getErrorOutput());
                DexArchiveBuilderConfig config =
                        new DexArchiveBuilderConfig(
                                NUM_THREADS,
                                dxContext,
                                optimizedDex,
                                minSdkVersion);

                DexArchiveBuilder converter = new DexArchiveBuilder(config);
                converter.convert(input, outputArchive);
            }
        };
    }

    /**
     * Returns a {@link FileCache.Inputs} object computed from the given parameters for the
     * predex-library task to use the build cache.
     */
    @NonNull
    private FileCache.Inputs getBuildCacheInputs(
            @NonNull File inputFile, @NonNull DexOptions dexOptions) throws IOException {
        // To use the cache, we need to specify all the inputs that affect the outcome of a pre-dex
        // (see DxDexKey for an exhaustive list of these inputs)
        FileCache.Inputs.Builder buildCacheInputs =
                new FileCache.Inputs.Builder(FileCache.Command.PREDEX_LIBRARY_TO_DEX_ARCHIVE);

        // As a general rule, we use the file's path and hash to uniquely identify a file. However,
        // certain types of files are usually copied/duplicated at different locations. To recognize
        // those files as the same file, instead of using the full file path, we use a substring of
        // the file path as the file's identifier.
        if (inputFile.getPath().contains("exploded-aar")) {
            // If the file is exploded from an aar file, we use the path relative to the
            // "exploded-aar" directory as the file's identifier, which contains the aar artifact
            // information (e.g., "exploded-aar/com.android.support/support-v4/23.3.0/jars/
            // classes.jar")
            buildCacheInputs.putString(
                    FileCacheInputParams.EXPLODED_AAR_FILE_PATH.name(),
                    inputFile.getPath().substring(inputFile.getPath().lastIndexOf("exploded-aar")));
        } else if (inputFile.getName().equals("instant-run.jar")) {
            // If the file is an instant-run.jar file, we use the the file name itself as
            // the file's identifier
            buildCacheInputs.putString(
                    FileCacheInputParams.INSTANT_RUN_JAR_FILE_NAME.name(), inputFile.getName());
        } else {
            // In all other cases, we use the file's path as the file's identifier
            buildCacheInputs.putFilePath(FileCacheInputParams.FILE_PATH.name(), inputFile);
        }

        // In all cases, in addition to the (full or extracted) file path, we always use the file's
        // hash to identify an input file, and provide other input parameters to the cache
        buildCacheInputs
                .putFileHash(FileCacheInputParams.FILE_HASH.name(), inputFile)
                .putString(FileCacheInputParams.DX_VERSION.name(), Version.VERSION)
                .putBoolean(FileCacheInputParams.JUMBO_MODE.name(), dexOptions.getJumboMode())
                .putBoolean(
                        FileCacheInputParams.OPTIMIZE.name(),
                        !dexOptions.getAdditionalParameters().contains("--no-optimize"));

        return buildCacheInputs.build();
    }

    /**
     * Returns the hash of a file.
     *
     * <p>If the file is a folder, it's a hash of its path. If the file is a file, then it's a hash
     * of the file itself.
     *
     * @param file the file to hash
     */
    @NonNull
    private static String getFileHash(@NonNull File file) throws IOException {
        HashCode hashCode;
        HashFunction hashFunction = Hashing.sha1();
        if (file.isDirectory()) {
            hashCode = hashFunction.hashString(file.getPath(), Charsets.UTF_16LE);
        } else {
            hashCode = Files.hash(file, hashFunction);
        }

        return hashCode.toString();
    }
}
