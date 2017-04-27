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

import static com.android.build.api.transform.QualifiedContent.Scope;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import com.android.builder.Version;
import com.android.builder.core.DesugarProcessBuilder;
import com.android.builder.utils.FileCache;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.process.JavaProcessExecutor;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessException;
import com.android.utils.PathUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.gradle.api.file.FileCollection;

/** Desugar all Java 8 bytecode. */
public class DesugarTransform extends Transform {

    private enum FileCacheInputParams {

        /** Path of an input file. */
        FILE_PATH,

        /** Relative path of an input file exploded from an aar file. */
        EXPLODED_AAR_FILE_PATH,

        /** Name of an input file that is an instant-run.jar file. */
        INSTANT_RUN_JAR_FILE_NAME,

        /** Hash of an input file. */
        FILE_HASH,

        /** Version of the plugin containing Desugar used to generate the output. */
        PLUGIN_VERSION,

        /** Minimum sdk version passed to Desugar, affects output. */
        MIN_SDK_VERSION,
    }

    private static class InputEntry {
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

    @NonNull private final Supplier<List<File>> androidJarClasspath;
    @NonNull private final List<Path> compilationBootclasspath;
    @Nullable private final FileCache userCache;
    @Nullable private final FileCache projectCache;
    private final int minSdk;
    @NonNull private final JavaProcessExecutor executor;
    @NonNull private FileCollection java8LangSupportJar;
    @NonNull private final WaitableExecutor waitableExecutor;
    private boolean verbose;

    @NonNull private Set<InputEntry> cacheMisses = Sets.newConcurrentHashSet();

    @Nullable private Path tmpDirForClasspathJars = null;

