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

package com.android.ide.common.builder.model;

import com.android.annotations.NonNull;
import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeFile;
import com.android.builder.model.NativeFolder;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Collection;
import java.util.Objects;

public final class IdeNativeArtifact extends IdeModel implements NativeArtifact {
    @NonNull private final String myName;
    @NonNull private final String myToolChain;
    @NonNull private final String myGroupName;
    @NonNull private final String myAssembleTaskName;
    @NonNull private final Collection<NativeFolder> mySourceFolders;
    @NonNull private final Collection<NativeFile> mySourceFiles;
    @NonNull private final Collection<File> myExportedHeaders;
    @NonNull private final String myAbi;
    @NonNull private final String myTargetName;
    @NonNull private final File myOutputFile;
    @NonNull private final Collection<File> myRuntimeFiles;
    private final int myHashCode;

    public IdeNativeArtifact(@NonNull NativeArtifact artifact, @NonNull ModelCache modelCache) {
        super(artifact, modelCache);
        myName = artifact.getName();
        myToolChain = artifact.getToolChain();
        myGroupName = artifact.getGroupName();
        myAssembleTaskName = artifact.getAssembleTaskName();
        mySourceFolders =
                copy(
                        artifact.getSourceFolders(),
                        modelCache,
                        folder -> new IdeNativeFolder(folder, modelCache));
        mySourceFiles =
                copy(
                        artifact.getSourceFiles(),
                        modelCache,
                        file -> new IdeNativeFile(file, modelCache));
        myExportedHeaders = ImmutableList.copyOf(artifact.getExportedHeaders());
        myAbi = artifact.getAbi();
        myTargetName = artifact.getTargetName();
        myOutputFile = artifact.getOutputFile();
        myRuntimeFiles = ImmutableList.copyOf(artifact.getRuntimeFiles());
        myHashCode = calculateHashCode();
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
    public Collection<NativeFolder> getSourceFolders() {
        return mySourceFolders;
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
        if (!(o instanceof IdeNativeArtifact)) {
            return false;
        }
        IdeNativeArtifact artifact = (IdeNativeArtifact) o;
        return Objects.equals(myName, artifact.myName)
                && Objects.equals(myToolChain, artifact.myToolChain)
                && Objects.equals(myGroupName, artifact.myGroupName)
                && Objects.equals(myAssembleTaskName, artifact.myAssembleTaskName)
                && Objects.equals(mySourceFolders, artifact.mySourceFolders)
                && Objects.equals(mySourceFiles, artifact.mySourceFiles)
                && Objects.equals(myExportedHeaders, artifact.myExportedHeaders)
                && Objects.equals(myAbi, artifact.myAbi)
                && Objects.equals(myTargetName, artifact.myTargetName)
                && Objects.equals(myOutputFile, artifact.myOutputFile)
                && Objects.equals(myRuntimeFiles, artifact.myRuntimeFiles);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(
                myName,
                myToolChain,
                myGroupName,
                myAssembleTaskName,
                mySourceFolders,
                mySourceFiles,
                myExportedHeaders,
                myAbi,
                myTargetName,
                myOutputFile,
                myRuntimeFiles);
    }

    @Override
    public String toString() {
        return "IdeNativeArtifact{"
                + "myName='"
                + myName
                + '\''
                + ", myToolChain='"
                + myToolChain
                + '\''
                + ", myGroupName='"
                + myGroupName
                + '\''
                + ", myAssembleTaskName='"
                + myAssembleTaskName
                + '\''
                + ", mySourceFolders="
                + mySourceFolders
                + ", mySourceFiles="
                + mySourceFiles
                + ", myExportedHeaders="
                + myExportedHeaders
                + ", myAbi='"
                + myAbi
                + '\''
                + ", myTargetName='"
                + myTargetName
                + '\''
                + ", myOutputFile="
                + myOutputFile
                + ", myRuntimeFiles="
                + myRuntimeFiles
                + "}";
    }
}
