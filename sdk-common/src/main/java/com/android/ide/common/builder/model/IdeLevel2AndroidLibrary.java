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
import org.gradle.tooling.model.UnsupportedMethodException;

/** Creates a deep copy of {@link Library} of type LIBRARY_ANDROID. */
public final class IdeLevel2AndroidLibrary extends IdeModel implements Library {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 1L;

    @NonNull private final String myArtifactAddress;
    @NonNull private final File myFolder;
    @NonNull private final String myManifest;
    @NonNull private final String myJarFile;
    @NonNull private final String myResFolder;
    @NonNull private final String myAssetsFolder;
    @NonNull private final Collection<String> myLocalJars;
    @NonNull private final String myJniFolder;
    @NonNull private final String myAidlFolder;
    @NonNull private final String myRenderscriptFolder;
    @NonNull private final String myProguardRules;
    @NonNull private final String myLintJar;
    @NonNull private final String myExternalAnnotations;
    @NonNull private final String myPublicResources;
    @Nullable private final String mySymbolFile;
    @Nullable private final File myArtifactFile;
    private final int myType;
    private final int myHashCode;

    IdeLevel2AndroidLibrary(
            @NonNull Object original,
            @NonNull ModelCache modelCache,
            @NonNull String artifactAddress,
            @NonNull File folder,
            @NonNull String manifest,
            @NonNull String jarFile,
            @NonNull String resFolder,
            @NonNull String assetsFolder,
            @NonNull Collection<String> localJars,
            @NonNull String jniFolder,
            @NonNull String aidlFolder,
            @NonNull String renderscriptFolder,
            @NonNull String proguardRules,
            @NonNull String lintJar,
            @NonNull String externalAnnotations,
            @NonNull String publicResources,
            @Nullable String symbolFile,
            @Nullable File artifactFile) {
        super(original, modelCache);
        myType = LIBRARY_ANDROID;
        myArtifactAddress = artifactAddress;
        myFolder = folder;
        myManifest = manifest;
        myJarFile = jarFile;
        myResFolder = resFolder;
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
        if (myArtifactFile == null) {
            throw new UnsupportedOperationException(
                    "getArtifact() cannot be called when getType() returns ANDROID_LIBRARY");
        }
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
    public String getResFolder() {
        return myResFolder;
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
        if (mySymbolFile != null) {
            return mySymbolFile;
        }
        throw new UnsupportedMethodException("Unsupported method: AndroidLibrary.getSymbolFile()");
    }

    @Override
    @Nullable
    public String getVariant() {
        throw new UnsupportedOperationException(
                "getVariant() cannot be called when getType() returns ANDROID_LIBRARY");
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
        if (!(o instanceof IdeLevel2AndroidLibrary)) {
            return false;
        }
        IdeLevel2AndroidLibrary that = (IdeLevel2AndroidLibrary) o;
        return myType == that.myType
                && Objects.equals(myArtifactAddress, that.myArtifactAddress)
                && Objects.equals(myFolder, that.myFolder)
                && Objects.equals(myManifest, that.myManifest)
                && Objects.equals(myJarFile, that.myJarFile)
                && Objects.equals(myResFolder, that.myResFolder)
                && Objects.equals(myAssetsFolder, that.myAssetsFolder)
                && Objects.equals(myLocalJars, that.myLocalJars)
                && Objects.equals(myJniFolder, that.myJniFolder)
                && Objects.equals(myAidlFolder, that.myAidlFolder)
                && Objects.equals(myRenderscriptFolder, that.myRenderscriptFolder)
                && Objects.equals(myProguardRules, that.myProguardRules)
                && Objects.equals(myLintJar, that.myLintJar)
                && Objects.equals(myExternalAnnotations, that.myExternalAnnotations)
                && Objects.equals(myPublicResources, that.myPublicResources)
                && Objects.equals(mySymbolFile, that.mySymbolFile)
                && Objects.equals(myArtifactFile, that.myArtifactFile);
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
        return "IdeLevel2AndroidLibrary{"
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
