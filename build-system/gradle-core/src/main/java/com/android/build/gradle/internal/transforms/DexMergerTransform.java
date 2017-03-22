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
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.SecondaryFile;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.builder.core.ErrorReporter;
import com.android.builder.dexing.DexingMode;
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
import com.google.common.base.Throwables;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.gradle.api.file.FileCollection;

/**
 * This transform processes dex archives, {@link ExtendedContentType#DEX_ARCHIVE}, and merges them
 * to final DEX file(s).
 *
 * <p>It consumes all streams having one of the {@link
 * TransformManager#SCOPE_FULL_WITH_IR_FOR_DEXING} scopes, and {@link
 * ExtendedContentType#DEX_ARCHIVE} type. Output it produces has {@link
 * TransformManager#CONTENT_DEX} type.
 *
 * <p>This transform will try to get incremental updates about its inputs (see {@link
 * #isIncremental()}). However, in {@link DexingMode#MONO_DEX} and {@link
 * DexingMode#LEGACY_MULTIDEX} modes, we will need to pass entire list of dex archives for merging.
 * This includes inputs that have not changed as well, as merged does not support incremental
 * merging of DEX files currently. Therefore, incremental and full build are the same in these two
 * modes.
 *
 * <p>In {@link DexingMode#NATIVE_MULTIDEX} mode, we will process only updated dex archives in the
 * following way. For full builds, all external jar libraries will be merged to DEX file(s).
 * Remaining inputs will produce a DEX file per input i.e. dex archive. Reason for this is that the
 * external libraries rarely change, and native multidex mode on android L does not support more
 * than 100 DEX files (see <a href="http://b.android.com/233093">http://b.android.com/233093</a>).
 * This means that in the incremental case, if the a dex archive of an external library has changed,
 * we will re-merge all external libraries again. If a dex archive of other type of input has
 * changed, we will re-merge only that dex archive.
 */
public class DexMergerTransform extends Transform {

    private static final LoggerWrapper logger = LoggerWrapper.getLogger(DexMergerTransform.class);

    @NonNull private final DexingMode dexingMode;
    @Nullable private final FileCollection mainDexListFile;
    @NonNull private final ErrorReporter errorReporter;
    @NonNull private final WaitableExecutor<Void> callableExecutor;
    @NonNull private final WaitableExecutor<Void> dexMergerExecutor;

    public DexMergerTransform(
            @NonNull DexingMode dexingMode,
            @Nullable FileCollection mainDexListFile,
            @NonNull ErrorReporter errorReporter) {
        this.dexingMode = dexingMode;
        this.mainDexListFile = mainDexListFile;
        Preconditions.checkState(
                (dexingMode == DexingMode.LEGACY_MULTIDEX) == (mainDexListFile != null),
                "Main dex list must only be set when in legacy multidex");
        this.errorReporter = errorReporter;
        this.callableExecutor = WaitableExecutor.useGlobalSharedThreadPool();
        this.dexMergerExecutor = WaitableExecutor.useGlobalSharedThreadPool();
    }

    @NonNull
    @Override
    public String getName() {
        return "dexMerger";
    }

    @NonNull
    @Override
    public Set<ContentType> getInputTypes() {
        return ImmutableSet.of(ExtendedContentType.DEX_ARCHIVE);
    }

    @NonNull
    @Override
    public Set<ContentType> getOutputTypes() {
        return TransformManager.CONTENT_DEX;
    }

    @NonNull
    @Override
    public Set<? super Scope> getScopes() {
        return TransformManager.SCOPE_FULL_WITH_IR_FOR_DEXING;
    }

    @NonNull
    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        if (mainDexListFile != null) {
            return ImmutableList.of(SecondaryFile.nonIncremental(mainDexListFile));
        } else {
            return ImmutableList.of();
        }
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        Map<String, Object> params = Maps.newHashMapWithExpectedSize(2);
        params.put("dexing-mode", dexingMode.name());
        params.put("dx-version", Version.VERSION);

