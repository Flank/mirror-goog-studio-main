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
import com.android.builder.dexing.DexMergerTool;
import com.android.builder.dexing.DexingType;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.DexParser;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutput;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.stream.Collectors;
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
 * #isIncremental()}). However, in {@link DexingType#MONO_DEX} and {@link
 * DexingType#LEGACY_MULTIDEX} modes, we will need to pass entire list of dex archives for merging.
 * This includes inputs that have not changed as well, as merged does not support incremental
 * merging of DEX files currently. Therefore, incremental and full build are the same in these two
 * modes.
 *
 * <p>In {@link DexingType#NATIVE_MULTIDEX} mode, we will process only updated dex archives in the
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
    private static final int ANDROID_L_MAX_DEX_FILES = 100;
    // We assume the maximum number of dexes that will be produced from the external dependencies is
    // JAR_DEX_FILES, so the remaining ANDROID_L_MAX_DEX_FILES - JAR_DEX_FILES can be used for
    // program classes. This is a generous assumption that 50 completely full dex files will be
    // needed for external dependencies.
    private static final int JAR_DEX_FILES = 50;

    @NonNull private final DexingType dexingType;
    @Nullable private final FileCollection mainDexListFile;
    @NonNull private final DexMergerTool dexMerger;
    private final int minSdkVersion;
    private final boolean isDebuggable;
    @NonNull private final ErrorReporter errorReporter;
    @NonNull private final ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();

    public DexMergerTransform(
            @NonNull DexingType dexingType,
            @Nullable FileCollection mainDexListFile,
            @NonNull ErrorReporter errorReporter,
            @NonNull DexMergerTool dexMerger,
            int minSdkVersion,
            boolean isDebuggable) {
        this.dexingType = dexingType;
        this.mainDexListFile = mainDexListFile;
        this.dexMerger = dexMerger;
        this.minSdkVersion = minSdkVersion;
        this.isDebuggable = isDebuggable;
        Preconditions.checkState(
                (dexingType == DexingType.LEGACY_MULTIDEX) == (mainDexListFile != null),
                "Main dex list must only be set when in legacy multidex");
        this.errorReporter = errorReporter;
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
        params.put("dexing-type", dexingType.name());
        params.put("dex-merger-tool", dexMerger.name());

        return params;
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public boolean isCacheable() {
        return true;
    }

    @Override
    public void transform(@NonNull TransformInvocation transformInvocation)
            throws TransformException, IOException, InterruptedException {
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        Preconditions.checkNotNull(
                outputProvider, "Missing output object for transform " + getName());

        if (dexMerger == DexMergerTool.D8) {
            logger.info("D8 is used to merge dex.");
        }

        ProcessOutputHandler outputHandler =
                new ParsingProcessOutputHandler(
                        new ToolOutputParser(new DexParser(), Message.Kind.ERROR, logger),
                        new ToolOutputParser(new DexParser(), logger),
                        errorReporter);

        if (!transformInvocation.isIncremental()) {
            outputProvider.deleteAll();
        }

        ProcessOutput output = null;
        List<ForkJoinTask<Void>> mergeTasks;
        try (Closeable ignored = output = outputHandler.createOutput()) {
            if (dexingType == DexingType.NATIVE_MULTIDEX) {
                mergeTasks =
                        handleNativeMultiDex(
                                transformInvocation.getInputs(),
                                output,
                                outputProvider,
                                transformInvocation.isIncremental());
            } else {
                mergeTasks =
                        handleLegacyAndMonoDex(
                                transformInvocation.getInputs(), output, outputProvider);
            }

            // now wait for all merge tasks completion
            mergeTasks.forEach(ForkJoinTask::join);

        } catch (IOException e) {
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
    @NonNull
    private List<ForkJoinTask<Void>> handleLegacyAndMonoDex(
            @NonNull Collection<TransformInput> inputs,
            @NonNull ProcessOutput output,
            @NonNull TransformOutputProvider outputProvider)
            throws IOException {
        ImmutableList.Builder<Path> dexArchiveBuilder = ImmutableList.builder();
        TransformInputUtil.getDirectories(inputs)
                .stream()
                .map(File::toPath)
                .forEach(dexArchiveBuilder::add);
        inputs.stream()
                .flatMap(transformInput -> transformInput.getJarInputs().stream())
                .filter(jarInput -> jarInput.getStatus() != Status.REMOVED)
                .map(jarInput -> jarInput.getFile().toPath())
                .forEach(dexArchiveBuilder::add);

        ImmutableList<Path> dexesToMerge = dexArchiveBuilder.build();
        if (dexesToMerge.isEmpty()) {
            return ImmutableList.of();
        }

        File outputDir =
                getDexOutputLocation(outputProvider, "main", TransformManager.SCOPE_FULL_PROJECT);
        // this deletes and creates the dir for the output
        FileUtils.cleanOutputDir(outputDir);

        Path mainDexClasses;
        if (mainDexListFile == null) {
            mainDexClasses = null;
        } else {
            mainDexClasses = mainDexListFile.getSingleFile().toPath();
        }

        return ImmutableList.of(submitForMerging(output, outputDir, dexesToMerge, mainDexClasses));
    }

    /**
     * All external library inputs will be merged together (this may result in multiple DEX files),
     * while other inputs will be merged individually (merging a single input might also result in
     * multiple DEX files).
     */
    @NonNull
    private List<ForkJoinTask<Void>> handleNativeMultiDex(
            @NonNull Collection<TransformInput> inputs,
            @NonNull ProcessOutput output,
            @NonNull TransformOutputProvider outputProvider,
            boolean isIncremental)
            throws IOException {

        ImmutableList.Builder<ForkJoinTask<Void>> subTasks = ImmutableList.builder();
        Multimap<Status, Path> externalLibs = ArrayListMultimap.create();
        List<DirectoryInput> directoryInputs =
                inputs.stream()
                        .flatMap(i -> i.getDirectoryInputs().stream())
                        .collect(Collectors.toList());
        subTasks.addAll(processDirectories(output, outputProvider, isIncremental, directoryInputs));

        List<JarInput> jarInputs =
                inputs.stream()
                        .flatMap(i -> i.getJarInputs().stream())
                        .collect(Collectors.toList());
        subTasks.addAll(
                processJars(output, outputProvider, isIncremental, jarInputs, externalLibs));

        File externalLibsOutput =
                getDexOutputLocation(
                        outputProvider, "externalLibs", ImmutableSet.of(Scope.EXTERNAL_LIBRARIES));

        if (!isIncremental
                || externalLibs.containsKey(Status.CHANGED)
                || externalLibs.containsKey(Status.ADDED)
                || externalLibs.containsKey(Status.REMOVED)) {
            // if non-incremental, or inputs have changed, merge again
            FileUtils.cleanOutputDir(externalLibsOutput);
            Iterable<Path> externalLibsToMerge =
                    Iterables.concat(
                            externalLibs.get(Status.CHANGED),
                            externalLibs.get(Status.NOTCHANGED),
                            externalLibs.get(Status.ADDED));
            if (!Iterables.isEmpty(externalLibsToMerge)) {
                subTasks.add(
                        submitForMerging(output, externalLibsOutput, externalLibsToMerge, null));
            }
        }
        return subTasks.build();
    }

    /**
     * If directory inputs should be merge individually, or we should merge all of them together.
     * Reason for this check is that on L (API levels 21 and 22) there is a 100 dex files limit that
     * we might hit if each of the directory inputs is merged individually.
     */
    private boolean shouldMergeAllDirInputs(@NonNull Collection<DirectoryInput> directories) {
        if (minSdkVersion > 22) {
            return false;
        }

        long dirInputsCount = directories.stream().filter(d -> d.getFile().exists()).count();
        return dirInputsCount > ANDROID_L_MAX_DEX_FILES - JAR_DEX_FILES;
    }

    private List<ForkJoinTask<Void>> processJars(
            @NonNull ProcessOutput output,
            @NonNull TransformOutputProvider outputProvider,
            boolean isIncremental,
            @NonNull Collection<JarInput> inputs,
            @NonNull Multimap<Status, Path> externalLibs)
            throws IOException {
        ImmutableList.Builder<ForkJoinTask<Void>> subTasks = ImmutableList.builder();
        for (JarInput jarInput : inputs) {
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
                subTasks.add(
                        submitForMerging(
                                output,
                                dexOutput,
                                ImmutableList.of(jarInput.getFile().toPath()),
                                null));
            }
        }
        return subTasks.build();
    }

    private List<ForkJoinTask<Void>> processDirectories(
            @NonNull ProcessOutput output,
            @NonNull TransformOutputProvider outputProvider,
            boolean isIncremental,
            @NonNull Collection<DirectoryInput> inputs)
            throws IOException {
        ImmutableList.Builder<ForkJoinTask<Void>> subTasks = ImmutableList.builder();
        List<DirectoryInput> deleted = new ArrayList<>();
        List<DirectoryInput> changed = new ArrayList<>();
        List<DirectoryInput> notChanged = new ArrayList<>();

        for (DirectoryInput directoryInput : inputs) {
            Path rootFolder = directoryInput.getFile().toPath();
            if (!Files.isDirectory(rootFolder)) {
                deleted.add(directoryInput);
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
                    changed.add(directoryInput);
                } else {
                    notChanged.add(directoryInput);
                }
            }
        }

        if (shouldMergeAllDirInputs(inputs)) {
            File dexOutput =
                    getDexOutputLocation(
                            outputProvider, "directories", ImmutableSet.of(Scope.PROJECT));
            if (!deleted.isEmpty() || !changed.isEmpty()) {
                FileUtils.cleanOutputDir(dexOutput);
            }

            if (!changed.isEmpty() || !notChanged.isEmpty()) {
                List<Path> toMerge = new ArrayList<>(changed.size() + notChanged.size());
                for (DirectoryInput input : Iterables.concat(changed, notChanged)) {
                    toMerge.add(input.getFile().toPath());
                }
                subTasks.add(submitForMerging(output, dexOutput, toMerge, null));
            }
        } else {
            for (DirectoryInput directoryInput : deleted) {
                File dexOutput =
                        getDexOutputLocation(
                                outputProvider,
                                directoryInput.getName(),
                                directoryInput.getScopes());
                FileUtils.cleanOutputDir(dexOutput);
            }
            for (DirectoryInput directoryInput : changed) {
                File dexOutput =
                        getDexOutputLocation(
                                outputProvider,
                                directoryInput.getName(),
                                directoryInput.getScopes());
                FileUtils.cleanOutputDir(dexOutput);
                subTasks.add(
                        submitForMerging(
                                output,
                                dexOutput,
                                ImmutableList.of(directoryInput.getFile().toPath()),
                                null));
            }
        }
        return subTasks.build();
    }

    /**
     * Add a merging task to the queue of tasks.
     *
     * @param output the process output that dx will output to.
     * @param dexOutputDir the directory to output dexes to
     * @param dexArchives the dex archive inputs
     * @param mainDexList the list of classes to keep in the main dex. Must be set <em>if and
     *     only</em> legacy multidex mode is used.
     * @return the {@link ForkJoinTask} instance for the submission.
     */
    @NonNull
    private ForkJoinTask<Void> submitForMerging(
            @NonNull ProcessOutput output,
            @NonNull File dexOutputDir,
            @NonNull Iterable<Path> dexArchives,
            @Nullable Path mainDexList) {
        DexMergerTransformCallable callable =
                new DexMergerTransformCallable(
                        dexingType,
                        output,
                        dexOutputDir,
                        dexArchives,
                        mainDexList,
                        forkJoinPool,
                        dexMerger,
                        minSdkVersion,
                        isDebuggable);
        return forkJoinPool.submit(callable);
    }

    @NonNull
    private File getDexOutputLocation(
            @NonNull TransformOutputProvider outputProvider,
            @NonNull String name,
            @NonNull Set<? super Scope> scopes) {
        return outputProvider.getContentLocation(name, getOutputTypes(), scopes, Format.DIRECTORY);
    }
}
