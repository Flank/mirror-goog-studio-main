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
package com.android.ide.common.builder.model.stubs;

import com.android.annotations.NonNull;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeSettings;
import com.android.builder.model.NativeToolchain;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class NativeAndroidProjectStub extends BaseStub implements NativeAndroidProject {
    @NonNull private final String myModelVersion;
    @NonNull private final String myName;
    @NonNull private final List<File> myBuildFiles;
    @NonNull private final Collection<NativeArtifact> myArtifacts;
    @NonNull private final Collection<NativeToolchain> myToolChains;
    @NonNull private final Collection<NativeSettings> mySettings;
    @NonNull private final Map<String, String> myFileExtensions;
    @NonNull private final Collection<String> myBuildSystems;
    private final int myApiVersion;

    public NativeAndroidProjectStub() {
        this(
                "1.0",
                "name",
                Collections.singletonList(new File("buildFile")),
                Collections.singletonList(new NativeArtifactStub()),
                Collections.singletonList(new NativeToolchainStub()),
                Collections.singletonList(new NativeSettingsStub()),
                ImmutableMap.<String, String>builder().put("key", "value").build(),
                Collections.singletonList("buildSystem"),
                1);
    }

    public NativeAndroidProjectStub(
            @NonNull String modelVersion,
            @NonNull String name,
            @NonNull List<File> buildFiles,
            @NonNull Collection<NativeArtifact> artifacts,
            @NonNull Collection<NativeToolchain> toolChains,
            @NonNull Collection<NativeSettings> settings,
            @NonNull Map<String, String> fileExtensions,
            @NonNull Collection<String> buildSystems,
            int apiVersion) {
        myModelVersion = modelVersion;
        myName = name;
        myBuildFiles = buildFiles;
        myArtifacts = artifacts;
        myToolChains = toolChains;
        mySettings = settings;
        myFileExtensions = fileExtensions;
        myBuildSystems = buildSystems;
        myApiVersion = apiVersion;
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
        if (!(o instanceof NativeAndroidProject)) {
            return false;
        }
        NativeAndroidProject project = (NativeAndroidProject) o;
        return getApiVersion() == project.getApiVersion()
                && Objects.equals(getModelVersion(), project.getModelVersion())
                && Objects.equals(getName(), project.getName())
                && Objects.equals(getBuildFiles(), project.getBuildFiles())
                && Objects.equals(getArtifacts(), project.getArtifacts())
                && Objects.equals(getToolChains(), project.getToolChains())
                && Objects.equals(getSettings(), project.getSettings())
                && Objects.equals(getFileExtensions(), project.getFileExtensions())
                && Objects.equals(getBuildSystems(), project.getBuildSystems());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getModelVersion(),
                getName(),
                getBuildFiles(),
                getArtifacts(),
                getToolChains(),
                getSettings(),
                getFileExtensions(),
                getBuildSystems(),
                getApiVersion());
    }
}
