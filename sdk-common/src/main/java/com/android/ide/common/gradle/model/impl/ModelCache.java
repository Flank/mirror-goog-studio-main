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

import static com.android.builder.model.AaptOptions.Namespacing.DISABLED;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.builder.model.AaptOptions;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.BuildType;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.ClassField;
import com.android.builder.model.JavaArtifact;
import com.android.builder.model.JavaCompileOptions;
import com.android.builder.model.MavenCoordinates;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SigningConfig;
import com.android.builder.model.SourceProviderContainer;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.TestOptions;
import com.android.builder.model.TestedTargetVariant;
import com.android.builder.model.Variant;
import com.android.builder.model.VectorDrawablesOptions;
import com.android.ide.common.gradle.model.IdeAaptOptions;
import com.android.ide.common.gradle.model.IdeAndroidArtifactOutput;
import com.android.ide.common.gradle.model.IdeAndroidGradlePluginProjectFlags;
import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.gradle.model.IdeBuildTypeContainer;
import com.android.ide.common.gradle.model.IdeDependenciesInfo;
import com.android.ide.common.gradle.model.IdeLintOptions;
import com.android.ide.common.gradle.model.IdeProductFlavorContainer;
import com.android.ide.common.gradle.model.IdeSigningConfig;
import com.android.ide.common.gradle.model.IdeSyncIssue;
import com.android.ide.common.gradle.model.IdeTestOptions;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.ide.common.gradle.model.IdeVariantBuildInformation;
import com.android.ide.common.gradle.model.IdeVectorDrawablesOptions;
import com.android.ide.common.gradle.model.IdeViewBindingOptions;
import com.android.ide.common.gradle.model.impl.ndk.v1.IdeNativeToolchainImpl;
import com.android.ide.common.gradle.model.ndk.v1.IdeNativeToolchain;
import com.android.ide.common.repository.GradleVersion;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public class ModelCache {
    public static final String LOCAL_AARS = "__local_aars__";
    private static final Object BAD_BAD =
            new Object() {
                @Override
                public String toString() {
                    return "<REFEREENCE-TO-SELF>";
                }
            };
    @NonNull private final Map<Object, Object> myData = new HashMap<>();
    @NonNull private final Map<String, String> myStrings;

    public ModelCache(@NonNull Map<String, String> strings) {
        myStrings = strings;
    }

    public ModelCache() {
        myStrings = new HashMap<>();
    }

    /**
     * Conceptually the same as {@link Map#computeIfAbsent(Object, Function)} except that this
     * method is synchronized and re-entrant.
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public synchronized <K, V> V computeIfAbsent(
            @NonNull K key, @NonNull Function<K, V> mappingFunction) {
        if (myData.containsKey(key)) {
            Object existing = myData.get(key);
            if (existing == BAD_BAD) {
                throw new IllegalStateException(
                        "Self reference detected while constructing an instance for: "
                                + key
                                + "\n while constructing:\n"
                                + myData.entrySet().stream()
                                        .filter(it -> it.getValue() == BAD_BAD)
                                        .map(it -> it.getKey().toString())
                                        .collect(Collectors.joining(",\n ")));
            }
            return (V) existing;
        } else {
            myData.put(key, BAD_BAD);
            V result = mappingFunction.apply(key);
            myData.put(key, result);
            return result;
        }
    }

    @NonNull
    public String deduplicateString(@NonNull String s) {
        String old = myStrings.putIfAbsent(s, s);
        return old != null ? old : s;
    }

    @NonNull
    @VisibleForTesting
    Map<Object, Object> getData() {
        return myData;
    }

    @NonNull
    private static Collection<IdeVariantBuildInformation> createVariantBuildInformation(
            @NonNull AndroidProject project, @Nullable GradleVersion agpVersion) {
        if (agpVersion != null && agpVersion.compareIgnoringQualifiers("4.1.0") >= 0) {
            // make deep copy of VariantBuildInformation.
            return project.getVariantsBuildInformation().stream()
                    .map(it -> IdeVariantBuildInformationImpl.createFrom(it))
                    .collect(ImmutableList.toImmutableList());
        }
        // VariantBuildInformation is not available.
        return Collections.emptyList();
    }

    @NonNull
    private static ImmutableList<String> computeVariantNames(Collection<IdeVariant> variants) {
        return variants.stream().map(IdeVariant::getName).collect(ImmutableList.toImmutableList());
    }

    private static int getProjectType(
            @NonNull AndroidProject project, @Nullable GradleVersion modelVersion) {
        if (modelVersion != null && modelVersion.isAtLeast(2, 3, 0)) {
            return project.getProjectType();
        }
        // Support for old Android Gradle Plugins must be maintained.
        //noinspection deprecation
        return project.isLibrary()
                ? IdeAndroidProject.PROJECT_TYPE_LIBRARY
                : IdeAndroidProject.PROJECT_TYPE_APP;
    }

    /** For older AGP versions pick a variant name based on a heuristic */
    @VisibleForTesting
    @Nullable
    static String getDefaultVariant(Collection<String> variantNames) {
        // Corner case of variant filter accidentally removing all variants.
        if (variantNames.isEmpty()) {
            return null;
        }

        // Favor the debug variant
        if (variantNames.contains("debug")) {
            return "debug";
        }
        // Otherwise the first alphabetically that has debug as a build type.
        ImmutableSortedSet<String> sortedNames = ImmutableSortedSet.copyOf(variantNames);
        for (String variantName : sortedNames) {
            if (variantName.endsWith("Debug")) {
                return variantName;
            }
        }
        // Otherwise fall back to the first alphabetically
        return sortedNames.first();
    }

    public IdeAndroidProjectImpl androidProjectFrom(
            @NonNull AndroidProject project,
            @NonNull IdeDependenciesFactory dependenciesFactory,
            @NonNull Collection<Variant> variants,
            @NonNull Collection<IdeVariant> cachedVariants,
            @NotNull Collection<SyncIssue> syncIssues) {
        // Old plugin versions do not return model version.
        GradleVersion parsedModelVersion = GradleVersion.tryParse(project.getModelVersion());

        IdeProductFlavorContainer defaultConfigCopy =
                computeIfAbsent(
                        project.getDefaultConfig(),
                        container -> productFlavorContainerFrom(container, this));

        Collection<IdeBuildTypeContainer> buildTypesCopy =
                copy(project.getBuildTypes(), container -> buildTypeContainerFrom(container, this));

        Collection<IdeProductFlavorContainer> productFlavorCopy =
                copy(
                        project.getProductFlavors(),
                        container -> productFlavorContainerFrom(container, this));

        Collection<IdeSyncIssue> syncIssuesCopy =
                copy(
                        syncIssues,
                        issue ->
                                new IdeSyncIssueImpl(
                                        issue.getMessage(),
                                        IdeModel.copyNewProperty(issue::getMultiLineMessage, null),
                                        issue.getData(),
                                        issue.getSeverity(),
                                        issue.getType()));

        Set<String> variantNames =
                variants.stream().map(it -> it.getName()).collect(Collectors.toSet());
        Collection<IdeVariant> variantsCopy =
          ImmutableList.copyOf(
            Iterables.concat(
              copy(
                variants,
                variant ->
                  IdeVariantImpl.createFrom(
                    variant,
                    this,
                    dependenciesFactory,
                    parsedModelVersion)),
              cachedVariants.stream()
                .filter(it -> !variantNames.contains(it.getName()))
                .collect(Collectors.toList())));

        Collection<String> variantNamesCopy =
                Objects.requireNonNull(
                        IdeModel.copyNewPropertyWithDefault(
                                () -> ImmutableList.copyOf(project.getVariantNames()),
                                () -> computeVariantNames(variantsCopy)));

        String defaultVariantCopy =
                IdeModel.copyNewPropertyWithDefault(
                        project::getDefaultVariant, () -> getDefaultVariant(variantNamesCopy));

        Collection<String> flavorDimensionCopy =
                IdeModel.copyNewPropertyNonNull(
                        () -> ImmutableList.copyOf(project.getFlavorDimensions()),
                        Collections.emptyList());

        Collection<String> bootClasspathCopy = ImmutableList.copyOf(project.getBootClasspath());

        Collection<IdeNativeToolchain> nativeToolchainsCopy =
                copy(
                        project.getNativeToolchains(),
                        toolChain -> new IdeNativeToolchainImpl(toolChain));

        Collection<IdeSigningConfig> signingConfigsCopy =
                copy(project.getSigningConfigs(), config -> signingConfigFrom(config));

        IdeLintOptions lintOptionsCopy =
                computeIfAbsent(
                        project.getLintOptions(),
                        options -> IdeLintOptionsImpl.createFrom(options, parsedModelVersion));

        // We need to use the unresolved dependencies to support older versions of the Android
        // Gradle Plugin.
        //noinspection deprecation
        Set<String> unresolvedDependenciesCopy =
                ImmutableSet.copyOf(project.getUnresolvedDependencies());

        IdeJavaCompileOptionsImpl javaCompileOptionsCopy =
                computeIfAbsent(
                        project.getJavaCompileOptions(),
                        options -> javaCompileOptionsFrom(options));

        IdeAaptOptionsImpl aaptOptionsCopy =
                computeIfAbsent(
                        project.getAaptOptions(), options -> aaptOptionsFrom(options));

        Collection<String> dynamicFeaturesCopy =
                ImmutableList.copyOf(
                        IdeModel.copyNewPropertyNonNull(
                                project::getDynamicFeatures, ImmutableList.of()));

        Collection<IdeVariantBuildInformation> variantBuildInformation =
                createVariantBuildInformation(project, parsedModelVersion);

        IdeViewBindingOptions viewBindingOptionsCopy =
                IdeModel.copyNewProperty(
                        () -> IdeViewBindingOptionsImpl.createFrom(project.getViewBindingOptions()),
                        null);

        IdeDependenciesInfo dependenciesInfoCopy =
                IdeModel.copyNewProperty(
                        () -> IdeDependenciesInfoImpl.createOrNull(project.getDependenciesInfo()),
                        null);

        String buildToolsVersionCopy =
                IdeModel.copyNewProperty(project::getBuildToolsVersion, null);

        String ndkVersionCopy = IdeModel.copyNewProperty(project::getNdkVersion, null);

        String groupId = null;
        if (parsedModelVersion != null
                && parsedModelVersion.isAtLeast(3, 6, 0, "alpha", 5, false)) {
            groupId = project.getGroupId();
        }

        List<File> lintRuleJarsCopy =
                IdeModel.copyNewProperty(
                        () -> ImmutableList.copyOf(project.getLintRuleJars()), null);

        // AndroidProject#isBaseSplit is always non null.
        //noinspection ConstantConditions
        boolean isBaseSplit = IdeModel.copyNewProperty(project::isBaseSplit, false);

        IdeAndroidGradlePluginProjectFlags agpFlags =
                Objects.requireNonNull(
                        IdeModel.copyNewProperty(
                                () ->
                                        IdeAndroidGradlePluginProjectFlagsImpl.createFrom(
                                                project.getFlags()),
                                new IdeAndroidGradlePluginProjectFlagsImpl()));

        return new IdeAndroidProjectImpl(
                project.getModelVersion(),
                parsedModelVersion,
                project.getName(),
                defaultConfigCopy,
                buildTypesCopy,
                productFlavorCopy,
                syncIssuesCopy,
                variantsCopy,
                variantNamesCopy,
                defaultVariantCopy,
                flavorDimensionCopy,
                project.getCompileTarget(),
                bootClasspathCopy,
                nativeToolchainsCopy,
                signingConfigsCopy,
                lintOptionsCopy,
                lintRuleJarsCopy,
                unresolvedDependenciesCopy,
                javaCompileOptionsCopy,
                aaptOptionsCopy,
                project.getBuildFolder(),
                dynamicFeaturesCopy,
                variantBuildInformation,
                viewBindingOptionsCopy,
                dependenciesInfoCopy,
                buildToolsVersionCopy,
                ndkVersionCopy,
                project.getResourcePrefix(),
                groupId,
                IdeModel.copyNewProperty(project::getPluginGeneration, null) != null,
                project.getApiVersion(),
                getProjectType(project, parsedModelVersion),
                isBaseSplit,
                agpFlags);
    }

    public static IdeAaptOptionsImpl aaptOptionsFrom(@NonNull AaptOptions original) {
        return new IdeAaptOptionsImpl(
                convertNamespacing(
                        IdeModel.copyNewPropertyNonNull(original::getNamespacing, DISABLED)));
    }

    @NotNull
    private static IdeAaptOptions.Namespacing convertNamespacing(AaptOptions.Namespacing namespacing) {
        IdeAaptOptions.Namespacing convertedNamespacing;
        switch (namespacing) {
            case DISABLED:
                convertedNamespacing = IdeAaptOptions.Namespacing.DISABLED;
                break;
            case REQUIRED:
                convertedNamespacing = IdeAaptOptions.Namespacing.REQUIRED;
                break;
            default:
                // No forward compatibility.
                throw new IllegalStateException("Unknown namespacing option: " + namespacing);
        }
        return convertedNamespacing;
    }

    @NonNull
    private static List<IdeAndroidArtifactOutput> copyOutputs(
            @NonNull AndroidArtifact artifact,
            @NonNull ModelCache modelCache,
            @Nullable GradleVersion agpVersion) {
        // getOutputs is deprecated in AGP 4.0.0.
        if (agpVersion != null && agpVersion.compareIgnoringQualifiers("4.0.0") >= 0) {
            return Collections.emptyList();
        }
        List<AndroidArtifactOutput> outputs;
        try {
            outputs = new ArrayList<>(artifact.getOutputs());
            return copy(outputs, output -> androidArtifactOutputFrom(output, modelCache));
        } catch (RuntimeException e) {
            System.err.println("Caught exception: " + e);
            // See http://b/64305584
            return Collections.emptyList();
        }
    }

    public static IdeAndroidArtifactImpl androidArtifactFrom(
            @NonNull AndroidArtifact artifact,
            @NonNull ModelCache modelCache,
            @NonNull IdeDependenciesFactory dependenciesFactory,
            @Nullable GradleVersion agpVersion) {
        return new IdeAndroidArtifactImpl(
                artifact.getName(),
                artifact.getCompileTaskName(),
                artifact.getAssembleTaskName(),
                IdeModel.copyNewPropertyNonNull(artifact::getAssembleTaskOutputListingFile, ""),
                artifact.getClassesFolder(),
                IdeModel.copyNewProperty(artifact::getJavaResourcesFolder, null),
                ImmutableSet.copyOf(IdeBaseArtifactImpl.getIdeSetupTaskNames(artifact)),
                new LinkedHashSet<File>(IdeBaseArtifactImpl.getGeneratedSourceFolders(artifact)),
                IdeBaseArtifactImpl.createSourceProvider(
                        modelCache, artifact.getVariantSourceProvider()),
                IdeBaseArtifactImpl.createSourceProvider(
                        modelCache, artifact.getMultiFlavorSourceProvider()),
                IdeModel.copyNewPropertyNonNull(
                        artifact::getAdditionalClassesFolders, Collections.emptySet()),
                dependenciesFactory.create(artifact),
                copyOutputs(artifact, modelCache, agpVersion),
                artifact.getApplicationId(),
                artifact.getSourceGenTaskName(),
                ImmutableList.copyOf(artifact.getGeneratedResourceFolders()),
                artifact.getSigningConfigName(),
                ImmutableSet
                        .copyOf( // In AGP 4.0 and below abiFilters was nullable, normalize null to
                                // empty set.
                                MoreObjects.firstNonNull(
                                        artifact.getAbiFilters(), ImmutableSet.of())),
                artifact.isSigned(),
                IdeModel.copyNewPropertyNonNull(
                        () -> new ArrayList<>(artifact.getAdditionalRuntimeApks()),
                        Collections.emptyList()),
                IdeModel.copyNewProperty(
                        modelCache,
                        artifact::getTestOptions,
                        testOptions -> testOptionsFrom(testOptions),
                        null),
                IdeModel.copyNewProperty(
                        modelCache,
                        artifact::getInstrumentedTestTaskName,
                        Function.identity(),
                        null),
                IdeModel.copyNewProperty(
                        modelCache, artifact::getBundleTaskName, Function.identity(), null),
                IdeModel.copyNewProperty(
                        modelCache,
                        artifact::getBundleTaskOutputListingFile,
                        Function.identity(),
                        null),
                IdeModel.copyNewProperty(
                        modelCache, artifact::getApkFromBundleTaskName, Function.identity(), null),
                IdeModel.copyNewProperty(
                        modelCache,
                        artifact::getApkFromBundleTaskOutputListingFile,
                        Function.identity(),
                        null),
                IdeModel.copyNewProperty(
                        modelCache, artifact::getCodeShrinker, Function.identity(), null));
    }

    public static IdeAndroidArtifactOutputImpl androidArtifactOutputFrom(
            @NonNull AndroidArtifactOutput output, @NonNull ModelCache modelCache) {
        return new IdeAndroidArtifactOutputImpl(
                copy(
                        output.getOutputs(),
                        outputFile -> new IdeOutputFileImpl(outputFile, modelCache)),
                IdeModel.copyNewProperty(
                        () -> ImmutableList.copyOf(output.getFilterTypes()),
                        Collections.emptyList()),
                IdeVariantOutputImpl.copyFilters(output),
                IdeModel.copyNewProperty(
                        modelCache,
                        output::getMainOutputFile,
                        file -> new IdeOutputFileImpl(file, modelCache),
                        null),
                IdeModel.copyNewProperty(output::getOutputType, null),
                output.getVersionCode(),
                IdeModel.copyNewProperty(
                        output::getOutputFile, output.getMainOutputFile().getOutputFile()));
    }

    public static IdeApiVersionImpl apiVersionFrom(@NonNull ApiVersion version) {
        return new IdeApiVersionImpl(
                version.getApiString(), version.getCodename(), version.getApiLevel());
    }

    public static IdeBuildTypeContainerImpl buildTypeContainerFrom(
            @NonNull BuildTypeContainer container, @NonNull ModelCache modelCache) {
        return new IdeBuildTypeContainerImpl(
                modelCache.computeIfAbsent(
                        container.getBuildType(),
                        buildType -> buildTypeFrom(buildType)),
                modelCache.computeIfAbsent(
                        container.getSourceProvider(),
                        provider ->
                                IdeSourceProviderImpl.createFrom(
                                        provider, modelCache::deduplicateString)),
                copy(
                        container.getExtraSourceProviders(),
                        sourceProviderContainer ->
                                sourceProviderContainerFrom(sourceProviderContainer, modelCache)));
    }

    public static IdeBuildTypeImpl buildTypeFrom(@NonNull BuildType buildType) {
        return new IdeBuildTypeImpl(
                buildType.getName(),
                copy(
                        buildType.getResValues(),
                        classField -> classFieldFrom(classField)),
                ImmutableList.copyOf(buildType.getProguardFiles()),
                ImmutableList.copyOf(buildType.getConsumerProguardFiles()),
                buildType.getManifestPlaceholders().entrySet().stream()
                        // AGP may return internal Groovy GString implementation as a value in
                        // manifestPlaceholders
                        // map. It cannot be serialized
                        // with IDEA's external system serialization. We convert values to String to
                        // make them
                        // usable as they are converted to String by
                        // the manifest merger anyway.

                        .collect(toImmutableMap(it -> it.getKey(), it -> it.getValue().toString())),
                buildType.getApplicationIdSuffix(),
                IdeModel.copyNewProperty(buildType::getVersionNameSuffix, null),
                IdeModel.copyNewProperty(buildType::getMultiDexEnabled, null),
                buildType.isDebuggable(),
                buildType.isJniDebuggable(),
                buildType.isRenderscriptDebuggable(),
                buildType.getRenderscriptOptimLevel(),
                buildType.isMinifyEnabled(),
                buildType.isZipAlignEnabled());
    }

    public static IdeClassFieldImpl classFieldFrom(@NonNull ClassField classField) {
        return new IdeClassFieldImpl(
                classField.getName(), classField.getType(), classField.getValue());
    }

    public static IdeFilterDataImpl filterDataFrom(@NonNull FilterData data) {
        return new IdeFilterDataImpl(data.getIdentifier(), data.getFilterType());
    }

    public static IdeJavaArtifactImpl javaArtifactFrom(
            @NonNull JavaArtifact artifact,
            @NonNull ModelCache seen,
            @NonNull IdeDependenciesFactory dependenciesFactory) {
        return new IdeJavaArtifactImpl(
                artifact.getName(),
                artifact.getCompileTaskName(),
                artifact.getAssembleTaskName(),
                IdeModel.copyNewPropertyNonNull(artifact::getAssembleTaskOutputListingFile, ""),
                artifact.getClassesFolder(),
                IdeModel.copyNewProperty(artifact::getJavaResourcesFolder, null),
                ImmutableSet.copyOf(IdeBaseArtifactImpl.getIdeSetupTaskNames(artifact)),
                new LinkedHashSet<File>(IdeBaseArtifactImpl.getGeneratedSourceFolders(artifact)),
                IdeBaseArtifactImpl.createSourceProvider(seen, artifact.getVariantSourceProvider()),
                IdeBaseArtifactImpl.createSourceProvider(
                        seen, artifact.getMultiFlavorSourceProvider()),
                IdeModel.copyNewPropertyNonNull(
                        artifact::getAdditionalClassesFolders, Collections.emptySet()),
                dependenciesFactory.create(artifact),
                IdeModel.copyNewProperty(artifact::getMockablePlatformJar, null));
    }

    public static IdeJavaCompileOptionsImpl javaCompileOptionsFrom(@NonNull JavaCompileOptions options) {
        return new IdeJavaCompileOptionsImpl(
                options.getEncoding(),
                options.getSourceCompatibility(),
                options.getTargetCompatibility(),
                IdeModel.copyNewPropertyNonNull(options::isCoreLibraryDesugaringEnabled, false));
    }

    public static IdeMavenCoordinatesImpl mavenCoordinatesFrom(@NonNull MavenCoordinates coordinates) {
        return new IdeMavenCoordinatesImpl(
                coordinates.getGroupId(),
                coordinates.getArtifactId(),
                coordinates.getVersion(),
                coordinates.getPackaging(),
                coordinates.getClassifier());
    }

    public static IdeMavenCoordinatesImpl mavenCoordinatesFrom(@NonNull File localJar) {
        return new IdeMavenCoordinatesImpl(
                LOCAL_AARS, localJar.getPath(), "unspecified", "jar", null);
    }

    public static IdeProductFlavorContainerImpl productFlavorContainerFrom(
            @NonNull ProductFlavorContainer container, @NonNull ModelCache modelCache) {
        return new IdeProductFlavorContainerImpl(
                modelCache.computeIfAbsent(
                        container.getProductFlavor(),
                        flavor -> productFlavorFrom(flavor, modelCache)),
                modelCache.computeIfAbsent(
                        container.getSourceProvider(),
                        provider ->
                                IdeSourceProviderImpl.createFrom(
                                        provider, modelCache::deduplicateString)),
                copy(
                        container.getExtraSourceProviders(),
                        sourceProviderContainer ->
                                sourceProviderContainerFrom(sourceProviderContainer, modelCache)));
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
                vectorDrawables, options -> vectorDrawablesOptionsFrom(options));
    }

    @Nullable
    private static IdeApiVersionImpl copy(
            @NonNull ModelCache modelCache, @Nullable ApiVersion apiVersion) {
        if (apiVersion != null) {
            return modelCache.computeIfAbsent(
                    apiVersion, version -> apiVersionFrom(version));
        }
        return null;
    }

    @Nullable
    private static IdeSigningConfig copy(
            @NonNull ModelCache modelCache, @Nullable SigningConfig signingConfig) {
        if (signingConfig != null) {
            return modelCache.computeIfAbsent(
                    signingConfig, config -> signingConfigFrom(config));
        }
        return null;
    }

    public static IdeProductFlavorImpl productFlavorFrom(@NonNull ProductFlavor flavor, @NonNull ModelCache modelCache) {
        return new IdeProductFlavorImpl(
                flavor.getName(),
                copy(
                        flavor.getResValues(),
                        classField -> classFieldFrom(classField)),
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

    public static IdeSigningConfigImpl signingConfigFrom(@NonNull SigningConfig config) {
        return new IdeSigningConfigImpl(
                config.getName(),
                config.getStoreFile(),
                config.getStorePassword(),
                config.getKeyAlias(),
                IdeModel.copyNewProperty(config::isV1SigningEnabled, null));
    }

    public static IdeSourceProviderContainerImpl sourceProviderContainerFrom(
            @NonNull SourceProviderContainer container, @NonNull ModelCache modelCache) {
        return new IdeSourceProviderContainerImpl(
                container.getArtifactName(),
                modelCache.computeIfAbsent(
                        container.getSourceProvider(),
                        provider ->
                                IdeSourceProviderImpl.createFrom(
                                        provider, modelCache::deduplicateString)));
    }

    public static IdeSyncIssueImpl syncIssueFrom(@NonNull SyncIssue issue) {
        return new IdeSyncIssueImpl(
                issue.getMessage(),
                IdeModel.copyNewProperty(issue::getMultiLineMessage, null),
                issue.getData(),
                issue.getSeverity(),
                issue.getType());
    }

    public static IdeTestedTargetVariantImpl testedTargetVariantFrom(@NonNull TestedTargetVariant variant) {
        return new IdeTestedTargetVariantImpl(
                variant.getTargetProjectPath(), variant.getTargetVariant());
    }

    @Nullable
    public static IdeTestOptions.Execution convertExecution(@Nullable TestOptions.Execution execution) {
        if (execution == null) return null;
        switch (execution) {
            case HOST:
                return IdeTestOptions.Execution.HOST;
            case ANDROID_TEST_ORCHESTRATOR:
                return IdeTestOptions.Execution.ANDROID_TEST_ORCHESTRATOR;
            case ANDROIDX_TEST_ORCHESTRATOR:
                return IdeTestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR;
            default:
                throw new IllegalStateException("Unknown execution option: " + execution);
        }
    }

    public static IdeTestOptionsImpl testOptionsFrom(@NonNull TestOptions testOptions) {
        return new IdeTestOptionsImpl(
                testOptions.getAnimationsDisabled(), convertExecution(testOptions.getExecution()));
    }

    public static IdeVectorDrawablesOptionsImpl vectorDrawablesOptionsFrom(
            @NonNull VectorDrawablesOptions options) {
        return new IdeVectorDrawablesOptionsImpl(options.getUseSupportLibrary());
    }

    @NonNull
    public static <K, V> List<V> copy(
      @NonNull Collection<K> original, @NonNull Function<K, V> mapper) {
        if (original.isEmpty()) {
            return Collections.emptyList();
        }
        ImmutableList.Builder<V> copies = ImmutableList.builder();
        for (K item : original) {
            copies.add(mapper.apply(item));
        }
        return copies.build();
    }

    @NonNull
    public static <K, V, R> Map<K, R> copy(
      @NonNull Map<K, V> original,
      @NonNull Function<V, R> mapper) {
        if (original.isEmpty()) {
            return Collections.emptyMap();
        }
        ImmutableMap.Builder<K, R> copies = ImmutableMap.builder();
        original.forEach((k, v) -> copies.put(k, mapper.apply(v)));
        return copies.build();
    }
}
