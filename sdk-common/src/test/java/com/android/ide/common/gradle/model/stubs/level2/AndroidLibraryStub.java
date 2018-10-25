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
import java.util.Collections;
import java.util.Objects;

public class AndroidLibraryStub extends BaseStub implements Library {
    private final int myHashCode;
    private final int myType;
    @NonNull private final String myArtifactAddress;
    @NonNull private final File myFolder;
    @NonNull private final String myManifest;
    @NonNull private final String myJarFile;
    @NonNull private final String myApiJarFile;
    @NonNull private final String myResFolder;
    @Nullable private final File resStaticLibrary;
    @NonNull private final String myAssetsFolder;
    @NonNull private final Collection<String> myLocalJars;
    @NonNull private final String myJniFolder;
    @NonNull private final String myAidlFolder;
    @NonNull private final String myRenderscriptFolder;
    @NonNull private final String myProguardRules;
    @NonNull private final String myLintJar;
    @NonNull private final String myExternalAnnotations;
    @NonNull private final String myPublicResources;
    @NonNull private final String mySymbolFile;
    @NonNull private final File myArtifactFile;

    public AndroidLibraryStub() {
        this(
                LIBRARY_ANDROID,
                "artifact:address:1.0",
                new File("libraryFolder"),
                "manifest.xml",
                "file.jar",
                "api.jar",
                "res",
                new File("libraryFolder/res.apk"),
                "assets",
                Collections.emptyList(),
                "jni",
                "aidl",
                "renderscriptFolder",
                "proguardRules",
                "lint.jar",
                "externalAnnotations",
                "publicResources",
                "symbolFile",
                new File("artifactFile"));
    }

