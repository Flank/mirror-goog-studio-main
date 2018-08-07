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
package com.android.ide.common.gradle.model;

import com.android.annotations.NonNull;
import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeSettings;
import com.android.builder.model.NativeToolchain;
import com.android.builder.model.NativeVariantAbi;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class IdeNativeVariantAbi extends IdeModel implements NativeVariantAbi {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 1L;

    @NonNull private final List<File> myBuildFiles;
    @NonNull private final Collection<NativeArtifact> myArtifacts;
    @NonNull private final Collection<NativeToolchain> myToolChains;
    @NonNull private final Collection<NativeSettings> mySettings;
    @NonNull private final Map<String, String> myFileExtensions;
    private final int myHashCode;

    public IdeNativeVariantAbi(@NonNull NativeVariantAbi variantAbi) {
        this(variantAbi, new ModelCache());
    }

    public IdeNativeVariantAbi(
            @NonNull NativeVariantAbi variantAbi, @NonNull ModelCache modelCache) {
        super(variantAbi, modelCache);
        myBuildFiles = ImmutableList.copyOf(variantAbi.getBuildFiles());
        myArtifacts =
                copy(
                        variantAbi.getArtifacts(),
                        modelCache,
                        artifact -> new IdeNativeArtifact(artifact, modelCache));
        myToolChains =
                copy(
                        variantAbi.getToolChains(),
                        modelCache,
                        toolchain -> new IdeNativeToolchain(toolchain, modelCache));
        mySettings =
                copy(
                        variantAbi.getSettings(),
                        modelCache,
                        settings -> new IdeNativeSettings(settings, modelCache));
        myFileExtensions = ImmutableMap.copyOf(variantAbi.getFileExtensions());
        myHashCode = calculateHashCode();
    }

    @NonNull
    @Override
    public Collection<File> getBuildFiles() {
        return myBuildFiles;
    }

    @NonNull
    @Override
    public Collection<NativeArtifact> getArtifacts() {
        return myArtifacts;
    }

    @NonNull
    @Override
    public Collection<NativeToolchain> getToolChains() {
        return myToolChains;
    }

    @NonNull
    @Override
    public Collection<NativeSettings> getSettings() {
        return mySettings;
    }

    @NonNull
    @Override
    public Map<String, String> getFileExtensions() {
        return myFileExtensions;
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(myBuildFiles, myArtifacts, myToolChains, mySettings, myFileExtensions);
    }

    @Override
    public String toString() {
        return "IdeNativeVariantAbi{"
                + "myBuildFiles="
                + myBuildFiles
                + ", myArtifacts="
                + myArtifacts
                + ", myToolChains="
                + myToolChains
                + ", mySettings="
                + mySettings
                + ", myFileExtensions="
                + myFileExtensions
                + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdeNativeVariantAbi)) return false;
        IdeNativeVariantAbi that = (IdeNativeVariantAbi) o;
        return Objects.equals(myBuildFiles, that.myBuildFiles)
                && Objects.equals(myArtifacts, that.myArtifacts)
                && Objects.equals(myToolChains, that.myToolChains)
                && Objects.equals(mySettings, that.mySettings)
                && Objects.equals(myFileExtensions, that.myFileExtensions);
    }
}
