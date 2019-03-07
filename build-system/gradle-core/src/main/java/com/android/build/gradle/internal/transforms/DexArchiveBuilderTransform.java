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

import static com.android.build.gradle.internal.workeractions.WorkerActionServiceRegistry.INSTANCE;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import com.android.build.gradle.internal.InternalScope;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.crash.PluginCrashReporter;
import com.android.build.gradle.internal.errors.MessageReceiverImpl;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.workeractions.WorkerActionServiceRegistry;
import com.android.build.gradle.options.SyncOptions;
import com.android.builder.core.DefaultDexOptions;
import com.android.builder.core.DexOptions;
import com.android.builder.dexing.ClassFileEntry;
import com.android.builder.dexing.ClassFileInput;
import com.android.builder.dexing.ClassFileInputs;
import com.android.builder.dexing.DexArchiveBuilder;
import com.android.builder.dexing.DexArchiveBuilderConfig;
import com.android.builder.dexing.DexArchiveBuilderException;
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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
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
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logging;
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

    /**
     * Classpath resources provider is shared between invocations, and this key uniquely identifies
     * it.
     */
    public static final class ClasspathServiceKey
            implements WorkerActionServiceRegistry.ServiceKey<ClassFileProviderFactory> {
        private final long id;

        public ClasspathServiceKey(long id) {
            this.id = id;
        }

        @NonNull
        @Override
        public Class<ClassFileProviderFactory> getType() {
            return ClassFileProviderFactory.class;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ClasspathServiceKey that = (ClasspathServiceKey) o;
            return id == that.id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    /** Wrapper around the {@link com.android.builder.dexing.r8.ClassFileProviderFactory}. */
    public static final class ClasspathService
            implements WorkerActionServiceRegistry.RegisteredService<ClassFileProviderFactory> {

        private final ClassFileProviderFactory providerFactory;

        public ClasspathService(ClassFileProviderFactory providerFactory) {
            this.providerFactory = providerFactory;
        }

        @NonNull
        @Override
        public ClassFileProviderFactory getService() {
            return providerFactory;
        }

        @Override
        public void shutdown() {
            // nothing to be done, as providerFactory is a closable
        }
    }

    private static final LoggerWrapper logger =
            LoggerWrapper.getLogger(DexArchiveBuilderTransform.class);

    public static final int DEFAULT_BUFFER_SIZE_IN_KB = 100;

    public static final int NUMBER_OF_SLICES_FOR_PROJECT_CLASSES = 10;

    private static final int DEFAULT_NUM_BUCKETS =
            Math.max(Runtime.getRuntime().availableProcessors() / 2, 1);

    @NonNull private final FileCollection androidJarClasspath;
    @NonNull private final DexOptions dexOptions;
    @NonNull private final MessageReceiver messageReceiver;
    @NonNull private final SyncOptions.ErrorFormatMode errorFormatMode;
    @VisibleForTesting @NonNull final WaitableExecutor executor;
    private final int minSdkVersion;
    @NonNull private final DexerTool dexer;
    @NonNull private String projectVariant;
    @NonNull private final DexArchiveBuilderCacheHandler cacheHandler;
    private final boolean useGradleWorkers;
    private final int inBufferSize;
    private final int outBufferSize;
    private final boolean isDebuggable;
    @NonNull private final VariantScope.Java8LangSupport java8LangSupportType;
    private final int numberOfBuckets;
    private final boolean includeFeaturesInScopes;

    private boolean enableDexingArtifactTransform;

    DexArchiveBuilderTransform(
            @NonNull FileCollection androidJarClasspath,
            @NonNull DexOptions dexOptions,
            @NonNull MessageReceiver messageReceiver,
            @NonNull SyncOptions.ErrorFormatMode errorFormatMode,
            @Nullable FileCache userLevelCache,
            int minSdkVersion,
            @NonNull DexerTool dexer,
            boolean useGradleWorkers,
            @Nullable Integer inBufferSize,
            @Nullable Integer outBufferSize,
            boolean isDebuggable,
            @NonNull VariantScope.Java8LangSupport java8LangSupportType,
            @NonNull String projectVariant,
            @Nullable Integer numberOfBuckets,
            boolean includeFeaturesInScopes,
            boolean enableDexingArtifactTransform) {
        this.androidJarClasspath = androidJarClasspath;
        this.dexOptions = dexOptions;
        this.messageReceiver = messageReceiver;
        this.errorFormatMode = errorFormatMode;
        this.minSdkVersion = minSdkVersion;
        this.dexer = dexer;
        this.projectVariant = projectVariant;
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
        this.numberOfBuckets = numberOfBuckets == null ? DEFAULT_NUM_BUCKETS : numberOfBuckets;
        this.includeFeaturesInScopes = includeFeaturesInScopes;
        this.enableDexingArtifactTransform = enableDexingArtifactTransform;
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
        if (enableDexingArtifactTransform) {
            return Sets.immutableEnumSet(Scope.PROJECT);
        } else if (includeFeaturesInScopes) {
            return TransformManager.SCOPE_FULL_WITH_IR_AND_FEATURES;
        } else {
            return TransformManager.SCOPE_FULL_WITH_IR_FOR_DEXING;
        }
    }

    @NonNull
    @Override
    public Set<? super Scope> getReferencedScopes() {
        Set<? super QualifiedContent.ScopeType> referenced =
                Sets.newHashSet(Scope.PROVIDED_ONLY, Scope.TESTED_CODE);
        if (enableDexingArtifactTransform) {
            referenced.add(Scope.SUB_PROJECTS);
            referenced.add(Scope.EXTERNAL_LIBRARIES);
            referenced.add(InternalScope.MAIN_SPLIT);
            if (includeFeaturesInScopes) {
                referenced.add(InternalScope.FEATURES);
            }
        }

        return referenced;
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        try {
            Map<String, Object> params = Maps.newHashMapWithExpectedSize(6);
            params.put("optimize", !dexOptions.getAdditionalParameters().contains("--no-optimize"));
            params.put("jumbo", dexOptions.getJumboMode());
            params.put("min-sdk-version", minSdkVersion);
            params.put("dex-builder-tool", dexer.name());
            params.put("enable-dexing-artifact-transform", enableDexingArtifactTransform);

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

        if (!transformInvocation.isIncremental()) {
            outputProvider.deleteAll();
        }

        Set<File> additionalPaths;
        DesugarIncrementalTransformHelper desugarIncrementalTransformHelper;
        if (java8LangSupportType != VariantScope.Java8LangSupport.D8) {
            additionalPaths = ImmutableSet.of();
            desugarIncrementalTransformHelper = null;
        } else {
            desugarIncrementalTransformHelper =
                    new DesugarIncrementalTransformHelper(
                            projectVariant, transformInvocation, executor);
            additionalPaths =
                    desugarIncrementalTransformHelper
                            .getAdditionalPaths()
                            .stream()
                            .map(Path::toFile)
                            .collect(Collectors.toSet());
        }

        List<DexArchiveBuilderCacheHandler.CacheableItem> cacheableItems = new ArrayList<>();
        boolean isIncremental = transformInvocation.isIncremental();
        List<Path> classpath =
                getClasspath(transformInvocation, java8LangSupportType)
                        .stream()
                        .map(Paths::get)
                        .collect(Collectors.toList());
        List<Path> bootclasspath =
                getBootClasspath(androidJarClasspath, java8LangSupportType)
                        .stream()
                        .map(Paths::get)
                        .collect(Collectors.toList());

        ClasspathServiceKey bootclasspathServiceKey = null;
        ClasspathServiceKey classpathServiceKey = null;
        try (ClassFileProviderFactory bootClasspathProvider =
                        new ClassFileProviderFactory(bootclasspath);
                ClassFileProviderFactory libraryClasspathProvider =
                        new ClassFileProviderFactory(classpath)) {
            bootclasspathServiceKey = new ClasspathServiceKey(bootClasspathProvider.getId());
            classpathServiceKey = new ClasspathServiceKey(libraryClasspathProvider.getId());
            INSTANCE.registerService(
                    bootclasspathServiceKey, () -> new ClasspathService(bootClasspathProvider));
            INSTANCE.registerService(
                    classpathServiceKey, () -> new ClasspathService(libraryClasspathProvider));

            for (TransformInput input : transformInvocation.getInputs()) {

                for (DirectoryInput dirInput : input.getDirectoryInputs()) {
                    logger.verbose("Dir input %s", dirInput.getFile().toString());
                    convertToDexArchive(
                            transformInvocation.getContext(),
                            dirInput,
                            outputProvider,
                            isIncremental,
                            bootclasspathServiceKey,
                            classpathServiceKey,
                            additionalPaths);
                }

                for (JarInput jarInput : input.getJarInputs()) {
                    logger.verbose("Jar input %s", jarInput.getFile().toString());

                    D8DesugaringCacheInfo cacheInfo =
                            getD8DesugaringCacheInfo(
                                    desugarIncrementalTransformHelper,
                                    bootclasspath,
                                    classpath,
                                    jarInput);

                    List<File> dexArchives =
                            processJarInput(
                                    transformInvocation.getContext(),
                                    isIncremental,
                                    jarInput,
                                    outputProvider,
                                    bootclasspathServiceKey,
                                    classpathServiceKey,
                                    additionalPaths,
                                    cacheInfo);
                    if (cacheInfo != D8DesugaringCacheInfo.DONT_CACHE && !dexArchives.isEmpty()) {
                        cacheableItems.add(
                                new DexArchiveBuilderCacheHandler.CacheableItem(
                                        jarInput,
                                        dexArchives,
                                        cacheInfo.orderedD8DesugaringDependencies));
                    }
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
            PluginCrashReporter.maybeReportException(e);
            logger.error(null, Throwables.getStackTraceAsString(e));
            throw new TransformException(e);
        } finally {
            if (classpathServiceKey != null) {
                INSTANCE.removeService(classpathServiceKey);
            }
            if (bootclasspathServiceKey != null) {
                INSTANCE.removeService(bootclasspathServiceKey);
            }
        }
    }

    @NonNull
    private D8DesugaringCacheInfo getD8DesugaringCacheInfo(
            @Nullable DesugarIncrementalTransformHelper desugarIncrementalTransformHelper,
            @NonNull List<Path> bootclasspath,
            @NonNull List<Path> classpath,
            @NonNull JarInput jarInput) {

        if (java8LangSupportType != VariantScope.Java8LangSupport.D8) {
            return D8DesugaringCacheInfo.NO_INFO;
        }

        Preconditions.checkNotNull(desugarIncrementalTransformHelper);

        Set<Path> unorderedD8DesugaringDependencies =
                desugarIncrementalTransformHelper.getDependenciesPaths(jarInput.getFile().toPath());

        // Don't cache libraries depending on class files in folders:
        // Folders content is expected to change often so probably not worth paying the cache cost
        // if we frequently need to rebuild anyway.
        // Supporting dependency to class files would also require special care to respect order.
        if (unorderedD8DesugaringDependencies
                .stream()
                .anyMatch(path -> !path.toString().endsWith(SdkConstants.DOT_JAR))) {
            return D8DesugaringCacheInfo.DONT_CACHE;
        }

        // DesugaringGraph is not calculating the bootclasspath dependencies so just keep the full
        // bootclasspath for now.
        List<Path> bootclasspathPaths =
                bootclasspath
                        .stream()
                        .distinct()
                        .collect(Collectors.toList());

        List<Path> classpathJars =
                classpath
                        .stream()
                        .distinct()
                        .filter(unorderedD8DesugaringDependencies::contains)
                        .collect(Collectors.toList());

        List<Path> allDependencies =
                new ArrayList<>(bootclasspathPaths.size() + classpathJars.size());

        allDependencies.addAll(bootclasspathPaths);
        allDependencies.addAll(classpathJars);
        return new D8DesugaringCacheInfo(allDependencies);
    }

    private void removeDeletedEntries(
            @NonNull TransformOutputProvider outputProvider, @NonNull TransformInput transformInput)
            throws IOException {
        for (DirectoryInput input : transformInput.getDirectoryInputs()) {
            for (Map.Entry<File, Status> entry : input.getChangedFiles().entrySet()) {
                if (entry.getValue() != Status.REMOVED) {
                    continue;
                }
                File file = entry.getKey();

                Path relativePath = input.getFile().toPath().relativize(file.toPath());

                String fileToDelete;
                if (file.getName().endsWith(SdkConstants.DOT_CLASS)) {
                    fileToDelete = ClassFileEntry.withDexExtension(relativePath.toString());
                } else {
                    fileToDelete = relativePath.toString();
                }

                File outputFile = getOutputForDir(outputProvider, input);
                FileUtils.deleteRecursivelyIfExists(
                        outputFile.toPath().resolve(fileToDelete).toFile());
            }
        }
    }

    @NonNull
    private List<File> processJarInput(
            @NonNull Context context,
            boolean isIncremental,
            @NonNull JarInput jarInput,
            @NonNull TransformOutputProvider transformOutputProvider,
            @NonNull ClasspathServiceKey bootclasspath,
            @NonNull ClasspathServiceKey classpath,
            @NonNull Set<File> additionalPaths,
            @NonNull D8DesugaringCacheInfo cacheInfo)
            throws Exception {
        if (!isIncremental) {
            Preconditions.checkState(
                    jarInput.getFile().exists(),
                    "File %s does not exist, yet it is reported as input. Try \n"
                            + "cleaning the build directory.",
                    jarInput.getFile().toString());
            return convertJarToDexArchive(
                    context,
                    jarInput,
                    transformOutputProvider,
                    bootclasspath,
                    classpath,
                    cacheInfo);
        } else if (jarInput.getStatus() != Status.NOTCHANGED
                || additionalPaths.contains(jarInput.getFile())) {
            // delete all preDex jars if they exists.
            for (int bucketId = 0; bucketId < numberOfBuckets; bucketId++) {
                File shardedOutput = getOutputForJar(transformOutputProvider, jarInput, bucketId);
                FileUtils.deleteIfExists(shardedOutput);
                if (jarInput.getStatus() != Status.REMOVED) {
                    FileUtils.mkdirs(shardedOutput.getParentFile());
                }
            }
            File nonShardedOutput = getOutputForJar(transformOutputProvider, jarInput, null);
            FileUtils.deleteIfExists(nonShardedOutput);
            if (jarInput.getStatus() != Status.REMOVED) {
                FileUtils.mkdirs(nonShardedOutput.getParentFile());
            }

            // and perform dexing if necessary.
            if (jarInput.getStatus() == Status.ADDED
                    || jarInput.getStatus() == Status.CHANGED
                    || additionalPaths.contains(jarInput.getFile())) {
                return convertJarToDexArchive(
                        context,
                        jarInput,
                        transformOutputProvider,
                        bootclasspath,
                        classpath,
                        cacheInfo);
            }
        }
        return ImmutableList.of();
    }

    private List<File> convertJarToDexArchive(
            @NonNull Context context,
            @NonNull JarInput toConvert,
            @NonNull TransformOutputProvider transformOutputProvider,
            @NonNull ClasspathServiceKey bootclasspath,
            @NonNull ClasspathServiceKey classpath,
            @NonNull D8DesugaringCacheInfo cacheInfo)
            throws Exception {

        if (cacheInfo != D8DesugaringCacheInfo.DONT_CACHE) {
            File cachedVersion =
                    cacheHandler.getCachedVersionIfPresent(
                            toConvert, cacheInfo.orderedD8DesugaringDependencies);
            if (cachedVersion != null) {
                File outputFile = getOutputForJar(transformOutputProvider, toConvert, null);
                Files.copy(
                        cachedVersion.toPath(),
                        outputFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                // no need to try to cache an already cached version.
                return ImmutableList.of();
            }
        }
        return convertToDexArchive(
                context,
                toConvert,
                transformOutputProvider,
                false,
                bootclasspath,
                classpath,
                ImmutableSet.of());
    }

    public static class DexConversionParameters implements Serializable {
        private final QualifiedContent input;
        private final ClasspathServiceKey bootClasspath;
        private final ClasspathServiceKey classpath;
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
        private final VariantScope.Java8LangSupport java8LangSupportType;
        @NonNull private final Set<File> additionalPaths;
        @Nonnull private final SyncOptions.ErrorFormatMode errorFormatMode;

        public DexConversionParameters(
                @NonNull QualifiedContent input,
                @NonNull ClasspathServiceKey bootClasspath,
                @NonNull ClasspathServiceKey classpath,
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
                @NonNull VariantScope.Java8LangSupport java8LangSupportType,
                @NonNull Set<File> additionalPaths,
                @Nonnull SyncOptions.ErrorFormatMode errorFormatMode) {
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
            this.java8LangSupportType = java8LangSupportType;
            this.additionalPaths = additionalPaths;
            this.errorFormatMode = errorFormatMode;
        }

        public boolean belongsToThisBucket(String path) {
            return getBucketForFile(input, path, numberOfBuckets) == buckedId;
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
                launchProcessing(
                        dexConversionParameters,
                        System.out,
                        System.err,
                        new MessageReceiverImpl(
                                dexConversionParameters.errorFormatMode,
                                Logging.getLogger(DexArchiveBuilderTransform.class)));
            } catch (Exception e) {
                throw new BuildException(e.getMessage(), e);
            }
        }
    }

    private static class D8DesugaringCacheInfo {

        @NonNull
        private static final D8DesugaringCacheInfo NO_INFO =
                new D8DesugaringCacheInfo(Collections.emptyList());

        @NonNull
        private static final D8DesugaringCacheInfo DONT_CACHE =
                new D8DesugaringCacheInfo(Collections.emptyList());

        @NonNull private final List<Path> orderedD8DesugaringDependencies;

        private D8DesugaringCacheInfo(@NonNull List<Path> orderedD8DesugaringDependencies) {
            this.orderedD8DesugaringDependencies = orderedD8DesugaringDependencies;
        }
    }

    private static DexArchiveBuilder getDexArchiveBuilder(
            int minSdkVersion,
            @NonNull List<String> dexAdditionalParameters,
            int inBufferSize,
            int outBufferSize,
            @NonNull ClasspathServiceKey bootClasspath,
            @NonNull ClasspathServiceKey classpath,
            @NonNull DexerTool dexer,
            boolean isDebuggable,
            boolean d8DesugaringEnabled,
            @NonNull OutputStream outStream,
            @NonNull OutputStream errStream,
            @NonNull MessageReceiver messageReceiver) {

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
                                INSTANCE.getService(bootClasspath).getService(),
                                INSTANCE.getService(classpath).getService(),
                                d8DesugaringEnabled,
                                messageReceiver);
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
            @NonNull ClasspathServiceKey bootClasspath,
            @NonNull ClasspathServiceKey classpath,
            @NonNull Set<File> additionalPaths) {

        logger.verbose("Dexing %s", input.getFile().getAbsolutePath());

        ImmutableList.Builder<File> dexArchives = ImmutableList.builder();
        for (int bucketId = 0; bucketId < numberOfBuckets; bucketId++) {

            File preDexOutputFile;
            if (input instanceof DirectoryInput) {
                preDexOutputFile = getOutputForDir(outputProvider, (DirectoryInput) input);
                FileUtils.mkdirs(preDexOutputFile);
            } else {
                preDexOutputFile = getOutputForJar(outputProvider, (JarInput) input, bucketId);
            }

            dexArchives.add(preDexOutputFile);
            DexConversionParameters parameters =
                    new DexConversionParameters(
                            input,
                            bootClasspath,
                            classpath,
                            preDexOutputFile,
                            numberOfBuckets,
                            bucketId,
                            minSdkVersion,
                            dexOptions.getAdditionalParameters(),
                            inBufferSize,
                            outBufferSize,
                            dexer,
                            isDebuggable,
                            isIncremental,
                            java8LangSupportType,
                            additionalPaths,
                            errorFormatMode);

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
                                        output.getErrorOutput(),
                                        messageReceiver);
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
            @NonNull OutputStream errStream,
            @NonNull MessageReceiver receiver)
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
                        VariantScope.Java8LangSupport.D8
                                == dexConversionParameters.java8LangSupportType,
                        outStream,
                        errStream,
                        receiver);

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

        try (ClassFileInput input = ClassFileInputs.fromPath(inputPath);
                Stream<ClassFileEntry> entries = input.entries(bucketFilter)) {
            dexArchiveBuilder.convert(
                    entries,
                    Paths.get(new URI(dexConversionParameters.output)),
                    dexConversionParameters.isDirectoryBased());
        } catch (DexArchiveBuilderException ex) {
            throw new DexArchiveBuilderException("Failed to process " + inputPath.toString(), ex);
        }
    }

    @NonNull
    private static List<String> getClasspath(
            @NonNull TransformInvocation transformInvocation,
            @NonNull VariantScope.Java8LangSupport java8LangSupportType) {
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
            @NonNull FileCollection androidJarClasspath,
            @NonNull VariantScope.Java8LangSupport java8LangSupportType) {

        if (java8LangSupportType != VariantScope.Java8LangSupport.D8) {
            return Collections.emptyList();
        }
        ImmutableList.Builder<String> classpathEntries = ImmutableList.builder();
        classpathEntries.addAll(
                androidJarClasspath.getFiles().stream().map(File::getPath).iterator());

        return classpathEntries.build();
    }

    @NonNull
    private static File getOutputForJar(
            @NonNull TransformOutputProvider output,
            @NonNull JarInput qualifiedContent,
            @Nullable Integer bucketId) {
        return output.getContentLocation(
                qualifiedContent.getFile().toString() + (bucketId == null ? "" : ("-" + bucketId)),
                ImmutableSet.of(ExtendedContentType.DEX_ARCHIVE),
                qualifiedContent.getScopes(),
                Format.JAR);
    }

    @NonNull
    private File getOutputForDir(
            @NonNull TransformOutputProvider output, @NonNull DirectoryInput directoryInput) {
        return output.getContentLocation(
                directoryInput.getFile().toString(),
                ImmutableSet.of(ExtendedContentType.DEX_ARCHIVE),
                directoryInput.getScopes(),
                Format.DIRECTORY);
    }

    public static String getSliceName(int bucketId) {
        return "slice_" + bucketId;
    }

    /**
     * Returns the bucket for the specified path. For jar inputs, path in the jar file should be
     * specified (both relative and absolute path work). For directories, absolute path should be
     * specified.
     */
    private static int getBucketForFile(
            @NonNull QualifiedContent content, @NonNull String path, int numberOfBuckets) {
        if (!(content instanceof DirectoryInput)) {
            return Math.abs(path.hashCode()) % numberOfBuckets;
        } else {
            Path filePath = Paths.get(path);
            Preconditions.checkArgument(filePath.isAbsolute(), "Path should be absolute: " + path);
            Path packagePath = filePath.getParent();
            if (packagePath == null) {
                return 0;
            }
            return Math.abs(packagePath.toString().hashCode()) % numberOfBuckets;
        }
    }
}
