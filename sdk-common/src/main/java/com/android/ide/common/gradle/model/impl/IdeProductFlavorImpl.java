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

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.SigningConfig;
import com.android.builder.model.VectorDrawablesOptions;
import com.android.ide.common.gradle.model.IdeApiVersion;
import com.android.ide.common.gradle.model.IdeClassField;
import com.android.ide.common.gradle.model.IdeProductFlavor;
import com.android.ide.common.gradle.model.IdeSigningConfig;
import com.android.ide.common.gradle.model.IdeVectorDrawablesOptions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** Creates a deep copy of a {@link ProductFlavor}. */
public final class IdeProductFlavorImpl extends IdeBaseConfigImpl implements IdeProductFlavor {
    // Increase the value when adding/removing fields or when changing the
    // serialization/deserialization mechanism.
    private static final long serialVersionUID = 4L;

    @NonNull private final Map<String, String> myTestInstrumentationRunnerArguments;
    @NonNull private final Collection<String> myResourceConfigurations;
    @Nullable private final IdeVectorDrawablesOptions myVectorDrawables;
    @Nullable private final String myDimension;
    @Nullable private final String myApplicationId;
    @Nullable private final Integer myVersionCode;
    @Nullable private final String myVersionName;
    @Nullable private final IdeApiVersion myMinSdkVersion;
    @Nullable private final IdeApiVersion myTargetSdkVersion;
    @Nullable private final Integer myMaxSdkVersion;
    @Nullable private final String myTestApplicationId;
    @Nullable private final String myTestInstrumentationRunner;
    @Nullable private final Boolean myTestFunctionalTest;
    @Nullable private final Boolean myTestHandleProfiling;
    @Nullable private final IdeSigningConfig mySigningConfig;
    private final int myHashCode;

    // Used for serialization by the IDE.
    IdeProductFlavorImpl() {
        super();

        myTestInstrumentationRunnerArguments = Collections.emptyMap();
        myResourceConfigurations = Collections.emptyList();
        myVectorDrawables = new IdeVectorDrawablesOptionsImpl();
        myDimension = null;
        myApplicationId = null;
        myVersionCode = null;
        myVersionName = null;
        myMinSdkVersion = null;
        myTargetSdkVersion = null;
        myMaxSdkVersion = null;
        myTestApplicationId = null;
        myTestInstrumentationRunner = null;
        myTestFunctionalTest = null;
        myTestHandleProfiling = null;
        mySigningConfig = null;

        myHashCode = 0;
    }

    public IdeProductFlavorImpl(
            @NotNull String name,
            @NotNull Map<String, IdeClassField> resValues,
            @NotNull ImmutableList<File> proguardFiles,
            @NotNull ImmutableList<File> consumerProguardFiles,
            @NotNull ImmutableMap<String, Object> manifestPlaceholders,
            @Nullable String applicationIdSuffix,
            @Nullable String versionNameSuffix,
            @Nullable Boolean multiDexEnabled,
            @NotNull ImmutableMap<String, String> testInstrumentationRunnerArguments,
            @NotNull ImmutableList<String> resourceConfigurations,
            @Nullable IdeVectorDrawablesOptions vectorDrawables,
            @Nullable String dimension,
            @Nullable String applicationId,
            @Nullable Integer versionCode,
            @Nullable String versionName,
            @Nullable IdeApiVersionImpl minSdkVersion,
            @Nullable IdeApiVersionImpl targetSdkVersion,
            @Nullable Integer maxSdkVersion,
            @Nullable String testApplicationId,
            @Nullable String testInstrumentationRunner,
            @Nullable Boolean testFunctionalTest,
            @Nullable Boolean testHandleProfiling,
            @Nullable IdeSigningConfig signingConfig) {
        super(
                name,
                resValues,
                proguardFiles,
                consumerProguardFiles,
                manifestPlaceholders,
                applicationIdSuffix,
                versionNameSuffix,
                multiDexEnabled);

        myTestInstrumentationRunnerArguments = testInstrumentationRunnerArguments;
        myResourceConfigurations = resourceConfigurations;
        myVectorDrawables = vectorDrawables;
        myDimension = dimension;
        myApplicationId = applicationId;
        myVersionCode = versionCode;
        myVersionName = versionName;
        myMinSdkVersion = minSdkVersion;
        myTargetSdkVersion = targetSdkVersion;
        myMaxSdkVersion = maxSdkVersion;
        myTestApplicationId = testApplicationId;
        myTestInstrumentationRunner = testInstrumentationRunner;
        myTestFunctionalTest = testFunctionalTest;
        myTestHandleProfiling = testHandleProfiling;
        mySigningConfig = signingConfig;

        myHashCode = calculateHashCode();
    }

