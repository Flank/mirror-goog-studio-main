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
package com.android.ide.common.gradle.model.stubs;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.*;
import com.android.builder.model.level2.DependencyGraphs;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.*;

public class AndroidArtifactStub extends BaseArtifactStub implements AndroidArtifact {
    @NonNull private final Collection<AndroidArtifactOutput> myOutputs;
    @NonNull private final String myApplicationId;
    @NonNull private final String mySourceGenTaskName;
    @NonNull private final Collection<File> myGeneratedResourceFolders = new ArrayList<>();
    @NonNull private final Collection<File> myAdditionalRuntimeApks;
    @NonNull private final Map<String, ClassField> myBuildConfigFields;
    @NonNull private final Map<String, ClassField> myResValues;
    @NonNull private final InstantRun myInstantRun;
    @Nullable private final TestOptions myTestOptions;
    @Nullable private final String mySigningConfigName;
    @Nullable private final Set<String> myAbiFilters;
    @Nullable private final Collection<NativeLibrary> myNativeLibraries;
    @Nullable private final String myInstrumentedTestTaskName;
    @Nullable private final String myBundleTaskName;
    @Nullable private final String myApkFromBundleTaskName;
    private final boolean mySigned;

    public AndroidArtifactStub() {
        myOutputs = Lists.newArrayList(new AndroidArtifactOutputStub());
        myApplicationId = "applicationId";
        mySourceGenTaskName = "sourceGenTaskName";
        myBuildConfigFields = ImmutableMap.of("buildConfigField", new ClassFieldStub());
        myResValues = ImmutableMap.of("resValue", new ClassFieldStub());
        myInstantRun = new InstantRunStub();
        mySigningConfigName = "signingConfigName";
        myAbiFilters = Sets.newHashSet("filter");
        myNativeLibraries = Lists.newArrayList(new NativeLibraryStub());
        myAdditionalRuntimeApks = Collections.emptyList();
        myTestOptions = new TestOptionsStub();
        myInstrumentedTestTaskName = "instrumentedTestsTaskName";
        myBundleTaskName = "bundleTaskName";
        myApkFromBundleTaskName = "apkFromBundleTaskNam";
        mySigned = true;
    }

    public AndroidArtifactStub(
            @NonNull String name,
            @NonNull String compileTaskName,
            @NonNull String assembleTaskName,
            @NonNull File classesFolder,
            @NonNull Set<File> classesFolders,
            @NonNull File javaResourcesFolder,
            @NonNull Dependencies dependencies,
            @NonNull Dependencies compileDependencies,
            @NonNull DependencyGraphs graphs,
            @NonNull Set<String> names,
            @NonNull Collection<File> folders,
            @Nullable SourceProvider variantSourceProvider,
            @Nullable SourceProvider multiFlavorSourceProvider,
            @NonNull Collection<AndroidArtifactOutput> outputs,
            @NonNull String applicationId,
            @NonNull String sourceGenTaskName,
            @NonNull Map<String, ClassField> buildConfigFields,
            @NonNull Map<String, ClassField> resValues,
            @NonNull InstantRun run,
            @Nullable String signingConfigName,
            @Nullable Set<String> filters,
            @Nullable Collection<NativeLibrary> libraries,
            @NonNull Collection<File> apks,
            @Nullable TestOptions testOptions,
            @Nullable String instrumentedTestTaskName,
            @Nullable String bundleTaskName,
            @Nullable String apkFromBundleTaskName,
            boolean signed) {
        super(
                name,
                compileTaskName,
                assembleTaskName,
                classesFolder,
                classesFolders,
                javaResourcesFolder,
                dependencies,
                compileDependencies,
                graphs,
                names,
                folders,
                variantSourceProvider,
                multiFlavorSourceProvider);
        myOutputs = outputs;
        myApplicationId = applicationId;
        mySourceGenTaskName = sourceGenTaskName;
        myBuildConfigFields = buildConfigFields;
        myResValues = resValues;
        myInstantRun = run;
        mySigningConfigName = signingConfigName;
        myAbiFilters = filters;
        myNativeLibraries = libraries;
        myAdditionalRuntimeApks = apks;
        myTestOptions = testOptions;
        myInstrumentedTestTaskName = instrumentedTestTaskName;
        myBundleTaskName = bundleTaskName;
        myApkFromBundleTaskName = apkFromBundleTaskName;
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

    @NonNull
    @Override
    public Collection<File> getAdditionalRuntimeApks() {
        return myAdditionalRuntimeApks;
    }

    @Nullable
    @Override
    public TestOptions getTestOptions() {
        return myTestOptions;
    }

    @Nullable
    @Override
    public String getInstrumentedTestTaskName() {
        return myInstrumentedTestTaskName;
    }

    @Nullable
    @Override
    public String getBundleTaskName() {
        return myBundleTaskName;
    }

    @Nullable
    @Override
    public String getApkFromBundleTaskName() {
        return myApkFromBundleTaskName;
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
                && Objects.equals(getAdditionalRuntimeApks(), artifact.getAdditionalRuntimeApks())
                && Objects.equals(getTestOptions(), artifact.getTestOptions())
                && Objects.equals(
                        getInstrumentedTestTaskName(), artifact.getInstrumentedTestTaskName())
                && Objects.equals(getBundleTaskName(), artifact.getBundleTaskName())
                && Objects.equals(getApkFromBundleTaskName(), artifact.getApkFromBundleTaskName())
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
                isSigned(),
                getAdditionalRuntimeApks(),
                getTestOptions(),
                getInstrumentedTestTaskName(),
                getBundleTaskName(),
                getApkFromBundleTaskName());
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
                + ", myInstrumentedTestTaskName="
                + myInstrumentedTestTaskName
                + ", myBundleTaskName="
                + myBundleTaskName
                + "} "
                + ", myApkFromBundleTaskName"
                + myApkFromBundleTaskName
                + "} "
                + super.toString();
    }
}
