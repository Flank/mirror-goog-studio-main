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
import com.android.ide.common.gradle.model.IdeAaptOptions;
import com.android.ide.common.gradle.model.IdeAndroidGradlePluginProjectFlags;
import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.gradle.model.IdeBuildTypeContainer;
import com.android.ide.common.gradle.model.IdeDependenciesInfo;
import com.android.ide.common.gradle.model.IdeJavaCompileOptions;
import com.android.ide.common.gradle.model.IdeLintOptions;
import com.android.ide.common.gradle.model.IdeProductFlavorContainer;
import com.android.ide.common.gradle.model.IdeSigningConfig;
import com.android.ide.common.gradle.model.IdeSyncIssue;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.ide.common.gradle.model.IdeVariantBuildInformation;
import com.android.ide.common.gradle.model.IdeViewBindingOptions;
import com.android.ide.common.gradle.model.ndk.v1.IdeNativeToolchain;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

public final class IdeAndroidProjectImpl implements IdeAndroidProject, Serializable {
    // Increase the value when adding/removing fields or when changing the
    // serialization/deserialization mechanism.
    private static final long serialVersionUID = 10L;

    @NonNull private final String myModelVersion;
    @NonNull private final String myName;
    @NonNull private final IdeProductFlavorContainer myDefaultConfig;
    @NonNull private final Collection<IdeBuildTypeContainer> myBuildTypes;
    @NonNull private final Collection<IdeProductFlavorContainer> myProductFlavors;
    @NonNull private final Collection<IdeSyncIssue> mySyncIssues;
    @NonNull private final Collection<IdeVariant> myVariants;
    @NonNull private final Collection<String> myVariantNames;
    @Nullable private final String myDefaultVariant;
    @NonNull private final Collection<String> myFlavorDimensions;
    @NonNull private final String myCompileTarget;
    @NonNull private final Collection<String> myBootClassPath;
    @NonNull private final Collection<IdeNativeToolchain> myNativeToolchains;
    @NonNull private final Collection<IdeSigningConfig> mySigningConfigs;
    @NonNull private final IdeLintOptions myLintOptions;
    @Nullable private final List<File> myLintRuleJars;
    @NonNull private final IdeJavaCompileOptions myJavaCompileOptions;
    @NonNull private final IdeAaptOptions myAaptOptions;
    @NonNull private final File myBuildFolder;
    @NonNull private final Collection<String> myDynamicFeatures;
    @NonNull private final Collection<IdeVariantBuildInformation> myVariantBuildInformation;
    @Nullable private final IdeViewBindingOptions myViewBindingOptions;
    @Nullable private final IdeDependenciesInfo myDependenciesInfo;
    @Nullable private final String myBuildToolsVersion;
    @Nullable private final String myNdkVersion;
    @Nullable private final String myResourcePrefix;
    @Nullable private final String myGroupId;
    private final boolean mySupportsPluginGeneration;
    private final int myApiVersion;
    private final int myProjectType;
    private final boolean myBaseSplit;
    @NonNull private final IdeAndroidGradlePluginProjectFlags myAgpFlags;
    private final int myHashCode;

    // Used for serialization by the IDE.
    @SuppressWarnings("unused")
    private IdeAndroidProjectImpl() {
        myModelVersion = "";
        myName = "";
        myDefaultConfig = new IdeProductFlavorContainerImpl();
        myBuildTypes = Collections.emptyList();
        myProductFlavors = Collections.emptyList();
        mySyncIssues = Collections.emptyList();
        myVariants = Collections.emptyList();
        myVariantNames = Collections.emptyList();
        myDefaultVariant = null;
        myFlavorDimensions = Collections.emptyList();
        myCompileTarget = "";
        myBootClassPath = Collections.emptyList();
        myNativeToolchains = Collections.emptyList();
        mySigningConfigs = Collections.emptyList();
        myLintOptions = new IdeLintOptionsImpl();
        myLintRuleJars = Collections.emptyList();
        myJavaCompileOptions = new IdeJavaCompileOptionsImpl();
        myAaptOptions = new IdeAaptOptionsImpl();
        //noinspection ConstantConditions
        myBuildFolder = null;
        myDynamicFeatures = Collections.emptyList();
        myVariantBuildInformation = Collections.emptyList();
        myViewBindingOptions = new IdeViewBindingOptionsImpl();
        myDependenciesInfo = new IdeDependenciesInfoImpl();
        myBuildToolsVersion = null;
        myNdkVersion = null;
        myResourcePrefix = null;
        myGroupId = null;
        mySupportsPluginGeneration = false;
        myApiVersion = 0;
        myProjectType = 0;
        myBaseSplit = false;
        myAgpFlags = new IdeAndroidGradlePluginProjectFlagsImpl();
        myHashCode = 0;
    }

