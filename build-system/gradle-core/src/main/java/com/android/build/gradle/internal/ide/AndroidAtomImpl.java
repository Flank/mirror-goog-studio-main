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

package com.android.build.gradle.internal.ide;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.model.AndroidAtom;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
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
public class AndroidAtomImpl extends LibraryImpl implements AndroidAtom, Serializable {
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
    private final String atomName;
    @NonNull
    private final File dexFolder;
    @NonNull
    private final File atomMetadataFile;
    @NonNull
    private final File libFolder;
    @NonNull
    private final File javaResFolder;
    @NonNull
    private final File resourcePackage;
    @NonNull
    private final List<AndroidAtom> androidAtoms;
    @NonNull
    private final List<AndroidLibrary> androidLibraries;
    @NonNull
    private final Collection<JavaLibrary> javaLibraries;

    AndroidAtomImpl(
            @NonNull AndroidAtom clonedAtom,
            @NonNull List<AndroidAtom> androidAtoms,
            @NonNull List<AndroidLibrary> androidLibraries,
            @NonNull Collection<JavaLibrary> javaLibraries) {
        super(clonedAtom);
        this.androidAtoms = ImmutableList.copyOf(androidAtoms);
        this.androidLibraries = ImmutableList.copyOf(androidLibraries);
        this.javaLibraries = ImmutableList.copyOf(javaLibraries);
        variant = clonedAtom.getProjectVariant();
        bundle = clonedAtom.getBundle();
        folder = clonedAtom.getFolder();
        manifest = clonedAtom.getManifest();
        jarFile = clonedAtom.getJarFile();
        resFolder = clonedAtom.getResFolder();
        assetsFolder = clonedAtom.getAssetsFolder();
        atomName = clonedAtom.getAtomName();
        dexFolder = clonedAtom.getDexFolder();
        atomMetadataFile = clonedAtom.getAtomMetadataFile();
        libFolder = clonedAtom.getLibFolder();
        javaResFolder = clonedAtom.getJavaResFolder();
        resourcePackage = clonedAtom.getResourcePackage();
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
    public List<? extends AndroidAtom> getAtomDependencies() {
        return androidAtoms;
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
    public String getAtomName() {
        return atomName;
    }

    @NonNull
    @Override
    public File getDexFolder() {
        return dexFolder;
    }

    @NonNull
    @Override
    public File getAtomMetadataFile() {
        return atomMetadataFile;
    }

    @NonNull
    @Override
    public File getLibFolder() {
        return libFolder;
    }

    @NonNull
    @Override
    public File getJavaResFolder() {
        return javaResFolder;
    }

    @NonNull
    @Override
    public File getResourcePackage() {
        return resourcePackage;
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
        AndroidAtomImpl that = (AndroidAtomImpl) o;
        return Objects.equal(variant, that.variant) &&
                Objects.equal(bundle, that.bundle) &&
                Objects.equal(folder, that.folder) &&
                Objects.equal(manifest, that.manifest) &&
                Objects.equal(jarFile, that.jarFile) &&
                Objects.equal(resFolder, that.resFolder) &&
                Objects.equal(assetsFolder, that.assetsFolder) &&
                Objects.equal(atomName, that.atomName) &&
                Objects.equal(dexFolder, that.dexFolder) &&
                Objects.equal(atomMetadataFile, that.atomMetadataFile) &&
                Objects.equal(libFolder, that.libFolder) &&
                Objects.equal(javaResFolder, that.javaResFolder) &&
                Objects.equal(resourcePackage, that.resourcePackage) &&
                Objects.equal(androidAtoms, that.androidAtoms) &&
                Objects.equal(androidLibraries, that.androidLibraries) &&
                Objects.equal(javaLibraries, that.javaLibraries);
    }

    @Override
    public int hashCode() {
        return Objects
                .hashCode(super.hashCode(), variant, bundle, folder, manifest, jarFile, resFolder,
                        assetsFolder, atomName, dexFolder, atomMetadataFile, libFolder,
                        javaResFolder, resourcePackage, androidAtoms, androidLibraries,
                        javaLibraries);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("name", getName())
                .add("project", getProject())
                .add("variant", variant)
                .add("requestedCoordinates", getRequestedCoordinates())
                .add("resolvedCoordinates", getResolvedCoordinates())
                .add("bundle", bundle)
                .add("folder", folder)
                .add("manifest", manifest)
                .add("jarFile", jarFile)
                .add("resFolder", resFolder)
                .add("assetsFolder", assetsFolder)
                .add("atomName", atomName)
                .add("dexFolder", dexFolder)
                .add("atomMetadataFile", atomMetadataFile)
                .add("libFolder", libFolder)
                .add("javaResFolder", javaResFolder)
                .add("resourcePackage", resourcePackage)
                .add("androidAtoms", androidAtoms)
                .add("androidLibraries", androidLibraries)
                .add("javaLibraries", javaLibraries)
                .add("super", super.toString())
                .toString();
    }
}
