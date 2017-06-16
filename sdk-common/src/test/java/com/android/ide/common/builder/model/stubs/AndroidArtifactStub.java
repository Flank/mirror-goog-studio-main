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
package com.android.ide.common.builder.model.stubs;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.ClassField;
import com.android.builder.model.InstantRun;
import com.android.builder.model.NativeLibrary;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class AndroidArtifactStub extends BaseArtifactStub implements AndroidArtifact {
    @NonNull private final Collection<AndroidArtifactOutput> myOutputs;
    @NonNull private final String myApplicationId;
    @NonNull private final String mySourceGenTaskName;
    @NonNull private final Collection<File> myGeneratedResourceFolders = new ArrayList<>();
    @NonNull private final Map<String, ClassField> myBuildConfigFields;
    @NonNull private final Map<String, ClassField> myResValues;
    @NonNull private final InstantRun myInstantRun;
    @Nullable private final String mySigningConfigName;
    @Nullable private final Set<String> myAbiFilters;
    @Nullable private final Collection<NativeLibrary> myNativeLibraries;
    private final boolean mySigned;

    public AndroidArtifactStub() {
        this(
                Lists.newArrayList(new AndroidArtifactOutputStub()),
                "applicationId",
                "sourceGenTaskName",
                ImmutableMap.of("buildConfigField", new ClassFieldStub()),
                ImmutableMap.of("resValue", new ClassFieldStub()),
                new InstantRunStub(),
                "signingConfigName",
                Sets.newHashSet("filter"),
                Lists.newArrayList(new NativeLibraryStub()),
                true);
    }

    public AndroidArtifactStub(
            @NonNull Collection<AndroidArtifactOutput> outputs,
            @NonNull String applicationId,
            @NonNull String sourceGenTaskName,
            @NonNull Map<String, ClassField> buildConfigFields,
            @NonNull Map<String, ClassField> resValues,
            @NonNull InstantRun run,
            @Nullable String signingConfigName,
            @Nullable Set<String> filters,
            @Nullable Collection<NativeLibrary> libraries,
            boolean signed) {
        myOutputs = outputs;
        myApplicationId = applicationId;
        mySourceGenTaskName = sourceGenTaskName;

        myBuildConfigFields = buildConfigFields;
        myResValues = resValues;
        myInstantRun = run;
        mySigningConfigName = signingConfigName;
        myAbiFilters = filters;
        myNativeLibraries = libraries;
        mySigned = signed;
    }

    @Override
    @NonNull
    public Collection<AndroidArtifactOutput> getOutputs() {
        return myOutputs;
    }

    @Override
    @NonNull
    public String getApplicationId() {
        return myApplicationId;
    }

    @Override
    @NonNull
    public String getSourceGenTaskName() {
        return mySourceGenTaskName;
    }

    @Override
    @NonNull
    public Collection<File> getGeneratedResourceFolders() {
        return myGeneratedResourceFolders;
    }

    @Override
    @NonNull
    public Map<String, ClassField> getBuildConfigFields() {
        return myBuildConfigFields;
    }

    @Override
    @NonNull
    public Map<String, ClassField> getResValues() {
        return myResValues;
    }

    @Override
    @NonNull
    public InstantRun getInstantRun() {
        return myInstantRun;
    }

    @Override
    @Nullable
    public String getSigningConfigName() {
        return mySigningConfigName;
    }

    @Override
    @Nullable
    public Set<String> getAbiFilters() {
        return myAbiFilters;
    }

    @Override
    @Nullable
    public Collection<NativeLibrary> getNativeLibraries() {
        return myNativeLibraries;
    }

    @Override
    public boolean isSigned() {
        return mySigned;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AndroidArtifact)) {
            return false;
        }
        AndroidArtifact artifact = (AndroidArtifact) o;
        return Objects.equals(getName(), artifact.getName())
                && Objects.equals(getCompileTaskName(), artifact.getCompileTaskName())
                && Objects.equals(getAssembleTaskName(), artifact.getAssembleTaskName())
                && Objects.equals(getClassesFolder(), artifact.getClassesFolder())
                && Objects.equals(getJavaResourcesFolder(), artifact.getJavaResourcesFolder())
                && Objects.equals(getDependencies(), artifact.getDependencies())
                && Objects.equals(getCompileDependencies(), artifact.getCompileDependencies())
                && Objects.equals(getDependencyGraphs(), artifact.getDependencyGraphs())
                && Objects.equals(getIdeSetupTaskNames(), artifact.getIdeSetupTaskNames())
                && Objects.equals(getGeneratedSourceFolders(), artifact.getGeneratedSourceFolders())
                && Objects.equals(getVariantSourceProvider(), artifact.getVariantSourceProvider())
                && Objects.equals(
                        getMultiFlavorSourceProvider(), artifact.getMultiFlavorSourceProvider())
                && isSigned() == artifact.isSigned()
                && Objects.equals(getOutputs(), artifact.getOutputs())
                && Objects.equals(getApplicationId(), artifact.getApplicationId())
                && Objects.equals(getSourceGenTaskName(), artifact.getSourceGenTaskName())
                && Objects.equals(
                        getGeneratedResourceFolders(), artifact.getGeneratedResourceFolders())
                && Objects.equals(getBuildConfigFields(), artifact.getBuildConfigFields())
                && Objects.equals(getResValues(), artifact.getResValues())
                && equals(artifact, AndroidArtifact::getInstantRun)
                && Objects.equals(getSigningConfigName(), artifact.getSigningConfigName())
                && Objects.equals(getAbiFilters(), artifact.getAbiFilters())
                && Objects.equals(getNativeLibraries(), artifact.getNativeLibraries());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getName(),
                getCompileTaskName(),
                getAssembleTaskName(),
                getClassesFolder(),
                getJavaResourcesFolder(),
                getDependencies(),
                getCompileDependencies(),
                getDependencyGraphs(),
                getIdeSetupTaskNames(),
                getGeneratedSourceFolders(),
                getVariantSourceProvider(),
                getMultiFlavorSourceProvider(),
                getOutputs(),
                getApplicationId(),
                getSourceGenTaskName(),
                getGeneratedResourceFolders(),
                getBuildConfigFields(),
                getResValues(),
                getInstantRun(),
                getSigningConfigName(),
                getAbiFilters(),
                getNativeLibraries(),
                isSigned());
    }

    @Override
    public String toString() {
        return "AndroidArtifactStub{"
                + "myOutputs="
                + myOutputs
                + ", myApplicationId='"
                + myApplicationId
                + '\''
                + ", mySourceGenTaskName='"
                + mySourceGenTaskName
                + '\''
                + ", myGeneratedResourceFolders="
                + myGeneratedResourceFolders
                + ", myBuildConfigFields="
                + myBuildConfigFields
                + ", myResValues="
                + myResValues
                + ", myInstantRun="
                + myInstantRun
                + ", mySigningConfigName='"
                + mySigningConfigName
                + '\''
                + ", myAbiFilters="
                + myAbiFilters
                + ", myNativeLibraries="
                + myNativeLibraries
                + ", mySigned="
                + mySigned
                + "} "
                + super.toString();
    }
}
