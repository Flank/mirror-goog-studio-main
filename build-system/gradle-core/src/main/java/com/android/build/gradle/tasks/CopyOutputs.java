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

package com.android.build.gradle.tasks;

import com.android.annotations.NonNull;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.BuildElements;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.AndroidVariantTask;
import com.android.build.gradle.internal.tasks.factory.EagerTaskCreationAction;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.tooling.BuildException;

/**
 * Copy the location our various tasks outputs into a single location.
 *
 * <p>This is useful when having configuration or feature splits which are located in different
 * folders since they are produced by different tasks.
 */
public class CopyOutputs extends AndroidVariantTask {

    BuildableArtifact fullApks;
    BuildableArtifact abiSplits;
    BuildableArtifact resourcesSplits;
    File destinationDir;

    @OutputDirectory
    public java.io.File getDestinationDir() {
        return destinationDir;
    }

    @InputFiles
    public BuildableArtifact getFullApks() {
        return fullApks;
    }

    @InputFiles
    @Optional
    public BuildableArtifact getAbiSplits() {
        return abiSplits;
    }

    @InputFiles
    @Optional
    public BuildableArtifact getResourcesSplits() {
        return resourcesSplits;
    }

    // FIX ME : add incrementality
    @TaskAction
    protected void copy() throws IOException {

        FileUtils.cleanOutputDir(getDestinationDir());
        // TODO : parallelize at this level.
        ImmutableList.Builder<BuildOutput> allCopiedFiles = ImmutableList.builder();
        allCopiedFiles.addAll(parallelCopy(InternalArtifactType.FULL_APK, fullApks.get()));
        allCopiedFiles.addAll(
                parallelCopy(InternalArtifactType.ABI_PACKAGED_SPLIT, abiSplits.get()));
        allCopiedFiles.addAll(
                parallelCopy(
                        InternalArtifactType.DENSITY_OR_LANGUAGE_PACKAGED_SPLIT,
                        resourcesSplits.get()));
        // now save the merged list.
        new BuildElements(allCopiedFiles.build()).save(getDestinationDir());
    }

    // TODO : shouldn't this be in parallel?
    private BuildElements parallelCopy(InternalArtifactType inputType, FileCollection inputs)
            throws IOException {

        return ExistingBuildElements.from(inputType, inputs)
                .transform(
                        (apkInfo, inputFile) -> {
                            File destination = new File(getDestinationDir(), inputFile.getName());
                            try {
                                FileUtils.copyFile(inputFile, destination);
                            } catch (IOException e) {
                                throw new BuildException(e.getMessage(), e);
                            }
                            return destination;
                        })
                .into(InternalArtifactType.APK);
    }

    public static class CreationAction extends EagerTaskCreationAction<CopyOutputs> {

        private final VariantScope variantScope;
        private final File outputDirectory;

        public CreationAction(VariantScope variantScope, File outputDirectory) {
            this.variantScope = variantScope;
            this.outputDirectory = outputDirectory;
        }

        @NonNull
        @Override
        public String getName() {
            return variantScope.getTaskName("copyOutputs");
        }

        @NonNull
        @Override
        public Class<CopyOutputs> getType() {
            return CopyOutputs.class;
        }

        @Override
        public void execute(@NonNull CopyOutputs task) {
            task.setVariantName(variantScope.getFullVariantName());
            BuildArtifactsHolder artifacts = variantScope.getArtifacts();
            task.fullApks = artifacts.getFinalArtifactFiles(
                    InternalArtifactType.FULL_APK);
            task.abiSplits = artifacts.getFinalArtifactFiles(
                    InternalArtifactType.ABI_PACKAGED_SPLIT);
            task.resourcesSplits = artifacts.getFinalArtifactFiles(
                                    InternalArtifactType.DENSITY_OR_LANGUAGE_PACKAGED_SPLIT);
            task.destinationDir = outputDirectory;
        }
    }
}
