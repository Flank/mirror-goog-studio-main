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
import com.android.ide.common.gradle.model.UnusedModelMethodException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public class AndroidProjectStub extends BaseStub implements AndroidProject {
    @NonNull private final String myModelVersion;
    @NonNull private final String myName;
    @Nullable private final String myGroupId;
    @NonNull private final ProductFlavorContainer myDefaultConfig;
    @NonNull private final Collection<BuildTypeContainer> myBuildTypes;
    @NonNull private final Collection<ProductFlavorContainer> myProductFlavors;
    @NonNull private final String myBuildToolsVersion;
    @NonNull private final Collection<SyncIssue> mySyncIssues;
    @NonNull private final Collection<Variant> myVariants;
    @NonNull private final Collection<String> myVariantNames;
    @Nullable private final String myDefaultVariant;
    @NonNull private final Collection<String> myFlavorDimensions;
    @NonNull private final String myCompileTarget;
    @NonNull private final Collection<String> myBootClasspath;
    @NonNull private final Collection<NativeToolchain> myNativeToolchains;
    @NonNull private final Collection<SigningConfig> mySigningConfigs;
    @NonNull private final LintOptions myLintOptions;
    @NonNull private final Collection<String> myUnresolvedDependencies;
    @NonNull private final JavaCompileOptions myJavaCompileOptions;
    @NonNull private final AaptOptions myAaptOptions;
    @NonNull private final File myBuildFolder;
    @NonNull private final ViewBindingOptions myViewBindingOptions;
    @Nullable private final String myResourcePrefix;
    private final int myApiVersion;
    private final boolean myLibrary;
    private final int myProjectType;
    private final boolean myBaseSplit;
    @NonNull private final AndroidGradlePluginProjectFlagsStub myFlags;

    public AndroidProjectStub(@NonNull String modelVersion) {
        this(
                modelVersion,
                "name",
                null,
                new ProductFlavorContainerStub(),
                Lists.newArrayList(new BuildTypeContainerStub()),
                Lists.newArrayList(new ProductFlavorContainerStub()),
                "buildToolsVersion",
                Lists.newArrayList(new SyncIssueStub()),
                Lists.newArrayList(new VariantStub()),
                Lists.newArrayList("debug", "release"),
                "debug",
                Lists.newArrayList("flavorDimension"),
                "compileTarget",
                Lists.newArrayList("bootClasspath"),
                Lists.newArrayList(new NativeToolchainStub()),
                Lists.newArrayList(new SigningConfigStub()),
                new LintOptionsStub(),
                Sets.newHashSet("unresolvedDependency"),
                new JavaCompileOptionsStub(),
                new AaptOptionsStub(),
                new ViewBindingOptionsStub(),
                new File("buildFolder"),
                "resourcePrefix",
                1,
                true,
                2,
                true,
                new AndroidGradlePluginProjectFlagsStub(Collections.emptyMap()));
    }

    public AndroidProjectStub(
            @NonNull String modelVersion,
            @NonNull String name,
            @Nullable String groupId,
            @NonNull ProductFlavorContainer defaultConfig,
            @NonNull Collection<BuildTypeContainer> buildTypes,
            @NonNull Collection<ProductFlavorContainer> productFlavors,
            @NonNull String buildToolsVersion,
            @NonNull Collection<SyncIssue> syncIssues,
            @NonNull Collection<Variant> variants,
            @NonNull Collection<String> variantNames,
            @Nullable String defaultVariant,
            @NonNull Collection<String> flavorDimensions,
            @NonNull String compileTarget,
            @NonNull Collection<String> bootClasspath,
            @NonNull Collection<NativeToolchain> nativeToolchains,
            @NonNull Collection<SigningConfig> signingConfigs,
            @NonNull LintOptions lintOptions,
            @NonNull Collection<String> unresolvedDependencies,
            @NonNull JavaCompileOptions javaCompileOptions,
            @NonNull AaptOptions aaptOptions,
            @NonNull ViewBindingOptions viewBindingOptions,
            @NonNull File buildFolder,
            @Nullable String resourcePrefix,
            int apiVersion,
            boolean library,
            int projectType,
            boolean baseSplit,
            AndroidGradlePluginProjectFlagsStub flags) {
        myModelVersion = modelVersion;
        myName = name;
        myGroupId = groupId;
        myDefaultConfig = defaultConfig;
        myBuildTypes = buildTypes;
        myProductFlavors = productFlavors;
        myBuildToolsVersion = buildToolsVersion;
        mySyncIssues = syncIssues;
        myVariants = variants;
        myVariantNames = variantNames;
        myDefaultVariant = defaultVariant;
        myFlavorDimensions = flavorDimensions;
        myCompileTarget = compileTarget;
        myBootClasspath = bootClasspath;
        myNativeToolchains = nativeToolchains;
        mySigningConfigs = signingConfigs;
        myLintOptions = lintOptions;
        myUnresolvedDependencies = unresolvedDependencies;
        myJavaCompileOptions = javaCompileOptions;
        myAaptOptions = aaptOptions;
        myViewBindingOptions = viewBindingOptions;
        myBuildFolder = buildFolder;
        myResourcePrefix = resourcePrefix;
        myApiVersion = apiVersion;
        myLibrary = library;
        myProjectType = projectType;
        myBaseSplit = baseSplit;
        myFlags = flags;
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

    @Nullable
    @Override
    public String getGroupId() {
        return myGroupId;
    }

    @Override
    @NonNull
    public ProductFlavorContainer getDefaultConfig() {
        return myDefaultConfig;
    }

    @Override
    @NonNull
    public Collection<BuildTypeContainer> getBuildTypes() {
        return myBuildTypes;
    }

    @Override
    @NonNull
    public Collection<ProductFlavorContainer> getProductFlavors() {
        return myProductFlavors;
    }

    @Override
    @NonNull
    public String getBuildToolsVersion() {
        return myBuildToolsVersion;
    }

    @Override
    @NonNull
    public Collection<SyncIssue> getSyncIssues() {
        return mySyncIssues;
    }

    @Override
    @NonNull
    public Collection<Variant> getVariants() {
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
    public Collection<ArtifactMetaData> getExtraArtifacts() {
        throw new UnusedModelMethodException("getExtraArtifacts");
    }

    @Override
    @NonNull
    public String getCompileTarget() {
        return myCompileTarget;
    }

    @Override
    @NonNull
    public Collection<String> getBootClasspath() {
        return myBootClasspath;
    }

    @Override
    @NonNull
    public Collection<File> getFrameworkSources() {
        throw new UnusedModelMethodException("getFrameworkSources");
    }

    @Override
    @NonNull
    public Collection<NativeToolchain> getNativeToolchains() {
        return myNativeToolchains;
    }

    @Override
    @NonNull
    public AaptOptions getAaptOptions() {
        return myAaptOptions;
    }

    @Override
    @NonNull
    public Collection<SigningConfig> getSigningConfigs() {
        return mySigningConfigs;
    }

    @Override
    @NonNull
    public LintOptions getLintOptions() {
        return myLintOptions;
    }

    @Override
    @NonNull
    public Collection<String> getUnresolvedDependencies() {
        return myUnresolvedDependencies;
    }

    @Override
    @NonNull
    public JavaCompileOptions getJavaCompileOptions() {
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

    @Deprecated
    @Override
    public boolean isLibrary() {
        return myLibrary;
    }

    @Override
    public int getProjectType() {
        return myProjectType;
    }

    @Override
    public int getPluginGeneration() {
        return GENERATION_ORIGINAL;
    }

    @Override
    public boolean isBaseSplit() {
        return myBaseSplit;
    }

    @NonNull
    @Override
    public Collection<String> getDynamicFeatures() {
        return ImmutableList.of();
    }

    @NonNull
    @Override
    public ViewBindingOptions getViewBindingOptions() {
        return myViewBindingOptions;
    }

    @NonNull
    @Override
    public AndroidGradlePluginProjectFlags getFlags() {
        return myFlags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AndroidProject)) {
            return false;
        }
        AndroidProject stub = (AndroidProject) o;
        return getApiVersion() == stub.getApiVersion()
                && isLibrary() == stub.isLibrary()
                && getProjectType() == stub.getProjectType()
                && isBaseSplit() == stub.isBaseSplit()
                && Objects.equals(getModelVersion(), stub.getModelVersion())
                && Objects.equals(getName(), stub.getName())
                && Objects.equals(getGroupId(), stub.getGroupId())
                && Objects.equals(getDefaultConfig(), stub.getDefaultConfig())
                && Objects.equals(getBuildTypes(), stub.getBuildTypes())
                && Objects.equals(getProductFlavors(), stub.getProductFlavors())
                && Objects.equals(getBuildToolsVersion(), stub.getBuildToolsVersion())
                && Objects.equals(getSyncIssues(), stub.getSyncIssues())
                && Objects.equals(getVariants(), stub.getVariants())
                && Objects.equals(getVariantNames(), stub.getVariantNames())
                && Objects.equals(getFlavorDimensions(), stub.getFlavorDimensions())
                && Objects.equals(getCompileTarget(), stub.getCompileTarget())
                && Objects.equals(getBootClasspath(), stub.getBootClasspath())
                && Objects.equals(getNativeToolchains(), stub.getNativeToolchains())
                && Objects.equals(getSigningConfigs(), stub.getSigningConfigs())
                && Objects.equals(getLintOptions(), stub.getLintOptions())
                && Objects.equals(getUnresolvedDependencies(), stub.getUnresolvedDependencies())
                && Objects.equals(getJavaCompileOptions(), stub.getJavaCompileOptions())
                && Objects.equals(getBuildFolder(), stub.getBuildFolder())
                && Objects.equals(getResourcePrefix(), stub.getResourcePrefix())
                && Objects.equals(getViewBindingOptions(), stub.getViewBindingOptions())
                && Objects.equals(getFlags(), stub.getFlags());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getModelVersion(),
                getName(),
                getGroupId(),
                getDefaultConfig(),
                getBuildTypes(),
                getProductFlavors(),
                getBuildToolsVersion(),
                getSyncIssues(),
                getVariants(),
                getVariantNames(),
                getFlavorDimensions(),
                getCompileTarget(),
                getBootClasspath(),
                getNativeToolchains(),
                getSigningConfigs(),
                getLintOptions(),
                getUnresolvedDependencies(),
                getJavaCompileOptions(),
                getBuildFolder(),
                getResourcePrefix(),
                getApiVersion(),
                isLibrary(),
                getProjectType(),
                isBaseSplit(),
                getViewBindingOptions(),
                getFlags());
    }

    @Override
    public String toString() {
        return "AndroidProjectStub{"
                + "myModelVersion='"
                + myModelVersion
                + '\''
                + ", myName='"
                + myName
                + '\''
                + ", myGroupId='"
                + myGroupId
                + '\''
                + ", myDefaultConfig="
                + myDefaultConfig
                + ", myBuildTypes="
                + myBuildTypes
                + ", myProductFlavors="
                + myProductFlavors
                + ", myBuildToolsVersion='"
                + myBuildToolsVersion
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
                + ", myBootClasspath="
                + myBootClasspath
                + ", myNativeToolchains="
                + myNativeToolchains
                + ", mySigningConfigs="
                + mySigningConfigs
                + ", myLintOptions="
                + myLintOptions
                + ", myUnresolvedDependencies="
                + myUnresolvedDependencies
                + ", myJavaCompileOptions="
                + myJavaCompileOptions
                + ", myBuildFolder="
                + myBuildFolder
                + ", myResourcePrefix='"
                + myResourcePrefix
                + '\''
                + ", myApiVersion="
                + myApiVersion
                + ", myLibrary="
                + myLibrary
                + ", myProjectType="
                + myProjectType
                + ", myBaseSplit="
                + myBaseSplit
                + ", myViewBindingOptions="
                + myViewBindingOptions
                + ", myFlags="
                + myFlags
                + "}";
    }
}
