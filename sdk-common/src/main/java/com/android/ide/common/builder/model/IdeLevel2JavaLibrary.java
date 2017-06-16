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
import com.android.annotations.Nullable;
import com.android.builder.model.level2.Library;
import java.io.File;
import java.util.Collection;
import java.util.Objects;

/** Creates a deep copy of {@link Library} of type LIBRARY_JAVA. */
public final class IdeLevel2JavaLibrary extends IdeModel implements Library {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 1L;

    @NonNull private final String myArtifactAddress;
    @NonNull private final File myArtifactFile;
    private final int myType;
    private final int myHashCode;

    IdeLevel2JavaLibrary(
            @NonNull String artifactAddress,
            @NonNull File artifactFile,
            @NonNull ModelCache modelCache,
            @NonNull Object sourceObject) {
        super(sourceObject, modelCache);
        myType = LIBRARY_JAVA;
        myArtifactAddress = artifactAddress;
        myArtifactFile = artifactFile;
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
        return myArtifactFile;
    }

    @Override
    @Nullable
    public String getVariant() {
        throw new UnsupportedOperationException(
                "getVariant() cannot be called when getType() returns LIBRARY_JAVA");
    }

    @Override
    @NonNull
    public String getProjectPath() {
        throw new UnsupportedOperationException(
                "getProjectPath() cannot be called when getType() returns LIBRARY_JAVA");
    }

    @Override
    @NonNull
    public File getFolder() {
        throw new UnsupportedOperationException(
                "getFolder() cannot be called when getType() returns LIBRARY_JAVA");
    }

    @Override
    @NonNull
    public String getManifest() {
        throw new UnsupportedOperationException(
                "getManifest() cannot be called when getType() returns LIBRARY_JAVA");
    }

    @Override
    @NonNull
    public String getJarFile() {
        throw new UnsupportedOperationException(
                "getJarFile() cannot be called when getType() returns LIBRARY_JAVA");
    }

    @Override
    @NonNull
    public String getResFolder() {
        throw new UnsupportedOperationException(
                "getResFolder() cannot be called when getType() returns LIBRARY_JAVA");
    }

    @Override
    @NonNull
    public String getAssetsFolder() {
        throw new UnsupportedOperationException(
                "getAssetsFolder() cannot be called when getType() returns LIBRARY_JAVA");
    }

    @Override
    @NonNull
    public Collection<String> getLocalJars() {
        throw new UnsupportedOperationException(
                "getLocalJars() cannot be called when getType() returns LIBRARY_JAVA");
    }

    @Override
    @NonNull
    public String getJniFolder() {
        throw new UnsupportedOperationException(
                "getJniFolder() cannot be called when getType() returns LIBRARY_JAVA");
    }

    @Override
    @NonNull
    public String getAidlFolder() {
        throw new UnsupportedOperationException(
                "getAidlFolder() cannot be called when getType() returns LIBRARY_JAVA");
    }

    @Override
    @NonNull
    public String getRenderscriptFolder() {
        throw new UnsupportedOperationException(
                "getRenderscriptFolder() cannot be called when getType() returns LIBRARY_JAVA");
    }

    @Override
    @NonNull
    public String getProguardRules() {
        throw new UnsupportedOperationException(
                "getProguardRules() cannot be called when getType() returns LIBRARY_JAVA");
    }

    @Override
    @NonNull
    public String getLintJar() {
        throw new UnsupportedOperationException(
                "getLintJar() cannot be called when getType() returns LIBRARY_JAVA");
    }

    @Override
    @NonNull
    public String getExternalAnnotations() {
        throw new UnsupportedOperationException(
                "getExternalAnnotations() cannot be called when getType() returns LIBRARY_JAVA");
    }

    @Override
    @NonNull
    public String getPublicResources() {
        throw new UnsupportedOperationException(
                "getPublicResources() cannot be called when getType() returns LIBRARY_JAVA");
    }

    @Override
    @NonNull
    public String getSymbolFile() {
        throw new UnsupportedOperationException(
                "getSymbolFile() cannot be called when getType() returns LIBRARY_JAVA");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeLevel2JavaLibrary)) {
            return false;
        }
        IdeLevel2JavaLibrary that = (IdeLevel2JavaLibrary) o;
        return myType == that.myType
                && Objects.equals(myArtifactAddress, that.myArtifactAddress)
                && Objects.equals(myArtifactFile, that.myArtifactFile);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(myType, myArtifactAddress, myArtifactFile);
    }

    @Override
    public String toString() {
        return "IdeLevel2JavaLibrary{"
                + "myType="
                + myType
                + ", myArtifactAddress='"
                + myArtifactAddress
                + '\''
                + ", myArtifactFile="
                + myArtifactFile
                + '}';
    }
}
