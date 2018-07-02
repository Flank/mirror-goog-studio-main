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
package com.android.ide.common.gradle.model.stubs;

import com.android.annotations.NonNull;
import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeFile;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public class NativeArtifactStub extends BaseStub implements NativeArtifact {
    @NonNull private final String myName;
    @NonNull private final String myToolChain;
    @NonNull private final String myGroupName;
    @NonNull private final String myAssembleTaskName;
    @NonNull private final Collection<NativeFile> mySourceFiles;
    @NonNull private final Collection<File> myExportedHeaders;
    @NonNull private final String myAbi;
    @NonNull private final String myTargetName;
    @NonNull private final File myOutputFile;
    @NonNull private final Collection<File> myRuntimeFiles;

    public NativeArtifactStub() {
        this(
                "name",
                "toolChain",
                "groupName",
                "assembleTaskName",
                Collections.singletonList(new NativeFileStub()),
                Collections.singletonList(new File("exportHeadher")),
                "abi",
                "targetName",
                new File("outputFile"),
                Collections.singletonList(new File("runtimeFile")));
    }

    public NativeArtifactStub(
            @NonNull String name,
            @NonNull String toolChain,
            @NonNull String groupName,
            @NonNull String assembleTaskName,
            @NonNull Collection<NativeFile> files,
            @NonNull Collection<File> exportedHeaders,
            @NonNull String abi,
            @NonNull String targetName,
            @NonNull File outputFile,
            @NonNull Collection<File> runtimeFiles) {
        myName = name;
        myToolChain = toolChain;
        myGroupName = groupName;
        myAssembleTaskName = assembleTaskName;
        mySourceFiles = files;
        myExportedHeaders = exportedHeaders;
        myAbi = abi;
        myTargetName = targetName;
        myOutputFile = outputFile;
        myRuntimeFiles = runtimeFiles;
    }

    @Override
    @NonNull
    public String getName() {
        return myName;
    }

    @Override
    @NonNull
    public String getToolChain() {
        return myToolChain;
    }

    @Override
    @NonNull
    public String getGroupName() {
        return myGroupName;
    }

    @Override
    @NonNull
    public String getAssembleTaskName() {
        return myAssembleTaskName;
    }

    @Override
    @NonNull
    public Collection<NativeFile> getSourceFiles() {
        return mySourceFiles;
    }

    @Override
    @NonNull
    public Collection<File> getExportedHeaders() {
        return myExportedHeaders;
    }

    @Override
    @NonNull
    public String getAbi() {
        return myAbi;
    }

    @Override
    @NonNull
    public String getTargetName() {
        return myTargetName;
    }

    @Override
    @NonNull
    public File getOutputFile() {
        return myOutputFile;
    }

    @Override
    @NonNull
    public Collection<File> getRuntimeFiles() {
        return myRuntimeFiles;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NativeArtifact)) {
            return false;
        }
        NativeArtifact artifact = (NativeArtifact) o;
        return Objects.equals(getName(), artifact.getName())
                && Objects.equals(getToolChain(), artifact.getToolChain())
                && Objects.equals(getGroupName(), artifact.getGroupName())
                && equals(artifact, NativeArtifact::getAssembleTaskName)
                && Objects.equals(getSourceFiles(), artifact.getSourceFiles())
                && Objects.equals(getExportedHeaders(), artifact.getExportedHeaders())
                && equals(artifact, NativeArtifact::getAbi)
                && equals(artifact, NativeArtifact::getTargetName)
                && Objects.equals(getOutputFile(), artifact.getOutputFile())
                && equals(artifact, NativeArtifact::getRuntimeFiles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getName(),
                getToolChain(),
                getGroupName(),
                getAssembleTaskName(),
                getSourceFiles(),
                getExportedHeaders(),
                getAbi(),
                getTargetName(),
                getOutputFile(),
                getRuntimeFiles());
    }
}
