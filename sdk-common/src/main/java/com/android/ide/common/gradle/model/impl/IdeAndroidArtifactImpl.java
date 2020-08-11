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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.CodeShrinker;
import com.android.ide.common.gradle.model.IdeAndroidArtifact;
import com.android.ide.common.gradle.model.IdeAndroidArtifactOutput;
import com.android.ide.common.gradle.model.IdeClassField;
import com.android.ide.common.gradle.model.IdeDependencies;
import com.android.ide.common.gradle.model.IdeSourceProvider;
import com.android.ide.common.gradle.model.IdeTestOptions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

public final class IdeAndroidArtifactImpl extends IdeBaseArtifactImpl
        implements IdeAndroidArtifact {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 5L;

    @NonNull private final List<IdeAndroidArtifactOutput> myOutputs;
    @NonNull private final String myApplicationId;
    @NonNull private final String mySourceGenTaskName;
    @NonNull private final List<File> myGeneratedResourceFolders;
    @NonNull private final List<File> myAdditionalRuntimeApks;
    @Nullable private final String mySigningConfigName;
    @NonNull private final Set<String> myAbiFilters;
    @Nullable private final IdeTestOptions myTestOptions;
    @Nullable private final String myInstrumentedTestTaskName;
    @Nullable private final String myBundleTaskName;
    @Nullable private final String myPostBundleTaskModelFile;
    @Nullable private final String myApkFromBundleTaskName;
    @Nullable private final String myPostApkFromBundleTaskModelFile;
    @Nullable private final CodeShrinker myCodeShrinker;

    private final boolean mySigned;
    private final int myHashCode;

    // Used for serialization by the IDE.
    IdeAndroidArtifactImpl() {
        super();
        myOutputs = Collections.emptyList();
        myApplicationId = "";
        mySourceGenTaskName = "";
        myGeneratedResourceFolders = Collections.emptyList();
        myAdditionalRuntimeApks = Collections.emptyList();
        mySigningConfigName = null;
        myAbiFilters = Collections.emptySet();
        myTestOptions = null;
        myInstrumentedTestTaskName = null;
        myBundleTaskName = null;
        myPostBundleTaskModelFile = null;
        myApkFromBundleTaskName = null;
        myPostApkFromBundleTaskModelFile = null;
        mySigned = false;
        myCodeShrinker = null;

        myHashCode = 0;
    }

    public IdeAndroidArtifactImpl(
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
      @NotNull List<IdeAndroidArtifactOutput> outputs,
      @NotNull String applicationId,
      @NotNull String sourceGenTaskName,
      @NotNull ImmutableList<File> generatedResourceFolders,
      @Nullable String signingConfigName,
      @NotNull ImmutableSet<String> abiFilters,
      boolean signed,
      @NotNull List<File> additionalRuntimeApks,
      @Nullable IdeTestOptionsImpl testOptions,
      @Nullable String instrumentedTestTaskName,
      @Nullable String bundleTaskName,
      @Nullable String postBundleTaskModelFile,
      @Nullable String apkFromBundleTaskName,
      @Nullable String postApkFromBundleTaskModelFile,
      @Nullable CodeShrinker codeShrinker
    ) {
        super(name,
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
        myOutputs = outputs;
        myApplicationId = applicationId;
        mySourceGenTaskName = sourceGenTaskName;
        myGeneratedResourceFolders = generatedResourceFolders;
        mySigningConfigName = signingConfigName;
        myAbiFilters = abiFilters;
        mySigned = signed;
        myAdditionalRuntimeApks = additionalRuntimeApks;
        myTestOptions = testOptions;
        myInstrumentedTestTaskName = instrumentedTestTaskName;
        myBundleTaskName = bundleTaskName;
        myPostBundleTaskModelFile = postBundleTaskModelFile;
        myApkFromBundleTaskName = apkFromBundleTaskName;
        myPostApkFromBundleTaskModelFile = postApkFromBundleTaskModelFile;
        myCodeShrinker = codeShrinker;
        myHashCode = calculateHashCode();
    }

    @Override
    @NonNull
    public List<IdeAndroidArtifactOutput> getOutputs() {
        return myOutputs;
    }

    @Override
    @NonNull
    public String getApplicationId() {
        return myApplicationId;
    }

    @Override
    @NonNull
    public List<File> getGeneratedResourceFolders() {
        return myGeneratedResourceFolders;
    }

    @Override
    @NonNull
    public Map<String, IdeClassField> getResValues() {
        return Collections.emptyMap();
    }

    @Override
    @NonNull
    public List<File> getAdditionalRuntimeApks() {
        return myAdditionalRuntimeApks;
    }

    @Override
    @Nullable
    public IdeTestOptions getTestOptions() {
        return myTestOptions;
    }

    @Override
    @Nullable
    public String getBundleTaskName() {
        return myBundleTaskName;
    }

    @Override
    @Nullable
    public String getBundleTaskOutputListingFile() {
        return myPostBundleTaskModelFile;
    }

    @Override
    @Nullable
    public String getApkFromBundleTaskName() {
        return myApkFromBundleTaskName;
    }

    @Override
    @Nullable
    public String getApkFromBundleTaskOutputListingFile() {
        return myPostApkFromBundleTaskModelFile;
    }

    @Override
    @Nullable
    public CodeShrinker getCodeShrinker() {
        return myCodeShrinker;
    }

    @Override
    @Nullable
    public String getSigningConfigName() {
        return mySigningConfigName;
    }

    @Override
    @NonNull
    public Set<String> getAbiFilters() {
        return myAbiFilters;
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
        if (!(o instanceof IdeAndroidArtifactImpl)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        IdeAndroidArtifactImpl artifact = (IdeAndroidArtifactImpl) o;
        return artifact.canEquals(this)
                && mySigned == artifact.mySigned
                && Objects.equals(myOutputs, artifact.myOutputs)
                && Objects.equals(myApplicationId, artifact.myApplicationId)
                && Objects.equals(mySourceGenTaskName, artifact.mySourceGenTaskName)
                && Objects.equals(myGeneratedResourceFolders, artifact.myGeneratedResourceFolders)
                && Objects.equals(mySigningConfigName, artifact.mySigningConfigName)
                && Objects.equals(myAbiFilters, artifact.myAbiFilters)
                && Objects.equals(myAdditionalRuntimeApks, artifact.myAdditionalRuntimeApks)
                && Objects.equals(myTestOptions, artifact.myTestOptions)
                && Objects.equals(myInstrumentedTestTaskName, artifact.myInstrumentedTestTaskName)
                && Objects.equals(myBundleTaskName, artifact.myBundleTaskName)
                && Objects.equals(myPostBundleTaskModelFile, artifact.myPostBundleTaskModelFile)
                && Objects.equals(myCodeShrinker, artifact.myCodeShrinker)
                && Objects.equals(myApkFromBundleTaskName, artifact.myApkFromBundleTaskName)
                && Objects.equals(
                        myPostApkFromBundleTaskModelFile,
                        artifact.myPostApkFromBundleTaskModelFile);
    }

    @Override
    protected boolean canEquals(Object other) {
        return other instanceof IdeAndroidArtifactImpl;
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    @Override
    protected int calculateHashCode() {
        return Objects.hash(
                super.calculateHashCode(),
                myOutputs,
                myApplicationId,
                mySourceGenTaskName,
                myGeneratedResourceFolders,
                mySigningConfigName,
                myAbiFilters,
                mySigned,
                myAdditionalRuntimeApks,
                myTestOptions,
                myInstrumentedTestTaskName,
                myBundleTaskName,
                myPostBundleTaskModelFile,
                myCodeShrinker,
                myApkFromBundleTaskName,
                myPostApkFromBundleTaskModelFile);
    }

    @Override
    public String toString() {
        return "IdeAndroidArtifact{"
                + super.toString()
                + ", myOutputs="
                + myOutputs
                + ", myApplicationId='"
                + myApplicationId
                + '\''
                + ", mySourceGenTaskName='"
                + mySourceGenTaskName
                + '\''
                + ", myGeneratedResourceFolders="
                + myGeneratedResourceFolders
                + ", mySigningConfigName='"
                + mySigningConfigName
                + '\''
                + ", myAbiFilters="
                + myAbiFilters
                + ", mySigned="
                + mySigned
                + ", myTestOptions="
                + myTestOptions
                + ", myInstrumentedTestTaskName="
                + myInstrumentedTestTaskName
                + ", myBundleTaskName="
                + myBundleTaskName
                + ", myPostBundleTaskModelFile="
                + myPostBundleTaskModelFile
                + ", myCodeShrinker="
                + myCodeShrinker
                + ", myApkFromBundleTaskName="
                + myApkFromBundleTaskName
                + ", myPostApkFromBundleTaskModelFile="
                + myPostApkFromBundleTaskModelFile
                + "}";
    }
}
