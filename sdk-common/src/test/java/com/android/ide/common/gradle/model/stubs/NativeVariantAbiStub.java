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
    @NonNull private final String myVariantName;
    @NonNull private final String myAbi;

    public NativeVariantAbiStub() {
        this(
                Collections.singletonList(new File("buildFile")),
                Collections.singletonList(new NativeArtifactStub()),
                Collections.singletonList(new NativeToolchainStub()),
                Collections.singletonList(new NativeSettingsStub()),
                ImmutableMap.<String, String>builder().put("key", "value").build(),
                "variant",
                "abi");
    }

    public NativeVariantAbiStub(
            @NonNull List<File> buildFiles,
            @NonNull Collection<NativeArtifact> artifacts,
            @NonNull Collection<NativeToolchain> toolChains,
            @NonNull Collection<NativeSettings> settings,
            @NonNull Map<String, String> fileExtensions,
            @NonNull String variantName,
            @NonNull String abi) {
        myBuildFiles = buildFiles;
        myArtifacts = artifacts;
        myToolChains = toolChains;
        mySettings = settings;
        myFileExtensions = fileExtensions;
        myVariantName = variantName;
        myAbi = abi;
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
    public String getVariantName() {
        return myVariantName;
    }

    @Override
    public String getAbi() {
        return myAbi;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NativeVariantAbi)) {
            return false;
        }
        NativeVariantAbi that = (NativeVariantAbi) o;
        return Objects.equals(getBuildFiles(), that.getBuildFiles())
                && Objects.equals(getArtifacts(), that.getArtifacts())
                && Objects.equals(getToolChains(), that.getToolChains())
                && Objects.equals(getSettings(), that.getSettings())
                && Objects.equals(getFileExtensions(), that.getFileExtensions())
                && Objects.equals(getVariantName(), that.getVariantName())
                && Objects.equals(getAbi(), that.getAbi());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getBuildFiles(),
                getArtifacts(),
                getToolChains(),
                getSettings(),
                getFileExtensions(),
                getVariantName(),
                getAbi());
    }
}
