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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.api.transform.Context;
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
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.builder.core.DefaultDexOptions;
import com.android.builder.core.DexOptions;
import com.android.builder.dexing.ClassFileEntry;
import com.android.builder.dexing.ClassFileInput;
import com.android.builder.dexing.ClassFileInputs;
import com.android.builder.dexing.DexArchive;
import com.android.builder.dexing.DexArchiveBuilder;
import com.android.builder.dexing.DexArchiveBuilderConfig;
import com.android.builder.dexing.DexArchiveBuilderException;
import com.android.builder.dexing.DexArchives;
import com.android.builder.dexing.DexerTool;
import com.android.builder.dexing.r8.ClassFileProviderFactory;
import com.android.builder.utils.FileCache;
import com.android.dx.command.dexer.DxContext;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.MessageReceiver;
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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Streams;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.gradle.tooling.BuildException;
import org.gradle.workers.IsolationMode;

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

    public static final int DEFAULT_BUFFER_SIZE_IN_KB = 100;

    public static final int NUMBER_OF_BUCKETS = 5;

    @NonNull private final Supplier<List<File>> androidJarClasspath;
    @NonNull private final DexOptions dexOptions;
    @NonNull private final MessageReceiver messageReceiver;
    @VisibleForTesting @NonNull final WaitableExecutor executor;
    private final int minSdkVersion;
    @NonNull private final DexerTool dexer;
    @NonNull private String projectVariant;
    private boolean enableIncrementalDesugaring;
    @NonNull private final DexArchiveBuilderCacheHandler cacheHandler;
    private final boolean useGradleWorkers;
    private final int inBufferSize;
    private final int outBufferSize;
    private final boolean isDebuggable;
    @NonNull private final VariantScope.Java8LangSupport java8LangSupportType;

    DexArchiveBuilderTransform(
            @NonNull Supplier<List<File>> androidJarClasspath,
            @NonNull DexOptions dexOptions,
            @NonNull MessageReceiver messageReceiver,
            @Nullable FileCache userLevelCache,
            int minSdkVersion,
            @NonNull DexerTool dexer,
            boolean useGradleWorkers,
            @Nullable Integer inBufferSize,
            @Nullable Integer outBufferSize,
            boolean isDebuggable,
            @NonNull VariantScope.Java8LangSupport java8LangSupportType,
            @NonNull String projectVariant,
            boolean enableIncrementalDesugaring) {
        this.androidJarClasspath = androidJarClasspath;
        this.dexOptions = dexOptions;
        this.messageReceiver = messageReceiver;
        this.minSdkVersion = minSdkVersion;
        this.dexer = dexer;
        this.projectVariant = projectVariant;
        this.enableIncrementalDesugaring = enableIncrementalDesugaring;
        this.executor = WaitableExecutor.useGlobalSharedThreadPool();
        this.cacheHandler =
                new DexArchiveBuilderCacheHandler(
                        userLevelCache, dexOptions, minSdkVersion, isDebuggable, dexer);
        this.useGradleWorkers = useGradleWorkers;
        this.inBufferSize =
                (inBufferSize == null ? DEFAULT_BUFFER_SIZE_IN_KB : inBufferSize) * 1024;
        this.outBufferSize =
                (outBufferSize == null ? DEFAULT_BUFFER_SIZE_IN_KB : outBufferSize) * 1024;
        this.isDebuggable = isDebuggable;
        this.java8LangSupportType = java8LangSupportType;
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
        return TransformManager.SCOPE_FULL_WITH_IR_FOR_DEXING;
    }

    @NonNull
    @Override
    public Set<? super Scope> getReferencedScopes() {
        return ImmutableSet.of(Scope.PROVIDED_ONLY, Scope.TESTED_CODE);
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        try {
            Map<String, Object> params = Maps.newHashMapWithExpectedSize(4);
            params.put("optimize", !dexOptions.getAdditionalParameters().contains("--no-optimize"));
            params.put("jumbo", dexOptions.getJumboMode());
            params.put("min-sdk-version", minSdkVersion);
            params.put("dex-builder-tool", dexer.name());

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

        if (dexer == DexerTool.D8) {
            logger.info("D8 is used to build dex.");
        }

        if (dexOptions.getAdditionalParameters().contains("--no-optimize")) {
            logger.warning(DefaultDexOptions.OPTIMIZE_WARNING);
        }

        logger.verbose("Task is incremental : %b ", transformInvocation.isIncremental());

        if (!transformInvocation.isIncremental()) {
            outputProvider.deleteAll();
        }

        Set<File> additionalPaths =
                incrementalAnalysis(transformInvocation)
                        .stream()
                        .map(Path::toFile)
                        .collect(Collectors.toSet());
        ClassFileProviderFactory classFileProviderFactory = new ClassFileProviderFactory();
        List<DexArchiveBuilderCacheHandler.CacheableItem> cacheableItems = new ArrayList<>();
        boolean isIncremental = transformInvocation.isIncremental();
        try {
            for (TransformInput input : transformInvocation.getInputs()) {

                List<String> bootclasspath =
                        getBootClasspath(androidJarClasspath, java8LangSupportType);
                List<String> classpath = getClasspath(transformInvocation, java8LangSupportType);

                for (DirectoryInput dirInput : input.getDirectoryInputs()) {
                    logger.verbose("Dir input %s", dirInput.getFile().toString());
                    convertToDexArchive(
                            transformInvocation.getContext(),
                            dirInput,
                            outputProvider,
                            isIncremental,
                            classFileProviderFactory,
                            bootclasspath,
                            classpath,
                            additionalPaths);
                }

                for (JarInput jarInput : input.getJarInputs()) {
                    logger.verbose("Jar input %s", jarInput.getFile().toString());
                    List<File> dexArchives =
                            processJarInput(
                                    transformInvocation.getContext(),
                                    isIncremental,
                                    jarInput,
                                    outputProvider,
                                    classFileProviderFactory,
                                    bootclasspath,
                                    classpath,
                                    additionalPaths);
                    cacheableItems.add(
                            new DexArchiveBuilderCacheHandler.CacheableItem(
                                    jarInput, dexArchives, bootclasspath, classpath));
                }
            }

            // all work items have been submitted, now wait for completion.
            if (useGradleWorkers) {
                transformInvocation.getContext().getWorkerExecutor().await();
            } else {
                executor.waitForTasksWithQuickFail(true);
            }

            // if we are in incremental mode, delete all removed files.
            if (transformInvocation.isIncremental()) {
                for (TransformInput transformInput : transformInvocation.getInputs()) {
                    removeDeletedEntries(outputProvider, transformInput);
                }
            }

            // and finally populate the caches.
            if (!cacheableItems.isEmpty()) {
                cacheHandler.populateCache(cacheableItems);
            }

            logger.verbose("Done with all dex archive conversions");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransformException(e);
        } catch (Exception e) {
            logger.error(null, Throwables.getStackTraceAsString(e));
            throw new TransformException(e);
        }
    }

    private static void removeDeletedEntries(
            @NonNull TransformOutputProvider outputProvider, @NonNull TransformInput transformInput)
            throws IOException {
        for (DirectoryInput directoryInput : transformInput.getDirectoryInputs()) {
            File outputFile = getPreDexFolder(outputProvider, directoryInput);
            try (DexArchive output = DexArchives.fromInput(outputFile.toPath())) {
                for (Map.Entry<File, Status> fileStatusEntry :
                        directoryInput.getChangedFiles().entrySet()) {
                    if (fileStatusEntry.getValue() == Status.REMOVED) {
                        Path relativePath =
                                directoryInput
                                        .getFile()
                                        .toPath()
                                        .relativize(fileStatusEntry.getKey().toPath());
                        String fileToDelete;
                        if (fileStatusEntry.getKey().getName().endsWith(SdkConstants.DOT_CLASS)) {
                            fileToDelete = ClassFileEntry.withDexExtension(relativePath.toString());
                        } else {
                            fileToDelete = relativePath.toString();
                        }
                        output.removeFile(fileToDelete);
                    }
                }
            }
        }
    }

    @NonNull
    private List<File> processJarInput(
            @NonNull Context context,
            boolean isIncremental,
            @NonNull JarInput jarInput,
            @NonNull TransformOutputProvider transformOutputProvider,
            @NonNull ClassFileProviderFactory classFileProviderFactory,
            @NonNull List<String> bootclasspath,
            @NonNull List<String> classpath,
            @NonNull Set<File> additionalPaths)
            throws Exception {
        if (!isIncremental || additionalPaths.contains(jarInput.getFile())) {
            Preconditions.checkState(
                    jarInput.getFile().exists(),
                    "File %s does not exist, yet it is reported as input. Try \n"
                            + "cleaning the build directory.",
                    jarInput.getFile().toString());
            return convertJarToDexArchive(
                    context,
                    jarInput,
                    transformOutputProvider,
                    classFileProviderFactory,
                    bootclasspath,
                    classpath);
        } else if (jarInput.getStatus() != Status.NOTCHANGED) {
            // delete all preDex jars if they exists.
            for (int bucketId = 0; bucketId < NUMBER_OF_BUCKETS; bucketId++) {
                File shardedOutput = getPreDexJar(transformOutputProvider, jarInput, bucketId);
                FileUtils.deleteIfExists(shardedOutput);
                if (jarInput.getStatus() != Status.REMOVED) {
                    FileUtils.mkdirs(shardedOutput.getParentFile());
                }
            }
            File nonShardedOutput = getPreDexJar(transformOutputProvider, jarInput, null);
            FileUtils.deleteIfExists(nonShardedOutput);
            if (jarInput.getStatus() != Status.REMOVED) {
                FileUtils.mkdirs(nonShardedOutput.getParentFile());
            }

            // and perform dexing if necessary.
            if (jarInput.getStatus() == Status.ADDED || jarInput.getStatus() == Status.CHANGED) {
                return convertJarToDexArchive(
                        context,
                        jarInput,
                        transformOutputProvider,
                        classFileProviderFactory,
                        bootclasspath,
                        classpath);
            }
        }
        return ImmutableList.of();
    }

    private List<File> convertJarToDexArchive(
            @NonNull Context context,
            @NonNull JarInput toConvert,
            @NonNull TransformOutputProvider transformOutputProvider,
            @NonNull ClassFileProviderFactory classFileProviderFactory,
            @NonNull List<String> bootclasspath,
            @NonNull List<String> classpath)
            throws Exception {

        File cachedVersion =
                cacheHandler.getCachedVersionIfPresent(toConvert, bootclasspath, classpath);
        if (cachedVersion == null) {
            return convertToDexArchive(
                    context,
                    toConvert,
                    transformOutputProvider,
                    false,
                    classFileProviderFactory,
                    bootclasspath,
                    classpath,
                    ImmutableSet.of());
        } else {
            File outputFile = getPreDexJar(transformOutputProvider, toConvert, null);
            Files.copy(
                    cachedVersion.toPath(),
                    outputFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            // no need to try to cache an already cached version.
            return ImmutableList.of();
        }
    }

    public static class DexConversionParameters implements Serializable {
        private final QualifiedContent input;
        private final List<String> bootClasspath;
        private final List<String> classpath;
        private final String output;
        private final int numberOfBuckets;
        private final int buckedId;
        private final int minSdkVersion;
        private final List<String> dexAdditionalParameters;
        private final int inBufferSize;
        private final int outBufferSize;
        private final DexerTool dexer;
        private final boolean isDebuggable;
        private final boolean isIncremental;
        private final ClassFileProviderFactory classFileProviderFactory;
        private final VariantScope.Java8LangSupport java8LangSupportType;
        @NonNull private final Set<File> additionalPaths;

        public DexConversionParameters(
                @NonNull QualifiedContent input,
                @NonNull List<String> bootClasspath,
                @NonNull List<String> classpath,
                @NonNull File output,
                int numberOfBuckets,
                int buckedId,
                int minSdkVersion,
                @NonNull List<String> dexAdditionalParameters,
                int inBufferSize,
                int outBufferSize,
                @NonNull DexerTool dexer,
                boolean isDebuggable,
                boolean isIncremental,
                @NonNull ClassFileProviderFactory classFileProviderFactory,
                @NonNull VariantScope.Java8LangSupport java8LangSupportType,
                @NonNull Set<File> additionalPaths) {
            this.input = input;
            this.bootClasspath = bootClasspath;
            this.classpath = classpath;
            this.numberOfBuckets = numberOfBuckets;
            this.buckedId = buckedId;
            this.output = output.toURI().toString();
            this.minSdkVersion = minSdkVersion;
            this.dexAdditionalParameters = dexAdditionalParameters;
            this.inBufferSize = inBufferSize;
            this.outBufferSize = outBufferSize;
            this.dexer = dexer;
            this.isDebuggable = isDebuggable;
            this.isIncremental = isIncremental;
            this.classFileProviderFactory = classFileProviderFactory;
            this.java8LangSupportType = java8LangSupportType;
            this.additionalPaths = additionalPaths;
        }

        public boolean belongsToThisBucket(String path) {
            return (Math.abs(path.hashCode()) % numberOfBuckets) == buckedId;
        }

        public boolean isDirectoryBased() {
            return input instanceof DirectoryInput;
        }
    }

    public static class DexConversionWorkAction implements Runnable {

        private final DexConversionParameters dexConversionParameters;

        @Inject
        public DexConversionWorkAction(@NonNull DexConversionParameters dexConversionParameters) {
            this.dexConversionParameters = dexConversionParameters;
        }

        @Override
        public void run() {
            try {
                launchProcessing(dexConversionParameters, System.out, System.err);
            } catch (Exception e) {
                throw new BuildException(e.getMessage(), e);
            }
        }
    }

    private static DexArchiveBuilder getDexArchiveBuilder(
            int minSdkVersion,
            @NonNull List<String> dexAdditionalParameters,
            int inBufferSize,
            int outBufferSize,
            @NonNull List<String> bootClasspath,
            @NonNull List<String> classpath,
            @NonNull DexerTool dexer,
            boolean isDebuggable,
            @NonNull ClassFileProviderFactory classFileProviderFactory,
            boolean d8DesugaringEnabled,
            @NonNull OutputStream outStream,
            @NonNull OutputStream errStream)
            throws IOException {

        DexArchiveBuilder dexArchiveBuilder;
        switch (dexer) {
            case DX:
                boolean optimizedDex = !dexAdditionalParameters.contains("--no-optimize");
                DxContext dxContext = new DxContext(outStream, errStream);
                DexArchiveBuilderConfig config =
                        new DexArchiveBuilderConfig(
                                dxContext,
                                optimizedDex,
                                inBufferSize,
                                minSdkVersion,
                                DexerTool.DX,
                                outBufferSize,
                                DexArchiveBuilderCacheHandler.isJumboModeEnabledForDx());

                dexArchiveBuilder = DexArchiveBuilder.createDxDexBuilder(config);
                break;
            case D8:
                dexArchiveBuilder =
                        DexArchiveBuilder.createD8DexBuilder(
                                minSdkVersion,
                                isDebuggable,
                                bootClasspath
                                        .stream()
                                        .map(string -> Paths.get(string))
                                        .collect(Collectors.toList()),
                                classpath
                                        .stream()
                                        .map(string -> Paths.get(string))
                                        .collect(Collectors.toList()),
                                classFileProviderFactory,
                                d8DesugaringEnabled);
                break;
            default:
                throw new AssertionError("Unknown dexer type: " + dexer.name());
        }
        return dexArchiveBuilder;
    }

    private List<File> convertToDexArchive(
            @NonNull Context context,
            @NonNull QualifiedContent input,
            @NonNull TransformOutputProvider outputProvider,
            boolean isIncremental,
            @NonNull ClassFileProviderFactory classFileProviderFactory,
            @NonNull List<String> bootClasspath,
            @NonNull List<String> classpath,
            @NonNull Set<File> additionalPaths)
            throws Exception {

        logger.verbose("Dexing %s", input.getFile().getAbsolutePath());

        ImmutableList.Builder<File> dexArchives = ImmutableList.builder();
        for (int bucketId = 0; bucketId < NUMBER_OF_BUCKETS; bucketId++) {

            File preDexOutputFile = getPreDexFile(outputProvider, input, bucketId);
            dexArchives.add(preDexOutputFile);
            DexConversionParameters parameters =
                    new DexConversionParameters(
                            input,
                            bootClasspath,
                            classpath,
                            preDexOutputFile,
                            NUMBER_OF_BUCKETS,
                            bucketId,
                            minSdkVersion,
                            dexOptions.getAdditionalParameters(),
                            inBufferSize,
                            outBufferSize,
                            dexer,
                            isDebuggable,
                            isIncremental,
                            classFileProviderFactory,
                            java8LangSupportType,
                            additionalPaths);

            if (useGradleWorkers) {
                context.getWorkerExecutor()
                        .submit(
                                DexConversionWorkAction.class,
                                configuration -> {
                                    configuration.setIsolationMode(IsolationMode.NONE);
                                    configuration.setParams(parameters);
                                });
            } else {
                executor.execute(
                        () -> {
                            ProcessOutputHandler outputHandler =
                                    new ParsingProcessOutputHandler(
                                            new ToolOutputParser(
                                                    new DexParser(), Message.Kind.ERROR, logger),
                                            new ToolOutputParser(new DexParser(), logger),
                                            messageReceiver);
                            ProcessOutput output = null;
                            try (Closeable ignored = output = outputHandler.createOutput()) {
                                launchProcessing(
                                        parameters,
                                        output.getStandardOutput(),
                                        output.getErrorOutput());
                            } finally {
                                if (output != null) {
                                    try {
                                        outputHandler.handleOutput(output);
                                    } catch (ProcessException e) {
                                        // ignore this one
                                    }
                                }
                            }
                            return null;
                        });
            }
        }
        return dexArchives.build();
    }

    private static void launchProcessing(
            @NonNull DexConversionParameters dexConversionParameters,
            @NonNull OutputStream outStream,
            @NonNull OutputStream errStream)
            throws IOException, URISyntaxException {
        DexArchiveBuilder dexArchiveBuilder =
                getDexArchiveBuilder(
                        dexConversionParameters.minSdkVersion,
                        dexConversionParameters.dexAdditionalParameters,
                        dexConversionParameters.inBufferSize,
                        dexConversionParameters.outBufferSize,
                        dexConversionParameters.bootClasspath,
                        dexConversionParameters.classpath,
                        dexConversionParameters.dexer,
                        dexConversionParameters.isDebuggable,
                        dexConversionParameters.classFileProviderFactory,
                        VariantScope.Java8LangSupport.D8
                                == dexConversionParameters.java8LangSupportType,
                        outStream,
                        errStream);

        Path inputPath = dexConversionParameters.input.getFile().toPath();
        Predicate<String> bucketFilter = dexConversionParameters::belongsToThisBucket;

        boolean hasIncrementalInfo =
                dexConversionParameters.isDirectoryBased() && dexConversionParameters.isIncremental;
        Predicate<String> toProcess =
                hasIncrementalInfo
                        ? path -> {
                            File resolved = inputPath.resolve(path).toFile();
                            if (dexConversionParameters.additionalPaths.contains(resolved)) {
                                return true;
                            }
                            Map<File, Status> changedFiles =
                                    ((DirectoryInput) dexConversionParameters.input)
                                            .getChangedFiles();

                            Status status = changedFiles.get(resolved);
                            return status == Status.ADDED || status == Status.CHANGED;
                        }
                        : path -> true;

        bucketFilter = bucketFilter.and(toProcess);

        logger.verbose("Dexing '" + inputPath + "' to '" + dexConversionParameters.output + "'");

        try (ClassFileInput input = ClassFileInputs.fromPath(inputPath)) {
            dexArchiveBuilder.convert(
                    input.entries(bucketFilter),
                    Paths.get(new URI(dexConversionParameters.output)),
                    dexConversionParameters.isDirectoryBased());
        } catch (DexArchiveBuilderException ex) {
            throw new DexArchiveBuilderException("Failed to process " + inputPath.toString(), ex);
        }
    }

    @NonNull
    private static List<String> getClasspath(
            @NonNull TransformInvocation transformInvocation,
            @NonNull VariantScope.Java8LangSupport java8LangSupportType)
            throws IOException {
        if (java8LangSupportType != VariantScope.Java8LangSupport.D8) {
            return Collections.emptyList();
        }
        ImmutableList.Builder<String> classpathEntries = ImmutableList.builder();

        Iterable<TransformInput> dependencies =
                Iterables.concat(
                        transformInvocation.getInputs(), transformInvocation.getReferencedInputs());
        classpathEntries.addAll(
                TransformInputUtil.getDirectories(dependencies)
                        .stream()
                        .map(File::getPath)
                        .distinct()
                        .iterator());

        classpathEntries.addAll(
                Streams.stream(dependencies)
                        .flatMap(transformInput -> transformInput.getJarInputs().stream())
                        .filter(jarInput -> jarInput.getStatus() != Status.REMOVED)
                        .map(jarInput -> jarInput.getFile().getPath())
                        .distinct()
                        .iterator());

        return classpathEntries.build();
    }

    @NonNull
    private static List<String> getBootClasspath(
            @NonNull Supplier<List<File>> androidJarClasspath,
            @NonNull VariantScope.Java8LangSupport java8LangSupportType)
            throws IOException {

        if (java8LangSupportType != VariantScope.Java8LangSupport.D8) {
            return Collections.emptyList();
        }
        ImmutableList.Builder<String> classpathEntries = ImmutableList.builder();
        classpathEntries.addAll(
                androidJarClasspath.get().stream().map(file -> file.getPath()).iterator());

        return classpathEntries.build();
    }

    @NonNull
    private static File getPreDexFile(
            @NonNull TransformOutputProvider output,
            @NonNull QualifiedContent qualifiedContent,
            int bucketId) {

        return qualifiedContent.getFile().isDirectory()
                ? getPreDexFolder(output, (DirectoryInput) qualifiedContent)
                : getPreDexJar(output, (JarInput) qualifiedContent, bucketId);
    }

    @NonNull
    private static File getPreDexJar(
            @NonNull TransformOutputProvider output,
            @NonNull JarInput qualifiedContent,
            @Nullable Integer bucketId) {

        return output.getContentLocation(
                qualifiedContent.getName() + (bucketId == null ? "" : ("-" + bucketId)),
                ImmutableSet.of(ExtendedContentType.DEX_ARCHIVE),
                qualifiedContent.getScopes(),
                Format.JAR);
    }

    @NonNull
    private static File getPreDexFolder(
            @NonNull TransformOutputProvider output, @NonNull DirectoryInput directoryInput) {

        return FileUtils.mkdirs(
                output.getContentLocation(
                        directoryInput.getName(),
                        ImmutableSet.of(ExtendedContentType.DEX_ARCHIVE),
                        directoryInput.getScopes(),
                        Format.DIRECTORY));
    }

    @NonNull
    private Set<Path> incrementalAnalysis(@NonNull TransformInvocation invocation)
            throws InterruptedException {
        if (!enableIncrementalDesugaring
                || java8LangSupportType != VariantScope.Java8LangSupport.D8) {
            return ImmutableSet.of();
        }

        DesugarIncrementalTransformHelper helper =
                new DesugarIncrementalTransformHelper(projectVariant);
        return helper.getAdditionalPaths(invocation, executor);
    }
}
