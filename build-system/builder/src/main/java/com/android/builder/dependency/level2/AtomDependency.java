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

package com.android.builder.dependency.level2;

import static com.android.SdkConstants.DOT_RES;
import static com.android.SdkConstants.FD_DEX;
import static com.android.SdkConstants.FD_INSTANTAPP_METADATA;
import static com.android.SdkConstants.FD_JAVA_RES;
import static com.android.SdkConstants.FD_NATIVE_LIBS;
import static com.android.SdkConstants.FN_ATOM_METADATA;
import static com.android.SdkConstants.FN_CLASSES_JAR;
import static com.android.SdkConstants.FN_RES_BASE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.dependency.HashCodeUtils;
import com.android.builder.model.MavenCoordinates;
import com.android.utils.FileUtils;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.List;
import java.util.Objects;

/**
 * Represents an Atom dependency and its content.
 */
@Immutable
public final class AtomDependency extends ExtractedDependency {

    @NonNull
    private final String atomName;

    private final int hashCode;

    public AtomDependency(
            @NonNull File artifactFile,
            @NonNull MavenCoordinates coordinates,
            @NonNull String name,
            @Nullable String projectPath,
            @NonNull File extractedFolder,
            @NonNull String atomName,
            @Nullable String variant) {
        super(artifactFile, coordinates, name, projectPath, extractedFolder, variant);
        this.atomName = atomName;
        hashCode = computeHashCode();
    }

    @NonNull
    public String getAtomName() {
        return atomName;
    }

    @Override @NonNull
    public File getJarFile() {
        return new File(getExtractedFolder(), FN_CLASSES_JAR);
    }

    @Nullable @Override
    public List<File> getAdditionalClasspath() {
        return ImmutableList.of();
    }

    @NonNull
    public File getDexFolder() {
        return new File(getExtractedFolder(), FD_DEX);
    }

    @NonNull
    public File getAtomMetadataFile() {
        return FileUtils.join(
                getExtractedFolder(),
                FD_INSTANTAPP_METADATA,
                FN_ATOM_METADATA);
    }

    @NonNull
    public File getLibFolder() {
        return new File(getExtractedFolder(), FD_NATIVE_LIBS);
    }

    @NonNull
    public File getJavaResFolder() {
        return new File(getExtractedFolder(), FD_JAVA_RES);
    }

    @NonNull
    public File getResourcePackage() {
        return new File(getExtractedFolder(), FN_RES_BASE + DOT_RES);
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
        return Objects.equals(atomName, that.atomName);
    }

    private int computeHashCode() {
        return HashCodeUtils.hashCode(super.hashCode(), atomName);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("artifactFile", getArtifactFile())
                .add("coordinates", getCoordinates())
                .add("projectPath", getProjectPath())
                .add("extractedFolder", getExtractedFolder())
                .add("atomName", atomName)
                .toString();
    }
}
