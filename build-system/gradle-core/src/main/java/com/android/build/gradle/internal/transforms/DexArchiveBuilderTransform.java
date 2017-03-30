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
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.builder.core.DefaultDexOptions;
import com.android.builder.core.DexOptions;
import com.android.builder.core.ErrorReporter;
import com.android.builder.utils.FileCache;
import com.android.dx.Version;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.DexParser;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutput;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Transform that converts CLASS files to dex archives, {@link
 * com.android.builder.dexing.DexArchive}. This will consume {@link TransformManager#CONTENT_CLASS},
 * and for each of the inputs, corresponding dex archive will be produced.
 *
 * <p>This transform is incremental, only changed streams will be converted again. Additionally, if
 * an input stream is able to provide a list of individual files that were changed, only those files
 * will be processed. Their corresponding dex archives will be updated.
 */
public class DexArchiveBuilderTransform extends Transform {

    private static final LoggerWrapper logger =
            LoggerWrapper.getLogger(DexArchiveBuilderTransform.class);

    @NonNull private final DexOptions dexOptions;
    @NonNull private final ErrorReporter errorReporter;
    @Nullable private final FileCache userLevelCache;
    @Nullable private final FileCache projectLevelCache;
    private boolean instantRunMode;
    @NonNull private final WaitableExecutor<Void> executor;
    private final int minSdkVersion;

    public DexArchiveBuilderTransform(
            @NonNull DexOptions dexOptions,
            @NonNull ErrorReporter errorReporter,
            @Nullable FileCache userLevelCache,
            @Nullable FileCache projectLevelCache,
            boolean instantRunMode,
            int minSdkVersion) {
        this.dexOptions = dexOptions;
        this.errorReporter = errorReporter;
        this.userLevelCache = userLevelCache;
        this.projectLevelCache = projectLevelCache;
        this.instantRunMode = instantRunMode;
        this.minSdkVersion = minSdkVersion;
        this.executor = WaitableExecutor.useGlobalSharedThreadPool();
    }

    @NonNull
    @Override
    public String getName() {
        return "dexBuilder";
    }

    @NonNull
    @Override
    public Set<ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<ContentType> getOutputTypes() {
        return ImmutableSet.of(ExtendedContentType.DEX_ARCHIVE);
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
            Map<String, Object> params = Maps.newHashMapWithExpectedSize(4);
            params.put("optimize", !dexOptions.getAdditionalParameters().contains("--no-optimize"));
            params.put("jumbo", dexOptions.getJumboMode());
            params.put("dx-version", Version.VERSION);
            params.put("min-sdk-version", minSdkVersion);

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

        if (dexOptions.getAdditionalParameters().contains("--no-optimize")) {
            logger.warning(DefaultDexOptions.OPTIMIZE_WARNING);
        }

        logger.verbose("Task is incremental : %b ", transformInvocation.isIncremental());

        ProcessOutputHandler outputHandler =
                new ParsingProcessOutputHandler(
                        new ToolOutputParser(new DexParser(), Message.Kind.ERROR, logger),
                        new ToolOutputParser(new DexParser(), logger),
                        errorReporter);

        if (!transformInvocation.isIncremental()) {
            outputProvider.deleteAll();
        }

        ProcessOutput processOutput = null;
        try (Closeable ignored = processOutput = outputHandler.createOutput()) {
            // hash to detect duplicate inputs (due to issue with library and tests)
            final Set<String> hashes = Sets.newHashSet();

            for (TransformInput input : transformInvocation.getInputs()) {
                for (DirectoryInput dirInput : input.getDirectoryInputs()) {
                    logger.verbose("Dir input %s", dirInput.getFile().toString());
                    File preDexOutputFile = getPreDexFile(outputProvider, dirInput);
                    processDirectoryInput(
                            preDexOutputFile,
                            processOutput,
                            hashes,
                            dirInput,
                            transformInvocation.isIncremental());
                }

                for (JarInput jarInput : input.getJarInputs()) {
                    logger.verbose("Jar input %s", jarInput.getFile().toString());
                    File preDexFile = getPreDexFile(outputProvider, jarInput);
                    processJarInput(
                            preDexFile,
                            processOutput,
                            hashes,
                            jarInput,
                            transformInvocation.isIncremental());
                }
            }

            executor.waitForTasksWithQuickFail(true);

            logger.verbose("Done with all dex archive conversions");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransformException(e);
        } catch (Exception e) {
            throw new TransformException(e);
        } finally {
            if (processOutput != null) {
                try {
                    outputHandler.handleOutput(processOutput);
                } catch (ProcessException e) {
                    // ignore this one
                }
            }
        }
    }

    private void processJarInput(
            @NonNull File preDexFile,
            @NonNull ProcessOutput processOutput,
            @NonNull Set<String> hashes,
            @NonNull JarInput jarInput,
            boolean isIncremental)
            throws Exception {
        if (!isIncremental) {
            if (jarInput.getFile().exists()) {
                convertToDexArchive(
                        jarInput, p -> true, p -> false, preDexFile, hashes, processOutput);
            } else {
                deleteDexArchive(jarInput.getFile());
            }
        } else {
            if (jarInput.getStatus() == Status.REMOVED) {
                deleteDexArchive(preDexFile);
            } else if (jarInput.getStatus() == Status.ADDED
                    || jarInput.getStatus() == Status.CHANGED) {
                convertToDexArchive(
                        jarInput, p -> true, p -> false, preDexFile, hashes, processOutput);
            }
        }
    }

    /** Returns if the qualified content is an external jar. */
    private static boolean isExternalLib(@NonNull QualifiedContent content) {
        return content.getFile().isFile()
                && content.getScopes().equals(Collections.singleton(Scope.EXTERNAL_LIBRARIES))
                && content.getContentTypes()
                        .equals(Collections.singleton(QualifiedContent.DefaultContentType.CLASSES));
    }

    private void processDirectoryInput(
            @NonNull File preDexFile,
            @NonNull ProcessOutput processOutput,
            @NonNull Set<String> hashes,
            @NonNull DirectoryInput directoryInput,
            boolean isIncremental)
            throws Exception {
        Path rootFolder = directoryInput.getFile().toPath();
        // The incremental mode only detect file level changes.
        // It does not handle removed root folders. However the transform
        // task will add the TransformInput right after it's removed so that it
        // can be detected by the transform.
        if (!Files.isDirectory(rootFolder)) {
            deleteDexArchive(preDexFile);
        } else if (!isIncremental) {
            // non-incremental; just re-run
            convertToDexArchive(
                    directoryInput, p -> true, p -> false, preDexFile, hashes, processOutput);
        } else {
            Predicate<Path> toRemove =
                    path -> {
                        File resolved = rootFolder.resolve(path).toFile();
                        return directoryInput.getChangedFiles().get(resolved) == Status.REMOVED;
                    };
            Predicate<Path> toProcess =
                    path -> {
                        File resolved = rootFolder.resolve(path).toFile();
                        Status status = directoryInput.getChangedFiles().get(resolved);
                        return status == Status.ADDED || status == Status.CHANGED;
                    };

            convertToDexArchive(
                    directoryInput, toProcess, toRemove, preDexFile, hashes, processOutput);
        }
    }

    private void convertToDexArchive(
            @NonNull QualifiedContent input,
            @NonNull Predicate<Path> toProcess,
            @NonNull Predicate<Path> toRemove,
            @NonNull File preDexFile,
            @NonNull Set<String> hashes,
            @NonNull ProcessOutput processOutput)
            throws Exception {
        // use only for external libs
        FileCache userCache =
                PreDexTransform.getBuildCache(
                        input.getFile(), isExternalLib(input), userLevelCache);
        // use only for jars
        FileCache projectCache = input.getFile().isFile() ? projectLevelCache : null;

        DexArchiveBuilderTransformCallable converter =
                new DexArchiveBuilderTransformCallable(
                        input.getFile().toPath(),
                        toProcess,
                        toRemove,
                        preDexFile,
                        hashes,
                        processOutput,
                        userCache,
                        projectCache,
                        dexOptions,
                        minSdkVersion);
        executor.execute(converter);
    }

    private void deleteDexArchive(@NonNull File toDelete) throws IOException {
        executor.execute(
                () -> {
                    FileUtils.deleteIfExists(toDelete);
                    return null;
                });
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
            name = PreDexTransform.getInstantRunFileName(qualifiedContent.getFile());
        } else {
            name = FileUtils.getDirectoryNameForJar(qualifiedContent.getFile());
        }

        Format outputFormat;
        if (qualifiedContent.getFile().isDirectory()) {
            outputFormat = Format.DIRECTORY;
        } else {
            outputFormat = Format.JAR;
        }

        File contentLocation =
                output.getContentLocation(
                        name,
                        ImmutableSet.of(ExtendedContentType.DEX_ARCHIVE),
                        qualifiedContent.getScopes(),
                        outputFormat);

        FileUtils.mkdirs(contentLocation.getParentFile());
        if (outputFormat == Format.DIRECTORY) {
            FileUtils.mkdirs(contentLocation);
        }
        return contentLocation;
    }
}
