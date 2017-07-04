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
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.gradle.internal.BuildCacheUtils;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.pipeline.OriginalStream;
import com.android.builder.core.DexOptions;
import com.android.builder.dexing.DexerTool;
import com.android.builder.utils.FileCache;
import com.android.dx.Version;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.base.Verify;
import com.google.common.collect.Multimap;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.concurrent.ExecutionException;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/** helper class used to cache dex archives in the user or build caches. */
class DexArchiveBuilderCacheHandler {

    private static final LoggerWrapper logger =
            LoggerWrapper.getLogger(DexArchiveBuilderTransform.class);

    private static final int CACHE_KEY_VERSION = 1;

    @Nullable private final FileCache userLevelCache;
    @NonNull private final DexOptions dexOptions;

    DexArchiveBuilderCacheHandler(
            @Nullable FileCache userLevelCache, @NonNull DexOptions dexOptions) {
        this.userLevelCache = userLevelCache;
        this.dexOptions = dexOptions;
    }

    @Nullable
    File getCachedVersionIfPresent(JarInput input) throws IOException {
        FileCache cache =
                PreDexTransform.getBuildCache(
                        input.getFile(), isExternalLib(input), userLevelCache);

        if (cache == null) {
            return null;
        }

        FileCache.Inputs buildCacheInputs =
                DexArchiveBuilderCacheHandler.getBuildCacheInputs(
                        input.getFile(), dexOptions, DexerTool.DX);
        return cache.cacheEntryExists(buildCacheInputs)
                ? cache.getFileInCache(buildCacheInputs)
                : null;
    }

    void populateCache(Multimap<QualifiedContent, File> cacheableItems)
            throws IOException, ExecutionException {

        for (QualifiedContent input : cacheableItems.keys()) {
            FileCache cache =
                    PreDexTransform.getBuildCache(
                            input.getFile(), isExternalLib(input), userLevelCache);
            if (cache != null) {
                FileCache.Inputs buildCacheInputs =
                        DexArchiveBuilderCacheHandler.getBuildCacheInputs(
                                input.getFile(), dexOptions, DexerTool.DX);
                FileCache.QueryResult result =
                        cache.createFileInCacheIfAbsent(
                                buildCacheInputs,
                                in -> {
                                    Collection<File> dexArchives = cacheableItems.get(input);
                                    logger.verbose(
                                            "Merging %1$s into %2$s",
                                            Joiner.on(',').join(dexArchives), in.getAbsolutePath());
                                    mergeJars(in, cacheableItems.get(input));
                                });
                if (result.getQueryEvent().equals(FileCache.QueryEvent.CORRUPTED)) {
                    Verify.verifyNotNull(result.getCauseOfCorruption());
                    logger.info(
                            "The build cache at '%1$s' contained an invalid cache entry.\n"
                                    + "Cause: %2$s\n"
                                    + "We have recreated the cache entry.\n"
                                    + "%3$s",
                            cache.getCacheDirectory().getAbsolutePath(),
                            Throwables.getStackTraceAsString(result.getCauseOfCorruption()),
                            BuildCacheUtils.BUILD_CACHE_TROUBLESHOOTING_MESSAGE);
                }
            }
        }
    }

    private static void mergeJars(File out, Iterable<File> dexArchives) throws IOException {

        try (JarOutputStream jarOutputStream =
                new JarOutputStream(new BufferedOutputStream(new FileOutputStream(out)))) {

            for (File dexArchive : dexArchives) {
                try (JarFile jarFile = new JarFile(dexArchive)) {
                    Enumeration<JarEntry> entries = jarFile.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry jarEntry = entries.nextElement();
                        jarOutputStream.putNextEntry(new JarEntry(jarEntry.getName()));
                        try (InputStream inputStream =
                                new BufferedInputStream(jarFile.getInputStream(jarEntry))) {
                            ByteStreams.copy(inputStream, jarOutputStream);
                        }
                        jarOutputStream.closeEntry();
                    }
                }
            }
        }
    }

    /** Returns if the qualified content is an external jar. */
    private static boolean isExternalLib(@NonNull QualifiedContent content) {
        return content.getFile().isFile()
                && content.getScopes()
                        .equals(Collections.singleton(QualifiedContent.Scope.EXTERNAL_LIBRARIES))
                && content.getContentTypes()
                        .equals(Collections.singleton(QualifiedContent.DefaultContentType.CLASSES))
                && !content.getName().startsWith(OriginalStream.LOCAL_JAR_GROUPID);
    }

    /**
     * Input parameters to be provided by the client when using {@link FileCache}.
     *
     * <p>The clients of {@link FileCache} need to exhaustively specify all the inputs that affect
     * the creation of an output file/directory. This enum class lists the input parameters that are
     * used in {@link DexArchiveBuilderCacheHandler}.
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
        OPTIMIZE,

        /** Tool used to produce the dex archive. */
        DEXER_TOOL,

        /** Version of the cache key. */
        CACHE_KEY_VERSION,
    }

    /**
     * Returns a {@link FileCache.Inputs} object computed from the given parameters for the
     * predex-library task to use the build cache.
     */
    @NonNull
    public static FileCache.Inputs getBuildCacheInputs(
            @NonNull File inputFile, @NonNull DexOptions dexOptions, @NonNull DexerTool dexerTool)
            throws IOException {
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
        // hash to identify an input file, and provide other input parameters to the cache
        buildCacheInputs
                .putFileHash(FileCacheInputParams.FILE_HASH.name(), inputFile)
                .putString(FileCacheInputParams.DX_VERSION.name(), Version.VERSION)
                .putBoolean(FileCacheInputParams.JUMBO_MODE.name(), isJumboModeEnabledForDx())
                .putBoolean(
                        FileCacheInputParams.OPTIMIZE.name(),
                        !dexOptions.getAdditionalParameters().contains("--no-optimize"))
                .putString(FileCacheInputParams.DEXER_TOOL.name(), dexerTool.name())
                .putLong(FileCacheInputParams.CACHE_KEY_VERSION.name(), CACHE_KEY_VERSION);

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
    public static String getFileHash(@NonNull File file) throws IOException {
        HashCode hashCode;
        HashFunction hashFunction = Hashing.sha1();
        if (file.isDirectory()) {
            hashCode = hashFunction.hashString(file.getPath(), Charsets.UTF_16LE);
        } else {
            hashCode = Files.hash(file, hashFunction);
        }

        return hashCode.toString();
    }

    /** Jumbo mode is always enabled for dex archives - see http://b.android.com/321744 */
    static boolean isJumboModeEnabledForDx() {
        return true;
    }
}
