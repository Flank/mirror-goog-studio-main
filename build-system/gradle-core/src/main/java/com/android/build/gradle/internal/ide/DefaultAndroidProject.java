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
import com.android.build.gradle.internal.CompileOptions;
import com.android.builder.model.AaptOptions;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ArtifactMetaData;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.JavaCompileOptions;
import com.android.builder.model.LintOptions;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SigningConfig;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.NativeToolchain;
import com.android.builder.model.Variant;
import com.google.common.collect.Lists;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;

/**
 * Implementation of the AndroidProject model object.
 */
final class DefaultAndroidProject implements AndroidProject, Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull
    private final String modelVersion;
    @NonNull
    private final String name;
    @NonNull
    private final String compileTarget;
    @NonNull
    private final Collection<String> bootClasspath;
    @NonNull
    private final Collection<File> frameworkSource;
    @NonNull
    private final Collection<SigningConfig> signingConfigs;
    @NonNull
    private final AaptOptions aaptOptions;
    @NonNull
    private final Collection<ArtifactMetaData> extraArtifacts;
    @NonNull
    private final Collection<String> unresolvedDependencies;
    @NonNull
    private final Collection<SyncIssue> syncIssues;

    private final int generation;

    @NonNull
    private final JavaCompileOptions javaCompileOptions;
    @NonNull
    private final LintOptions lintOptions;
    @NonNull
    private final File buildFolder;
    @NonNull
    private final String buildToolsVersion;
    @Nullable
    private final String resourcePrefix;
    @NonNull
    private final Collection<NativeToolchain> nativeToolchains;
    private final int projectType;
    private final int apiVersion;

    @NonNull
    private final ProductFlavorContainer defaultConfig;

    private final Collection<BuildTypeContainer> buildTypes = Lists.newArrayList();
    private final Collection<ProductFlavorContainer> productFlavors = Lists.newArrayList();
    private final Collection<Variant> variants = Lists.newArrayList();

    @NonNull
    private final Collection<String> flavorDimensions;

    DefaultAndroidProject(
            @NonNull String modelVersion,
            @NonNull String name,
            @NonNull ProductFlavorContainer defaultConfig,
            @NonNull Collection<String> flavorDimensions,
            @NonNull String compileTarget,
            @NonNull Collection<String> bootClasspath,
            @NonNull Collection<File> frameworkSource,
            @NonNull Collection<SigningConfig> signingConfigs,
            @NonNull AaptOptions aaptOptions,
            @NonNull Collection<ArtifactMetaData> extraArtifacts,
            @NonNull Collection<String> unresolvedDependencies,
            @NonNull Collection<SyncIssue> syncIssues,
            @NonNull CompileOptions compileOptions,
            @NonNull LintOptions lintOptions,
            @NonNull File buildFolder,
            @Nullable String resourcePrefix,
            @NonNull Collection<NativeToolchain> nativeToolchains,
            @NonNull String buildToolsVersion,
            int projectType,
            int apiVersion,
            int generation) {
        this.modelVersion = modelVersion;
        this.name = name;
        this.defaultConfig = defaultConfig;
        this.flavorDimensions = flavorDimensions;
        this.compileTarget = compileTarget;
        this.bootClasspath = bootClasspath;
        this.frameworkSource = frameworkSource;
        this.signingConfigs = signingConfigs;
        this.aaptOptions = aaptOptions;
        this.extraArtifacts = extraArtifacts;
        this.unresolvedDependencies = unresolvedDependencies;
        this.syncIssues = syncIssues;
        this.javaCompileOptions = new DefaultJavaCompileOptions(compileOptions);
        this.lintOptions = lintOptions;
        this.buildFolder = buildFolder;
        this.resourcePrefix = resourcePrefix;
        this.projectType = projectType;
        this.apiVersion = apiVersion;
        this.generation = generation;
        this.nativeToolchains = nativeToolchains;
        this.buildToolsVersion = buildToolsVersion;
    }

    @NonNull
    DefaultAndroidProject addBuildType(@NonNull BuildTypeContainer buildTypeContainer) {
        buildTypes.add(buildTypeContainer);
        return this;
    }

    @NonNull
    DefaultAndroidProject addProductFlavors(
            @NonNull ProductFlavorContainer productFlavorContainer) {
        productFlavors.add(productFlavorContainer);
        return this;
    }

    @NonNull
    DefaultAndroidProject addVariant(@NonNull VariantImpl variant) {
        variants.add(variant);
        return this;
    }

    @Override
    @NonNull
    public String getModelVersion() {
        return modelVersion;
    }

    @Override
    public int getApiVersion() {
        return apiVersion;
    }

    @Override
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    @NonNull
    public ProductFlavorContainer getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    @NonNull
    public Collection<BuildTypeContainer> getBuildTypes() {
        return buildTypes;
    }

    @Override
    @NonNull
    public Collection<ProductFlavorContainer> getProductFlavors() {
        return productFlavors;
    }

    @Override
    @NonNull
    public Collection<Variant> getVariants() {
        return variants;
    }

    @NonNull
    @Override
    public Collection<String> getFlavorDimensions() {
        return flavorDimensions;
    }

    @NonNull
    @Override
    public Collection<ArtifactMetaData> getExtraArtifacts() {
        return extraArtifacts;
    }

    @Override
    public boolean isLibrary() {
        return getProjectType() == PROJECT_TYPE_LIBRARY;
    }

    @Override
    public int getProjectType() {
        return projectType;
    }

    @Override
    @NonNull
    public String getCompileTarget() {
        return compileTarget;
    }

    @Override
    @NonNull
    public Collection<String> getBootClasspath() {
        return bootClasspath;
    }

    @Override
    @NonNull
    public Collection<File> getFrameworkSources() {
        return frameworkSource;
    }

    @Override
    @NonNull
    public Collection<SigningConfig> getSigningConfigs() {
        return signingConfigs;
    }

    @Override
    @NonNull
    public AaptOptions getAaptOptions() {
        return aaptOptions;
    }

    @Override
    @NonNull
    public LintOptions getLintOptions() {
        return lintOptions;
    }

    @Override
    @NonNull
    public Collection<String> getUnresolvedDependencies() {
        return unresolvedDependencies;
    }

    @NonNull
    @Override
    public Collection<SyncIssue> getSyncIssues() {
        return syncIssues;
    }

    @Override
    @NonNull
    public JavaCompileOptions getJavaCompileOptions() {
        return javaCompileOptions;
    }

    @Override
    @NonNull
    public File getBuildFolder() {
        return buildFolder;
    }

    @Override
    @Nullable
    public String getResourcePrefix() {
        return resourcePrefix;
    }

    @NonNull
    @Override
    public Collection<NativeToolchain> getNativeToolchains() {
        return nativeToolchains;
    }

    @NonNull
    @Override
    public String getBuildToolsVersion() {
        return buildToolsVersion;
    }

    @Override
    public int getPluginGeneration() {
        return generation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultAndroidProject that = (DefaultAndroidProject) o;
        return generation == that.generation &&
                projectType == that.projectType &&
                apiVersion == that.apiVersion &&
                Objects.equals(modelVersion, that.modelVersion) &&
                Objects.equals(name, that.name) &&
                Objects.equals(compileTarget, that.compileTarget) &&
                Objects.equals(bootClasspath, that.bootClasspath) &&
                Objects.equals(frameworkSource, that.frameworkSource) &&
                Objects.equals(signingConfigs, that.signingConfigs) &&
                Objects.equals(aaptOptions, that.aaptOptions) &&
                Objects.equals(extraArtifacts, that.extraArtifacts) &&
                Objects.equals(unresolvedDependencies, that.unresolvedDependencies) &&
                Objects.equals(syncIssues, that.syncIssues) &&
                Objects.equals(javaCompileOptions, that.javaCompileOptions) &&
                Objects.equals(lintOptions, that.lintOptions) &&
                Objects.equals(buildFolder, that.buildFolder) &&
                Objects.equals(buildToolsVersion, that.buildToolsVersion) &&
                Objects.equals(resourcePrefix, that.resourcePrefix) &&
                Objects.equals(nativeToolchains, that.nativeToolchains) &&
                Objects.equals(buildTypes, that.buildTypes) &&
                Objects.equals(productFlavors, that.productFlavors) &&
                Objects.equals(variants, that.variants) &&
                Objects.equals(defaultConfig, that.defaultConfig) &&
                Objects.equals(flavorDimensions, that.flavorDimensions);
    }

    @Override
    public int hashCode() {
        return Objects
                .hash(modelVersion, name, compileTarget, bootClasspath, frameworkSource,
                        signingConfigs,
                        aaptOptions, extraArtifacts, unresolvedDependencies, syncIssues, generation,
                        javaCompileOptions, lintOptions, buildFolder, buildToolsVersion,
                        resourcePrefix,
                        nativeToolchains, projectType, apiVersion, buildTypes, productFlavors,
                        variants,
                        defaultConfig, flavorDimensions);
    }
}
