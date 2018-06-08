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

import static com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES;
import static com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.builder.packaging.JarMerger;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.utils.FileUtils;
import com.android.utils.PathUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A Transforms that takes the project/project local streams for CLASSES and RESOURCES, and
 * processes and outputs fine grained jars/directories that can be consumed by other projects.
 *
 * <p>This typically tries to output the following jars: - main jar (classes only, all coming from
 * jars) - main classes dir (classes only, all coming from directories) - local jars (class only) -
 * java resources (both scopes).
 *
 * <p>If the input contains both scopes, then the output will only be in the main jar.
 *
 * <p>Regarding Streams, this is a no-op transform as it does not write any output to any stream. It
 * uses secondary outputs to write directly into the given folder.
 */
public class LibraryIntermediateJarsTransform extends LibraryBaseTransform {

    private static final Pattern CLASS_PATTERN = Pattern.compile(".*\\.class$");
    private static final Pattern META_INF_PATTERN = Pattern.compile("^META-INF/.*$");
    @NonNull private final File resJarLocation;
    @NonNull private final File mainClassDir;

    public LibraryIntermediateJarsTransform(
            @NonNull File mainClassJar,
            @NonNull File mainClassDir,
            @NonNull File resJarLocation,
            @NonNull Supplier<String> packageNameSupplier,
            boolean packageBuildConfig) {
        super(mainClassJar, null, null, packageNameSupplier, packageBuildConfig);
        this.mainClassDir = mainClassDir;
        this.resJarLocation = resJarLocation;
    }

