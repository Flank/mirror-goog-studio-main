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
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeSettings;
import com.android.builder.model.NativeToolchain;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class IdeNativeAndroidProjectImpl extends IdeModel implements IdeNativeAndroidProject {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 1L;

    @NonNull private final String myModelVersion;
    @NonNull private final String myName;
    @NonNull private final List<File> myBuildFiles;
    @NonNull private final Collection<NativeArtifact> myArtifacts;
    @NonNull private final Collection<NativeToolchain> myToolChains;
    @NonNull private final Collection<NativeSettings> mySettings;
    @NonNull private final Map<String, String> myFileExtensions;
    @NonNull private final Collection<String> myBuildSystems;
    private final int myApiVersion;
    private final int myHashCode;

    public IdeNativeAndroidProjectImpl(@NonNull NativeAndroidProject project) {
        this(project, new ModelCache());
    }

    @VisibleForTesting
    IdeNativeAndroidProjectImpl(
            @NonNull NativeAndroidProject project, @NonNull ModelCache modelCache) {
        super(project, modelCache);
        myModelVersion = project.getModelVersion();
        myApiVersion = project.getApiVersion();
        myName = project.getName();
        myBuildFiles = ImmutableList.copyOf(project.getBuildFiles());
        myArtifacts =
                copy(
                        project.getArtifacts(),
                        modelCache,
                        artifact -> new IdeNativeArtifact(artifact, modelCache));
        myToolChains =
                copy(
                        project.getToolChains(),
                        modelCache,
                        toolchain -> new IdeNativeToolchain(toolchain, modelCache));
        mySettings =
                copy(
                        project.getSettings(),
                        modelCache,
                        settings -> new IdeNativeSettings(settings, modelCache));
        myFileExtensions = ImmutableMap.copyOf(project.getFileExtensions());
        myBuildSystems = ImmutableList.copyOf(project.getBuildSystems());
        myHashCode = calculateHashCode();
    }

    @Override
    @NonNull
    public String getModelVersion() {
        return myModelVersion;
    }

    @Override
    public int getApiVersion() {
        return myApiVersion;
    }

    @Override
    @NonNull
    public String getName() {
        return myName;
    }

    @Override
    @NonNull
    public Collection<File> getBuildFiles() {
        return myBuildFiles;
    }

    @Override
    @NonNull
    public Collection<NativeArtifact> getArtifacts() {
        return myArtifacts;
    }

    @Override
    @NonNull
    public Collection<NativeToolchain> getToolChains() {
        return myToolChains;
    }

    @Override
    @NonNull
    public Collection<NativeSettings> getSettings() {
        return mySettings;
    }

    @Override
    @NonNull
    public Map<String, String> getFileExtensions() {
        return myFileExtensions;
    }

    @Override
    @NonNull
    public Collection<String> getBuildSystems() {
        return myBuildSystems;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeNativeAndroidProjectImpl)) {
            return false;
        }
        IdeNativeAndroidProjectImpl project = (IdeNativeAndroidProjectImpl) o;
        return myApiVersion == project.myApiVersion
                && Objects.equals(myModelVersion, project.myModelVersion)
                && Objects.equals(myName, project.myName)
                && Objects.equals(myBuildFiles, project.myBuildFiles)
                && Objects.equals(myArtifacts, project.myArtifacts)
                && Objects.equals(myToolChains, project.myToolChains)
                && Objects.equals(mySettings, project.mySettings)
                && Objects.equals(myFileExtensions, project.myFileExtensions)
                && Objects.equals(myBuildSystems, project.myBuildSystems);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(
                myModelVersion,
                myName,
                myBuildFiles,
                myArtifacts,
                myToolChains,
                mySettings,
                myFileExtensions,
                myBuildSystems,
                myApiVersion);
    }

    @Override
    public String toString() {
        return "IdeNativeAndroidProject{"
                + "myModelVersion='"
                + myModelVersion
                + '\''
                + ", myName='"
                + myName
                + '\''
                + ", myBuildFiles="
                + myBuildFiles
                + ", myArtifacts="
                + myArtifacts
                + ", myToolChains="
                + myToolChains
                + ", mySettings="
                + mySettings
                + ", myFileExtensions="
                + myFileExtensions
                + ", myBuildSystems="
                + myBuildSystems
                + ", myApiVersion="
                + myApiVersion
                + "}";
    }

    public static class FactoryImpl implements Factory {
        @Override
        @NonNull
        public IdeNativeAndroidProject create(@NonNull NativeAndroidProject project) {
            return new IdeNativeAndroidProjectImpl(project);
        }
    }
}
