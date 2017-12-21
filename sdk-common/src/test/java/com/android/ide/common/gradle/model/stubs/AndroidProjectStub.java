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

import static com.android.SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.*;
import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.ide.common.gradle.model.UnusedModelMethodException;
import com.android.ide.common.repository.GradleVersion;
import com.google.common.collect.Maps;
import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class AndroidProjectStub extends BaseStub implements IdeAndroidProject {
    protected static final String DEFAULT_MODEL_VERSION =
            GRADLE_PLUGIN_MINIMUM_VERSION + "-SNAPSHOT";

    @NonNull private final String myName;
    @NonNull private final ProductFlavorContainer myDefaultConfig;
    @NonNull private final Map<String, BuildTypeContainer> myBuildTypes;
    @NonNull private final Map<String, ProductFlavorContainer> myProductFlavors;
    @NonNull private final String myBuildToolsVersion;
    @NonNull private final Collection<SyncIssue> mySyncIssues;
    @NonNull private final Map<String, Variant> myVariants;
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
    private final int myPluginGeneration;
    private final boolean myBaseSplit;

    @NonNull private String myModelVersion = DEFAULT_MODEL_VERSION;
    private int myProjectType = PROJECT_TYPE_APP;

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
            int projectType,
            int pluginGeneration,
            boolean baseSplit) {
        myModelVersion = modelVersion;
        myName = name;
        myDefaultConfig = defaultConfig;
        myBuildTypes = indexBuildTypesByName(buildTypes);
        myProductFlavors = indexProductFlavorsByName(productFlavors);
        myBuildToolsVersion = buildToolsVersion;
        mySyncIssues = syncIssues;
        myVariants = indexVariantByName(variants);
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
        myProjectType = projectType;
        myPluginGeneration = pluginGeneration;
        myBaseSplit = baseSplit;
    }

    @NonNull
    private static Map<String, BuildTypeContainer> indexBuildTypesByName(
            @NonNull Collection<BuildTypeContainer> buildTypes) {
        Map<String, BuildTypeContainer> buildTypesByName = Maps.newLinkedHashMap();
        for (BuildTypeContainer buildType : buildTypes) {
            buildTypesByName.put(buildType.getBuildType().getName(), buildType);
        }
        return buildTypesByName;
    }

    @NonNull
    private static Map<String, ProductFlavorContainer> indexProductFlavorsByName(
            @NonNull Collection<ProductFlavorContainer> productFlavors) {
        Map<String, ProductFlavorContainer> productFlavorsByName = Maps.newLinkedHashMap();
        for (ProductFlavorContainer productFlavor : productFlavors) {
            productFlavorsByName.put(productFlavor.getProductFlavor().getName(), productFlavor);
        }
        return productFlavorsByName;
    }

    @NonNull
    private static Map<String, Variant> indexVariantByName(Collection<Variant> variants) {
        Map<String, Variant> variantsByName = Maps.newLinkedHashMap();
        for (Variant variant : variants) {
            variantsByName.put(variant.getName(), variant);
        }
        return variantsByName;
    }

    @Override
    @NonNull
    public String getModelVersion() {
        return myModelVersion;
    }

    public void setModelVersion(@NonNull String modelVersion) {
        myModelVersion = modelVersion;
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
        return myBuildTypes.values();
    }

    @Override
    @NonNull
    public Collection<ProductFlavorContainer> getProductFlavors() {
        return myProductFlavors.values();
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
        return myVariants.values();
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
        return myProjectType == PROJECT_TYPE_LIBRARY;
    }

    @Override
    public int getProjectType() {
        return myProjectType;
    }

    public void setProjectType(int projectType) {
        myProjectType = projectType;
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
                + getModelVersion()
                + '\''
                + ", myName='"
                + getName()
                + '\''
                + ", myDefaultConfig="
                + getDefaultConfig()
                + ", myBuildTypes="
                + getBuildTypes()
                + ", myProductFlavors="
                + getProductFlavors()
                + ", myBuildToolsVersion='"
                + getBuildToolsVersion()
                + '\''
                + ", mySyncIssues="
                + getSyncIssues()
                + ", myVariants="
                + getVariants()
                + ", myFlavorDimensions="
                + getFlavorDimensions()
                + ", myCompileTarget='"
                + getCompileTarget()
                + '\''
                + ", myBootClasspath="
                + getBootClasspath()
                + ", myNativeToolchains="
                + getNativeToolchains()
                + ", mySigningConfigs="
                + getSigningConfigs()
                + ", myLintOptions="
                + getLintOptions()
                + ", myUnresolvedDependencies="
                + getUnresolvedDependencies()
                + ", myJavaCompileOptions="
                + getJavaCompileOptions()
                + ", myBuildFolder="
                + getBuildFolder()
                + ", myResourcePrefix='"
                + getResourcePrefix()
                + '\''
                + ", myApiVersion="
                + getApiVersion()
                + ", myLibrary="
                + isLibrary()
                + ", myProjectType="
                + getProjectType()
                + ", myPluginGeneration="
                + getPluginGeneration()
                + ", myBaseSplit="
                + isBaseSplit()
                + "}";
    }

    @Nullable
    @Override
    public GradleVersion getParsedModelVersion() {
        return GradleVersion.tryParse(myModelVersion);
    }

    @Override
    public void forEachVariant(@NonNull Consumer<IdeVariant> action) {
        for (Variant variant : getVariants()) {
            action.accept((IdeVariant) variant);
        }
    }

    protected void addBuildType(@NonNull BuildTypeContainer buildType) {
        String name = buildType.getBuildType().getName();
        myBuildTypes.put(name, buildType);
    }

    @Nullable
    public BuildTypeContainer findBuildType(@NonNull String name) {
        return myBuildTypes.get(name);
    }

    public void addProductFlavor(@NonNull ProductFlavorContainer productFlavor) {
        String name = productFlavor.getProductFlavor().getName();
        myProductFlavors.put(name, productFlavor);
    }

    public void addFlavorDimension(@NonNull String dimension) {
        if (!myFlavorDimensions.contains(dimension)) {
            myFlavorDimensions.add(dimension);
        }
    }

    @Nullable
    public ProductFlavorContainer findProductFlavor(@NonNull String name) {
        return myProductFlavors.get(name);
    }

    public void addVariant(@NonNull Variant variant) {
        myVariants.put(variant.getName(), variant);
    }
}
