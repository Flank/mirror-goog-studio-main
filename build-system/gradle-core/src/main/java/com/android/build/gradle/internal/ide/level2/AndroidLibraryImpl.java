/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.ide.level2;

import static com.android.SdkConstants.FD_AIDL;
import static com.android.SdkConstants.FD_ASSETS;
import static com.android.SdkConstants.FD_JARS;
import static com.android.SdkConstants.FD_JNI;
import static com.android.SdkConstants.FD_RENDERSCRIPT;
import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.FN_ANNOTATIONS_ZIP;
import static com.android.SdkConstants.FN_CLASSES_JAR;
import static com.android.SdkConstants.FN_LINT_JAR;
import static com.android.SdkConstants.FN_PROGUARD_TXT;
import static com.android.SdkConstants.FN_PUBLIC_TXT;
import static com.android.SdkConstants.FN_RESOURCE_TEXT;
import static com.android.utils.FileUtils.relativePossiblyNonExistingPath;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.level2.Library;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of Level 2 AndroidLibrary
 */
public final class AndroidLibraryImpl implements Library, Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull
    private final String address;
    @Nullable private final File artifactFile;
    @NonNull
    private final File folder;
    @NonNull
    private final List<String> localJarPath;

    public AndroidLibraryImpl(
            @NonNull String address,
            @Nullable File artifactFile,
            @NonNull File folder,
            @NonNull Collection<File> localJarOverride) {
        this.address = address;
        this.artifactFile = artifactFile;
        this.folder = folder;
        // TODO Fix me once we are always extracting AARs during sync.
        localJarPath = Lists.newArrayListWithCapacity(localJarOverride.size());
        for (File localJar : localJarOverride) {
            localJarPath.add(relativePossiblyNonExistingPath(localJar, folder).intern());
        }
    }

    @Override
    public int getType() {
        return LIBRARY_ANDROID;
    }

    @NonNull
    @Override
    public String getArtifactAddress() {
        return address;
    }

    @NonNull
    @Override
    public File getArtifact() {
        if (artifactFile == null) {
            throw new UnsupportedOperationException(
                    "getArtifact() cannot be called when getType() returns ANDROID_LIBRARY");
        }
        return artifactFile;
    }

    @NonNull
    @Override
    public File getFolder() {
        return folder;
    }

    @NonNull
    @Override
    public String getManifest() {
        return FN_ANDROID_MANIFEST_XML;
    }

    @NonNull
    @Override
    public String getJarFile() {
        return FD_JARS + File.separatorChar + FN_CLASSES_JAR;
    }

    @NonNull
    @Override
    public String getResFolder() {
        return FD_RES;
    }

    @NonNull
    @Override
    public String getAssetsFolder() {
        return FD_ASSETS;
    }

    @NonNull
    @Override
    public Collection<String> getLocalJars() {
        return localJarPath;
    }

    @NonNull
    @Override
    public String getJniFolder() {
        return FD_JNI;
    }

    @NonNull
    @Override
    public String getAidlFolder() {
        return FD_AIDL;
    }

    @NonNull
    @Override
    public String getRenderscriptFolder() {
        return FD_RENDERSCRIPT;
    }

    @NonNull
    @Override
    public String getProguardRules() {
        return FN_PROGUARD_TXT;
    }

    @NonNull
    @Override
    public String getLintJar() {
        return FD_JARS + File.separatorChar + FN_LINT_JAR;
    }

    @NonNull
    @Override
    public String getExternalAnnotations() {
        return FD_JARS + File.separatorChar + FN_ANNOTATIONS_ZIP;
    }

    @NonNull
    @Override
    public String getPublicResources() {
        return FN_PUBLIC_TXT;
    }

    @NonNull
    @Override
    public String getSymbolFile() {
        return FN_RESOURCE_TEXT;
    }

    @Nullable
    @Override
    public String getVariant() {
        throw new UnsupportedOperationException(
                "getVariant() cannot be called when getType() returns ANDROID_LIBRARY");
    }

    @NonNull
    @Override
    public String getProjectPath() {
        throw new UnsupportedOperationException(
                "getProjectPath() cannot be called when getType() returns ANDROID_LIBRARY");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AndroidLibraryImpl that = (AndroidLibraryImpl) o;
        return Objects.equals(address, that.address) &&
                Objects.equals(artifactFile, that.artifactFile) &&
                Objects.equals(folder, that.folder) &&
                Objects.equals(localJarPath, that.localJarPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, artifactFile, folder, localJarPath);
    }
}