    public IdeAndroidProjectImpl(
            @NonNull String modelVersion,
            @NonNull String name,
            @NonNull IdeProductFlavorContainer defaultConfig,
            @NonNull Collection<IdeBuildTypeContainer> buildTypes,
            @NonNull Collection<IdeProductFlavorContainer> productFlavors,
            @NonNull Collection<IdeSyncIssue> syncIssues,
            @NonNull Collection<IdeVariant> variants,
            @NonNull Collection<String> variantNames,
            @Nullable String defaultVariant,
            @NonNull Collection<String> flavorDimensions,
            @NonNull String compileTarget,
            @NonNull Collection<String> bootClassPath,
            @NonNull Collection<IdeNativeToolchain> nativeToolchains,
            @NonNull Collection<IdeSigningConfig> signingConfigs,
            @NonNull IdeLintOptions lintOptions,
            @Nullable List<File> lintRuleJars,
            @NonNull IdeJavaCompileOptions javaCompileOptions,
            @NonNull IdeAaptOptions aaptOptions,
            @NonNull File buildFolder,
            @NonNull Collection<String> dynamicFeatures,
            @NonNull Collection<IdeVariantBuildInformation> variantBuildInformation,
            @Nullable IdeViewBindingOptions viewBindingOptions,
            @Nullable IdeDependenciesInfo dependenciesInfo,
            @Nullable String buildToolsVersion,
            @Nullable String ndkVersion,
            @Nullable String resourcePrefix,
            @Nullable String groupId,
            boolean supportsPluginGeneration,
            int apiVersion,
            int projectType,
            boolean baseSplit,
            @NonNull IdeAndroidGradlePluginProjectFlags agpFlags) {
        myModelVersion = modelVersion;
        myName = name;
        myDefaultConfig = defaultConfig;
        myBuildTypes = buildTypes;
        myProductFlavors = productFlavors;
        mySyncIssues = syncIssues;
        myVariants = variants;
        myVariantNames = variantNames;
        myDefaultVariant = defaultVariant;
        myFlavorDimensions = flavorDimensions;
        myCompileTarget = compileTarget;
        myBootClassPath = bootClassPath;
        myNativeToolchains = nativeToolchains;
        mySigningConfigs = signingConfigs;
        myLintOptions = lintOptions;
        myLintRuleJars = lintRuleJars;
        myJavaCompileOptions = javaCompileOptions;
        myAaptOptions = aaptOptions;
        myBuildFolder = buildFolder;
        myDynamicFeatures = dynamicFeatures;
        myVariantBuildInformation = variantBuildInformation;
        myViewBindingOptions = viewBindingOptions;
        myDependenciesInfo = dependenciesInfo;
        myBuildToolsVersion = buildToolsVersion;
        myNdkVersion = ndkVersion;
        myResourcePrefix = resourcePrefix;
        myGroupId = groupId;
        mySupportsPluginGeneration = supportsPluginGeneration;
        myApiVersion = apiVersion;
        myProjectType = projectType;
        myBaseSplit = baseSplit;
        myAgpFlags = agpFlags;
        myHashCode = calculateHashCode();
    }

    @Override
    @NonNull
    public String getModelVersion() {
        return myModelVersion;
    }

    @Override
    @NonNull
    public String getName() {
        return myName;
    }

    @Override
    @NonNull
    public IdeProductFlavorContainer getDefaultConfig() {
        return myDefaultConfig;
    }

    @Override
    @NonNull
    public Collection<IdeBuildTypeContainer> getBuildTypes() {
        return myBuildTypes;
    }

    @Override
    @NonNull
    public Collection<IdeProductFlavorContainer> getProductFlavors() {
        return myProductFlavors;
    }

    @Override
    @Nullable
    public String getBuildToolsVersion() {
        return myBuildToolsVersion;
    }

    @Override
    @Nullable
    public String getNdkVersion() {
        return myNdkVersion;
    }

