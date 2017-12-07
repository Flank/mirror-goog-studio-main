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

import static com.android.SdkConstants.DOT_CLASS;
import static com.android.build.api.transform.QualifiedContent.Scope;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.SecondaryFile;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.builder.core.DesugarProcessBuilder;
import com.android.builder.model.Version;
import com.android.builder.utils.FileCache;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.process.JavaProcessExecutor;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.utils.FileUtils;
import com.android.utils.PathUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.gradle.workers.WorkerExecutor;

/** Desugar all Java 8 bytecode. */
public class DesugarTransform extends Transform {

    private enum FileCacheInputParams {

        /** The input file. */
        FILE,

        /** Version of the plugin containing Desugar used to generate the output. */
        PLUGIN_VERSION,

        /** Minimum sdk version passed to Desugar, affects output. */
        MIN_SDK_VERSION,
    }

    @VisibleForTesting
    static class InputEntry {
        @Nullable private final FileCache cache;
        @Nullable private final FileCache.Inputs inputs;
        @NonNull private final Path inputPath;
        @NonNull private final Path outputPath;

        public InputEntry(
                @Nullable FileCache cache,
                @Nullable FileCache.Inputs inputs,
                @NonNull Path inputPath,
                @NonNull Path outputPath) {
            this.cache = cache;
            this.inputs = inputs;
            this.inputPath = inputPath;
            this.outputPath = outputPath;
        }

        @Nullable
        public FileCache getCache() {
            return cache;
        }

        @Nullable
        public FileCache.Inputs getInputs() {
            return inputs;
        }

        @NonNull
        public Path getInputPath() {
            return inputPath;
        }

        @NonNull
        public Path getOutputPath() {
            return outputPath;
        }
    }

    private static final LoggerWrapper logger = LoggerWrapper.getLogger(DesugarTransform.class);

    @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
    // we initialize this field only once, so having unsynchronized reads is fine
    private static final AtomicReference<Path> desugarJar = new AtomicReference<Path>(null);

    private static final String DESUGAR_JAR = "desugar_deploy.jar";
    /**
     * Minimum number of files in the directory (including subdirectories), for which we may
     * consider copying the changed class files to a temporary directory. This is in order to avoid
     * processing entire directory if only a few class files change.
     */
    static final int MIN_INPUT_SIZE_TO_COPY_TO_TMP = 400;

    @NonNull private final Supplier<List<File>> androidJarClasspath;
    @NonNull private final List<Path> compilationBootclasspath;
    @Nullable private final FileCache userCache;
    private final int minSdk;
    @NonNull private final JavaProcessExecutor executor;
    @NonNull private final Path tmpDir;
    @NonNull private final WaitableExecutor waitableExecutor;
    private boolean verbose;
    private final boolean enableGradleWorkers;
    @NonNull private final String projectVariant;
    private final boolean enableIncrementalDesugaring;

    @NonNull private Set<InputEntry> cacheMisses = Sets.newConcurrentHashSet();

    public DesugarTransform(
            @NonNull Supplier<List<File>> androidJarClasspath,
            @NonNull String compilationBootclasspath,
            @Nullable FileCache userCache,
            int minSdk,
            @NonNull JavaProcessExecutor executor,
            boolean verbose,
            boolean enableGradleWorkers,
            @NonNull Path tmpDir,
            @NonNull String projectVariant,
            boolean enableIncrementalDesugaring) {
        this(
                androidJarClasspath,
                compilationBootclasspath,
                userCache,
                minSdk,
                executor,
                verbose,
                enableGradleWorkers,
                tmpDir,
                projectVariant,
                enableIncrementalDesugaring,
                WaitableExecutor.useGlobalSharedThreadPool());
    }

    @VisibleForTesting
    DesugarTransform(
            @NonNull Supplier<List<File>> androidJarClasspath,
            @NonNull String compilationBootclasspath,
            @Nullable FileCache userCache,
            int minSdk,
            @NonNull JavaProcessExecutor executor,
            boolean verbose,
            boolean enableGradleWorkers,
            @NonNull Path tmpDir,
            @NonNull String projectVariant,
            boolean enableIncrementalDesugaring,
            @NonNull WaitableExecutor waitableExecutor) {
        this.androidJarClasspath = androidJarClasspath;
        this.compilationBootclasspath = PathUtils.getClassPathItems(compilationBootclasspath);
        this.userCache = null;
        this.minSdk = minSdk;
        this.executor = executor;
        this.waitableExecutor = waitableExecutor;
        this.verbose = verbose;
        this.enableGradleWorkers = enableGradleWorkers;
        this.tmpDir = tmpDir;
        this.projectVariant = projectVariant;
        this.enableIncrementalDesugaring = enableIncrementalDesugaring;
    }

