/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.SecondaryFile;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.inject.Inject;
import org.gradle.api.artifacts.Configuration;
import org.gradle.workers.IsolationMode;
import org.jacoco.core.instr.Instrumenter;
import org.jacoco.core.runtime.OfflineInstrumentationAccessGenerator;

/**
 * Jacoco Transform
 */
public class JacocoTransform extends Transform {

    private static final Pattern CLASS_PATTERN = Pattern.compile(".*\\.class$");
    // META-INF/*.kotlin_module files need to be copied to output so they show up
    // in the intermediate classes jar.
    private static final Pattern KOTLIN_MODULE_PATTERN =
            Pattern.compile("^META-INF/.*\\.kotlin_module$");
    @NonNull private final Configuration jacocoAntTaskConfiguration;

    public JacocoTransform(@NonNull Configuration jacocoAntTaskConfiguration) {
        this.jacocoAntTaskConfiguration = jacocoAntTaskConfiguration;
    }

    @NonNull
    @Override
    public String getName() {
        return "jacoco";
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        // only run on the project classes
        return Sets.immutableEnumSet(QualifiedContent.Scope.PROJECT);
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public boolean isCacheable() {
        return true;
    }

    @NonNull
    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        return ImmutableList.of(SecondaryFile.nonIncremental(jacocoAntTaskConfiguration));
    }