        return params;
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(@NonNull TransformInvocation transformInvocation)
            throws TransformException, IOException, InterruptedException {
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        Preconditions.checkNotNull(
                outputProvider, "Missing output object for transform " + getName());

        ProcessOutputHandler outputHandler =
                new ParsingProcessOutputHandler(
                        new ToolOutputParser(new DexParser(), Message.Kind.ERROR, logger),
                        new ToolOutputParser(new DexParser(), logger),
                        errorReporter);

        if (!transformInvocation.isIncremental()) {
            outputProvider.deleteAll();
        }

        ProcessOutput output = null;
        try (Closeable ignored = output = outputHandler.createOutput()) {
            if (dexingMode == DexingMode.NATIVE_MULTIDEX) {
                handleNativeMultiDex(
                        transformInvocation.getInputs(),
                        output,
                        outputProvider,
                        transformInvocation.isIncremental());
            } else {
                handleLegacyAndMonoDex(transformInvocation.getInputs(), output, outputProvider);
            }

            callableExecutor.waitForTasksWithQuickFail(true);
            dexMergerExecutor.waitForTasksWithQuickFail(true);
        } catch (IOException e) {
            throw new TransformException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransformException(e);
        } catch (Exception e) {
            throw new TransformException(Throwables.getRootCause(e));
        } finally {
            if (output != null) {
                try {
                    outputHandler.handleOutput(output);
                } catch (ProcessException e) {
                    // ignore this one
                }
            }
        }
    }

    /** For legacy and mono-dex we always merge all dex archives, non-incrementally. */
    private void handleLegacyAndMonoDex(
            @NonNull Collection<TransformInput> inputs,
            @NonNull ProcessOutput output,
            @NonNull TransformOutputProvider outputProvider)
            throws IOException {
        ImmutableList.Builder<Path> dexArchiveBuilder = ImmutableList.builder();
        TransformInputUtil.getDirectories(inputs)
                .stream()
                .map(File::toPath)
                .forEach(dexArchiveBuilder::add);
        TransformInputUtil.getJarFiles(inputs)
                .stream()
                .map(File::toPath)
                .forEach(dexArchiveBuilder::add);

        File outputDir =
                getDexOutputLocation(outputProvider, "main", TransformManager.SCOPE_FULL_PROJECT);
        // this deletes and creates the dir for the output
        FileUtils.cleanOutputDir(outputDir);

        Set<String> mainDexClasses;
        if (mainDexListFile == null) {
            mainDexClasses = null;
        } else {
            mainDexClasses = Sets.newHashSet();
            Path mainDexListPath = mainDexListFile.getSingleFile().toPath();
            mainDexClasses.addAll(Files.readAllLines(mainDexListPath));
        }

        submitForMerging(output, outputDir, dexArchiveBuilder.build(), mainDexClasses);
    }

    /**
     * All external library inputs will be merged together (this may result in multiple DEX files),
     * while other inputs will be merged individually (merging a single input might also result in
     * multiple DEX files).
     */
    private void handleNativeMultiDex(
            @NonNull Collection<TransformInput> inputs,
            @NonNull ProcessOutput output,
            @NonNull TransformOutputProvider outputProvider,
            boolean isIncremental)
            throws IOException {

        Multimap<Status, Path> externalLibs = ArrayListMultimap.create();
        for (TransformInput input : inputs) {
            processDirectories(output, outputProvider, isIncremental, input);
            processJars(output, outputProvider, isIncremental, input, externalLibs);
        }

        File externalLibsOutput =
                getDexOutputLocation(
                        outputProvider, "externalLibs", ImmutableSet.of(Scope.EXTERNAL_LIBRARIES));

        if (!isIncremental
                || externalLibs.keySet().contains(Status.CHANGED)
                || externalLibs.keySet().contains(Status.ADDED)
                || externalLibs.keys().contains(Status.REMOVED)) {
            // if non-incremental, or inputs have changed, merge again
            FileUtils.cleanOutputDir(externalLibsOutput);
            if (!externalLibs.isEmpty()) {
                submitForMerging(output, externalLibsOutput, externalLibs.values(), null);
            }
        }
    }

