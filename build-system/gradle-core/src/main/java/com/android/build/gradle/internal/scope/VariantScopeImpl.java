/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.scope;

import static com.android.SdkConstants.DOT_JAR;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES_JAR;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;
import static com.android.build.gradle.internal.scope.ArtifactPublishingUtil.publishArtifactToConfiguration;
import static com.android.build.gradle.internal.scope.ArtifactPublishingUtil.publishArtifactToDefaultVariant;
import static com.android.build.gradle.options.BooleanOption.USE_NEW_APK_CREATOR;
import static com.android.build.gradle.options.BooleanOption.USE_NEW_JAR_CREATOR;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.impl.ArtifactsImpl;
import com.android.build.api.dsl.CompileOptions;
import com.android.build.api.variant.ComponentIdentity;
import com.android.build.gradle.internal.PostprocessingFeatures;
import com.android.build.gradle.internal.ProguardFileType;
import com.android.build.gradle.internal.component.ConsumableCreationConfig;
import com.android.build.gradle.internal.component.VariantCreationConfig;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.core.PostProcessingOptions;
import com.android.build.gradle.internal.core.dsl.ComponentDslInfo;
import com.android.build.gradle.internal.dependency.AndroidAttributes;
import com.android.build.gradle.internal.dependency.ProvidedClasspath;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.packaging.JarCreatorType;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType;
import com.android.build.gradle.internal.publishing.PublishedConfigSpec;
import com.android.build.gradle.internal.publishing.PublishingSpecs;
import com.android.build.gradle.internal.services.BaseServices;
import com.android.build.gradle.internal.testFixtures.TestFixturesUtil;
import com.android.build.gradle.internal.variant.VariantPathHelper;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.OptionalBooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.builder.errors.IssueReporter.Type;
import com.android.builder.internal.packaging.ApkCreatorType;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.SelfResolvingDependency;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;

/** A scope containing data for a specific variant. */
public class VariantScopeImpl implements VariantScope {

    // Variant specific Data
    @NonNull private final ComponentIdentity componentIdentity;
    @NonNull private final ComponentDslInfo dslInfo;
    @NonNull private final VariantPathHelper pathHelper;
    @NonNull private final ArtifactsImpl artifacts;
    @NonNull private final VariantDependencies variantDependencies;

    @NonNull private final PublishingSpecs.VariantSpec variantPublishingSpec;

    @NonNull private final String compileSdkVersion;

    private final boolean hasDynamicFeatures;

    @Nullable private final VariantCreationConfig testedVariantProperties;

    // other

    @NonNull private final Map<Abi, File> ndkDebuggableLibraryFolders = Maps.newHashMap();

    @NonNull private final PostProcessingOptions postProcessingOptions;
    @NonNull private final BaseServices baseServices;

    public VariantScopeImpl(
            @NonNull ComponentIdentity componentIdentity,
            @NonNull ComponentDslInfo dslInfo,
            @NonNull VariantDependencies variantDependencies,
            @NonNull VariantPathHelper pathHelper,
            @NonNull ArtifactsImpl artifacts,
            @NonNull BaseServices baseServices,
            @Nullable String compileSdkVersion,
            boolean hasDynamicFeatures,
            @Nullable VariantCreationConfig testedVariantProperties) {
        this.componentIdentity = componentIdentity;
        this.dslInfo = dslInfo;
        this.variantDependencies = variantDependencies;
        this.pathHelper = pathHelper;
        this.artifacts = artifacts;
        this.baseServices = baseServices;
        this.variantPublishingSpec = PublishingSpecs.getVariantSpec(dslInfo.getComponentType());
        this.compileSdkVersion = compileSdkVersion;
        this.hasDynamicFeatures = hasDynamicFeatures;
        this.testedVariantProperties = testedVariantProperties;

        this.postProcessingOptions = dslInfo.getPostProcessingOptions();

        configureNdk();
    }

    private void configureNdk() {
        File objFolder =
                pathHelper
                        .intermediatesDir("ndk", pathHelper.getDirName(), "obj")
                        .get()
                        .getAsFile();

        for (Abi abi : Abi.values()) {
            addNdkDebuggableLibraryFolders(abi, new File(objFolder, "local/" + abi.getTag()));
        }
    }