    @Override
    @NonNull
    public Collection<IdeSyncIssue> getSyncIssues() {
        return mySyncIssues;
    }

    @Override
    @NonNull
    public Collection<IdeVariant> getVariants() {
        return myVariants;
    }

    @Override
    @NonNull
    public Collection<String> getVariantNames() {
        return myVariantNames;
    }

    @Nullable
    @Override
    public String getDefaultVariant() {
        return myDefaultVariant;
    }

    @Override
    @NonNull
    public Collection<String> getFlavorDimensions() {
        return myFlavorDimensions;
    }

    @Override
    @NonNull
    public String getCompileTarget() {
        return myCompileTarget;
    }

    @Override
    @NonNull
    public Collection<String> getBootClasspath() {
        return myBootClassPath;
    }

    @Override
    @NonNull
    public IdeAaptOptions getAaptOptions() {
        return myAaptOptions;
    }

    @Override
    @NonNull
    public Collection<IdeSigningConfig> getSigningConfigs() {
        return mySigningConfigs;
    }

    @Override
    @NonNull
    public IdeLintOptions getLintOptions() {
        return myLintOptions;
    }

    @Override
    @NonNull
    public IdeJavaCompileOptions getJavaCompileOptions() {
        return myJavaCompileOptions;
    }

    @Override
    @NonNull
    public File getBuildFolder() {
        return myBuildFolder;
    }

    @Override
    @Nullable
    public String getResourcePrefix() {
        return myResourcePrefix;
    }

    @Override
    public int getApiVersion() {
        return myApiVersion;
    }

    @Override
    public int getProjectType() {
        return myProjectType;
    }

    @Override
    public boolean isBaseSplit() {
        return myBaseSplit;
    }

    @NonNull
    @Override
    public Collection<String> getDynamicFeatures() {
        return myDynamicFeatures;
    }

    @Nullable
    @Override
    public IdeViewBindingOptions getViewBindingOptions() {
        return myViewBindingOptions;
    }

    @Nullable
    @Override
    public IdeDependenciesInfo getDependenciesInfo() {
        return myDependenciesInfo;
    }

    @Nullable
    @Override
    public String getGroupId() {
        return myGroupId;
    }

    @Override
    public @NotNull IdeAndroidGradlePluginProjectFlags getAgpFlags() {
        return myAgpFlags;
    }

    @Override
    public void forEachVariant(@NonNull Consumer<IdeVariant> action) {
        for (IdeVariant variant : myVariants) {
            action.accept(variant);
        }
    }

    @NonNull
    @Override
    public Collection<IdeVariantBuildInformation> getVariantsBuildInformation() {
        return myVariantBuildInformation;
    }

    @Nullable
    @Override
    public List<File> getLintRuleJars() {
        return myLintRuleJars;
    }

    @Nullable private transient Map<String, Object> clientProperties;

    @Nullable
    @Override
    public Object putClientProperty(@NonNull String key, @Nullable Object value) {
        if (value == null) {
            if (clientProperties != null) {
                clientProperties.remove(key);
            }
        } else {
            if (clientProperties == null) {
                clientProperties = new HashMap<>();
            }
            clientProperties.put(key, value);
        }

        return value;
    }

