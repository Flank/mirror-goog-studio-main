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
package com.android.builder.dependency;

import static com.android.SdkConstants.FD_AIDL;
import static com.android.SdkConstants.FD_ASSETS;
import static com.android.SdkConstants.FD_JARS;
import static com.android.SdkConstants.FD_RENDERSCRIPT;
import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FN_ANNOTATIONS_ZIP;
import static com.android.SdkConstants.FN_CLASSES_JAR;
import static com.android.SdkConstants.FN_PUBLIC_TXT;
import static com.android.SdkConstants.FN_RESOURCE_TEXT;
import static com.android.SdkConstants.LIBS_FOLDER;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.MavenCoordinates;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class LibraryDependency extends AbstractBundleDependency implements AndroidLibrary, SkippableLibrary {

    private boolean mSkipped = false;
    private final boolean mIsProvided;
    @NonNull
    private final File mJarsRootFolder;

    public LibraryDependency(
            @NonNull File bundle,
            @NonNull File explodedBundle,
            @NonNull List<LibraryDependency> androidDependencies,
            @NonNull Collection<JarDependency> jarDependencies,
            @Nullable String name,
            @Nullable String variantName,
            @Nullable String projectPath,
            @Nullable MavenCoordinates requestedCoordinates,
            @NonNull MavenCoordinates resolvedCoordinates,
            boolean isProvided) {
        this(bundle,
                explodedBundle,
                androidDependencies,
                jarDependencies,
                name,
                variantName,
                projectPath,
                requestedCoordinates,
                resolvedCoordinates,
                new File(explodedBundle, FD_JARS),
                isProvided);
    }

    public LibraryDependency(
            @NonNull File bundle,
            @NonNull File explodedBundle,
            @NonNull List<LibraryDependency> androidDependencies,
            @NonNull Collection<JarDependency> jarDependencies,
            @Nullable String name,
            @Nullable String variantName,
            @Nullable String projectPath,
            @Nullable MavenCoordinates requestedCoordinates,
            @NonNull MavenCoordinates resolvedCoordinates,
            @NonNull File jarsRootFolder,
            boolean isProvided) {
        super(bundle,
                explodedBundle,
                androidDependencies,
                jarDependencies,
                name,
                variantName,
                projectPath,
                requestedCoordinates,
                resolvedCoordinates);
        this.mJarsRootFolder = jarsRootFolder;
        this.mIsProvided = isProvided;
    }

    @Override
    public boolean isProvided() {
        return mIsProvided;
    }

    @NonNull
    @Override
    public List<File> getLocalJars() {
        List<File> localJars = Lists.newArrayList();
        File[] jarList = new File(getJarsRootFolder(), LIBS_FOLDER).listFiles();
        if (jarList != null) {
            for (File jars : jarList) {
                if (jars.isFile() && jars.getName().endsWith(".jar")) {
                    localJars.add(jars);
                }
            }
        }

        return localJars;
    }

    @Override
    @NonNull
    public File getJarFile() {
        return new File(getJarsRootFolder(), FN_CLASSES_JAR);
    }

    @Override
    @NonNull
    public File getResFolder() {
        return new File(getFolder(), FD_RES);
    }

    @Override
    @NonNull
    public File getAssetsFolder() {
        return new File(getFolder(), FD_ASSETS);
    }

    @Override
    @NonNull
    public File getJniFolder() {
        return new File(getFolder(), "jni");
    }

    @Override
    @NonNull
    public File getAidlFolder() {
        return new File(getFolder(), FD_AIDL);
    }

    @Override
    @NonNull
    public File getRenderscriptFolder() {
        return new File(getFolder(), FD_RENDERSCRIPT);
    }

    @Override
    @NonNull
    public File getProguardRules() {
        return new File(getFolder(), FN_PROGUARD_TXT);
    }

    @Override
    @NonNull
    public File getLintJar() {
        return new File(getJarsRootFolder(), "lint.jar");
    }

    @Override
    @NonNull
    public File getExternalAnnotations() {
        return new File(getFolder(), FN_ANNOTATIONS_ZIP);
    }

    @Override
    @NonNull
    public File getPublicResources() {
        return new File(getFolder(), FN_PUBLIC_TXT);
    }

    @Override
    @NonNull
    public File getSymbolFile() {
        return new File(getFolder(), FN_RESOURCE_TEXT);
    }

    @NonNull
    protected File getJarsRootFolder() {
        return mJarsRootFolder;
    }

    @Override
    public boolean isSkipped() {
        return mSkipped;
    }

    @Override
    public void skip() {
        mSkipped = true;
    }

    @Override
    @Deprecated
    public boolean isOptional() {
        return isProvided();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        LibraryDependency that = (LibraryDependency) o;
        return mSkipped == that.mSkipped &&
                mIsProvided == that.mIsProvided &&
                Objects.equals(mJarsRootFolder, that.mJarsRootFolder);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(super.hashCode(), mSkipped, mIsProvided, mJarsRootFolder);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("super", super.toString())
                .add("mSkipped", mSkipped)
                .add("mIsProvided", mIsProvided)
                .add("mJarsRootFolder", mJarsRootFolder)
                .toString();
    }
}

