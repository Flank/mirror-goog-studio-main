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
import com.android.ide.common.repository.GradleVersion;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Consumer;
import org.gradle.tooling.model.UnsupportedMethodException;

/** Creates a deep copy of an {@link AndroidProject}. */
public final class IdeAndroidProjectImpl extends IdeModel implements IdeAndroidProject {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 2L;

    @NonNull private final String myModelVersion;
    @NonNull private final String myName;
    @NonNull private final ProductFlavorContainer myDefaultConfig;
    @NonNull private final Collection<BuildTypeContainer> myBuildTypes;
    @NonNull private final Collection<ProductFlavorContainer> myProductFlavors;
    @NonNull private final Collection<SyncIssue> mySyncIssues;
    @NonNull private final Collection<Variant> myVariants;
    @NonNull private final Collection<String> myFlavorDimensions;
    @NonNull private final String myCompileTarget;
    @NonNull private final Collection<String> myBootClassPath;
    @NonNull private final Collection<NativeToolchain> myNativeToolchains;
    @NonNull private final Collection<SigningConfig> mySigningConfigs;
    @NonNull private final LintOptions myLintOptions;
    @NonNull private final Collection<String> myUnresolvedDependencies;
    @NonNull private final JavaCompileOptions myJavaCompileOptions;
    @NonNull private final File myBuildFolder;
    @Nullable private final GradleVersion myParsedModelVersion;
    @Nullable private final String myBuildToolsVersion;
    @Nullable private final String myResourcePrefix;
    @Nullable private final Integer myPluginGeneration;
    private final int myApiVersion;
    private final boolean myLibrary;
    private final int myProjectType;
    private final boolean myBaseSplit;
    private final int myHashCode;

    public IdeAndroidProjectImpl(
            @NonNull AndroidProject project,
            @NonNull IdeLevel2DependenciesFactory dependenciesFactory) {
        this(project, new ModelCache(), dependenciesFactory);
    }

    @VisibleForTesting
    IdeAndroidProjectImpl(
            @NonNull AndroidProject project,
            @NonNull ModelCache modelCache,
            @NonNull IdeLevel2DependenciesFactory dependenciesFactory) {
        super(project, modelCache);
        myModelVersion = project.getModelVersion();
        // Old plugin versions do not return model version.
        myParsedModelVersion = GradleVersion.tryParse(myModelVersion);

        myName = project.getName();
        myDefaultConfig =
                modelCache.computeIfAbsent(
                        project.getDefaultConfig(),
                        container -> new IdeProductFlavorContainer(container, modelCache));
        myBuildTypes =
                copy(
                        project.getBuildTypes(),
                        modelCache,
                        container -> new IdeBuildTypeContainer(container, modelCache));
        myProductFlavors =
                copy(
                        project.getProductFlavors(),
                        modelCache,
                        container -> new IdeProductFlavorContainer(container, modelCache));
        myBuildToolsVersion = copyNewProperty(project::getBuildToolsVersion, null);
        mySyncIssues =
                copy(
                        project.getSyncIssues(),
                        modelCache,
                        issue -> new IdeSyncIssue(issue, modelCache));
        myVariants =
                copy(
                        project.getVariants(),
                        modelCache,
                        variant ->
                                new IdeVariantImpl(
                                        variant,
                                        modelCache,
                                        dependenciesFactory,
                                        myParsedModelVersion));
        myFlavorDimensions =
                copyNewProperty(
                        () -> ImmutableList.copyOf(project.getFlavorDimensions()),
                        Collections.emptyList());
        myCompileTarget = project.getCompileTarget();
        myBootClassPath = ImmutableList.copyOf(project.getBootClasspath());
        myNativeToolchains =
                copy(
                        project.getNativeToolchains(),
                        modelCache,
                        toolchain -> new IdeNativeToolchain(toolchain, modelCache));
        mySigningConfigs =
                copy(
                        project.getSigningConfigs(),
                        modelCache,
                        config -> new IdeSigningConfig(config, modelCache));
        myLintOptions =
                modelCache.computeIfAbsent(
                        project.getLintOptions(),
                        options -> new IdeLintOptions(options, modelCache, myParsedModelVersion));
        myUnresolvedDependencies = ImmutableSet.copyOf(project.getUnresolvedDependencies());
        myJavaCompileOptions =
                modelCache.computeIfAbsent(
                        project.getJavaCompileOptions(),
                        options -> new IdeJavaCompileOptions(options, modelCache));
        myBuildFolder = project.getBuildFolder();
        myResourcePrefix = project.getResourcePrefix();
        myApiVersion = project.getApiVersion();
        myLibrary = project.isLibrary();
        myProjectType = getProjectType(project, myParsedModelVersion);
        myPluginGeneration = copyNewProperty(project::getPluginGeneration, null);
        myBaseSplit = copyNewProperty(project::isBaseSplit, false);

        myHashCode = calculateHashCode();
    }