    @Nullable
    private static IdeVectorDrawablesOptions copyVectorDrawables(
            @NonNull ProductFlavor flavor, @NonNull ModelCache modelCache) {
        VectorDrawablesOptions vectorDrawables;
        try {
            vectorDrawables = flavor.getVectorDrawables();
        } catch (UnsupportedOperationException e) {
            return null;
        }
        return modelCache.computeIfAbsent(
                vectorDrawables, options -> IdeVectorDrawablesOptionsImpl.createFrom(options));
    }

    @Nullable
    private static IdeApiVersionImpl copy(
            @NonNull ModelCache modelCache, @Nullable ApiVersion apiVersion) {
        if (apiVersion != null) {
            return modelCache.computeIfAbsent(
                    apiVersion, version -> IdeApiVersionImpl.createFrom(version));
        }
        return null;
    }

    @Nullable
    private static IdeSigningConfig copy(
            @NonNull ModelCache modelCache, @Nullable SigningConfig signingConfig) {
        if (signingConfig != null) {
            return modelCache.computeIfAbsent(
                    signingConfig, config -> IdeSigningConfigImpl.createFrom(config));
        }
        return null;
    }

    @Override
    @NonNull
    public Map<String, String> getTestInstrumentationRunnerArguments() {
        return myTestInstrumentationRunnerArguments;
    }

    @Override
    @NonNull
    public Collection<String> getResourceConfigurations() {
        return myResourceConfigurations;
    }

    @Override
    @NonNull
    public IdeVectorDrawablesOptions getVectorDrawables() {
        if (myVectorDrawables != null) {
            return myVectorDrawables;
        }
        throw new UnsupportedOperationException(
                "Unsupported method: ProductFlavor.getVectorDrawables");
    }

    @Override
    @Nullable
    public String getDimension() {
        return myDimension;
    }

    @Override
    @Nullable
    public String getApplicationId() {
        return myApplicationId;
    }

    @Override
    @Nullable
    public Integer getVersionCode() {
        return myVersionCode;
    }

    @Override
    @Nullable
    public String getVersionName() {
        return myVersionName;
    }

    @Override
    @Nullable
    public IdeApiVersion getMinSdkVersion() {
        return myMinSdkVersion;
    }

    @Override
    @Nullable
    public IdeApiVersion getTargetSdkVersion() {
        return myTargetSdkVersion;
    }

    @Override
    @Nullable
    public Integer getMaxSdkVersion() {
        return myMaxSdkVersion;
    }

    @Override
    @Nullable
    public String getTestApplicationId() {
        return myTestApplicationId;
    }

    @Override
    @Nullable
    public String getTestInstrumentationRunner() {
        return myTestInstrumentationRunner;
    }

    @Override
    @Nullable
    public Boolean getTestHandleProfiling() {
        return myTestHandleProfiling;
    }