    private void processJars(
            @NonNull ProcessOutput output,
            @NonNull TransformOutputProvider outputProvider,
            boolean isIncremental,
            @NonNull TransformInput input,
            @NonNull Multimap<Status, Path> externalLibs)
            throws IOException {
        for (JarInput jarInput : input.getJarInputs()) {
            if (jarInput.getScopes().equals(Collections.singleton(Scope.EXTERNAL_LIBRARIES))) {
                externalLibs.put(jarInput.getStatus(), jarInput.getFile().toPath());
                continue;
            }

            File dexOutput =
                    getDexOutputLocation(outputProvider, jarInput.getName(), jarInput.getScopes());

            if (!isIncremental || jarInput.getStatus() != Status.NOTCHANGED) {
                FileUtils.cleanOutputDir(dexOutput);
            }

            if (!isIncremental
                    || jarInput.getStatus() == Status.ADDED
                    || jarInput.getStatus() == Status.CHANGED) {
                submitForMerging(
                        output, dexOutput, ImmutableList.of(jarInput.getFile().toPath()), null);
            }
        }
    }

    private void processDirectories(
            @NonNull ProcessOutput output,
            @NonNull TransformOutputProvider outputProvider,
            boolean isIncremental,
            TransformInput input)
            throws IOException {
        for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
            Path rootFolder = directoryInput.getFile().toPath();
            File dexOutput =
                    getDexOutputLocation(
                            outputProvider, directoryInput.getName(), directoryInput.getScopes());
            // The incremental mode only detect file level changes.
            // It does not handle removed root folders. However the transform
            // task will add the TransformInput right after it's removed so that it
            // can be detected by the transform.
            if (!Files.isDirectory(rootFolder)) {
                FileUtils.deleteDirectoryContents(dexOutput);
            } else {
                boolean runAgain = !isIncremental;

                if (!runAgain) {
                    // check the incremental case
                    Collection<Status> statuses = directoryInput.getChangedFiles().values();
                    runAgain =
                            statuses.contains(Status.ADDED)
                                    || statuses.contains(Status.REMOVED)
                                    || statuses.contains(Status.CHANGED);
                }

                if (runAgain) {
                    FileUtils.cleanOutputDir(dexOutput);
                    submitForMerging(
                            output,
                            dexOutput,
                            ImmutableList.of(directoryInput.getFile().toPath()),
                            null);
                }
            }
        }
    }

    /**
     * Add a merging task to the queue of tasks.
     *
     * @param output the process output that dx will output to.
     * @param dexOutputDir the directory to output dexes to
     * @param dexArchives the dex archive inputs
     * @param mainDexList the list of classes to keep in the main dex. Must be set <em>if and only
     *     if</em> the dex mode is legacy multidex.
     */
    private void submitForMerging(
            @NonNull ProcessOutput output,
            @NonNull File dexOutputDir,
            @NonNull Collection<Path> dexArchives,
            @Nullable Set<String> mainDexList) {
        DexMergerTransformCallable callable =
                new DexMergerTransformCallable(
                        dexingMode,
                        output,
                        dexOutputDir,
                        dexArchives,
                        mainDexList,
                        dexMergerExecutor);
        callableExecutor.execute(callable);
    }

    @NonNull
    private File getDexOutputLocation(
            @NonNull TransformOutputProvider outputProvider,
            @NonNull String name,
            @NonNull Set<? super Scope> scopes) {
        return outputProvider.getContentLocation(name, getOutputTypes(), scopes, Format.DIRECTORY);
    }
}
