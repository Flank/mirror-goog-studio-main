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
import com.android.builder.model.AaptOptions;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ArtifactMetaData;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.JavaCompileOptions;
import com.android.builder.model.LintOptions;
import com.android.builder.model.NativeToolchain;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SigningConfig;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.Variant;
import com.android.ide.common.builder.model.UnusedModelMethodException;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.Collection;
import java.util.Objects;

public class AndroidProjectStub extends BaseStub implements AndroidProject {
    @NonNull private final String myModelVersion;
    @NonNull private final String myName;
    @NonNull private final ProductFlavorContainer myDefaultConfig;
    @NonNull private final Collection<BuildTypeContainer> myBuildTypes;
    @NonNull private final Collection<ProductFlavorContainer> myProductFlavors;
    @NonNull private final String myBuildToolsVersion;
    @NonNull private final Collection<SyncIssue> mySyncIssues;
    @NonNull private final Collection<Variant> myVariants;
    @NonNull private final Collection<String> myFlavorDimensions;
    @NonNull private final String myCompileTarget;
    @NonNull private final Collection<String> myBootClasspath;
    @NonNull private final Collection<NativeToolchain> myNativeToolchains;
    @NonNull private final Collection<SigningConfig> mySigningConfigs;
    @NonNull private final LintOptions myLintOptions;
    @NonNull private final Collection<String> myUnresolvedDependencies;
    @NonNull private final JavaCompileOptions myJavaCompileOptions;
    @NonNull private final File myBuildFolder;
    @Nullable private final String myResourcePrefix;
    private final int myApiVersion;
    private final boolean myLibrary;
    private final int myProjectType;
    private final int myPluginGeneration;
    private final boolean myBaseSplit;

    public AndroidProjectStub(@NonNull String modelVersion) {
        this(
                modelVersion,
                "name",
                new ProductFlavorContainerStub(),
                Lists.newArrayList(new BuildTypeContainerStub()),
                Lists.newArrayList(new ProductFlavorContainerStub()),
                "buildToolsVersion",
                Lists.newArrayList(new SyncIssueStub()),
                Lists.newArrayList(new VariantStub()),
                Lists.newArrayList("flavorDimension"),
                "compileTarget",
                Lists.newArrayList("bootClasspath"),
                Lists.newArrayList(new NativeToolchainStub()),
                Lists.newArrayList(new SigningConfigStub()),
                new LintOptionsStub(),
                Sets.newHashSet("unresolvedDependency"),
                new JavaCompileOptionsStub(),
                new File("buildFolder"),
                "resourcePrefix",
                1,
                true,
                2,
                3,
                true);
    }

    public AndroidProjectStub(
            @NonNull String modelVersion,
            @NonNull String name,
            @NonNull ProductFlavorContainer defaultConfig,
            @NonNull Collection<BuildTypeContainer> buildTypes,
            @NonNull Collection<ProductFlavorContainer> productFlavors,
            @NonNull String buildToolsVersion,
            @NonNull Collection<SyncIssue> syncIssues,
            @NonNull Collection<Variant> variants,
            @NonNull Collection<String> flavorDimensions,
            @NonNull String compileTarget,
            @NonNull Collection<String> bootClasspath,
            @NonNull Collection<NativeToolchain> nativeToolchains,
            @NonNull Collection<SigningConfig> signingConfigs,
            @NonNull LintOptions lintOptions,
            @NonNull Collection<String> unresolvedDependencies,
            @NonNull JavaCompileOptions javaCompileOptions,
            @NonNull File buildFolder,
            @Nullable String resourcePrefix,
            int apiVersion,
            boolean library,
            int projectType,
            int pluginGeneration,
            boolean baseSplit) {
        myModelVersion = modelVersion;
        myName = name;
        myDefaultConfig = defaultConfig;
        myBuildTypes = buildTypes;
        myProductFlavors = productFlavors;
        myBuildToolsVersion = buildToolsVersion;
        mySyncIssues = syncIssues;
        myVariants = variants;
        myFlavorDimensions = flavorDimensions;
        myCompileTarget = compileTarget;
        myBootClasspath = bootClasspath;
        myNativeToolchains = nativeToolchains;
        mySigningConfigs = signingConfigs;
        myLintOptions = lintOptions;
        myUnresolvedDependencies = unresolvedDependencies;
        myJavaCompileOptions = javaCompileOptions;
        myBuildFolder = buildFolder;
        myResourcePrefix = resourcePrefix;
        myApiVersion = apiVersion;
        myLibrary = library;
        myProjectType = projectType;
        myPluginGeneration = pluginGeneration;
        myBaseSplit = baseSplit;
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
        throw new UnusedModelMethodException("getAaptOptions");
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
        return myPluginGeneration;
    }

    @Override
    public boolean isBaseSplit() {
        return myBaseSplit;
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
                && getPluginGeneration() == stub.getPluginGeneration()
                && isBaseSplit() == stub.isBaseSplit()
                && Objects.equals(getModelVersion(), stub.getModelVersion())
                && Objects.equals(getName(), stub.getName())
                && Objects.equals(getDefaultConfig(), stub.getDefaultConfig())
                && Objects.equals(getBuildTypes(), stub.getBuildTypes())
                && Objects.equals(getProductFlavors(), stub.getProductFlavors())
                && Objects.equals(getBuildToolsVersion(), stub.getBuildToolsVersion())
                && Objects.equals(getSyncIssues(), stub.getSyncIssues())
                && Objects.equals(getVariants(), stub.getVariants())
                && Objects.equals(getFlavorDimensions(), stub.getFlavorDimensions())
                && Objects.equals(getCompileTarget(), stub.getCompileTarget())
                && Objects.equals(getBootClasspath(), stub.getBootClasspath())
                && Objects.equals(getNativeToolchains(), stub.getNativeToolchains())
                && Objects.equals(getSigningConfigs(), stub.getSigningConfigs())
                && Objects.equals(getLintOptions(), stub.getLintOptions())
                && Objects.equals(getUnresolvedDependencies(), stub.getUnresolvedDependencies())
                && Objects.equals(getJavaCompileOptions(), stub.getJavaCompileOptions())
                && Objects.equals(getBuildFolder(), stub.getBuildFolder())
                && Objects.equals(getResourcePrefix(), stub.getResourcePrefix());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getModelVersion(),
                getName(),
                getDefaultConfig(),
                getBuildTypes(),
                getProductFlavors(),
                getBuildToolsVersion(),
                getSyncIssues(),
                getVariants(),
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
                getPluginGeneration(),
                isBaseSplit());
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
                + ", myPluginGeneration="
                + myPluginGeneration
                + ", myBaseSplit="
                + myBaseSplit
                + "}";
    }
}
