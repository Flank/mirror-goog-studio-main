/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.internal.ide;

import com.android.annotations.NonNull;
import com.android.annotations.concurrency.Immutable;
import com.android.build.OutputFile;
import com.android.builder.model.AndroidArtifactOutput;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;

/**
 * Implementation of AndroidArtifactOutput that is serializable
 */
@Immutable
final class AndroidArtifactOutputImpl implements AndroidArtifactOutput, Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull
    private final File generatedManifest;
    @NonNull
    private final String assembleTaskName;
    private final int versionCode;
    private final Collection<OutputFile> outputFiles;
    private final OutputFile mainOutputFile;

    AndroidArtifactOutputImpl(
            @NonNull Collection<OutputFile> outputFiles,
            @NonNull String assembleTaskName,
            @NonNull File generatedManifest,
            int versionCode) {
        this.generatedManifest = generatedManifest;
        this.assembleTaskName = assembleTaskName;
        this.versionCode = versionCode;
        this.outputFiles = outputFiles;
        // check that we have the a main output file.
        for (OutputFile outputFile : outputFiles) {
            if (outputFile.getOutputType().equals(OutputFile.MAIN)
                    || outputFile.getOutputType().equals(OutputFile.FULL_SPLIT)) {
                mainOutputFile = outputFile;
                return;
            }
        }
        throw new IllegalStateException("No main output file for variant");
    }

    @NonNull
    @Override
    public OutputFile getMainOutputFile() {
        return mainOutputFile;
    }

    @NonNull
    @Override
    public Collection<OutputFile> getOutputs() {
        return outputFiles;
    }

    @NonNull
    @Override
    public String getAssembleTaskName() {
        return assembleTaskName;
    }

    @NonNull
    @Override
    public File getGeneratedManifest() {
        return generatedManifest;
    }

    @Override
    public int getVersionCode() {
        return versionCode;
    }

    @NonNull
    @Override
    public File getSplitFolder() {
        return getMainOutputFile().getOutputFile().getParentFile();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AndroidArtifactOutputImpl that = (AndroidArtifactOutputImpl) o;
        return versionCode == that.versionCode &&
                Objects.equals(generatedManifest, that.generatedManifest) &&
                Objects.equals(assembleTaskName, that.assembleTaskName) &&
                Objects.equals(outputFiles, that.outputFiles) &&
                Objects.equals(mainOutputFile, that.mainOutputFile);
    }

    @Override
    public int hashCode() {
        return Objects
                .hash(generatedManifest, assembleTaskName, versionCode, outputFiles,
                        mainOutputFile);
    }
}
