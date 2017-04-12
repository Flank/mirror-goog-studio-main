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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.android.SdkConstants;
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
import com.google.common.collect.Sets;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.jacoco.core.instr.Instrumenter;
import org.jacoco.core.runtime.OfflineInstrumentationAccessGenerator;

/**
 * Jacoco Transform
 */
public class JacocoTransform extends Transform {

    private final File jacocoAgent;

    public JacocoTransform(@NonNull  final File jacocoAgent) {
        this.jacocoAgent = jacocoAgent;
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

    @NonNull
    @Override
    public Collection<SecondaryFile> getSecondaryFiles() {
        return ImmutableList.of(SecondaryFile.nonIncremental(jacocoAgent));
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(@NonNull TransformInvocation invocation)
            throws IOException, TransformException, InterruptedException {
        // This task requires the jacocoagent.jar to be in the transform stream.  Checking the
        // jacocoagent.jar exists indicates the tasks dependency is setup correctly.
        checkState(jacocoAgent.isFile());

        checkNotNull(invocation.getOutputProvider(),
                "Missing output object for transform " + getName());

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

                Instrumenter instrumenter =
                        new Instrumenter(new OfflineInstrumentationAccessGenerator());
                if (invocation.isIncremental()) {
                    instrumentFilesIncremental(
                            instrumenter, inputDir, outputDir, directoryInput.getChangedFiles());
                } else {
                    instrumentFilesFullRun(instrumenter, inputDir, outputDir);
                }
            }
        }
    }

    private static void instrumentFilesIncremental(
            @NonNull Instrumenter instrumenter,
            @NonNull File inputDir,
            @NonNull File outputDir,
            @NonNull Map<File, Status> changedFiles) throws IOException {
        for (Map.Entry<File, Status> changedInput : changedFiles.entrySet()) {
            File inputFile = changedInput.getKey();
            if (!inputFile.getName().endsWith(SdkConstants.DOT_CLASS)) {
                continue;
            }

            File outputFile = new File(outputDir,
                    FileUtils.relativePossiblyNonExistingPath(inputFile, inputDir));
            switch (changedInput.getValue()) {
                case REMOVED:
                    FileUtils.delete(outputFile);
                    break;
                case ADDED:
                    // fall through
                case CHANGED:
                    instrumentFile(instrumenter, inputFile, outputFile);
                    break;
                case NOTCHANGED:
                    // do nothing
                    break;
            }
        }
    }

    private static void instrumentFilesFullRun(
            @NonNull Instrumenter instrumenter,
            @NonNull File inputDir,
            @NonNull File outputDir) throws IOException {
        FileUtils.cleanOutputDir(outputDir);
        Iterable<File> files = FileUtils.getAllFiles(inputDir);
        for (File inputFile : files) {
            if (!inputFile.getName().endsWith(SdkConstants.DOT_CLASS)) {
                continue;
            }

            File outputFile = new File(outputDir, FileUtils.relativePath(inputFile, inputDir));
            instrumentFile(instrumenter, inputFile, outputFile);
        }
    }

    private static void instrumentFile(
            @NonNull Instrumenter instrumenter,
            @NonNull File inputFile,
            @NonNull File outputFile) throws IOException {
        InputStream inputStream = null;
        try {
            inputStream = Files.asByteSource(inputFile).openBufferedStream();
            Files.createParentDirs(outputFile);
            byte[] instrumented = instrumenter.instrument(
                    inputStream,
                    inputFile.toString());
            Files.write(instrumented, outputFile);
        } finally {
            Closeables.closeQuietly(inputStream);
        }
    }

}
