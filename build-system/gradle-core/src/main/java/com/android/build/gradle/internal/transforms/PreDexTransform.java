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

package com.android.build.gradle.internal.transforms;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.BuildCacheUtils;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.DexOptions;
import com.android.builder.sdk.TargetInfo;
import com.android.builder.utils.FileCache;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.DexParser;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.repository.Revision;
import com.android.sdklib.BuildToolInfo;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * Pre-dexing transform. This will consume {@link TransformManager#CONTENT_CLASS}, and for each of
 * the inputs, corresponding DEX will be produced. It runs if all of the following conditions hold:
 * <ul>
 *     <li>variant is native multidex or mono-dex
 *     <li>users have not explicitly disabled pre-dexing
 *     <li>minification is turned off
 * </ul>
 * or we run in instant run mode.
 *
 * <p>This transform is incremental. Only streams with changed files will be pre-dexed again. Build
 * cache {@link FileCache}, if available, is used to store DEX files of external libraries.
 */
public class PreDexTransform extends Transform {

    private static final LoggerWrapper logger = LoggerWrapper.getLogger(PreDexTransform.class);

    @NonNull private final DexOptions dexOptions;

    @NonNull private final AndroidBuilder androidBuilder;

    @Nullable private final FileCache buildCache;

    @NonNull private final DexingMode dexingMode;

    private boolean instantRunMode;

    public PreDexTransform(
            @NonNull DexOptions dexOptions,
            @NonNull AndroidBuilder androidBuilder,
            @Nullable FileCache buildCache,
            @NonNull DexingMode dexingMode,
            boolean instantRunMode) {
        this.dexOptions = dexOptions;
        this.androidBuilder = androidBuilder;
        this.buildCache = buildCache;
        this.dexingMode = dexingMode;
        this.instantRunMode = instantRunMode;
    }

    @NonNull
    @Override
    public String getName() {
        return "preDex";
    }

    @NonNull
    @Override
    public Set<ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<ContentType> getOutputTypes() {
        return TransformManager.CONTENT_DEX;
    }

    @NonNull
    @Override
    public Set<? super Scope> getScopes() {
        return TransformManager.SCOPE_FULL_INSTANT_RUN_PROJECT;
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        try {
            // ATTENTION: if you add something here, consider adding the value to DexKey - it needs
            // to be saved if affects how dx is invoked.

            Map<String, Object> params = Maps.newHashMapWithExpectedSize(6);

            params.put("optimize", true);
            params.put("jumbo", dexOptions.getJumboMode());
            params.put("multidex-mode", dexingMode.toString());
            params.put("java-max-heap-size", dexOptions.getJavaMaxHeapSize());
            params.put(
                    "additional-parameters",
                    Iterables.toString(dexOptions.getAdditionalParameters()));

            TargetInfo targetInfo = androidBuilder.getTargetInfo();
            Preconditions.checkState(
                    targetInfo != null,
                    "androidBuilder.targetInfo required for task '%s'.",
                    getName());
            BuildToolInfo buildTools = targetInfo.getBuildTools();
            params.put("build-tools", buildTools.getRevision().toString());

            return params;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(@NonNull TransformInvocation transformInvocation)
            throws TransformException, IOException, InterruptedException {
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        Preconditions.checkNotNull(outputProvider, "Missing output provider.");

        List<JarInput> jarInputs = Lists.newArrayList();
        List<DirectoryInput> directoryInputs = Lists.newArrayList();
        for (TransformInput input : transformInvocation.getInputs()) {
            jarInputs.addAll(input.getJarInputs());
            directoryInputs.addAll(input.getDirectoryInputs());
        }

        logger.verbose("Task is incremental : %b ", transformInvocation.isIncremental());
        logger.verbose("JarInputs %s", Joiner.on(",").join(jarInputs));
        logger.verbose("DirInputs %s", Joiner.on(",").join(directoryInputs));

        ProcessOutputHandler outputHandler =
                new ParsingProcessOutputHandler(
                        new ToolOutputParser(new DexParser(), Message.Kind.ERROR, logger),
                        new ToolOutputParser(new DexParser(), logger),
                        androidBuilder.getErrorReporter());

        if (!transformInvocation.isIncremental()) {
            outputProvider.deleteAll();
        }

        try {
            // hash to detect duplicate jars (due to issue with library and tests)
            final Set<String> hashes = Sets.newHashSet();
            // input files to output file map
            final Map<File, File> inputFiles = Maps.newHashMap();
            // stuff to delete. Might be folders.
            final List<File> deletedFiles = Lists.newArrayList();
            Set<File> externalLibJarFiles = Sets.newHashSet();

            // first gather the different inputs to be dexed separately.
            for (DirectoryInput directoryInput : directoryInputs) {
                File rootFolder = directoryInput.getFile();
                // The incremental mode only detect file level changes.
                // It does not handle removed root folders. However the transform
                // task will add the TransformInput right after it's removed so that it
                // can be detected by the transform.
                if (!rootFolder.exists()) {
                    // if the root folder is gone we need to remove the previous
                    // output
                    File preDexedFile = getPreDexFile(outputProvider, directoryInput);
                    if (preDexedFile.exists()) {
                        deletedFiles.add(preDexedFile);
                    }
                } else if (!transformInvocation.isIncremental()
                        || !directoryInput.getChangedFiles().isEmpty()) {
                    // add the folder for re-dexing only if we're not in incremental
                    // mode or if it contains changed files.
                    logger.verbose(
                            "Changed file for %s are %s",
                            directoryInput.getFile().getAbsolutePath(),
                            Joiner.on(",").join(directoryInput.getChangedFiles().entrySet()));
                    File preDexFile = getPreDexFile(outputProvider, directoryInput);
                    inputFiles.put(rootFolder, preDexFile);
                }
            }

            for (JarInput jarInput : jarInputs) {
                switch (jarInput.getStatus()) {
                    case NOTCHANGED:
                        if (transformInvocation.isIncremental()) {
                            break;
                        }
                        // intended fall-through
                    case CHANGED:
                    case ADDED:
                        {
                            File preDexFile = getPreDexFile(outputProvider, jarInput);
                            inputFiles.put(jarInput.getFile(), preDexFile);
                            if (jarInput.getScopes()
                                    .equals(Collections.singleton(Scope.EXTERNAL_LIBRARIES))) {
                                externalLibJarFiles.add(jarInput.getFile());
                            }
                            break;
                        }
                    case REMOVED:
                        {
                            File preDexedFile = getPreDexFile(outputProvider, jarInput);
                            if (preDexedFile.exists()) {
                                deletedFiles.add(preDexedFile);
                            }
                            break;
                        }
                }
            }

            logger.verbose("inputFiles : %s", Joiner.on(",").join(inputFiles.keySet()));
            WaitableExecutor<Void> executor = WaitableExecutor.useGlobalSharedThreadPool();

            for (Map.Entry<File, File> entry : inputFiles.entrySet()) {
                Callable<Void> action =
                        new PreDexTask(
                                entry.getKey(),
                                entry.getValue(),
                                hashes,
                                outputHandler,
                                getBuildCache(entry.getKey(), externalLibJarFiles));
                logger.verbose("Adding PreDexTask for %s : %s", entry.getKey(), action);
                executor.execute(action);
            }

            for (final File file : deletedFiles) {
                executor.execute(
                        () -> {
                            FileUtils.deletePath(file);
                            return null;
                        });
            }

            executor.waitForTasksWithQuickFail(false);
            logger.verbose("Done with all dexing");
        } catch (Exception e) {
            throw new TransformException(e);
        }
    }



    private final class PreDexTask implements Callable<Void> {

        @NonNull private final File from;
        @NonNull private final File to;
        @NonNull private final Set<String> hashes;
        @NonNull private final ProcessOutputHandler outputHandler;
        @Nullable private final FileCache buildCache;

        private PreDexTask(
                @NonNull File from,
                @NonNull File to,
                @NonNull Set<String> hashes,
                @NonNull ProcessOutputHandler outputHandler,
                @Nullable FileCache buildCache) {
            this.from = from;
            this.to = to;
            this.hashes = hashes;
            this.outputHandler = outputHandler;
            this.buildCache = buildCache;
        }

        @Override
        public Void call() throws Exception {
            logger.verbose("predex called for %s", from);
            // TODO remove once we can properly add a library as a dependency of its test.
            String hash = getFileHash(from);

            synchronized (hashes) {
                if (hashes.contains(hash)) {
                    logger.verbose("Input with the same hash exists. Pre-dexing skipped.");
                    return null;
                }

                hashes.add(hash);
            }

            Callable<Void> preDexLibraryAction =
                    () -> {
                        FileUtils.deletePath(to);
                        Files.createParentDirs(to);
                        if (dexingMode.isMultiDex) {
                            FileUtils.mkdirs(to);
                        }
                        androidBuilder.preDexLibrary(
                                from, to, dexingMode.isMultiDex, dexOptions, outputHandler);
                        return null;
                    };

            // If the build cache is used, run pre-dexing using the cache
            if (buildCache != null) {
                FileCache.Inputs buildCacheInputs =
                        getBuildCacheInputs(
                                from,
                                androidBuilder.getTargetInfo().getBuildTools().getRevision(),
                                dexOptions,
                                dexingMode.isMultiDex);
                FileCache.QueryResult result;
                try {
                    result = buildCache.createFile(to, buildCacheInputs, preDexLibraryAction);
                } catch (ExecutionException exception) {
                    throw new RuntimeException(
                            String.format(
                                    "Unable to pre-dex '%1$s' to '%2$s'",
                                    from.getAbsolutePath(),
                                    to.getAbsolutePath()),
                            exception);
                } catch (Exception exception) {
                    throw new RuntimeException(
                            String.format(
                                    "Unable to pre-dex '%1$s' to '%2$s' using the build cache at"
                                            + " '%3$s'.\n"
                                            + "%4$s",
                                    from.getAbsolutePath(),
                                    to.getAbsolutePath(),
                                    buildCache.getCacheDirectory().getAbsolutePath(),
                                    BuildCacheUtils.BUILD_CACHE_TROUBLESHOOTING_MESSAGE),
                            exception);
                }
                if (result.getQueryEvent().equals(FileCache.QueryEvent.CORRUPTED)) {
                    logger.verbose(
                            "The build cache at '%1$s' contained an invalid cache entry.\n"
                                    + "Cause: %2$s\n"
                                    + "We have recreated the cache entry.\n"
                                    + "%3$s",
                            buildCache.getCacheDirectory().getAbsolutePath(),
                            Throwables.getStackTraceAsString(result.getCauseOfCorruption().get()),
                            BuildCacheUtils.BUILD_CACHE_TROUBLESHOOTING_MESSAGE);
                }
            } else {
                preDexLibraryAction.call();
            }
            return null;
        }
    }

    /**
     * Returns the build cache if it should be used for the predex-library task, and
     * {@code null} otherwise.
     */
    @Nullable
    private FileCache getBuildCache(@NonNull File inputFile, @NonNull Set<File> external) {
        // We use the build cache only when it is enabled and the input file is a (non-snapshot)
        // external-library jar file
        if (buildCache == null || !external.contains(inputFile)) {
            return null;
        }
        // After the check above, here the build cache should be enabled and the input file is an
        // external-library jar file. We now check whether it is a snapshot version or not (to
        // address http://b.android.com/228623).
        // Note that the current check is based on the file path; if later on there is a more
        // reliable way to verify whether an input file is a snapshot, we should replace this check
        // with that.
        if (inputFile.getPath().contains("-SNAPSHOT")) {
            return null;
        } else {
            return buildCache;
        }
    }

    /**
     * Returns a {@link FileCache.Inputs} object computed from the given parameters for the
     * predex-library task to use the build cache.
     */
    @NonNull
    private FileCache.Inputs getBuildCacheInputs(
            @NonNull File inputFile,
            @NonNull Revision buildToolsRevision,
            @NonNull DexOptions dexOptions,
            boolean multiDex)
            throws IOException {
        // To use the cache, we need to specify all the inputs that affect the outcome of a pre-dex
        // (see DxDexKey for an exhaustive list of these inputs)
        FileCache.Inputs.Builder buildCacheInputs =
                new FileCache.Inputs.Builder(FileCache.Command.PREDEX_LIBRARY);

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
                .putString(
                        FileCacheInputParams.BUILD_TOOLS_REVISION.name(),
                        buildToolsRevision.toString())
                .putBoolean(FileCacheInputParams.JUMBO_MODE.name(), dexOptions.getJumboMode())
                .putBoolean(FileCacheInputParams.OPTIMIZE.name(), true)
                .putBoolean(FileCacheInputParams.MULTI_DEX.name(), multiDex);

        List<String> additionalParams = dexOptions.getAdditionalParameters();
        for (int i = 0; i < additionalParams.size(); i++) {
            buildCacheInputs.putString(
                    FileCacheInputParams.ADDITIONAL_PARAMETERS.name() + "[" + i + "]",
                    additionalParams.get(i));
        }

        return buildCacheInputs.build();
    }

    /**
     * Input parameters to be provided by the client when using {@link FileCache}.
     *
     * <p>The clients of {@link FileCache} need to exhaustively specify all the inputs that affect
     * the creation of an output file/directory. This enum class lists the input parameters that are
     * used in {@link PreDexTransform.PreDexTask}.
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

        /** Revision of the build tools. */
        BUILD_TOOLS_REVISION,

        /** Whether jumbo mode is enabled. */
        JUMBO_MODE,

        /** Whether optimize is enabled. */
        OPTIMIZE,

        /** Whether multi-dex is enabled. */
        MULTI_DEX,

        /** List of additional parameters. */
        ADDITIONAL_PARAMETERS
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

    @NonNull
    private File getPreDexFile(
            @NonNull TransformOutputProvider output, @NonNull QualifiedContent qualifiedContent) {
        // In InstantRun mode, all files are guaranteed to have a unique name due to the slicer
        // transform. adding sha1 to the name can lead to cleaning issues in device, it's much
        // easier if the slices always have the same names, irrespective of the current variant,
        // last version wins.
        String name;
        if (instantRunMode
                && (qualifiedContent.getScopes().contains(Scope.PROJECT)
                || qualifiedContent.getScopes().contains(Scope.SUB_PROJECTS))) {
            name = getInstantRunFileName(qualifiedContent.getFile());
        } else {
            name = getFilename(qualifiedContent.getFile());
        }

        File contentLocation =
                output.getContentLocation(
                        name,
                        TransformManager.CONTENT_DEX,
                        qualifiedContent.getScopes(),
                        dexingMode.isMultiDex ? Format.DIRECTORY : Format.JAR);
        if (dexingMode.isMultiDex) {
            FileUtils.mkdirs(contentLocation);
        } else {
            FileUtils.mkdirs(contentLocation.getParentFile());
        }
        return contentLocation;
    }

    @NonNull
    private String getInstantRunFileName(@NonNull File inputFile) {
        if (inputFile.isDirectory()) {
            return inputFile.getName();
        } else {
            return inputFile.getName().replace(".", "_");
        }
    }

    @NonNull
    private String getFilename(@NonNull File inputFile) {
        // If multidex is enabled, this name will be used for a folder and classes*.dex files will
        // inside of it.
        String suffix = dexingMode.isMultiDex ? "" : SdkConstants.DOT_JAR;
        return FileUtils.getDirectoryNameForJar(inputFile) + suffix;
    }
}