    private static int getProjectType(
            @NonNull AndroidProject project, @Nullable GradleVersion modelVersion) {
        if (modelVersion != null && modelVersion.isAtLeast(2, 3, 0)) {
            return project.getProjectType();
        }
        return project.isLibrary() ? PROJECT_TYPE_LIBRARY : PROJECT_TYPE_APP;
    }

    @Override
    @Nullable
    public GradleVersion getParsedModelVersion() {
        return myParsedModelVersion;
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
        if (myBuildToolsVersion != null) {
            return myBuildToolsVersion;
        }
        throw new UnsupportedMethodException(
                "Unsupported method: AndroidProject.getBuildToolsVersion()");
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
        return myBootClassPath;
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

    @Deprecated
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
        if (myPluginGeneration != null) {
            return myPluginGeneration;
        }
        throw new UnsupportedMethodException(
                "Unsupported method: AndroidProject.getPluginGeneration()");
    }

    @Override
    public boolean isBaseSplit() {
        return myBaseSplit;
    }

    @Override
    public void forEachVariant(@NonNull Consumer<IdeVariant> action) {
        for (Variant variant : myVariants) {
            action.accept((IdeVariant) variant);
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
                && myLibrary == project.myLibrary
                && myProjectType == project.myProjectType
                && myBaseSplit == project.myBaseSplit
                && Objects.equals(myPluginGeneration, project.myPluginGeneration)
                && Objects.equals(myModelVersion, project.myModelVersion)
                && Objects.equals(myParsedModelVersion, project.myParsedModelVersion)
                && Objects.equals(myName, project.myName)
                && Objects.equals(myDefaultConfig, project.myDefaultConfig)
                && Objects.equals(myBuildTypes, project.myBuildTypes)
                && Objects.equals(myProductFlavors, project.myProductFlavors)
                && Objects.equals(myBuildToolsVersion, project.myBuildToolsVersion)
                && Objects.equals(mySyncIssues, project.mySyncIssues)
                && Objects.equals(myVariants, project.myVariants)
                && Objects.equals(myFlavorDimensions, project.myFlavorDimensions)
                && Objects.equals(myCompileTarget, project.myCompileTarget)
                && Objects.equals(myBootClassPath, project.myBootClassPath)
                && Objects.equals(myNativeToolchains, project.myNativeToolchains)
                && Objects.equals(mySigningConfigs, project.mySigningConfigs)
                && Objects.equals(myLintOptions, project.myLintOptions)
                && Objects.equals(myUnresolvedDependencies, project.myUnresolvedDependencies)
                && Objects.equals(myJavaCompileOptions, project.myJavaCompileOptions)
                && Objects.equals(myBuildFolder, project.myBuildFolder)
                && Objects.equals(myResourcePrefix, project.myResourcePrefix);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(
                myModelVersion,
                myParsedModelVersion,
                myName,
                myDefaultConfig,
                myBuildTypes,
                myProductFlavors,
                myBuildToolsVersion,
                mySyncIssues,
                myVariants,
                myFlavorDimensions,
                myCompileTarget,
                myBootClassPath,
                myNativeToolchains,
                mySigningConfigs,
                myLintOptions,
                myUnresolvedDependencies,
                myJavaCompileOptions,
                myBuildFolder,
                myResourcePrefix,
                myApiVersion,
                myLibrary,
                myProjectType,
                myPluginGeneration,
                myBaseSplit);
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
                + ", myBootClassPath="
                + myBootClassPath
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
