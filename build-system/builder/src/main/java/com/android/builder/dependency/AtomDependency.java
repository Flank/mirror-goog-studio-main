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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidAtom;
import com.android.builder.model.AndroidBundle;
import com.android.builder.model.MavenCoordinates;
import com.android.utils.FileUtils;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * Represents an Atom dependency.
 */
public class AtomDependency extends AbstractBundleDependency implements AndroidAtom {

    @NonNull
    private final List<AtomDependency> mAtomDependencies;

    @NonNull
    private final List<AndroidBundle> mBundleDependencies;

    public AtomDependency(
            @NonNull File bundle,
            @NonNull File explodedBundle,
            @NonNull List<LibraryDependency> androidDependencies,
            @NonNull List<AtomDependency> atomDependencies,
            @NonNull Collection<JarDependency> jarDependencies,
            @Nullable String name,
            @Nullable String variantName,
            @Nullable String projectPath,
            @Nullable MavenCoordinates requestedCoordinates,
            @NonNull MavenCoordinates resolvedCoordinates) {
        super(bundle,
                explodedBundle,
                androidDependencies,
                jarDependencies,
                name,
                projectPath,
                variantName,
                requestedCoordinates,
                resolvedCoordinates);
        this.mAtomDependencies = ImmutableList.copyOf(atomDependencies);
        this.mBundleDependencies = ImmutableList.<AndroidBundle>builder()
                .addAll(androidDependencies)
                .addAll(atomDependencies)
                .build();
    }

    @Override
    @NonNull
    public List<? extends AndroidBundle> getBundleDependencies() {
        return mBundleDependencies;
    }

    @Override
    @NonNull
    public List<? extends AndroidAtom> getAtomDependencies() {
        return mAtomDependencies;
    }

    @Override
    public boolean isSkipped() {
        return false;
    }

    @Override
    public boolean isProvided() {
        return false;
    }

    @Override
    @NonNull
    public File getJarFile() {
        return new File(getFolder(), "classes.jar");
    }

    @Override
    @NonNull
    public File getAtomFolder() {
        return new File(getFolder(), SdkConstants.EXT_ATOM);
    }

    @Override
    @NonNull
    public File getAtomMetadataFile() {
        return FileUtils.join(
                getFolder(),
                SdkConstants.FD_INSTANTAPP_METADATA,
                SdkConstants.FN_ATOM_METADATA);
    }

    @Override
    @NonNull
    public File getResourcePackageFile() {
        return new File(getFolder(), "resources.ap_");
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
        AtomDependency that = (AtomDependency) o;
        return Objects.equal(mAtomDependencies, that.mAtomDependencies);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(
                super.hashCode(),
                mAtomDependencies);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("mAtomDependencies", mAtomDependencies)
                .add("super", super.toString())
                .toString();
    }

}
