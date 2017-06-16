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
import com.android.annotations.Nullable;
import com.android.builder.model.level2.Library;
import java.io.File;
import java.util.Collection;
import java.util.Objects;

public class Level2JavaLibraryStub extends BaseStub implements Library {
    private final int myHashCode;
    private final int myType;
    @NonNull private final String myArtifactAddress;
    @NonNull private final File myArtifactFile;

    public Level2JavaLibraryStub() {
        this(LIBRARY_JAVA, "artifact:address:1.0", new File("artifactFile"));
    }

    public Level2JavaLibraryStub(
            int type, @NonNull String artifactAddress, @NonNull File artifactFile) {
        myType = type;
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
        if (!(o instanceof Library)) {
            return false;
        }
        Library that = (Library) o;
        return myType == that.getType()
                && Objects.equals(myArtifactAddress, that.getArtifactAddress())
                && Objects.equals(myArtifactFile, that.getArtifact());
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
        return "Level2JavaLibraryStub{"
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