    @Override
    @Nullable
    public Boolean getTestFunctionalTest() {
        return myTestFunctionalTest;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeProductFlavorImpl)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        IdeProductFlavorImpl flavor = (IdeProductFlavorImpl) o;
        return flavor.canEqual(this)
                && Objects.equals(
                        myTestInstrumentationRunnerArguments,
                        flavor.myTestInstrumentationRunnerArguments)
                && Objects.equals(myResourceConfigurations, flavor.myResourceConfigurations)
                && Objects.equals(myVectorDrawables, flavor.myVectorDrawables)
                && Objects.equals(myDimension, flavor.myDimension)
                && Objects.equals(myApplicationId, flavor.myApplicationId)
                && Objects.equals(myVersionCode, flavor.myVersionCode)
                && Objects.equals(myVersionName, flavor.myVersionName)
                && Objects.equals(myMinSdkVersion, flavor.myMinSdkVersion)
                && Objects.equals(myTargetSdkVersion, flavor.myTargetSdkVersion)
                && Objects.equals(myMaxSdkVersion, flavor.myMaxSdkVersion)
                && Objects.equals(myTestApplicationId, flavor.myTestApplicationId)
                && Objects.equals(myTestInstrumentationRunner, flavor.myTestInstrumentationRunner)
                && Objects.equals(myTestFunctionalTest, flavor.myTestFunctionalTest)
                && Objects.equals(myTestHandleProfiling, flavor.myTestHandleProfiling)
                && Objects.equals(mySigningConfig, flavor.mySigningConfig);
    }

    @Override
    public boolean canEqual(Object other) {
        return other instanceof IdeProductFlavorImpl;
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    @Override
    protected int calculateHashCode() {
        return Objects.hash(
                super.calculateHashCode(),
                myTestInstrumentationRunnerArguments,
                myResourceConfigurations,
                myVectorDrawables,
                myDimension,
                myApplicationId,
                myVersionCode,
                myVersionName,
                myMinSdkVersion,
                myTargetSdkVersion,
                myMaxSdkVersion,
                myTestApplicationId,
                myTestInstrumentationRunner,
                myTestFunctionalTest,
                myTestHandleProfiling,
                mySigningConfig);
    }

    @Override
    public String toString() {
        return "IdeProductFlavor{"
                + super.toString()
                + ", myTestInstrumentationRunnerArguments="
                + myTestInstrumentationRunnerArguments
                + ", myResourceConfigurations="
                + myResourceConfigurations
                + ", myVectorDrawables="
                + myVectorDrawables
                + ", myDimension='"
                + myDimension
                + '\''
                + ", myApplicationId='"
                + myApplicationId
                + '\''
                + ", myVersionCode="
                + myVersionCode
                + ", myVersionName='"
                + myVersionName
                + '\''
                + ", myMinSdkVersion="
                + myMinSdkVersion
                + ", myTargetSdkVersion="
                + myTargetSdkVersion
                + ", myMaxSdkVersion="
                + myMaxSdkVersion
                + ", myTestApplicationId='"
                + myTestApplicationId
                + '\''
                + ", myTestInstrumentationRunner='"
                + myTestInstrumentationRunner
                + '\''
                + ", myTestFunctionalTest="
                + myTestFunctionalTest
                + ", myTestHandleProfiling="
                + myTestHandleProfiling
                + ", mySigningConfig="
                + mySigningConfig
                + "}";
    }

    public static IdeProductFlavorImpl createFrom(
            @NonNull ProductFlavor flavor, @NonNull ModelCache modelCache) {
        return new IdeProductFlavorImpl(
                flavor.getName(),
                IdeModel.copy(
                        flavor.getResValues(),
                        modelCache,
                        classField -> IdeClassFieldImpl.createFrom(classField)),
                ImmutableList.copyOf(flavor.getProguardFiles()),
                ImmutableList.copyOf(flavor.getConsumerProguardFiles()),
                flavor.getManifestPlaceholders().entrySet().stream()
                        // AGP may return internal Groovy GString implementation as a value in
                        // manifestPlaceholders
                        // map. It cannot be serialized
                        // with IDEA's external system serialization. We convert values to String to
                        // make them
                        // usable as they are converted to String by
                        // the manifest merger anyway.

                        .collect(toImmutableMap(it -> it.getKey(), it -> it.getValue().toString())),
                flavor.getApplicationIdSuffix(),
                IdeModel.copyNewProperty(flavor::getVersionNameSuffix, null),
                IdeModel.copyNewProperty(flavor::getMultiDexEnabled, null),
                ImmutableMap.copyOf(flavor.getTestInstrumentationRunnerArguments()),
                ImmutableList.copyOf(flavor.getResourceConfigurations()),
                copyVectorDrawables(flavor, modelCache),
                flavor.getDimension(),
                flavor.getApplicationId(),
                flavor.getVersionCode(),
                flavor.getVersionName(),
                copy(modelCache, flavor.getMinSdkVersion()),
                copy(modelCache, flavor.getTargetSdkVersion()),
                flavor.getMaxSdkVersion(),
                flavor.getTestApplicationId(),
                flavor.getTestInstrumentationRunner(),
                flavor.getTestFunctionalTest(),
                flavor.getTestHandleProfiling(),
                copy(modelCache, flavor.getSigningConfig()));
    }
}