    @Override
    public void transform(@NonNull TransformInvocation invocation)
            throws IOException, TransformException, InterruptedException {

        for (TransformInput input : invocation.getInputs()) {
            // we don't want jar inputs.
            Preconditions.checkState(input.getJarInputs().isEmpty());

            for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                File inputDir = directoryInput.getFile();

                File outputDir =
                        invocation
                                .getOutputProvider()
                                .getContentLocation(
                                        directoryInput.getName(),
                                        getOutputTypes(),
                                        getScopes(),
                                        Format.DIRECTORY);
                FileUtils.mkdirs(outputDir);

                Map<Action, List<File>> toProcess;
                if (invocation.isIncremental()) {
                    toProcess =
                            getFilesForInstrumentationIncrementally(
                                    inputDir, outputDir, directoryInput.getChangedFiles());
                } else {
                    toProcess = getFilesForInstrumentationNonIncrementally(inputDir, outputDir);
                }

                invocation
                        .getContext()
                        .getWorkerExecutor()
                        .submit(
                                JacocoWorkerAction.class,
                                workerConfiguration -> {
                                    workerConfiguration.setIsolationMode(IsolationMode.CLASSLOADER);
                                    workerConfiguration.classpath(
                                            jacocoAntTaskConfiguration.getFiles());
                                    workerConfiguration.setParams(toProcess, inputDir, outputDir);
                                });
            }
        }
        invocation.getContext().getWorkerExecutor().await();
    }

    @NonNull
    private static Map<Action, List<File>> getFilesForInstrumentationIncrementally(
            @NonNull File inputDir,
            @NonNull File outputDir,
            @NonNull Map<File, Status> changedFiles)
            throws IOException {
        Map<Action, List<File>> toProcess = Maps.newHashMap();
        for (Map.Entry<File, Status> changedInput : changedFiles.entrySet()) {
            File inputFile = changedInput.getKey();
            Action fileAction = calculateAction(inputFile, inputDir);
            if (fileAction == Action.IGNORE) {
                continue;
            }
            Preconditions.checkState(fileAction == Action.COPY || fileAction == Action.INSTRUMENT);

            switch (changedInput.getValue()) {
                case REMOVED:
                    File outputFile =
                            new File(
                                    outputDir,
                                    FileUtils.relativePossiblyNonExistingPath(inputFile, inputDir));
                    FileUtils.delete(outputFile);
                    break;
                case ADDED:
                    // fall through
                case CHANGED:
                    List<File> files = toProcess.getOrDefault(fileAction, new ArrayList<>());
                    files.add(inputFile);
                    toProcess.put(fileAction, files);
                    break;
                case NOTCHANGED:
                    // do nothing
                    break;
            }
        }
        return toProcess;
    }

    private static Map<Action, List<File>> getFilesForInstrumentationNonIncrementally(
            @NonNull File inputDir, @NonNull File outputDir) throws IOException {
        Map<Action, List<File>> toProcess = Maps.newHashMap();
        FileUtils.cleanOutputDir(outputDir);
        Iterable<File> files = FileUtils.getAllFiles(inputDir);
        for (File inputFile : files) {
            Action fileAction = calculateAction(inputFile, inputDir);
            switch (fileAction) {
                case COPY:
                    // fall through
                case INSTRUMENT:
                    List<File> actionFiles = toProcess.getOrDefault(fileAction, new ArrayList<>());
                    actionFiles.add(inputFile);
                    toProcess.put(fileAction, actionFiles);
                    break;
                case IGNORE:
                    // do nothing
                    break;
                default:
                    throw new AssertionError("Unsupported Action: " + fileAction);
            }
        }
        return toProcess;
    }

    private static Action calculateAction(@NonNull File inputFile, @NonNull File inputDir) {
        final String inputRelativePath =
                FileUtils.toSystemIndependentPath(
                        FileUtils.relativePossiblyNonExistingPath(inputFile, inputDir));
        for (Pattern pattern : Action.COPY.getPatterns()) {
            if (pattern.matcher(inputRelativePath).matches()) {
                return Action.COPY;
            }
        }
        for (Pattern pattern : Action.INSTRUMENT.getPatterns()) {
            if (pattern.matcher(inputRelativePath).matches()) {
                return Action.INSTRUMENT;
            }
        }
        return Action.IGNORE;
    }

    /** The possible actions which can happen to an input file */
    private enum Action {

        /** The file is just copied to the transform output. */
        COPY(KOTLIN_MODULE_PATTERN),

        /** The file is ignored. */
        IGNORE(),

        /** The file is instrumented and added to the transform output. */
        INSTRUMENT(CLASS_PATTERN);

        private final ImmutableList<Pattern> patterns;

        /**
         * @param patterns Patterns are compared to files' relative paths to determine if they
         *     undergo the corresponding action.
         */
        Action(@NonNull Pattern... patterns) {
            ImmutableList.Builder<Pattern> builder = new ImmutableList.Builder<>();
            for (Pattern pattern : patterns) {
                Preconditions.checkNotNull(pattern);
                builder.add(pattern);
            }
            this.patterns = builder.build();
        }

        @NonNull
        ImmutableList<Pattern> getPatterns() {
            return patterns;
        }
    }

    private static class JacocoWorkerAction implements Runnable {
        @NonNull private Map<Action, List<File>> inputs;
        @NonNull private File inputDir;
        @NonNull private File outputDir;

        @Inject
        public JacocoWorkerAction(
                @NonNull Map<Action, List<File>> inputs,
                @NonNull File inputDir,
                @NonNull File outputDir) {
            this.inputs = inputs;
            this.inputDir = inputDir;
            this.outputDir = outputDir;
        }

        @Override
        public void run() {
            Instrumenter instrumenter =
                    new Instrumenter(new OfflineInstrumentationAccessGenerator());
            for (File toInstrument : inputs.getOrDefault(Action.INSTRUMENT, ImmutableList.of())) {
                try (InputStream inputStream =
                        Files.asByteSource(toInstrument).openBufferedStream()) {
                    byte[] instrumented =
                            instrumenter.instrument(inputStream, toInstrument.toString());
                    File outputFile =
                            new File(outputDir, FileUtils.relativePath(toInstrument, inputDir));
                    Files.createParentDirs(outputFile);
                    Files.write(instrumented, outputFile);
                } catch (IOException e) {
                    throw new UncheckedIOException(
                            "Unable to instrument file with Jacoco: " + toInstrument, e);
                }
            }

            for (File toCopy : inputs.getOrDefault(Action.COPY, ImmutableList.of())) {
                File outputFile = new File(outputDir, FileUtils.relativePath(toCopy, inputDir));
                try {
                    Files.createParentDirs(outputFile);
                    Files.copy(toCopy, outputFile);
                } catch (IOException e) {
                    throw new UncheckedIOException("Unable to copy file: " + toCopy, e);
                }
            }
        }
    }
}
