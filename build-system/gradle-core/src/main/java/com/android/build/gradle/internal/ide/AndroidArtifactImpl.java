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
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.ClassField;
import com.android.builder.model.Dependencies;
import com.android.builder.model.InstantRun;
import com.android.builder.model.NativeLibrary;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.level2.DependencyGraphs;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Sets;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Implementation of AndroidArtifact that is serializable
 */
@Immutable
final class AndroidArtifactImpl extends BaseArtifactImpl implements AndroidArtifact, Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull
    private final Collection<AndroidArtifactOutput> outputs;
    private final boolean isSigned;
    @Nullable
    private final String signingConfigName;
    @NonNull
    private final String applicationId;
    @NonNull
    private final String sourceGenTaskName;

    @NonNull
    private final List<File> generatedResourceFolders;
    @Nullable
    private final Set<String> abiFilters;
    @NonNull
    private final Collection<NativeLibrary> nativeLibraries;
    @NonNull
    private final Map<String, ClassField> buildConfigFields;
    @NonNull
    private final Map<String, ClassField> resValues;
    @NonNull
    private final InstantRun instantRun;

    AndroidArtifactImpl(
            @NonNull String name,
            @NonNull Collection<AndroidArtifactOutput> outputs,
            @NonNull String assembleTaskName,
            boolean isSigned,
            @Nullable String signingConfigName,
            @NonNull String applicationId,
            @NonNull String sourceGenTaskName,
            @NonNull String compileTaskName,
            @NonNull List<File> generatedSourceFolders,
            @NonNull List<File> generatedResourceFolders,
            @NonNull File classesFolder,
            @NonNull File javaResourcesFolder,
            @NonNull Dependencies compileDependencies,
            @NonNull DependencyGraphs dependencyGraphs,
            @Nullable SourceProvider variantSourceProvider,
            @Nullable SourceProvider multiFlavorSourceProviders,
            @Nullable Set<String> abiFilters,
            @NonNull Collection<NativeLibrary> nativeLibraries,
            @NonNull Map<String,ClassField> buildConfigFields,
            @NonNull Map<String,ClassField> resValues,
            @NonNull InstantRun instantRun) {
        super(name, assembleTaskName, compileTaskName,
                classesFolder, javaResourcesFolder,
                compileDependencies, dependencyGraphs,
                variantSourceProvider, multiFlavorSourceProviders,
                generatedSourceFolders);

        this.outputs = outputs;
        this.isSigned = isSigned;
        this.signingConfigName = signingConfigName;
        this.applicationId = applicationId;
        this.sourceGenTaskName = sourceGenTaskName;
        this.generatedResourceFolders = generatedResourceFolders;
        this.abiFilters = abiFilters;
        this.nativeLibraries = nativeLibraries;
        this.buildConfigFields = buildConfigFields;
        this.resValues = resValues;
        this.instantRun = instantRun;
    }

    @NonNull
    @Override
    public Collection<AndroidArtifactOutput> getOutputs() {
        return outputs;
    }

    @Override
    public boolean isSigned() {
        return isSigned;
    }

    @Nullable
    @Override
    public String getSigningConfigName() {
        return signingConfigName;
    }

    @NonNull
    @Override
    public String getApplicationId() {
        return applicationId;
    }

    @NonNull
    @Override
    public String getSourceGenTaskName() {
        return sourceGenTaskName;
    }

    @NonNull
    @Override
    public Set<String> getIdeSetupTaskNames() {
        return Sets.newHashSet(getSourceGenTaskName());
    }

    @NonNull
    @Override
    public List<File> getGeneratedResourceFolders() {
        return generatedResourceFolders;
    }

    @Nullable
    @Override
    public Set<String> getAbiFilters() {
        return abiFilters;
    }

    @NonNull
    @Override
    public Collection<NativeLibrary> getNativeLibraries() {
        return nativeLibraries;
    }

    @NonNull
    @Override
    public Map<String, ClassField> getBuildConfigFields() {
        return buildConfigFields;
    }

    @NonNull
    @Override
    public Map<String, ClassField> getResValues() {
        return resValues;
    }

    @NonNull
    @Override
    public InstantRun getInstantRun() {
        return instantRun;
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
        AndroidArtifactImpl that = (AndroidArtifactImpl) o;
        return isSigned == that.isSigned &&
                Objects.equals(outputs, that.outputs) &&
                Objects.equals(signingConfigName, that.signingConfigName) &&
                Objects.equals(applicationId, that.applicationId) &&
                Objects.equals(sourceGenTaskName, that.sourceGenTaskName) &&
                Objects.equals(generatedResourceFolders, that.generatedResourceFolders) &&
                Objects.equals(abiFilters, that.abiFilters) &&
                Objects.equals(nativeLibraries, that.nativeLibraries) &&
                Objects.equals(buildConfigFields, that.buildConfigFields) &&
                Objects.equals(resValues, that.resValues) &&
                Objects.equals(instantRun, that.instantRun);
    }

    @Override
    public int hashCode() {
        return Objects
                .hash(super.hashCode(), outputs, isSigned, signingConfigName, applicationId,
                        sourceGenTaskName, generatedResourceFolders, abiFilters, nativeLibraries,
                        buildConfigFields, resValues, instantRun);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("outputs", outputs)
                .add("isSigned", isSigned)
                .add("signingConfigName", signingConfigName)
                .add("applicationId", applicationId)
                .add("sourceGenTaskName", sourceGenTaskName)
                .add("generatedResourceFolders", generatedResourceFolders)
                .add("abiFilters", abiFilters)
                .add("nativeLibraries", nativeLibraries)
                .add("buildConfigFields", buildConfigFields)
                .add("resValues", resValues)
                .add("instantRun", instantRun)
                .toString();
    }
}
