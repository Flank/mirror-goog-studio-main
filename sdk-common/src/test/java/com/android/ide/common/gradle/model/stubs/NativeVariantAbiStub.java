/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.builder.model.*;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.*;

public class NativeVariantAbiStub extends BaseStub implements NativeVariantAbi {

    @NonNull private final List<File> myBuildFiles;
    @NonNull private final Collection<NativeArtifact> myArtifacts;
    @NonNull private final Collection<NativeToolchain> myToolChains;
    @NonNull private final Collection<NativeSettings> mySettings;
    @NonNull private final Map<String, String> myFileExtensions;

    public NativeVariantAbiStub() {
        this(
                Collections.singletonList(new File("buildFile")),
                Collections.singletonList(new NativeArtifactStub()),
                Collections.singletonList(new NativeToolchainStub()),
                Collections.singletonList(new NativeSettingsStub()),
                ImmutableMap.<String, String>builder().put("key", "value").build());
    }

    public NativeVariantAbiStub(
            @NonNull List<File> buildFiles,
            @NonNull Collection<NativeArtifact> artifacts,
            @NonNull Collection<NativeToolchain> toolChains,
            @NonNull Collection<NativeSettings> settings,
            @NonNull Map<String, String> fileExtensions) {
        myBuildFiles = buildFiles;
        myArtifacts = artifacts;
        myToolChains = toolChains;
        mySettings = settings;
        myFileExtensions = fileExtensions;
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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NativeAndroidProject)) {
            return false;
        }
        NativeAndroidProject project = (NativeAndroidProject) o;
        return Objects.equals(getBuildFiles(), project.getBuildFiles())
                && Objects.equals(getArtifacts(), project.getArtifacts())
                && Objects.equals(getToolChains(), project.getToolChains())
                && Objects.equals(getSettings(), project.getSettings())
                && Objects.equals(getFileExtensions(), project.getFileExtensions());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getBuildFiles(),
                getArtifacts(),
                getToolChains(),
                getSettings(),
                getFileExtensions());
    }
}
