/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.internal.ide;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * Serializable implementation of AndroidLibrary for use in the model.
 */
@Immutable
final class AndroidLibraryImpl extends LibraryImpl implements AndroidLibrary, Serializable {
    private static final long serialVersionUID = 1L;

    @Nullable
    private final String variant;
    @NonNull
    private final File bundle;
    @NonNull
    private final File folder;
    @NonNull
    private final File manifest;
    @NonNull
    private final File jarFile;
    @NonNull
    private final File resFolder;
    @NonNull
    private final File assetsFolder;
    @NonNull
    private final File jniFolder;
    @NonNull
    private final File aidlFolder;
    @NonNull
    private final File renderscriptFolder;
    @NonNull
    private final File proguardRules;
    @NonNull
    private final File lintJar;
    @NonNull
    private final File annotations;
    @NonNull
    private final File publicResources;
    @NonNull
    private final File symbolFile;
    @NonNull
    private final List<AndroidLibrary> androidLibraries;
    @NonNull
    private final Collection<JavaLibrary> javaLibraries;
    @NonNull
    private final Collection<File> localJars;

    AndroidLibraryImpl(
            @NonNull AndroidLibrary clonedLibrary,
            boolean isSkipped,
            @NonNull List<AndroidLibrary> androidLibraries,
            @NonNull Collection<JavaLibrary> javaLibraries,
            @NonNull Collection<File> localJavaLibraries) {
        super(clonedLibrary, isSkipped);
        this.androidLibraries = ImmutableList.copyOf(androidLibraries);
        this.javaLibraries = ImmutableList.copyOf(javaLibraries);
        this.localJars = ImmutableList.copyOf(localJavaLibraries);
        variant = clonedLibrary.getProjectVariant();
        bundle = clonedLibrary.getBundle();
        folder = clonedLibrary.getFolder();
        manifest = clonedLibrary.getManifest();
        jarFile = clonedLibrary.getJarFile();
        resFolder = clonedLibrary.getResFolder();
        assetsFolder = clonedLibrary.getAssetsFolder();
        jniFolder = clonedLibrary.getJniFolder();
        aidlFolder = clonedLibrary.getAidlFolder();
        renderscriptFolder = clonedLibrary.getRenderscriptFolder();
        proguardRules = clonedLibrary.getProguardRules();
        lintJar = clonedLibrary.getLintJar();
        annotations = clonedLibrary.getExternalAnnotations();
        publicResources = clonedLibrary.getPublicResources();
        symbolFile = clonedLibrary.getSymbolFile();
    }

    @Nullable
    @Override
    public String getProjectVariant() {
        return variant;
    }

    @NonNull
    @Override
    public File getBundle() {
        return bundle;
    }

    @NonNull
    @Override
    public File getFolder() {
        return folder;
    }

    @NonNull
    @Override
    public List<? extends AndroidLibrary> getLibraryDependencies() {
        return androidLibraries;
    }

    @NonNull
    @Override
    public Collection<? extends JavaLibrary> getJavaDependencies() {
        return javaLibraries;
    }

    @NonNull
    @Override
    public Collection<File> getLocalJars() {
        return localJars;
    }

    @NonNull
    @Override
    public File getManifest() {
        return manifest;
    }

    @NonNull
    @Override
    public File getJarFile() {
        return jarFile;
    }

    @NonNull
    @Override
    public File getResFolder() {
        return resFolder;
    }

    @NonNull
    @Override
    public File getAssetsFolder() {
        return assetsFolder;
    }

    @NonNull
    @Override
    public File getJniFolder() {
        return jniFolder;
    }

    @NonNull
    @Override
    public File getAidlFolder() {
        return aidlFolder;
    }

    @NonNull
    @Override
    public File getRenderscriptFolder() {
        return renderscriptFolder;
    }

    @NonNull
    @Override
    public File getProguardRules() {
        return proguardRules;
    }

    @NonNull
    @Override
    public File getLintJar() {
        return lintJar;
    }

    @NonNull
    @Override
    public File getExternalAnnotations() {
        return annotations;
    }

    @Override
    @NonNull
    public File getPublicResources() {
        return publicResources;
    }

    @Override
    @Deprecated
    public boolean isOptional() {
        return isProvided();
    }

    @NonNull
    @Override
    public File getSymbolFile() {
        return symbolFile;
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
        AndroidLibraryImpl that = (AndroidLibraryImpl) o;
        return Objects.equal(variant, that.variant) &&
                Objects.equal(bundle, that.bundle) &&
                Objects.equal(folder, that.folder) &&
                Objects.equal(manifest, that.manifest) &&
                Objects.equal(jarFile, that.jarFile) &&
                Objects.equal(resFolder, that.resFolder) &&
                Objects.equal(assetsFolder, that.assetsFolder) &&
                Objects.equal(jniFolder, that.jniFolder) &&
                Objects.equal(aidlFolder, that.aidlFolder) &&
                Objects.equal(renderscriptFolder, that.renderscriptFolder) &&
                Objects.equal(proguardRules, that.proguardRules) &&
                Objects.equal(lintJar, that.lintJar) &&
                Objects.equal(annotations, that.annotations) &&
                Objects.equal(publicResources, that.publicResources) &&
                Objects.equal(symbolFile, that.symbolFile) &&
                Objects.equal(androidLibraries, that.androidLibraries) &&
                Objects.equal(javaLibraries, that.javaLibraries) &&
                Objects.equal(localJars, that.localJars);
    }

    @Override
    public int hashCode() {
        return Objects
                .hashCode(super.hashCode(), variant, bundle, folder, manifest, jarFile, resFolder,
                        assetsFolder, jniFolder, aidlFolder, renderscriptFolder, proguardRules,
                        lintJar,
                        annotations, publicResources, symbolFile, androidLibraries, javaLibraries,
                        localJars);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", getName())
                .add("project", getProject())
                .add("variant", variant)
                .add("bundle", bundle)
                .add("folder", folder)
                .add("manifest", manifest)
                .add("jarFile", jarFile)
                .add("resFolder", resFolder)
                .add("assetsFolder", assetsFolder)
                .add("jniFolder", jniFolder)
                .add("aidlFolder", aidlFolder)
                .add("renderscriptFolder", renderscriptFolder)
                .add("proguardRules", proguardRules)
                .add("lintJar", lintJar)
                .add("annotations", annotations)
                .add("publicResources", publicResources)
                .add("symbolFile", symbolFile)
                .add("androidLibraries", androidLibraries)
                .add("javaLibraries", javaLibraries)
                .add("localJars", localJars)
                .add("super", super.toString())
                .toString();
    }
}