    @NonNull
    @Override
    public String getName() {
        return "prepareIntermediateJars";
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileOutputs() {
        return ImmutableList.of(mainClassLocation, resJarLocation);
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryDirectoryOutputs() {
        return ImmutableList.of(mainClassDir);
    }

    @Override
    public boolean isIncremental() {
        // TODO used mainly to detect differences between the 3 outputs. Could be improved with incremental support inside each output.
        return true;
    }

    @Override
    public void transform(@NonNull TransformInvocation invocation)
            throws TransformException, InterruptedException, IOException {
        Preconditions.checkState(typedefRecipe == null, "Type def recipe should be null");
        final boolean incrementalDisabled = !invocation.isIncremental();
        List<Pattern> excludePatterns = computeExcludeList();

        // first look for what inputs we have. There shouldn't be that many inputs so it should
        // be quick and it'll allow us to minimize jar merging if we don't have to.
        boolean mainClassInputChanged = incrementalDisabled;
        List<DirectoryInput> mainClassDirs = new ArrayList<>();
        List<JarInput> mainClassJars = new ArrayList<>();
        boolean resJarInputChanged = incrementalDisabled;
        List<QualifiedContent> resJarInputs = new ArrayList<>();

        for (TransformInput input : invocation.getReferencedInputs()) {
            for (JarInput jarInput : input.getJarInputs()) {
                final boolean changed = jarInput.getStatus() != Status.NOTCHANGED;

                // handle res and java separately, as we'll go through all the inputs anyway
                // and if they're jars will just look inside for either.
                if (jarInput.getContentTypes().contains(RESOURCES)) {
                    resJarInputs.add(jarInput);
                    resJarInputChanged |= changed;
                }

                if (jarInput.getContentTypes().contains(CLASSES)) {
                    mainClassJars.add(jarInput);
                    mainClassInputChanged |= changed;
                }
            }

            for (DirectoryInput dirInput : input.getDirectoryInputs()) {
                final boolean changed = !dirInput.getChangedFiles().isEmpty();

                // handle res and java separately, as we'll go through all the inputs anyway
                // and if they're jars will just look inside for either.
                if (dirInput.getContentTypes().contains(RESOURCES)) {
                    resJarInputs.add(dirInput);
                    resJarInputChanged |= changed;
                }

                if (dirInput.getContentTypes().contains(CLASSES)) {
                    mainClassDirs.add(dirInput);
                    mainClassInputChanged |= changed;
                }
            }
        }

        WaitableExecutor executor = WaitableExecutor.useGlobalSharedThreadPool();

        if (mainClassInputChanged) {
            executor.execute(
                    () -> {
                        handleMainClass(mainClassJars, mainClassDirs, excludePatterns);
                        return null;
                    });
        }

        if (resJarInputChanged) {
            executor.execute(() -> {
                handleMainRes(resJarInputs);
                return null;
            });
        }

        executor.waitForTasksWithQuickFail(true);
    }

    private void handleMainClass(
            @NonNull List<JarInput> mainClassJars,
            @NonNull List<DirectoryInput> mainClassDirs,
            @NonNull List<Pattern> excludePatterns)
            throws IOException {
        FileUtils.deleteIfExists(mainClassLocation);
        FileUtils.mkdirs(mainClassLocation.getParentFile());

        // Include both the classes and META-INF files in there as it can be used during
        // compilation. For instance, META-INF pattern will include files like service loaders
        // for annotation processors, or kotlin modules. Both of them are needed by the consuming
        // compiler.
        final Predicate<String> filter =
                archivePath ->
                        (CLASS_PATTERN.matcher(archivePath).matches()
                                        || META_INF_PATTERN.matcher(archivePath).matches())
                                && checkEntry(excludePatterns, archivePath);

        handleJarOutput(mainClassJars, mainClassLocation, filter);
        handleDirOutput(mainClassDirs, mainClassDir, filter);
    }

    private static void handleDirOutput(
            @NonNull List<DirectoryInput> mainClassDirs,
            @NonNull File mainClassDir,
            @NonNull Predicate<String> filter)
            throws IOException {
        FileUtils.cleanOutputDir(mainClassDir);

        for (DirectoryInput classDir : mainClassDirs) {
            File file = classDir.getFile();

            try (Stream<Path> walk = Files.walk(file.toPath())) {
                walk.forEach(
                        p -> {
                            Path relative = file.toPath().relativize(p);
                            String relativePath = PathUtils.toSystemIndependentPath(relative);
                            if (filter.test(relativePath)) {
                                Path copyPath = mainClassDir.toPath().resolve(relative);
                                try {
                                    Files.createDirectories(copyPath.getParent());
                                    Files.copy(p, copyPath);
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            }
                        });
            }
        }
    }

    private void handleMainRes(
            @NonNull List<QualifiedContent> resJarInputs) throws IOException {
        FileUtils.deleteIfExists(resJarLocation);
        FileUtils.mkdirs(resJarLocation.getParentFile());

        // Only remove the classes files here. Because the main class jar (above) is only consumed
        // as a jar of classes, we need to include the META-INF files here as well so that
        // the file find their way in the APK.
        final Predicate<String> filter =
                archivePath -> !CLASS_PATTERN.matcher(archivePath).matches();

        handleJarOutput(resJarInputs, resJarLocation, filter);
    }

    private static void handleJarOutput(
            @NonNull List<? extends QualifiedContent> inputs,
            @NonNull File toFile,
            @Nullable Predicate<String> filter)
            throws IOException {
        if (inputs.isEmpty()) {
            try (JarMerger jarMerger = new JarMerger(toFile.toPath())) {
                // At configuration time, we don't know whether file will exist, so we need to
                // create it always, because we publish it. Creating an empty jar was causing issues
                // on windows, because javac would not release the file handle, so add an entry.
                jarMerger.addEntry("empty", new ByteArrayInputStream(new byte[0]));
            }
        }

        if (inputs.size() == 1) {
            QualifiedContent content = inputs.get(0);

            if (content instanceof JarInput) {
                copyJarWithContentFilter(content.getFile(), toFile, filter);
            } else {
                jarFolderToLocation(content.getFile(), toFile, filter);
            }
        } else {
            mergeInputsToLocation(inputs, toFile, true, filter, null);
        }
    }

    protected static void jarFolderToLocation(
            @NonNull File fromFolder, @NonNull File toFile, @Nullable Predicate<String> filter)
            throws IOException {
        try (JarMerger jarMerger = new JarMerger(toFile.toPath())) {
            jarMerger.addDirectory(fromFolder.toPath(), filter, null, null);
        }
    }
}