    @Override
    @NonNull
    public PublishingSpecs.VariantSpec getPublishingSpec() {
        return variantPublishingSpec;
    }

    /**
     * Publish an intermediate artifact.
     *
     * @param artifact Provider of File or FileSystemLocation to be published.
     * @param artifactType the artifact type.
     * @param configSpecs the PublishedConfigSpec.
     * @param libraryElements the artifact's library elements
     * @param isTestFixturesArtifact whether the artifact is from a test fixtures component
     */
    @Override
    public void publishIntermediateArtifact(
            @NonNull Provider<?> artifact,
            @NonNull ArtifactType artifactType,
            @NonNull Set<PublishedConfigSpec> configSpecs,
            @Nullable LibraryElements libraryElements,
            boolean isTestFixturesArtifact) {

        Preconditions.checkState(!configSpecs.isEmpty());

        for (PublishedConfigSpec configSpec : configSpecs) {
            Configuration config = variantDependencies.getElements(configSpec);
            PublishedConfigType configType = configSpec.getConfigType();
            if (config != null) {
                if (configType.isPublicationConfig()) {
                    String classifier = null;
                    boolean isSourcePublication =
                            configType == PublishedConfigType.SOURCE_PUBLICATION;
                    boolean isJavaDocPublication =
                            configType == PublishedConfigType.JAVA_DOC_PUBLICATION;
                    if (configSpec.isClassifierRequired()) {
                        if (isSourcePublication) {
                            classifier = componentIdentity.getName() + "-" + DocsType.SOURCES;
                        } else if (isJavaDocPublication) {
                            classifier = componentIdentity.getName() + "-" + DocsType.JAVADOC;
                        } else {
                            classifier = componentIdentity.getName();
                        }
                    } else if (isTestFixturesArtifact) {
                        classifier = TestFixturesUtil.testFixturesClassifier;
                    } else if (isSourcePublication) {
                        classifier = DocsType.SOURCES;
                    } else if (isJavaDocPublication) {
                        classifier = DocsType.JAVADOC;
                    }
                    publishArtifactToDefaultVariant(config, artifact, artifactType, classifier);
                } else {
                    publishArtifactToConfiguration(
                            config,
                            artifact,
                            artifactType,
                            new AndroidAttributes(null, libraryElements));
                }
            }
        }
    }

    @Override
    public boolean consumesFeatureJars() {
        return dslInfo.getComponentType().isBaseModule()
                && dslInfo.getPostProcessingOptions().codeShrinkerEnabled()
                && hasDynamicFeatures;
    }

    @Override
    public boolean getNeedsJavaResStreams() {
        // We need to create original java resource stream only if we're in a library module with
        // custom transforms.
        return dslInfo.getComponentType().isAar() && !dslInfo.getTransforms().isEmpty();
    }

    @NonNull
    @Override
    public List<File> getConsumerProguardFiles() {
        return gatherProguardFiles(ProguardFileType.CONSUMER);
    }

    @NonNull
    @Override
    public List<File> getConsumerProguardFilesForFeatures() {
        // We include proguardFiles if we're in a dynamic-feature module.
        final boolean includeProguardFiles = dslInfo.getComponentType().isDynamicFeature();
        final Collection<File> consumerProguardFiles = getConsumerProguardFiles();
        if (includeProguardFiles) {
            consumerProguardFiles.addAll(gatherProguardFiles(ProguardFileType.EXPLICIT));
        }

        return ImmutableList.copyOf(consumerProguardFiles);
    }

    @NonNull
    private List<File> gatherProguardFiles(ProguardFileType type) {
        List<File> files = new ArrayList<>();
        dslInfo.gatherProguardFiles(
                type,
                regularFile -> {
                    files.add(regularFile.getAsFile());
                    return null;
                });
        return files;
    }

    @Override
    @Nullable
    public PostprocessingFeatures getPostprocessingFeatures() {
        return postProcessingOptions.getPostprocessingFeatures();
    }

