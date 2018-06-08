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
package com.android.ide.common.gradle.model.stubs.level2;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.level2.Library;
import com.android.ide.common.gradle.model.stubs.BaseStub;
import java.io.File;
import java.util.Collection;
import java.util.Objects;

public class ModuleLibraryStub extends BaseStub implements Library {
    private final int myHashCode;
    private final int myType;
    @NonNull private final String myArtifactAddress;
    @Nullable private final String myBuildId;
    @Nullable private final String myProjectPath;
    @Nullable private final String myVariant;

    public ModuleLibraryStub() {
        this(LIBRARY_MODULE, "artifact:address:1.0", null, null, null);
    }

    public ModuleLibraryStub(
            int type,
            @NonNull String artifactAddress,
            @Nullable String buildId,
            @Nullable String projectPath,
            @Nullable String variant) {
        myType = type;
        myArtifactAddress = artifactAddress;
        myBuildId = buildId;
        myProjectPath = projectPath;
        myVariant = variant;
        myHashCode = calculateHashCode();
    }

    @Override
    public int getType() {
        return myType;
    }

    @Override
    @NonNull
    public String getArtifactAddress() {
        return myArtifactAddress;
    }

    @Override
    @NonNull
    public File getArtifact() {
        throw new UnsupportedOperationException(
                "getArtifact() cannot be called when getType() returns LIBRARY_MODULE");
    }

    @Nullable
    @Override
    public String getBuildId() {
        return myBuildId;
    }

    @Override
    @Nullable
    public String getProjectPath() {
        return myProjectPath;
    }

    @Override
    @Nullable
    public String getVariant() {
        return myVariant;
    }

    @Override
    @NonNull
    public File getFolder() {
        throw new UnsupportedOperationException(
                "getFolder() cannot be called when getType() returns LIBRARY_MODULE");
    }

    @Override
    @NonNull
    public String getManifest() {
        throw new UnsupportedOperationException(
                "getManifest() cannot be called when getType() returns LIBRARY_MODULE");
    }

    @Override
    @NonNull
    public String getJarFile() {
        throw new UnsupportedOperationException(
                "getJarFile() cannot be called when getType() returns LIBRARY_MODULE");
    }

    @Override
    @NonNull
    public String getResFolder() {
        throw new UnsupportedOperationException(
                "getResFolder() cannot be called when getType() returns LIBRARY_MODULE");
    }

    @Override
    @NonNull
    public File getResStaticLibrary() {
        throw new UnsupportedOperationException(
                "getResStaticLibrary() cannot be called when getType() returns LIBRARY_MODULE");
    }

    @Override
    @NonNull
    public String getAssetsFolder() {
        throw new UnsupportedOperationException(
                "getAssetsFolder() cannot be called when getType() returns LIBRARY_MODULE");
    }

    @Override
    @NonNull
    public Collection<String> getLocalJars() {
        throw new UnsupportedOperationException(
                "getLocalJars() cannot be called when getType() returns LIBRARY_MODULE");
    }

    @Override
    @NonNull
    public String getJniFolder() {
        throw new UnsupportedOperationException(
                "getJniFolder() cannot be called when getType() returns LIBRARY_MODULE");
    }

    @Override
    @NonNull
    public String getAidlFolder() {
        throw new UnsupportedOperationException(
                "getAidlFolder() cannot be called when getType() returns LIBRARY_MODULE");
    }

    @Override
    @NonNull
    public String getRenderscriptFolder() {
        throw new UnsupportedOperationException(
                "getRenderscriptFolder() cannot be called when getType() returns LIBRARY_MODULE");
    }

    @Override
    @NonNull
    public String getProguardRules() {
        throw new UnsupportedOperationException(
                "getProguardRules() cannot be called when getType() returns LIBRARY_MODULE");
    }

    @Override
    @NonNull
    public String getLintJar() {
        throw new UnsupportedOperationException(
                "getLintJar() cannot be called when getType() returns LIBRARY_MODULE");
    }

    @Override
    @NonNull
    public String getExternalAnnotations() {
        throw new UnsupportedOperationException(
                "getExternalAnnotations() cannot be called when getType() returns LIBRARY_MODULE");
    }

    @Override
    @NonNull
    public String getPublicResources() {
        throw new UnsupportedOperationException(
                "getPublicResources() cannot be called when getType() returns LIBRARY_MODULE");
    }

    @Override
    @NonNull
    public String getSymbolFile() {
        throw new UnsupportedOperationException(
                "getSymbolFile() cannot be called when getType() returns LIBRARY_MODULE");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Library)) {
            return false;
        }
        Library that = (Library) o;
        return myType == that.getType()
                && Objects.equals(myArtifactAddress, that.getArtifactAddress())
                && Objects.equals(myBuildId, that.getBuildId())
                && Objects.equals(myProjectPath, that.getProjectPath())
                && Objects.equals(myVariant, that.getVariant());
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(myType, myArtifactAddress, myBuildId, myProjectPath, myVariant);
    }

    @Override
    public String toString() {
        return "Level2ModuleLibraryStub{"
                + "myType="
                + myType
                + ", myArtifactAddress='"
                + myArtifactAddress
                + '\''
                + ", myBuildId='"
                + myBuildId
                + '\''
                + ", myProjectPath='"
                + myProjectPath
                + '\''
                + ", myVariant='"
                + myVariant
                + '\''
                + '}';
    }
}
