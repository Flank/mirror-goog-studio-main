/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.ide.common.gradle.model.impl;

import com.android.annotations.Nullable;
import com.android.ide.common.gradle.model.IdeDependencies;
import com.android.ide.common.gradle.model.IdeJavaArtifact;
import com.android.ide.common.gradle.model.IdeSourceProvider;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/** Creates a deep copy of a `JavaArtifact`. */
public final class IdeJavaArtifactImpl extends IdeBaseArtifactImpl implements IdeJavaArtifact {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 2L;

    @Nullable private final File myMockablePlatformJar;
    private final int myHashCode;

    // Used for serialization by the IDE.
    @SuppressWarnings("unused")
    IdeJavaArtifactImpl() {
        myMockablePlatformJar = null;

        myHashCode = 0;
    }

    public IdeJavaArtifactImpl(
            @NotNull String name,
            @NotNull String compileTaskName,
            @NotNull String assembleTaskName,
            @NotNull String postAssembleModelFile,
            @NotNull File classesFolder,
            @Nullable File javaResourcesFolder,
            @NotNull ImmutableSet<String> ideSetupTaskNames,
            @NotNull LinkedHashSet<File> generatedSourceFolders,
            @Nullable IdeSourceProvider variantSourceProvider,
            @Nullable IdeSourceProvider multiFlavorSourceProvider,
            @NotNull Set<File> additionalClassFolders,
            @NotNull IdeDependencies level2Dependencies,
            @Nullable File mockablePlatformJar) {
        super(
                name,
                compileTaskName,
                assembleTaskName,
                postAssembleModelFile,
                classesFolder,
                javaResourcesFolder,
                ideSetupTaskNames,
                generatedSourceFolders,
                variantSourceProvider,
                multiFlavorSourceProvider,
                additionalClassFolders,
                level2Dependencies);
        myMockablePlatformJar = mockablePlatformJar;

        myHashCode = calculateHashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeJavaArtifactImpl)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        IdeJavaArtifactImpl artifact = (IdeJavaArtifactImpl) o;
        return artifact.canEquals(this)
                && Objects.equals(myMockablePlatformJar, artifact.myMockablePlatformJar);
    }

    @Override
    protected boolean canEquals(Object other) {
        return other instanceof IdeJavaArtifactImpl;
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    @Override
    protected int calculateHashCode() {
        return Objects.hash(super.calculateHashCode(), myMockablePlatformJar);
    }


    @Override
    public String toString() {
        return "IdeJavaArtifact{"
                + super.toString()
                + ", myMockablePlatformJar="
                + myMockablePlatformJar
                + "}";
    }

    @Nullable
    @Override
    public File getMockablePlatformJar() {
        return myMockablePlatformJar;
    }
}