    @NonNull
    @Override
    public String getName() {
        return "desugar";
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<? super Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @NonNull
    @Override
    public Set<? super Scope> getReferencedScopes() {
        return ImmutableSet.of(Scope.PROVIDED_ONLY, Scope.TESTED_CODE);
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        return ImmutableMap.of("Min sdk", minSdk);
    }

    @NonNull
    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        ImmutableList.Builder<SecondaryFile> files = ImmutableList.builder();
        androidJarClasspath.get().forEach(file -> files.add(SecondaryFile.nonIncremental(file)));

        compilationBootclasspath.forEach(
                file -> files.add(SecondaryFile.nonIncremental(file.toFile())));

        return files.build();
    }

    @Override
    public boolean isIncremental() {
        return enableIncrementalDesugaring;
    }

    @Override
    public void transform(@NonNull TransformInvocation transformInvocation)
            throws TransformException, InterruptedException, IOException {
        try {
            Set<File> additionalPaths = incrementalAnalysis(transformInvocation);

            initDesugarJar(userCache);
            processInputs(transformInvocation, additionalPaths);
            waitableExecutor.waitForTasksWithQuickFail(true);

            if (enableGradleWorkers) {
                processNonCachedOnesWithGradleExecutor(
                        transformInvocation.getContext().getWorkerExecutor(),
                        getClasspath(transformInvocation));
            } else {
                processNonCachedOnes(getClasspath(transformInvocation));
                waitableExecutor.waitForTasksWithQuickFail(true);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransformException(e);
        } catch (Exception e) {
            throw new TransformException(e);
        }
    }

    @NonNull
    private Set<File> incrementalAnalysis(@NonNull TransformInvocation invocation)
            throws InterruptedException {
        if (!enableIncrementalDesugaring) {
            return ImmutableSet.of();
        }

        DesugarIncrementalTransformHelper helper =
                new DesugarIncrementalTransformHelper(projectVariant);
        Set<Path> additionalPaths = helper.getAdditionalPaths(invocation, waitableExecutor);
        return additionalPaths.stream().map(Path::toFile).collect(Collectors.toSet());
    }

    @VisibleForTesting
    void processInputs(
            @NonNull TransformInvocation transformInvocation, @NonNull Set<File> additionalPaths)
            throws Exception {
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        Preconditions.checkNotNull(outputProvider);

        if (!transformInvocation.isIncremental()) {
            outputProvider.deleteAll();
        }

        for (TransformInput input : transformInvocation.getInputs()) {
            for (DirectoryInput dirInput : input.getDirectoryInputs()) {
                Path output = getOutputPath(transformInvocation.getOutputProvider(), dirInput);
                if (!dirInput.getFile().isDirectory()) {
                    PathUtils.deleteIfExists(output);
                }
                processDirectory(
                        output, additionalPaths, dirInput, transformInvocation.isIncremental());
            }

            for (JarInput jarInput : input.getJarInputs()) {
                if (transformInvocation.isIncremental()
                        && jarInput.getStatus() == Status.NOTCHANGED
                        && !additionalPaths.contains(jarInput.getFile())) {
                    continue;
                }

                Path output = getOutputPath(outputProvider, jarInput);
                PathUtils.deleteIfExists(output);
                processSingle(jarInput.getFile().toPath(), output, jarInput.getScopes());
            }
        }
    }

    @VisibleForTesting
    @NonNull
    Set<InputEntry> getCacheMisses() {
        return cacheMisses;
    }

    private void processDirectory(
            @NonNull Path output,
            @NonNull Set<File> additionalPaths,
            @NonNull DirectoryInput dirInput,
            boolean isIncremental)
            throws Exception {
        Path dirPath = dirInput.getFile().toPath();

        if (!isIncremental) {
            PathUtils.deleteIfExists(output);
            processSingle(dirPath, output, dirInput.getScopes());
            return;
        }

        List<File> additionalFromThisDir = getAllInDir(dirInput.getFile(), additionalPaths);
        Map<Status, Set<File>> byStatus = TransformInputUtil.getByStatus(dirInput);
        if (byStatus.get(Status.ADDED).isEmpty()
                && byStatus.get(Status.REMOVED).isEmpty()
                && byStatus.get(Status.CHANGED).isEmpty()
                && additionalFromThisDir.isEmpty()) {
            // nothing changed
            return;
        }

        int cntFilesToProcess =
                byStatus.get(Status.ADDED).size()
                        + byStatus.get(Status.CHANGED).size()
                        + additionalFromThisDir.size();
        int totalSize = FileUtils.getAllFiles(dirPath.toFile()).size();

        if (totalSize < MIN_INPUT_SIZE_TO_COPY_TO_TMP || cntFilesToProcess > totalSize / 10) {
            // input size too small, or too many files changed
            PathUtils.deleteIfExists(output);
            processSingle(dirPath, output, dirInput.getScopes());
            return;
        }

        // All files in the same directory whose name starts as the changed one will be copied to
        // the temporary dir, and all outputs whose name starts as the changed one will be removed.
        Files.createDirectories(tmpDir);
        Path changedClasses = Files.createTempDirectory(tmpDir, "desugar_changed");
        for (File file :
                Iterables.concat(
                        byStatus.get(Status.ADDED),
                        byStatus.get(Status.CHANGED),
                        byStatus.get(Status.REMOVED),
                        additionalFromThisDir)) {
            String name = file.getName();
            if (!name.endsWith(DOT_CLASS)) {
                continue;
            }
            File parentFile = file.getParentFile();
            if (!parentFile.isDirectory()) {
                // parent dir was removed, just remove the output mapped to that dir
                Path relativeDirPath = dirPath.relativize(parentFile.toPath());
                PathUtils.deleteIfExists(relativeDirPath);
                continue;
            }

            Path parentRelativePathToInput = dirPath.relativize(parentFile.toPath());
            Path tmpCopyDir = changedClasses.resolve(parentRelativePathToInput);
            Files.createDirectories(tmpCopyDir);

            String nameNoExt = name.substring(0, name.length() - DOT_CLASS.length());
            for (File sibling : Objects.requireNonNull(parentFile.listFiles())) {
                if (sibling.getName().startsWith(nameNoExt)
                        && sibling.getName().endsWith(DOT_CLASS)) {
                    Path finalPath = tmpCopyDir.resolve(sibling.getName());

                    if (Files.notExists(finalPath)) {
                        Files.copy(sibling.toPath(), finalPath);
                    }
                }
            }

            File outputDirForFile = output.resolve(parentRelativePathToInput).toFile();
            if (outputDirForFile.isDirectory()) {
                for (File outputSibling : Objects.requireNonNull(outputDirForFile.listFiles())) {
                    if (outputSibling.getName().startsWith(nameNoExt)
                            && outputSibling.getName().endsWith(DOT_CLASS)) {
                        FileUtils.deleteIfExists(outputSibling);
                    }
                }
            }
        }
        processSingle(changedClasses, output, dirInput.getScopes());
    }

    @NonNull
    private static List<File> getAllInDir(@NonNull File dir, @NonNull Set<File> additionalPaths) {
        List<File> inDir = new ArrayList<>();
        for (File additionalPath : additionalPaths) {
            if (FileUtils.isFileInDirectory(additionalPath, dir)) {
                inDir.add(additionalPath);
            }
        }
        return inDir;
    }

    private void processNonCachedOnes(List<Path> classpath) {
        int parallelExecutions = waitableExecutor.getParallelism();

        int index = 0;
        Multimap<Integer, InputEntry> procBuckets = ArrayListMultimap.create();
        for (InputEntry pathPathEntry : cacheMisses) {
            int bucketId = index % parallelExecutions;
            procBuckets.put(bucketId, pathPathEntry);
            index++;
        }

        List<Path> desugarBootclasspath = getBootclasspath();
        for (Integer bucketId : procBuckets.keySet()) {
            Callable<Void> callable =
                    () -> {
                        Map<Path, Path> inToOut = Maps.newHashMap();
                        for (InputEntry e : procBuckets.get(bucketId)) {
                            inToOut.put(e.getInputPath(), e.getOutputPath());
                        }

                        DesugarProcessBuilder processBuilder =
                                new DesugarProcessBuilder(
                                        desugarJar.get(),
                                        verbose,
                                        inToOut,
                                        classpath,
                                        desugarBootclasspath,
                                        minSdk,
                                        tmpDir);
                        boolean isWindows =
                                SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS;
                        executor.execute(
                                        processBuilder.build(isWindows),
                                        new LoggedProcessOutputHandler(logger))
                                .rethrowFailure()
                                .assertNormalExitValue();

                        // now copy to the cache because now we have the file
                        for (InputEntry e : procBuckets.get(bucketId)) {
                            if (e.getCache() != null && e.getInputs() != null) {
                                e.getCache()
                                        .createFileInCacheIfAbsent(
                                                e.getInputs(),
                                                in -> Files.copy(e.getOutputPath(), in.toPath()));
                            }
                        }

                        return null;
                    };
            waitableExecutor.execute(callable);
        }
    }

    private void processNonCachedOnesWithGradleExecutor(
            WorkerExecutor workerExecutor, List<Path> classpath)
            throws IOException, ExecutionException {
        List<Path> desugarBootclasspath = getBootclasspath();
        for (InputEntry pathPathEntry : cacheMisses) {
            DesugarWorkerItem workerItem =
                    new DesugarWorkerItem(
                            desugarJar.get(),
                            PathUtils.createTmpDirToRemoveOnShutdown("gradle_lambdas"),
                            true,
                            pathPathEntry.getInputPath(),
                            pathPathEntry.getOutputPath(),
                            classpath,
                            desugarBootclasspath,
                            minSdk);

            workerExecutor.submit(DesugarWorkerItem.DesugarAction.class, workerItem::configure);
        }

        workerExecutor.await();

        for (InputEntry e : cacheMisses) {
            if (e.getCache() != null && e.getInputs() != null) {
                e.getCache()
                        .createFileInCacheIfAbsent(
                                e.getInputs(), in -> Files.copy(e.getOutputPath(), in.toPath()));
            }
        }
    }

    @NonNull
    private List<Path> getClasspath(@NonNull TransformInvocation transformInvocation) {
        ImmutableList.Builder<Path> classpathEntries = ImmutableList.builder();

        classpathEntries.addAll(
                TransformInputUtil.getAllFiles(transformInvocation.getInputs())
                        .stream()
                        .map(File::toPath)
                        .iterator());

        classpathEntries.addAll(
                TransformInputUtil.getAllFiles(transformInvocation.getReferencedInputs())
                        .stream()
                        .map(File::toPath)
                        .iterator());

        return classpathEntries.build();
    }

    @NonNull
    private List<Path> getBootclasspath() {
        List<Path> desugarBootclasspath =
                androidJarClasspath.get().stream().map(File::toPath).collect(Collectors.toList());
        desugarBootclasspath.addAll(compilationBootclasspath);

        return desugarBootclasspath;
    }

    private void processSingle(
            @NonNull Path input, @NonNull Path output, @NonNull Set<? super Scope> scopes) {
        waitableExecutor.execute(
                () -> {
                    if (Files.notExists(input)) {
                        return null;
                    }

                    if (output.toString().endsWith(SdkConstants.DOT_JAR)) {
                        Files.createDirectories(output.getParent());
                    } else {
                        Files.createDirectories(output);
                    }

                    FileCache cacheToUse;
                    if (Files.isRegularFile(input)
                            && Objects.equals(
                                    scopes, Collections.singleton(Scope.EXTERNAL_LIBRARIES))) {
                        cacheToUse = userCache;
                    } else {
                        cacheToUse = null;
                    }

                    processUsingCache(input, output, cacheToUse);
                    return null;
                });
    }

    private void processUsingCache(
            @NonNull Path input, @NonNull Path output, @Nullable FileCache cache) {
        if (cache != null) {
            try {
                FileCache.Inputs cacheKey = getBuildCacheInputs(input, minSdk);
                if (cache.cacheEntryExists(cacheKey)) {
                    FileCache.QueryResult result =
                            cache.createFile(
                                    output.toFile(),
                                    cacheKey,
                                    () -> {
                                        throw new AssertionError("Entry should exist.");
                                    });

                    if (result.getQueryEvent().equals(FileCache.QueryEvent.CORRUPTED)) {
                        Objects.requireNonNull(result.getCauseOfCorruption());
                        logger.verbose(
                                "The build cache at '%1$s' contained an invalid cache entry.\n"
                                        + "Cause: %2$s\n"
                                        + "We have recreated the cache entry.\n",
                                cache.getCacheDirectory().getAbsolutePath(),
                                Throwables.getStackTraceAsString(result.getCauseOfCorruption()));
                    }

                    if (Files.notExists(output)) {
                        throw new RuntimeException(
                                String.format(
                                        "Entry for %s is invalid. Please clean your build cache "
                                                + "under %s.",
                                        output.toString(),
                                        cache.getCacheDirectory().getAbsolutePath()));
                    }
                } else {
                    cacheMissAction(cache, cacheKey, input, output);
                }
            } catch (Exception exception) {
                logger.error(
                        null,
                        String.format(
                                "Unable to Desugar '%1$s' to '%2$s' using the build cache at"
                                        + " '%3$s'.\n",
                                input.toString(),
                                output.toString(),
                                cache.getCacheDirectory().getAbsolutePath()));
                throw new RuntimeException(exception);
            }
        } else {
            cacheMissAction(null, null, input, output);
        }
    }

    private void cacheMissAction(
            @Nullable FileCache cache,
            @Nullable FileCache.Inputs inputs,
            @NonNull Path input,
            @NonNull Path output) {
        // add it to the list of cache misses, that will be processed
        cacheMisses.add(new InputEntry(cache, inputs, input, output));
    }

    @NonNull
    private static Path getOutputPath(
            @NonNull TransformOutputProvider outputProvider, @NonNull QualifiedContent content) {
        return outputProvider
                .getContentLocation(
                        content.getName(),
                        content.getContentTypes(),
                        content.getScopes(),
                        content instanceof DirectoryInput ? Format.DIRECTORY : Format.JAR)
                .toPath();
    }

    @NonNull
    private static FileCache.Inputs getBuildCacheInputs(@NonNull Path input, int minSdkVersion) {
        FileCache.Inputs.Builder buildCacheInputs =
                new FileCache.Inputs.Builder(FileCache.Command.DESUGAR_LIBRARY);

        buildCacheInputs
                .putFile(
                        FileCacheInputParams.FILE.name(),
                        input.toFile(),
                        FileCache.FileProperties.PATH_HASH)
                .putString(
                        FileCacheInputParams.PLUGIN_VERSION.name(),
                        Version.ANDROID_GRADLE_PLUGIN_VERSION)
                .putLong(FileCacheInputParams.MIN_SDK_VERSION.name(), minSdkVersion);

        return buildCacheInputs.build();
    }

    /** Set this location of extracted desugar jar that is used for processing. */
    private static void initDesugarJar(@Nullable FileCache cache) throws IOException {
        if (isDesugarJarInitialized()) {
            return;
        }

        URL url = DesugarProcessBuilder.class.getClassLoader().getResource(DESUGAR_JAR);
        Preconditions.checkNotNull(url);

        Path extractedDesugar = null;
        if (cache != null) {
            try {
                String fileHash;
                try (HashingInputStream stream =
                        new HashingInputStream(Hashing.sha256(), url.openStream())) {
                    fileHash = stream.hash().toString();
                }
                FileCache.Inputs inputs =
                        new FileCache.Inputs.Builder(FileCache.Command.EXTRACT_DESUGAR_JAR)
                                .putString("pluginVersion", Version.ANDROID_GRADLE_PLUGIN_VERSION)
                                .putString("jarUrl", url.toString())
                                .putString("fileHash", fileHash)
                                .build();

                File cachedFile =
                        cache.createFileInCacheIfAbsent(
                                        inputs, file -> copyDesugarJar(url, file.toPath()))
                                .getCachedFile();
                Preconditions.checkNotNull(cachedFile);
                extractedDesugar = cachedFile.toPath();
            } catch (IOException | ExecutionException e) {
                logger.error(e, "Unable to cache Desugar jar. Extracting to temp dir.");
            }
        }

        synchronized (desugarJar) {
            if (isDesugarJarInitialized()) {
                return;
            }

            if (extractedDesugar == null) {
                extractedDesugar = PathUtils.createTmpToRemoveOnShutdown(DESUGAR_JAR);
                copyDesugarJar(url, extractedDesugar);
            }
            desugarJar.set(extractedDesugar);
        }
    }

    private static void copyDesugarJar(@NonNull URL inputUrl, @NonNull Path targetPath)
            throws IOException {
        try (InputStream inputStream = inputUrl.openConnection().getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean isDesugarJarInitialized() {
        return desugarJar.get() != null && Files.isRegularFile(desugarJar.get());
    }
}