    public DesugarTransform(
            @NonNull Supplier<List<File>> androidJarClasspath,
            @NonNull String compilationBootclasspath,
            @Nullable FileCache userCache,
            @Nullable FileCache projectCache,
            int minSdk,
            @NonNull JavaProcessExecutor executor,
            @NonNull FileCollection java8LangSupportJar,
            boolean verbose) {
        this.androidJarClasspath = androidJarClasspath;
        this.compilationBootclasspath = splitBootclasspath(compilationBootclasspath);
        this.userCache = userCache;
        this.projectCache = projectCache;
        this.minSdk = minSdk;
        this.executor = executor;
        this.java8LangSupportJar = java8LangSupportJar;
        this.waitableExecutor = WaitableExecutor.useGlobalSharedThreadPool();
        this.verbose = verbose;
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

        files.add(SecondaryFile.nonIncremental(java8LangSupportJar));

        return files.build();
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(@NonNull TransformInvocation transformInvocation)
            throws TransformException, InterruptedException, IOException {
        try {
            processInputs(transformInvocation);
            waitableExecutor.waitForTasksWithQuickFail(true);

            processNonCachedOnes(getClasspath(transformInvocation));
            waitableExecutor.waitForTasksWithQuickFail(true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransformException(e);
        } catch (Exception e) {
            throw new TransformException(e);
        } finally {
            removeTmpClasspathJars();
        }
    }

    private void processInputs(@NonNull TransformInvocation transformInvocation) throws Exception {
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        Preconditions.checkNotNull(outputProvider);

        if (!transformInvocation.isIncremental()) {
            outputProvider.deleteAll();
        }

        for (TransformInput input : transformInvocation.getInputs()) {
            for (DirectoryInput dirInput : input.getDirectoryInputs()) {
                Path rootFolder = dirInput.getFile().toPath();
                Path output = getOutputPath(transformInvocation.getOutputProvider(), dirInput);
                if (Files.notExists(rootFolder)) {
                    PathUtils.deleteIfExists(output);
                } else {
                    Set<Status> statuses = Sets.newHashSet(dirInput.getChangedFiles().values());
                    boolean reRun =
                            !transformInvocation.isIncremental()
                                    || !Objects.equals(
                                            statuses, Collections.singleton(Status.NOTCHANGED));

                    if (reRun) {
                        PathUtils.deleteIfExists(output);
                        processSingle(rootFolder, output, dirInput.getScopes());
                    }
                }
            }

            for (JarInput jarInput : input.getJarInputs()) {
                if (transformInvocation.isIncremental()
                        && jarInput.getStatus() == Status.NOTCHANGED) {
                    continue;
                }

                Path output = getOutputPath(outputProvider, jarInput);
                PathUtils.deleteIfExists(output);
                processSingle(jarInput.getFile().toPath(), output, jarInput.getScopes());
            }
        }
    }

    private void processNonCachedOnes(List<Path> classpath) throws IOException, ProcessException {
        int parallelExecutions = waitableExecutor.getParallelism();

        int index = 0;
        Multimap<Integer, InputEntry> procBuckets = ArrayListMultimap.create();
        for (InputEntry pathPathEntry : cacheMisses) {
            int bucketId = index % parallelExecutions;
            procBuckets.put(bucketId, pathPathEntry);
            index++;
        }

        for (Integer bucketId : procBuckets.keySet()) {
            Callable<Void> callable =
                    () -> {
                        Map<Path, Path> inToOut = Maps.newHashMap();
                        for (InputEntry e : procBuckets.get(bucketId)) {
                            inToOut.put(e.getInputPath(), e.getOutputPath());
                        }

                        DesugarProcessBuilder processBuilder =
                                new DesugarProcessBuilder(
                                        java8LangSupportJar.getSingleFile().toPath(),
                                        verbose,
                                        inToOut,
                                        classpath,
                                        this.compilationBootclasspath,
                                        minSdk);
                        executor.execute(
                                        processBuilder.build(),
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

    @NonNull
    private List<Path> getClasspath(@NonNull TransformInvocation transformInvocation)
            throws IOException {
        Iterator<Path> inputs =
                TransformInputUtil.getAllFiles(transformInvocation.getInputs())
                        .stream()
                        .map(File::toPath)
                        .iterator();

        Iterator<Path> referenced =
                TransformInputUtil.getAllFiles(transformInvocation.getReferencedInputs())
                        .stream()
                        .map(File::toPath)
                        .iterator();

        Iterator<Path> androidBootclasspath =
                androidJarClasspath.get().stream().map(File::toPath).iterator();

        tmpDirForClasspathJars = Files.createTempDirectory("desugar_classpath");
        assert tmpDirForClasspathJars != null;

        return Lists.newArrayList(Iterators.concat(inputs, referenced, androidBootclasspath))
                .stream()
                .map(e -> convertDirToJar(e, tmpDirForClasspathJars))
                .collect(Collectors.toList());
    }

    private void processSingle(
            @NonNull Path input, @NonNull Path output, @NonNull Set<? super Scope> scopes)
            throws Exception {
        waitableExecutor.execute(
                () -> {
                    if (output.toString().endsWith(SdkConstants.DOT_JAR)) {
                        Files.createDirectories(output.getParent());
                    } else {
                        Files.createDirectories(output);
                    }

                    FileCache cacheToUse;
                    if (Files.isDirectory(input)) {
                        cacheToUse = null;
                    } else if (Objects.equals(
                            scopes, Collections.singleton(Scope.EXTERNAL_LIBRARIES))) {
                        cacheToUse = userCache;
                    } else if (scopes.equals(Collections.singleton(Scope.PROJECT_LOCAL_DEPS))
                            || scopes.equals(
                                    Collections.singleton(Scope.SUB_PROJECTS_LOCAL_DEPS))) {
                        cacheToUse = projectCache;
                    } else {
                        cacheToUse = null;
                    }

                    processUsingCache(input, output, cacheToUse);
                    return null;
                });
    }

    private void processUsingCache(
            @NonNull Path input,
            @NonNull Path output,
            @Nullable FileCache cache)
            throws Exception {
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
            @NonNull Path output)
            throws IOException, ProcessException {
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
                        content.getFile().isDirectory() ? Format.DIRECTORY : Format.JAR)
                .toPath();
    }

    @NonNull
    private static FileCache.Inputs getBuildCacheInputs(@NonNull Path input, int minSdkVersion)
            throws IOException {
        FileCache.Inputs.Builder buildCacheInputs =
                new FileCache.Inputs.Builder(FileCache.Command.DESUGAR_LIBRARY);

        if (input.toString().contains("exploded-aar")) {
            buildCacheInputs.putString(
                    FileCacheInputParams.EXPLODED_AAR_FILE_PATH.name(),
                    input.toString().substring(input.toString().lastIndexOf("exploded-aar")));
        } else if (Objects.equals(input.toString(), "instant-run.jar")) {
            buildCacheInputs.putString(
                    FileCacheInputParams.INSTANT_RUN_JAR_FILE_NAME.name(), input.toString());
        } else {
            buildCacheInputs.putFilePath(FileCacheInputParams.FILE_PATH.name(), input.toFile());
        }

        buildCacheInputs
                .putFileHash(FileCacheInputParams.FILE_HASH.name(), input.toFile())
                .putString(
                        FileCacheInputParams.PLUGIN_VERSION.name(),
                        Version.ANDROID_GRADLE_PLUGIN_VERSION)
                .putLong(FileCacheInputParams.MIN_SDK_VERSION.name(), minSdkVersion);

        return buildCacheInputs.build();
    }

    /** Tmp solution until support for classpath directories is added to Desugar. */
    @NonNull
    private static Path convertDirToJar(@NonNull Path input, @NonNull Path tmpDir) {
        if (Files.isRegularFile(input)) {
            return input;
        }

        Path jarPath;
        try {
            jarPath = Files.createTempFile(tmpDir, null, SdkConstants.DOT_JAR);
            jarPath.toFile().deleteOnExit();
        } catch (IOException e) {
            throw new UncheckedIOException(
                    String.format("Unable to create tmp file for zip from %s.", input.toString()),
                    e);
        }

        try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(jarPath))) {
            Files.walk(input)
                    .filter(Files::isRegularFile)
                    .forEach(
                            p -> {
                                String entryName =
                                        PathUtils.toSystemIndependentPath(input.relativize(p));
                                ZipEntry entry = new ZipEntry(entryName);
                                try {
                                    outputStream.putNextEntry(entry);
                                    outputStream.write(Files.readAllBytes(p));
                                    outputStream.closeEntry();
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            });
        } catch (IOException e) {
            throw new UncheckedIOException(
                    String.format("Unable to create zip from %s.", input.toString()), e);
        }

        return jarPath;
    }

    private void removeTmpClasspathJars() throws IOException {
        if (tmpDirForClasspathJars != null) {
            // delete the tmp files where classpath for desugar got zipped
            Files.list(tmpDirForClasspathJars)
                    .forEach(
                            f -> {
                                try {
                                    Files.delete(f);
                                } catch (IOException ignored) {
                                    // best effort, keep on going
                                    logger.verbose(
                                            "Unable to remove Desugar classpath tmp file "
                                                    + f.toString());
                                }
                            });
            Files.delete(tmpDirForClasspathJars);
        }
    }

    @NonNull
    private static List<Path> splitBootclasspath(@NonNull String bootClasspath) {
        Iterable<String> components = Splitter.on(File.pathSeparator).split(bootClasspath);

        List<Path> bootClasspathJars = Lists.newArrayList();
        PathMatcher zipOrJar =
                FileSystems.getDefault()
                        .getPathMatcher(
                                String.format(
                                        "glob:**{%s,%s}",
                                        SdkConstants.EXT_ZIP, SdkConstants.EXT_JAR));

        for (String component : components) {
            Path componentPath = Paths.get(component);
            if (Files.isRegularFile(componentPath)) {
                bootClasspathJars.add(componentPath);
            } else {
                // this is a directory containing zips or jars, get them all
                try {
                    Files.walk(componentPath)
                            .filter(zipOrJar::matches)
                            .forEach(bootClasspathJars::add);
                } catch (IOException ignored) {
                    // just ignore, users can specify non-existing dirs as bootclasspath
                }
            }
        }

        return bootClasspathJars;
    }
}