    /**
     * Determine if the final output should be marked as testOnly to prevent uploading to Play
     * store.
     *
     * <p>Uploading to Play store is disallowed if:
     *
     * <ul>
     *   <li>An injected option is set (usually by the IDE for testing purposes).
     *   <li>compileSdkVersion, minSdkVersion or targetSdkVersion is a preview
     * </ul>
     *
     * <p>This value can be overridden by the OptionalBooleanOption.IDE_TEST_ONLY property.
     *
     * @param variant {@link VariantCreationConfig} for this variant scope.
     */
    @Override
    public boolean isTestOnly(VariantCreationConfig variant) {
        ProjectOptions projectOptions = baseServices.getProjectOptions();
        Boolean isTestOnlyOverride = projectOptions.get(OptionalBooleanOption.IDE_TEST_ONLY);

        if (isTestOnlyOverride != null) {
            return isTestOnlyOverride;
        }

        return !Strings.isNullOrEmpty(projectOptions.get(StringOption.IDE_BUILD_TARGET_ABI))
                || !Strings.isNullOrEmpty(projectOptions.get(StringOption.IDE_BUILD_TARGET_DENSITY))
                || projectOptions.get(IntegerOption.IDE_TARGET_DEVICE_API) != null
                || isPreviewTargetPlatform()
                || variant.getMinSdkVersion().getCodename() != null
                || variant.getTargetSdkVersion().getCodename() != null;
    }

    private boolean isPreviewTargetPlatform() {
        AndroidVersion version = AndroidTargetHash.getVersionFromHash(compileSdkVersion);
        return version != null && version.isPreview();
    }

    /**
     * Returns if core library desugaring is enabled.
     *
     * <p>Java language desugaring and multidex are required for enabling core library desugaring.
     */
    @Override
    public boolean isCoreLibraryDesugaringEnabled(ConsumableCreationConfig creationConfig) {
        CompileOptions compileOptions = creationConfig.getGlobal().getCompileOptions();

        boolean libDesugarEnabled = compileOptions.isCoreLibraryDesugaringEnabled();
        boolean multidexEnabled = creationConfig.isMultiDexEnabled();

        Java8LangSupport langSupportType = creationConfig.getJava8LangSupportType();
        boolean langDesugarEnabled =
                langSupportType == Java8LangSupport.D8 || langSupportType == Java8LangSupport.R8;

        if (libDesugarEnabled && !langDesugarEnabled) {
            creationConfig
                    .getServices()
                    .getIssueReporter()
                    .reportError(
                            Type.GENERIC,
                            "In order to use core library desugaring, "
                                    + "please enable java 8 language desugaring with D8 or R8.");
        }

        if (libDesugarEnabled && !multidexEnabled) {
            creationConfig
                    .getServices()
                    .getIssueReporter()
                    .reportError(
                            Type.GENERIC,
                            "In order to use core library desugaring, "
                                    + "please enable multidex.");
        }
        return libDesugarEnabled;
    }

    @Override
    public void addNdkDebuggableLibraryFolders(@NonNull Abi abi, @NonNull File searchPath) {
        this.ndkDebuggableLibraryFolders.put(abi, searchPath);
    }

    @NonNull
    @Override
    public FileCollection getProvidedOnlyClasspath() {
        ArtifactCollection compile =
                variantDependencies.getArtifactCollection(COMPILE_CLASSPATH, ALL, CLASSES_JAR);
        ArtifactCollection runtime =
                variantDependencies.getArtifactCollection(RUNTIME_CLASSPATH, ALL, CLASSES_JAR);

        return ProvidedClasspath.getProvidedClasspath(compile, runtime);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).addValue(componentIdentity.getName()).toString();
    }

    @NonNull
    @Override
    public JarCreatorType getJarCreatorType() {
        if (baseServices.getProjectOptions().get(USE_NEW_JAR_CREATOR)) {
            return JarCreatorType.JAR_FLINGER;
        } else {
            return JarCreatorType.JAR_MERGER;
        }
    }

    @NonNull
    @Override
    public ApkCreatorType getApkCreatorType() {
        if (baseServices.getProjectOptions().get(USE_NEW_APK_CREATOR)) {
            return ApkCreatorType.APK_FLINGER;
        } else {
            return ApkCreatorType.APK_Z_FILE_CREATOR;
        }
    }
}
