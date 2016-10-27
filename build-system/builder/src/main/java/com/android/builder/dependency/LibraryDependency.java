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

import static com.android.SdkConstants.DOT_JAR;
import static com.android.SdkConstants.FD_AIDL;
import static com.android.SdkConstants.FD_JARS;
import static com.android.SdkConstants.FD_JNI;
import static com.android.SdkConstants.FD_NATIVE_LIBS;
import static com.android.SdkConstants.FD_RENDERSCRIPT;
import static com.android.SdkConstants.FN_ANNOTATIONS_ZIP;
import static com.android.SdkConstants.FN_CLASSES_JAR;
import static com.android.SdkConstants.FN_LINT_JAR;
import static com.android.SdkConstants.FN_PROGUARD_TXT;
import static com.android.SdkConstants.FN_PUBLIC_TXT;
import static com.android.SdkConstants.FN_RESOURCE_TEXT;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.MavenCoordinates;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * An implementation of an AndroidLibrary that represents an extracted AAR.
 */
public final class LibraryDependency extends AbstractBundleDependency implements AndroidLibrary, SkippableLibrary {

    private final boolean mIsProvided;
    @NonNull
    private final File mJarsRootFolder;

    private final int hashCode;

    /**
     * Whether the library is an android Lib sub-module. This is different from testing
     * {@link #getProject()} as a module could wrap a local aar, which is not the same as a
     * lib sub-module.
     */
    private final boolean mIsSubModule;

    public static LibraryDependency createLocalTestedAarLibrary(
            @NonNull File bundle,
            @NonNull File bundleFolder,
            @Nullable String name,
            @Nullable String projectPath,
            @NonNull String projectVariant) {
        return new LibraryDependency(
                bundle,
                bundleFolder,
                ImmutableList.of(), /* androidDependencies */
                ImmutableList.of(), /* jarDependencies */
                name,
                projectPath,
                projectVariant,
                null, /* requestedCoordinates */
                new MavenCoordinatesImpl(
                        "__tested_library__",
                        bundle.getPath(),
                        "unspecified"),
                bundleFolder, /*jarsRootFolder*/
                false /*isProvided*/,
                true /*IsSubModule*/);
    }

    public static LibraryDependency createStagedAarLibrary(
            @NonNull File bundle,
            @NonNull File stagedFolder,
            @NonNull List<LibraryDependency> androidDependencies,
            @NonNull Collection<JarDependency> jarDependencies,
            @Nullable String name,
            @Nullable String variantName,
            @Nullable String projectPath,
            @Nullable MavenCoordinates requestedCoordinates,
            @NonNull MavenCoordinates resolvedCoordinates,
            boolean isProvided) {
        return new LibraryDependency(
                bundle,
                stagedFolder,
                androidDependencies,
                jarDependencies,
                name,
                variantName,
                projectPath,
                requestedCoordinates,
                resolvedCoordinates,
                stagedFolder,
                isProvided,
                true /*IsSubModule*/
        );
    }

    public static LibraryDependency createExplodedAarLibrary(
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
        return new LibraryDependency(
                bundle,
                explodedBundle,
                androidDependencies,
                jarDependencies,
                name,
                variantName,
                projectPath,
                requestedCoordinates,
                resolvedCoordinates,
                new File(explodedBundle, FD_JARS),
                isProvided,
                false /*IsSubModule*/);
    }

    private LibraryDependency(
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
            boolean isProvided,
            boolean isSubModule) {
        super(bundle,
                explodedBundle,
                androidDependencies,
                jarDependencies,
                name,
                variantName,
                projectPath,
                requestedCoordinates,
                resolvedCoordinates);
        mJarsRootFolder = jarsRootFolder;
        mIsProvided = isProvided;
        mIsSubModule = isSubModule;
        hashCode = computeHashCode();
    }

    /**
     * Returns whether the library is an android Lib sub-module.
     *
     * This is different from testing {@link #getProject()} as a module could wrap a local aar,
     * which is not the same as a lib sub-module.
     */
    public boolean isSubModule() {
        return mIsSubModule;
    }

    @Override
    public boolean isProvided() {
        return mIsProvided;
    }

    @NonNull
    @Override
    public List<File> getLocalJars() {
        List<File> localJars = Lists.newArrayList();
        File[] jarList = new File(getJarsRootFolder(), FD_NATIVE_LIBS).listFiles();
        if (jarList != null) {
            for (File jars : jarList) {
                if (jars.isFile() && jars.getName().endsWith(DOT_JAR)) {
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
    public File getJniFolder() {
        return new File(getFolder(), FD_JNI);
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
        return new File(getJarsRootFolder(), FN_LINT_JAR);
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
        throw new IllegalAccessError("Call isSkipped on DependenciesMutableData");
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
        return mIsProvided == that.mIsProvided &&
                mIsSubModule == that.mIsSubModule &&
                Objects.equals(mJarsRootFolder, that.mJarsRootFolder);
    }

    private int computeHashCode() {
        return HashCodeUtils.hashCode(
                super.hashCode(),
                mIsProvided,
                mIsSubModule,
                mJarsRootFolder);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("super", super.toString())
                .add("mIsProvided", mIsProvided)
                .add("mIsSubModule", mIsSubModule)
                .add("mJarsRootFolder", mJarsRootFolder)
                .toString();
    }
}