    @Nullable
    @Override
    public Object getClientProperty(@NonNull String key) {
        if (clientProperties == null) {
            return null;
        } else {
            return clientProperties.get(key);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeAndroidProjectImpl)) {
            return false;
        }
        IdeAndroidProjectImpl project = (IdeAndroidProjectImpl) o;
        return myApiVersion == project.myApiVersion
                && myProjectType == project.myProjectType
                && myBaseSplit == project.myBaseSplit
                && mySupportsPluginGeneration == project.mySupportsPluginGeneration
                && Objects.equals(myModelVersion, project.myModelVersion)
                && Objects.equals(myName, project.myName)
                && Objects.equals(myDefaultConfig, project.myDefaultConfig)
                && Objects.equals(myBuildTypes, project.myBuildTypes)
                && Objects.equals(myProductFlavors, project.myProductFlavors)
                && Objects.equals(myBuildToolsVersion, project.myBuildToolsVersion)
                && Objects.equals(myNdkVersion, project.myNdkVersion)
                && Objects.equals(mySyncIssues, project.mySyncIssues)
                && Objects.equals(myVariants, project.myVariants)
                && Objects.equals(myVariantNames, project.myVariantNames)
                && Objects.equals(myDefaultVariant, project.myDefaultVariant)
                && Objects.equals(myFlavorDimensions, project.myFlavorDimensions)
                && Objects.equals(myCompileTarget, project.myCompileTarget)
                && Objects.equals(myBootClassPath, project.myBootClassPath)
                && Objects.equals(myNativeToolchains, project.myNativeToolchains)
                && Objects.equals(mySigningConfigs, project.mySigningConfigs)
                && Objects.equals(myLintOptions, project.myLintOptions)
                && Objects.equals(myLintRuleJars, project.myLintRuleJars)
                && Objects.equals(myJavaCompileOptions, project.myJavaCompileOptions)
                && Objects.equals(myAaptOptions, project.myAaptOptions)
                && Objects.equals(myBuildFolder, project.myBuildFolder)
                && Objects.equals(myResourcePrefix, project.myResourcePrefix)
                && Objects.equals(myDynamicFeatures, project.myDynamicFeatures)
                && Objects.equals(myViewBindingOptions, project.myViewBindingOptions)
                && Objects.equals(myDependenciesInfo, project.myDependenciesInfo)
                && Objects.equals(myGroupId, project.myGroupId)
                && Objects.equals(myAgpFlags, project.myAgpFlags)
                && Objects.equals(myVariantBuildInformation, project.myVariantBuildInformation);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(
                myModelVersion,
                myName,
                myDefaultConfig,
                myBuildTypes,
                myProductFlavors,
                myBuildToolsVersion,
                myNdkVersion,
                mySyncIssues,
                myVariants,
                myVariantNames,
                myDefaultVariant,
                myFlavorDimensions,
                myCompileTarget,
                myBootClassPath,
                myNativeToolchains,
                mySigningConfigs,
                myLintOptions,
                myLintRuleJars,
                myJavaCompileOptions,
                myBuildFolder,
                myResourcePrefix,
                myApiVersion,
                myProjectType,
                mySupportsPluginGeneration,
                myAaptOptions,
                myBaseSplit,
                myDynamicFeatures,
                myViewBindingOptions,
                myDependenciesInfo,
                myGroupId,
                myAgpFlags,
                myVariantBuildInformation);
    }

    @Override
    public String toString() {
        return "IdeAndroidProject{"
                + "myModelVersion='"
                + myModelVersion
                + '\''
                + ", myName='"
                + myName
                + '\''
                + ", myDefaultConfig="
                + myDefaultConfig
                + ", myBuildTypes="
                + myBuildTypes
                + ", myProductFlavors="
                + myProductFlavors
                + ", myBuildToolsVersion='"
                + myBuildToolsVersion
                + ", myNdkVersion='"
                + myNdkVersion
                + '\''
                + ", mySyncIssues="
                + mySyncIssues
                + ", myVariants="
                + myVariants
                + ", myVariantNames="
                + myVariantNames
                + ", myDefaultVariant="
                + myDefaultVariant
                + ", myFlavorDimensions="
                + myFlavorDimensions
                + ", myCompileTarget='"
                + myCompileTarget
                + '\''
                + ", myBootClassPath="
                + myBootClassPath
                + ", myNativeToolchains="
                + myNativeToolchains
                + ", mySigningConfigs="
                + mySigningConfigs
                + ", myLintOptions="
                + myLintOptions
                + ", myJavaCompileOptions="
                + myJavaCompileOptions
                + ", myBuildFolder="
                + myBuildFolder
                + ", myResourcePrefix='"
                + myResourcePrefix
                + '\''
                + ", myApiVersion="
                + myApiVersion
                + ", myProjectType="
                + myProjectType
                + ", mySupportsPluginGeneration="
                + mySupportsPluginGeneration
                + ", myAaptOptions="
                + myAaptOptions
                + ", myBaseSplit="
                + myBaseSplit
                + ", myDynamicFeatures="
                + myDynamicFeatures
                + ", myViewBindingOptions="
                + myViewBindingOptions
                + ", myDependenciesInfo="
                + myDependenciesInfo
                + ", myGroupId="
                + myGroupId
                + ", myAgpFlags="
                + myAgpFlags
                + ", myVariantBuildInformation="
                + myVariantBuildInformation
                + "}";
    }
}
