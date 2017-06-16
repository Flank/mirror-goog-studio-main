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

package com.android.ide.common.builder.model;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.ClassField;
import com.android.builder.model.InstantRun;
import com.android.builder.model.NativeLibrary;
import com.android.ide.common.repository.GradleVersion;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.gradle.tooling.model.UnsupportedMethodException;

/** Creates a deep copy of {@link AndroidArtifact}. */
public final class IdeAndroidArtifactImpl extends IdeBaseArtifactImpl
        implements IdeAndroidArtifact {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 2L;

    @NonNull private final Collection<AndroidArtifactOutput> myOutputs;
    @NonNull private final String myApplicationId;
    @NonNull private final String mySourceGenTaskName;
    @NonNull private final Collection<File> myGeneratedResourceFolders;
    @NonNull private final Map<String, ClassField> myBuildConfigFields;
    @NonNull private final Map<String, ClassField> myResValues;
    @Nullable private final IdeInstantRun myInstantRun;
    @Nullable private final String mySigningConfigName;
    @Nullable private final Set<String> myAbiFilters;
    @Nullable private final Collection<NativeLibrary> myNativeLibraries;
    private final boolean mySigned;
    private final int myHashCode;

    public IdeAndroidArtifactImpl(
            @NonNull AndroidArtifact artifact,
            @NonNull ModelCache modelCache,
            @NonNull IdeLevel2DependenciesFactory dependenciesFactory,
            @Nullable GradleVersion gradleVersion) {
        super(artifact, modelCache, dependenciesFactory, gradleVersion);
        myOutputs =
                copy(
                        artifact.getOutputs(),
                        modelCache,
                        output -> new IdeAndroidArtifactOutput(output, modelCache));
        myApplicationId = artifact.getApplicationId();
        mySourceGenTaskName = artifact.getSourceGenTaskName();
        myGeneratedResourceFolders = ImmutableList.copyOf(artifact.getGeneratedResourceFolders());
        myBuildConfigFields =
                copy(
                        artifact.getBuildConfigFields(),
                        modelCache,
                        classField -> new IdeClassField(classField, modelCache));
        myResValues =
                copy(
                        artifact.getResValues(),
                        modelCache,
                        classField -> new IdeClassField(classField, modelCache));
        myInstantRun =
                copyNewProperty(
                        modelCache,
                        artifact::getInstantRun,
                        instantRun -> new IdeInstantRun(instantRun, modelCache),
                        null);
        mySigningConfigName = artifact.getSigningConfigName();
        myAbiFilters = copy(artifact.getAbiFilters());
        myNativeLibraries = copy(modelCache, artifact.getNativeLibraries());
        mySigned = artifact.isSigned();

        myHashCode = calculateHashCode();
    }

    @Nullable
    private static Collection<NativeLibrary> copy(
            @NonNull ModelCache modelCache, @Nullable Collection<NativeLibrary> original) {
        return original != null
                ? copy(original, modelCache, library -> new IdeNativeLibrary(library, modelCache))
                : null;
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
        if (myInstantRun != null) {
            return myInstantRun;
        }
        throw new UnsupportedMethodException("Unsupported method: AndroidArtifact.getInstantRun()");
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
                && Objects.equals(myBuildConfigFields, artifact.myBuildConfigFields)
                && Objects.equals(myResValues, artifact.myResValues)
                && Objects.equals(myInstantRun, artifact.myInstantRun)
                && Objects.equals(mySigningConfigName, artifact.mySigningConfigName)
                && Objects.equals(myAbiFilters, artifact.myAbiFilters)
                && Objects.equals(myNativeLibraries, artifact.myNativeLibraries);
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
                myBuildConfigFields,
                myResValues,
                myInstantRun,
                mySigningConfigName,
                myAbiFilters,
                myNativeLibraries,
                mySigned);
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
                + "}";
    }
}