    public AndroidLibraryStub(
            int type,
            @NonNull String artifactAddress,
            @NonNull File folder,
            @NonNull String manifest,
            @NonNull String jarFile,
            @NonNull String apiJarFile,
            @NonNull String resFolder,
            @Nullable File resStaticLibrary,
            @NonNull String assetsFolder,
            @NonNull Collection<String> localJars,
            @NonNull String jniFolder,
            @NonNull String aidlFolder,
            @NonNull String renderscriptFolder,
            @NonNull String proguardRules,
            @NonNull String lintJar,
            @NonNull String externalAnnotations,
            @NonNull String publicResources,
            @NonNull String symbolFile,
            @NonNull File artifactFile) {
        myType = type;
        myArtifactAddress = artifactAddress;
        myFolder = folder;
        myManifest = manifest;
        myJarFile = jarFile;
        myApiJarFile = apiJarFile;
        myResFolder = resFolder;
        this.resStaticLibrary = resStaticLibrary;
        myAssetsFolder = assetsFolder;
        myLocalJars = localJars;
        myJniFolder = jniFolder;
        myAidlFolder = aidlFolder;
        myRenderscriptFolder = renderscriptFolder;
        myProguardRules = proguardRules;
        myLintJar = lintJar;
        myExternalAnnotations = externalAnnotations;
        myPublicResources = publicResources;
        mySymbolFile = symbolFile;
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
    @NonNull
    public File getFolder() {
        return myFolder;
    }

    @Override
    @NonNull
    public String getManifest() {
        return myManifest;
    }

    @Override
    @NonNull
    public String getJarFile() {
        return myJarFile;
    }

    @Override
    @NonNull
    public String getApiJarFile() {
        return myApiJarFile;
    }

    @Override
    @NonNull
    public String getResFolder() {
        return myResFolder;
    }

    @Nullable
    @Override
    public File getResStaticLibrary() {
        return resStaticLibrary;
    }

    @Override
    @NonNull
    public String getAssetsFolder() {
        return myAssetsFolder;
    }

    @Override
    @NonNull
    public Collection<String> getLocalJars() {
        return myLocalJars;
    }

    @Override
    @NonNull
    public String getJniFolder() {
        return myJniFolder;
    }

    @Override
    @NonNull
    public String getAidlFolder() {
        return myAidlFolder;
    }

    @Override
    @NonNull
    public String getRenderscriptFolder() {
        return myRenderscriptFolder;
    }

    @Override
    @NonNull
    public String getProguardRules() {
        return myProguardRules;
    }

    @Override
    @NonNull
    public String getLintJar() {
        return myLintJar;
    }

    @Override
    @NonNull
    public String getExternalAnnotations() {
        return myExternalAnnotations;
    }

    @Override
    @NonNull
    public String getPublicResources() {
        return myPublicResources;
    }

    @Override
    @NonNull
    public String getSymbolFile() {
        return mySymbolFile;
    }

    @Override
    @Nullable
    public String getVariant() {
        throw new UnsupportedOperationException(
                "getVariant() cannot be called when getType() returns ANDROID_LIBRARY");
    }

    @Nullable
    @Override
    public String getBuildId() {
        throw new UnsupportedOperationException(
                "getBuildId() cannot be called when getType() returns ANDROID_LIBRARY");
    }

    @Override
    @NonNull
    public String getProjectPath() {
        throw new UnsupportedOperationException(
                "getProjectPath() cannot be called when getType() returns ANDROID_LIBRARY");
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
                && Objects.equals(myFolder, that.getFolder())
                && Objects.equals(myManifest, that.getManifest())
                && Objects.equals(myJarFile, that.getJarFile())
                && Objects.equals(myApiJarFile, that.getApiJarFile())
                && Objects.equals(myResFolder, that.getResFolder())
                && Objects.equals(myAssetsFolder, that.getAssetsFolder())
                && Objects.equals(myLocalJars, that.getLocalJars())
                && Objects.equals(myJniFolder, that.getJniFolder())
                && Objects.equals(myAidlFolder, that.getAidlFolder())
                && Objects.equals(myRenderscriptFolder, that.getRenderscriptFolder())
                && Objects.equals(myProguardRules, that.getProguardRules())
                && Objects.equals(myLintJar, that.getLintJar())
                && Objects.equals(myExternalAnnotations, that.getExternalAnnotations())
                && Objects.equals(myPublicResources, that.getPublicResources())
                && Objects.equals(mySymbolFile, that.getSymbolFile())
                && Objects.equals(myArtifactFile, that.getArtifact());
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(
                myType,
                myArtifactAddress,
                myFolder,
                myManifest,
                myJarFile,
                myApiJarFile,
                myResFolder,
                myAssetsFolder,
                myLocalJars,
                myJniFolder,
                myAidlFolder,
                myRenderscriptFolder,
                myProguardRules,
                myLintJar,
                myExternalAnnotations,
                myPublicResources,
                mySymbolFile,
                myArtifactFile);
    }

    @Override
    public String toString() {
        return "Level2AndroidLibraryStub{"
                + "myType="
                + myType
                + ", myArtifactAddress='"
                + myArtifactAddress
                + '\''
                + ", myFolder="
                + myFolder
                + ", myManifest='"
                + myManifest
                + '\''
                + ", myJarFile='"
                + myJarFile
                + '\''
                + ", myApiJarFile='"
                + myApiJarFile
                + '\''
                + ", myResFolder='"
                + myResFolder
                + '\''
                + ", myAssetsFolder='"
                + myAssetsFolder
                + '\''
                + ", myLocalJars="
                + myLocalJars
                + ", myJniFolder='"
                + myJniFolder
                + '\''
                + ", myAidlFolder='"
                + myAidlFolder
                + '\''
                + ", myRenderscriptFolder='"
                + myRenderscriptFolder
                + '\''
                + ", myProguardRules='"
                + myProguardRules
                + '\''
                + ", myLintJar='"
                + myLintJar
                + '\''
                + ", myExternalAnnotations='"
                + myExternalAnnotations
                + '\''
                + ", myPublicResources='"
                + myPublicResources
                + '\''
                + ", mySymbolFile='"
                + mySymbolFile
                + '\''
                + ", myArtifactFile="
                + myArtifactFile
                + '}';
    }
}
