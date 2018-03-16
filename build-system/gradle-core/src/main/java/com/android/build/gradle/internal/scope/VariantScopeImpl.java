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

import static com.android.SdkConstants.FD_COMPILED;
import static com.android.SdkConstants.FD_MERGED;
import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.FN_CLASSES_JAR;
import static com.android.build.gradle.internal.dsl.BuildType.PostprocessingConfiguration.POSTPROCESSING_BLOCK;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ARTIFACT_TYPE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.MODULE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.COMPILE_ONLY_NAMESPACED_R_CLASS_JAR;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.SHARED_CLASSES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.API_ELEMENTS;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.BUNDLE_ELEMENTS;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.METADATA_ELEMENTS;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS;
import static com.android.build.gradle.internal.scope.CodeShrinker.ANDROID_GRADLE;
import static com.android.build.gradle.internal.scope.CodeShrinker.PROGUARD;
import static com.android.build.gradle.internal.scope.CodeShrinker.R8;
import static com.android.build.gradle.options.BooleanOption.ENABLE_D8;
import static com.android.build.gradle.options.BooleanOption.ENABLE_D8_DESUGARING;
import static com.android.build.gradle.options.BooleanOption.ENABLE_DEX_ARCHIVE;
import static com.android.build.gradle.options.BooleanOption.ENABLE_R8;
import static com.android.build.gradle.options.BooleanOption.ENABLE_R8_DESUGARING;
import static com.android.builder.model.AndroidProject.FD_GENERATED;
import static com.android.builder.model.AndroidProject.FD_OUTPUTS;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.BuildableArtifact;
import com.android.build.gradle.ProguardFiles;
import com.android.build.gradle.internal.InstantRunTaskManager;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.PostprocessingFeatures;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.coverage.JacocoReportTask;
import com.android.build.gradle.internal.dependency.AndroidTestResourceArtifactCollection;
import com.android.build.gradle.internal.dependency.ArtifactCollectionWithExtraArtifact;
import com.android.build.gradle.internal.dependency.FilteredArtifactCollection;
import com.android.build.gradle.internal.dependency.SubtractingArtifactCollection;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.dsl.CoreProductFlavor;
import com.android.build.gradle.internal.dsl.PostprocessingOptions;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType;
import com.android.build.gradle.internal.publishing.PublishingSpecs;
import com.android.build.gradle.internal.publishing.PublishingSpecs.OutputSpec;
import com.android.build.gradle.internal.publishing.PublishingSpecs.VariantSpec;
import com.android.build.gradle.internal.tasks.CheckManifest;
import com.android.build.gradle.internal.tasks.GenerateApkDataTask;
import com.android.build.gradle.internal.tasks.TaskInputHelper;
import com.android.build.gradle.internal.tasks.databinding.DataBindingExportBuildInfoTask;
import com.android.build.gradle.internal.variant.ApplicationVariantData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.internal.variant.TestedVariantData;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.DeploymentDevice;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.OptionalBooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.build.gradle.tasks.AidlCompile;
import com.android.build.gradle.tasks.ExternalNativeBuildTask;
import com.android.build.gradle.tasks.ExternalNativeJsonGenerator;
import com.android.build.gradle.tasks.GenerateBuildConfig;
import com.android.build.gradle.tasks.MergeSourceSetFolders;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.android.build.gradle.tasks.RenderscriptCompile;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.BootClasspathBuilder;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.VariantType;
import com.android.builder.dexing.DexMergerTool;
import com.android.builder.dexing.DexerTool;
import com.android.builder.dexing.DexingType;
import com.android.builder.errors.EvalIssueException;
import com.android.builder.errors.EvalIssueReporter.Type;
import com.android.builder.model.BaseConfig;
import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.LoggerProgressIndicatorWrapper;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.android.utils.StringHelper;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.JavaVersion;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.SelfResolvingDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.compile.JavaCompile;

/**
 * A scope containing data for a specific variant.
 */
public class VariantScopeImpl extends GenericVariantScopeImpl implements VariantScope {

    private static final ILogger LOGGER = LoggerWrapper.getLogger(VariantScopeImpl.class);

    @NonNull private final PublishingSpecs.VariantSpec variantPublishingSpec;

    @NonNull private final GlobalScope globalScope;
    @NonNull private final BaseVariantData variantData;
    @NonNull private final TransformManager transformManager;
    @Nullable private Collection<Object> ndkBuildable;
    @Nullable private Collection<File> ndkSoFolder;
    @Nullable private File ndkObjFolder;
    @NonNull private final Map<Abi, File> ndkDebuggableLibraryFolders = Maps.newHashMap();

    @Nullable private File mergeResourceOutputDir;

    // Tasks
    private DefaultTask assembleTask;
    private DefaultTask preBuildTask;

    private Task sourceGenTask;
    private Task resourceGenTask;
    private Task assetGenTask;
    private CheckManifest checkManifestTask;

    private RenderscriptCompile renderscriptCompileTask;
    private AidlCompile aidlCompileTask;
    @Nullable private MergeSourceSetFolders mergeAssetsTask;
    private GenerateBuildConfig generateBuildConfigTask;

    private Sync processJavaResourcesTask;
    private TransformTask mergeJavaResourcesTask;

    @Nullable private JavaCompile javacTask;

    // empty anchor compile task to set all compilations tasks as dependents.
    private Task compileTask;

    @Nullable private DefaultTask connectedTask;

    private GenerateApkDataTask microApkTask;

    @Nullable private ExternalNativeBuildTask externalNativeBuild;

    @Nullable private ExternalNativeJsonGenerator externalNativeJsonGenerator;

    @Nullable private CodeShrinker defaultCodeShrinker;

    @NonNull private BuildArtifactsHolder buildArtifactsHolder;

    /**
     * This is an instance of {@link JacocoReportTask} in android test variants, an umbrella {@link
     * Task} in app and lib variants and null in unit test variants.
     */
    private Task coverageReportTask;

    private File resourceOutputDir;

    private InstantRunTaskManager instantRunTaskManager;

    private ConfigurableFileCollection desugarTryWithResourcesRuntimeJar;
    private DataBindingExportBuildInfoTask dataBindingExportBuildInfoTask;

    public VariantScopeImpl(
            @NonNull GlobalScope globalScope,
            @NonNull TransformManager transformManager,
            @NonNull BaseVariantData variantData) {
        this.globalScope = globalScope;
        this.transformManager = transformManager;
        this.variantData = variantData;
        this.variantPublishingSpec = PublishingSpecs.getVariantSpec(variantData.getType());
        ProjectOptions projectOptions = globalScope.getProjectOptions();
        this.instantRunBuildContext =
                new InstantRunBuildContext(
                        variantData.getVariantConfiguration().isInstantRunBuild(globalScope),
                        AaptGeneration.fromProjectOptions(projectOptions),
                        DeploymentDevice.getDeploymentDeviceAndroidVersion(projectOptions),
                        projectOptions.get(StringOption.IDE_BUILD_TARGET_ABI),
                        projectOptions.get(StringOption.IDE_BUILD_TARGET_DENSITY),
                        projectOptions.get(BooleanOption.ENABLE_SEPARATE_APK_RESOURCES));
        this.buildArtifactsHolder =
                new VariantBuildArtifactsHolder(
                        getProject(),
                        getFullVariantName(),
                        globalScope.getBuildDir(),
                        globalScope.getDslScope());

        validatePostprocessingOptions();
    }

    private void validatePostprocessingOptions() {
        PostprocessingOptions postprocessingOptions = getPostprocessingOptionsIfUsed();
        if (postprocessingOptions == null) {
            return;
        }

        if (postprocessingOptions.getCodeShrinkerEnum() == ANDROID_GRADLE) {
            if (postprocessingOptions.isObfuscate()) {
                globalScope
                        .getErrorHandler()
                        .reportError(
                                Type.GENERIC,
                                new EvalIssueException(
                                        "The 'android-gradle' code shrinker does not support obfuscating."));
            }

            if (postprocessingOptions.isOptimizeCode()) {
                globalScope
                        .getErrorHandler()
                        .reportError(
                                Type.GENERIC,
                                new EvalIssueException(
                                        "The 'android-gradle' code shrinker does not support optimizing code."));
            }
        }
    }

    @Override
    protected Project getProject() {
        return globalScope.getProject();
    }

    @Override
    @NonNull
    public PublishingSpecs.VariantSpec getPublishingSpec() {
        return variantPublishingSpec;
    }

    @Override
    public ConfigurableFileCollection addTaskOutput(
            @NonNull com.android.build.api.artifact.ArtifactType outputType,
            @NonNull Object file,
            @Nullable String taskName) {
        ConfigurableFileCollection fileCollection;
        try {
            fileCollection = super.addTaskOutput(outputType, file, taskName);
        } catch (TaskOutputAlreadyRegisteredException e) {
            throw new RuntimeException(
                    String.format(
                            "OutputType '%s' already registered for variant '%s'",
                            e.getOutputType(), this.getFullVariantName()),
                    e);
        }

        if (file instanceof File) {
            OutputSpec taskSpec = variantPublishingSpec.getSpec(outputType);
            if (taskSpec != null) {
                Preconditions.checkNotNull(taskName);
                publishIntermediateArtifact(
                        file,
                        taskName,
                        taskSpec.getArtifactType(),
                        taskSpec.getPublishedConfigTypes());
            }
        }
        return fileCollection;
    }

    /**
     * Temporary override to handle artifacts still published in the old TaskOutputHolder and those
     * published in the new BuildableArtifactHolder.
     */
    @Override
    public boolean hasOutput(@NonNull com.android.build.api.artifact.ArtifactType outputType) {
        return super.hasOutput(outputType) || buildArtifactsHolder.hasArtifact(outputType);
    }

    /**
     * Temporary override to handle artifacts still published in the old TaskOutputHolder and those
     * published in the new BuildableArtifactHolder.
     */
    @NonNull
    @Override
    public FileCollection getOutput(@NonNull com.android.build.api.artifact.ArtifactType outputType)
            throws MissingTaskOutputException {
        try {
            return super.getOutput(outputType);
        } catch (MissingTaskOutputException e) {
            if (getArtifacts().hasArtifact(outputType)) {
                return getArtifacts().getFinalArtifactFiles(outputType).get();
            }
            throw new RuntimeException(
                    String.format(
                            "Variant '%1$s' in project '%2$s' has no output with type '%3$s'",
                            this.getFullVariantName(),
                            this.getProject().getPath(),
                            e.getOutputType()),
                    e);
        }
    }

    /**
     * Publish an intermediate artifact.
     *
     * @param artifact BuildableArtifact to be published. Must not be an appendable
     *     BuildableArtifact.
     * @param artifactType the artifact type.
     * @param configTypes the PublishedConfigType. (e.g. api, runtime, etc)
     */
    @Override
    public void publishIntermediateArtifact(
            @NonNull BuildableArtifact artifact,
            @NonNull ArtifactType artifactType,
            @NonNull Collection<PublishedConfigType> configTypes) {
        // Create Provider so that the BuildableArtifact is not resolved until needed.
        Provider<File> provider =
                getProject().provider(() -> Iterables.getOnlyElement(artifact.getFiles()));
        publishIntermediateArtifact(provider, artifact, artifactType, configTypes);
    }

    /**
     * Publish an intermediate artifact.
     *
     * @deprecated inline this into the other publishIntermediateArtifact when all tasks are
     *     converted to use BuildArtifactHolder instead of TaskOutputHolder.
     * @param file file to be published, this should be either a File or a Provider<File>
     * @param builtBy the tasks that produce the file. This is evaluated as per {@link
     *     Task#dependsOn(Object...)}.
     * @param artifactType the artifact type.
     * @param configTypes the PublishedConfigType. (e.g. api, runtime, etc)
     */
    @Deprecated
    private void publishIntermediateArtifact(
            @NonNull Object file,
            @NonNull Object builtBy,
            @NonNull ArtifactType artifactType,
            @NonNull Collection<PublishedConfigType> configTypes) {
        Preconditions.checkState(!configTypes.isEmpty());

        // FIXME this needs to be parameterized based on the variant's publishing type.
        final VariantDependencies variantDependency = getVariantData().getVariantDependency();

        if (configTypes.contains(API_ELEMENTS)) {
            Preconditions.checkNotNull(
                    variantDependency.getApiElements(),
                    "Publishing to API Element with no ApiElements configuration object. VariantType: "
                            + getType());
            publishArtifactToConfiguration(
                    variantDependency.getApiElements(), file, builtBy, artifactType);
        }

        if (configTypes.contains(RUNTIME_ELEMENTS)) {
            Preconditions.checkNotNull(
                    variantDependency.getRuntimeElements(),
                    "Publishing to Runtime Element with no RuntimeElements configuration object. VariantType: "
                            + getType());
            publishArtifactToConfiguration(
                    variantDependency.getRuntimeElements(), file, builtBy, artifactType);
        }

        if (configTypes.contains(METADATA_ELEMENTS)) {
            Preconditions.checkNotNull(
                    variantDependency.getMetadataElements(),
                    "Publishing to Metadata Element with no MetaDataElements configuration object. VariantType: "
                            + getType());
            publishArtifactToConfiguration(
                    variantDependency.getMetadataElements(), file, builtBy, artifactType);
        }

        if (configTypes.contains(BUNDLE_ELEMENTS)) {
            Preconditions.checkNotNull(
                    variantDependency.getBundleElements(),
                    "Publishing to Bundle Element with no BundleElements configuration object. VariantType: "
                            + getType());
            publishArtifactToConfiguration(
                    variantDependency.getBundleElements(), file, builtBy, artifactType);
        }

    }

    private static void publishArtifactToConfiguration(
            @NonNull Configuration configuration,
            @NonNull Object file,
            @NonNull Object builtBy,
            @NonNull ArtifactType artifactType) {
        String type = artifactType.getType();
        configuration
                .getOutgoing()
                .variants(
                        (NamedDomainObjectContainer<ConfigurationVariant> variants) -> {
                            variants.create(
                                    type,
                                    (variant) ->
                                            variant.artifact(
                                                    file,
                                                    (artifact) -> {
                                                        artifact.setType(type);
                                                        artifact.builtBy(builtBy);
                                                    }));
                        });
    }

    @Override
    @NonNull
    public GlobalScope getGlobalScope() {
        return globalScope;
    }

    @Override
    @NonNull
    public BaseVariantData getVariantData() {
        return variantData;
    }

    @Override
    @NonNull
    public GradleVariantConfiguration getVariantConfiguration() {
        return variantData.getVariantConfiguration();
    }

    @NonNull
    @Override
    public String getFullVariantName() {
        return getVariantConfiguration().getFullName();
    }

    /** Returns the {@link PostprocessingOptions} if they should be used, null otherwise. */
    @Nullable
    private PostprocessingOptions getPostprocessingOptionsIfUsed() {
        CoreBuildType coreBuildType = getCoreBuildType();

        // This may not be the case with the experimental plugin.
        if (coreBuildType instanceof BuildType) {
            BuildType dslBuildType = (BuildType) coreBuildType;
            if (dslBuildType.getPostprocessingConfiguration() == POSTPROCESSING_BLOCK) {
                return dslBuildType.getPostprocessing();
            }
        }

        return null;
    }

    @NonNull
    private CoreBuildType getCoreBuildType() {
        return getVariantConfiguration().getBuildType();
    }

    @Override
    public boolean useResourceShrinker() {
        PostprocessingOptions postprocessingOptions = getPostprocessingOptionsIfUsed();

        boolean userEnabledShrinkResources;
        if (postprocessingOptions != null) {
            userEnabledShrinkResources = postprocessingOptions.isRemoveUnusedResources();
        } else {
            //noinspection deprecation - this needs to use the old DSL methods.
            userEnabledShrinkResources = getCoreBuildType().isShrinkResources();
        }

        if (!userEnabledShrinkResources) {
            return false;
        }

        if (variantData.getType().isAar()) {
            if (!getProject().getPlugins().hasPlugin("com.android.feature")) {
                globalScope
                        .getErrorHandler()
                        .reportError(
                                Type.GENERIC,
                                new EvalIssueException(
                                        "Resource shrinker cannot be used for libraries."));
            }
            return false;
        }

        if (getCodeShrinker() == null) {
            globalScope
                    .getErrorHandler()
                    .reportError(
                            Type.GENERIC,
                            new EvalIssueException(
                                    "Removing unused resources requires unused code shrinking to be turned on. See "
                                            + "http://d.android.com/r/tools/shrink-resources.html "
                                            + "for more information."));

            return false;
        }

        return true;
    }

    @Override
    public boolean isCrunchPngs() {
        // If set for this build type, respect that.
        Boolean buildTypeOverride = getVariantConfiguration().getBuildType().isCrunchPngs();
        if (buildTypeOverride != null) {
            return buildTypeOverride;
        }
        // Otherwise, if set globally, respect that.
        Boolean globalOverride =
                globalScope.getExtension().getAaptOptions().getCruncherEnabledOverride();
        if (globalOverride != null) {
            return globalOverride;
        }
        // If not overridden, use the default from the build type.
        //noinspection deprecation TODO: Remove once the global cruncher enabled flag goes away.
        return getVariantConfiguration().getBuildType().isCrunchPngsDefault();
    }

    @Nullable
    @Override
    public CodeShrinker getCodeShrinker() {
        boolean isTestComponent = getVariantConfiguration().getType().isTestComponent();

        //noinspection ConstantConditions - getType() will not return null for a testing variant.
        if (isTestComponent && getTestedVariantData().getType().isAar()) {
            // For now we seem to include the production library code as both program and library
            // input to the test ProGuard run, which confuses it.
            return null;
        }

        PostprocessingOptions postprocessingOptions = getPostprocessingOptionsIfUsed();

        if (postprocessingOptions == null) { // Old DSL used:
            CoreBuildType coreBuildType = getCoreBuildType();
            //noinspection deprecation - this needs to use the old DSL methods.
            if (!coreBuildType.isMinifyEnabled()) {
                return null;
            }

            CodeShrinker shrinkerForBuildType;

            //noinspection deprecation - this needs to use the old DSL methods.
            Boolean useProguard = coreBuildType.isUseProguard();
            if (globalScope.getProjectOptions().get(ENABLE_R8)) {
                shrinkerForBuildType = R8;
            } else if (useProguard == null) {
                shrinkerForBuildType = getDefaultCodeShrinker();
            } else {
                shrinkerForBuildType = useProguard ? PROGUARD : ANDROID_GRADLE;
            }

            if (!isTestComponent) {
                return shrinkerForBuildType;
            } else {
                if (shrinkerForBuildType == PROGUARD || shrinkerForBuildType == R8) {
                    // ProGuard or R8 is used for main app code and we don't know if it gets
                    // obfuscated, so we need to run the same tool on test code just in case.
                    return shrinkerForBuildType;
                } else {
                    return null;
                }
            }
        } else { // New DSL used:
            CodeShrinker chosenShrinker = postprocessingOptions.getCodeShrinkerEnum();
            if (globalScope.getProjectOptions().get(ENABLE_R8)) {
                chosenShrinker = R8;
            }
            if (chosenShrinker == null) {
                chosenShrinker = getDefaultCodeShrinker();
            }

            switch (chosenShrinker) {
                case R8:
                    // fall through
                case PROGUARD:
                    if (!isTestComponent) {
                        boolean somethingToDo =
                                postprocessingOptions.isRemoveUnusedCode()
                                        || postprocessingOptions.isObfuscate()
                                        || postprocessingOptions.isOptimizeCode();
                        return somethingToDo ? chosenShrinker : null;
                    } else {
                        // For testing code, we only run ProGuard/R8 if main code is obfuscated.
                        return postprocessingOptions.isObfuscate() ? chosenShrinker : null;
                    }
                case ANDROID_GRADLE:
                    if (isTestComponent) {
                        return null;
                    } else {
                        return postprocessingOptions.isRemoveUnusedCode() ? ANDROID_GRADLE : null;
                    }
                default:
                    throw new AssertionError("Unknown value " + chosenShrinker);
            }
        }
    }

    @NonNull
    @Override
    public List<File> getProguardFiles() {
        List<File> result =
                gatherProguardFiles(
                        PostprocessingOptions::getProguardFiles, BaseConfig::getProguardFiles);

        if (getPostprocessingOptionsIfUsed() == null) {
            // For backwards compatibility, we keep the old behavior: if there are no files
            // specified, use a default one.
            if (result.isEmpty()) {
                result.add(
                        ProguardFiles.getDefaultProguardFile(
                                ProguardFiles.ProguardFile.DONT_OPTIMIZE.fileName, getProject()));
            }
        }

        return result;
    }

    @NonNull
    @Override
    public List<File> getTestProguardFiles() {
        return gatherProguardFiles(
                PostprocessingOptions::getTestProguardFiles, BaseConfig::getTestProguardFiles);
    }

    @NonNull
    @Override
    public List<File> getConsumerProguardFiles() {
        return gatherProguardFiles(
                PostprocessingOptions::getConsumerProguardFiles,
                BaseConfig::getConsumerProguardFiles);
    }

    @NonNull
    private List<File> gatherProguardFiles(
            @NonNull Function<PostprocessingOptions, List<File>> postprocessingGetter,
            @NonNull Function<BaseConfig, Collection<File>> baseConfigGetter) {
        GradleVariantConfiguration variantConfiguration = getVariantConfiguration();

        List<File> result = new ArrayList<>();
        result.addAll(baseConfigGetter.apply(variantConfiguration.getDefaultConfig()));

        PostprocessingOptions postprocessingOptions = getPostprocessingOptionsIfUsed();
        if (postprocessingOptions == null) {
            result.addAll(baseConfigGetter.apply(variantConfiguration.getBuildType()));
        } else {
            result.addAll(postprocessingGetter.apply(postprocessingOptions));
        }

        for (CoreProductFlavor flavor : variantConfiguration.getProductFlavors()) {
            result.addAll(baseConfigGetter.apply(flavor));
        }

        return result;
    }

    @Override
    @Nullable
    public PostprocessingFeatures getPostprocessingFeatures() {
        // If the new DSL block is not used, all these flags need to be in the config files.
        PostprocessingOptions postprocessingOptions = getPostprocessingOptionsIfUsed();
        if (postprocessingOptions != null) {
            return new PostprocessingFeatures(
                    postprocessingOptions.isRemoveUnusedCode(),
                    postprocessingOptions.isObfuscate(),
                    postprocessingOptions.isOptimizeCode());
        } else {
            return null;
        }
    }

    @NonNull
    private CodeShrinker getDefaultCodeShrinker() {
        if (defaultCodeShrinker == null) {
            if (getInstantRunBuildContext().isInInstantRunMode()) {
                String message = "Using the built-in class shrinker for an Instant Run build.";
                PostprocessingFeatures postprocessingFeatures = getPostprocessingFeatures();
                if (postprocessingFeatures == null || postprocessingFeatures.isObfuscate()) {
                    message += " Build won't be obfuscated.";
                }
                LOGGER.warning(message);

                defaultCodeShrinker = ANDROID_GRADLE;
            } else {
                defaultCodeShrinker = PROGUARD;
            }
        }

        return defaultCodeShrinker;
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
     */
    @Override
    public boolean isTestOnly() {
        ProjectOptions projectOptions = globalScope.getProjectOptions();
        Boolean isTestOnlyOverride = projectOptions.get(OptionalBooleanOption.IDE_TEST_ONLY);

        if (isTestOnlyOverride != null) {
            return isTestOnlyOverride;
        }

        return !Strings.isNullOrEmpty(projectOptions.get(StringOption.IDE_BUILD_TARGET_ABI))
                || !Strings.isNullOrEmpty(projectOptions.get(StringOption.IDE_BUILD_TARGET_DENSITY))
                || projectOptions.get(IntegerOption.IDE_TARGET_DEVICE_API) != null
                || globalScope.getAndroidBuilder().isPreviewTarget()
                || getMinSdkVersion().getCodename() != null
                || getVariantConfiguration().getTargetSdkVersion().getCodename() != null;
    }

    @NonNull
    @Override
    public VariantType getType() {
        return variantData.getVariantConfiguration().getType();
    }

    @NonNull
    @Override
    public DexingType getDexingType() {
        if (getInstantRunBuildContext().isInInstantRunMode()) {
            return DexingType.NATIVE_MULTIDEX;
        } else {
            return variantData.getVariantConfiguration().getDexingType();
        }
    }

    @NonNull
    @Override
    public AndroidVersion getMinSdkVersion() {
        return getVariantConfiguration().getMinSdkVersion();
    }

    @NonNull
    @Override
    public String getDirName() {
        return variantData.getVariantConfiguration().getDirName();
    }

    @NonNull
    @Override
    public Collection<String> getDirectorySegments() {
        return variantData.getVariantConfiguration().getDirectorySegments();
    }

    @NonNull
    @Override
    public TransformManager getTransformManager() {
        return transformManager;
    }

    @Override
    @NonNull
    public String getTaskName(@NonNull String prefix) {
        return getTaskName(prefix, "");
    }

    @Override
    @NonNull
    public String getTaskName(@NonNull String prefix, @NonNull String suffix) {
        return variantData.getTaskName(prefix, suffix);
    }

    @Override
    @Nullable
    public Collection<Object> getNdkBuildable() {
        return ndkBuildable;
    }

    @Override
    public void setNdkBuildable(@NonNull Collection<Object> ndkBuildable) {
        this.ndkBuildable = ndkBuildable;
    }

    @Override
    @Nullable
    public Collection<File> getNdkSoFolder() {
        return ndkSoFolder;
    }

    @Override
    public void setNdkSoFolder(@NonNull Collection<File> ndkSoFolder) {
        this.ndkSoFolder = ndkSoFolder;
    }

    /**
     * Return the folder containing the shared object with debugging symbol for the specified ABI.
     */
    @Override
    @Nullable
    public File getNdkDebuggableLibraryFolders(@NonNull Abi abi) {
        return ndkDebuggableLibraryFolders.get(abi);
    }

    @Override
    public void addNdkDebuggableLibraryFolders(@NonNull Abi abi, @NonNull File searchPath) {
        this.ndkDebuggableLibraryFolders.put(abi, searchPath);
    }

    @Override
    @Nullable
    public BaseVariantData getTestedVariantData() {
        return variantData instanceof TestVariantData ?
                (BaseVariantData) ((TestVariantData) variantData).getTestedVariantData() :
                null;
    }

    @NonNull
    @Override
    public File getBuildInfoOutputFolder() {
        return new File(globalScope.getIntermediatesDir(), "/build-info/" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getReloadDexOutputFolder() {
        return new File(globalScope.getIntermediatesDir(), "/reload-dex/" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getRestartDexOutputFolder() {
        return new File(globalScope.getIntermediatesDir(), "/restart-dex/" + getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getInstantRunSplitApkOutputFolder() {
        return new File(globalScope.getIntermediatesDir(), "/split-apk/" + getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getDefaultInstantRunApkLocation() {
        return FileUtils.join(globalScope.getIntermediatesDir(), "instant-run-apk");
    }

    @NonNull
    @Override
    public File getInstantRunPastIterationsFolder() {
        return new File(globalScope.getIntermediatesDir(), "/builds/" + getVariantConfiguration().getDirName());
    }

    // Precomputed file paths.

    @Override
    @NonNull
    public FileCollection getJavaClasspath(
            @NonNull ConsumedConfigType configType, @NonNull ArtifactType classesType) {
        return getJavaClasspath(configType, classesType, null);
    }

    @Override
    @NonNull
    public FileCollection getJavaClasspath(
            @NonNull ConsumedConfigType configType,
            @NonNull ArtifactType classesType,
            @Nullable Object generatedBytecodeKey) {
        FileCollection mainCollection = getArtifactFileCollection(configType, ALL, classesType);

        mainCollection =
                mainCollection.plus(getVariantData().getGeneratedBytecode(generatedBytecodeKey));

        if (Boolean.TRUE.equals(globalScope.getExtension().getAaptOptions().getNamespaced())) {
            mainCollection =
                    mainCollection.plus(
                            getOutput(InternalArtifactType.COMPILE_ONLY_NAMESPACED_R_CLASS_JAR));
            mainCollection =
                    mainCollection.plus(
                            getArtifactFileCollection(
                                    configType, ALL, COMPILE_ONLY_NAMESPACED_R_CLASS_JAR));
            mainCollection =
                    mainCollection.plus(getArtifactFileCollection(configType, ALL, SHARED_CLASSES));
            BaseVariantData tested = getTestedVariantData();
            if (tested != null) {
                mainCollection =
                        mainCollection.plus(
                                tested.getScope()
                                        .getOutput(
                                                InternalArtifactType
                                                        .COMPILE_ONLY_NAMESPACED_R_CLASS_JAR));
            }
        } else {
            if (buildArtifactsHolder.hasArtifact(
                    InternalArtifactType.COMPILE_ONLY_NOT_NAMESPACED_R_CLASS_JAR)) {
                BuildableArtifact rJar =
                        buildArtifactsHolder.getFinalArtifactFiles(
                                InternalArtifactType.COMPILE_ONLY_NOT_NAMESPACED_R_CLASS_JAR);
                mainCollection = mainCollection.plus(rJar.get());
            }
            BaseVariantData tested = getTestedVariantData();
            if (tested != null
                    && tested.getScope()
                            .getArtifacts()
                            .hasArtifact(
                                    InternalArtifactType.COMPILE_ONLY_NOT_NAMESPACED_R_CLASS_JAR)) {
                BuildableArtifact rJar =
                        tested.getScope()
                                .getArtifacts()
                                .getFinalArtifactFiles(
                                        InternalArtifactType
                                                .COMPILE_ONLY_NOT_NAMESPACED_R_CLASS_JAR);
                mainCollection = mainCollection.plus(rJar.get());
            }
        }

        return mainCollection;
    }

    @NonNull
    @Override
    public ArtifactCollection getJavaClasspathArtifacts(
            @NonNull ConsumedConfigType configType,
            @NonNull ArtifactType classesType,
            @Nullable Object generatedBytecodeKey) {
        ArtifactCollection mainCollection = getArtifactCollection(configType, ALL, classesType);

        return ArtifactCollectionWithExtraArtifact.makeExtraCollection(
                mainCollection,
                getVariantData().getGeneratedBytecode(generatedBytecodeKey),
                getProject().getPath());
    }

    @Override
    public boolean keepDefaultBootstrap() {
        // javac 1.8 may generate code that uses class not available in android.jar.  This is fine
        // if desugar is used to compile code for the app or compile task is created only
        // for unit test. In those cases, we want to keep the default bootstrap classpath.
        if (!JavaVersion.current().isJava8Compatible()) {
            return false;
        }

        VariantScope.Java8LangSupport java8LangSupport = getJava8LangSupportType();

        // only if target and source is explicitly specified to 1.8 (and above), we keep the
        // default bootclasspath with Desugar. Otherwise, we use android.jar.
        return java8LangSupport == VariantScope.Java8LangSupport.DESUGAR
                || java8LangSupport == VariantScope.Java8LangSupport.D8
                || java8LangSupport == VariantScope.Java8LangSupport.R8;
    }

    @NonNull
    @Override
    public File getManifestCheckerDir() {
        return new File(
                globalScope.getIntermediatesDir(),
                "/manifest-checker/" + variantData.getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getInstantRunMainApkResourcesDir() {
        return new File(
                globalScope.getIntermediatesDir(),
                "/instant-run-main-apk-res/" + variantData.getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getIncrementalRuntimeSupportJar() {
        return new File(globalScope.getIntermediatesDir(), "/incremental-runtime-classes/" +
                variantData.getVariantConfiguration().getDirName() + "/instant-run.jar");
    }

    @Override
    @NonNull
    public File getIncrementalApplicationSupportDir() {
        return new File(globalScope.getIntermediatesDir(), "/incremental-classes/" +
                variantData.getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getInstantRunResourcesFile() {
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                "instant-run-resources",
                "resources-" + variantData.getVariantConfiguration().getDirName() + ".ir.ap_");
    }

    @Override
    @NonNull
    public File getIncrementalVerifierDir() {
        return new File(globalScope.getIntermediatesDir(), "/incremental-verifier/" +
                variantData.getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public BuildArtifactsHolder getArtifacts() {
        return buildArtifactsHolder;
    }

    @Override
    @NonNull
    public FileCollection getArtifactFileCollection(
            @NonNull ConsumedConfigType configType,
            @NonNull ArtifactScope scope,
            @NonNull ArtifactType artifactType) {
        ArtifactCollection artifacts = computeArtifactCollection(configType, scope, artifactType);

        FileCollection fileCollection;

        final VariantType type = getVariantConfiguration().getType();
        if (configType == RUNTIME_CLASSPATH
                && type.isFeatureSplit()
                && artifactType != ArtifactType.FEATURE_TRANSITIVE_DEPS) {
            fileCollection =
                    new FilteredArtifactCollection(
                                    globalScope.getProject(),
                                    artifacts,
                                    computeArtifactCollection(
                                                    RUNTIME_CLASSPATH,
                                                    MODULE,
                                                    ArtifactType.FEATURE_TRANSITIVE_DEPS)
                                            .getArtifactFiles())
                            .getArtifactFiles();
        } else {
            fileCollection = artifacts.getArtifactFiles();
        }

        if (configType.needsTestedComponents()) {
            return handleTestedComponent(
                    fileCollection,
                    configType,
                    scope,
                    artifactType,
                    (mainCollection, testedCollection, unused) ->
                            mainCollection.plus(testedCollection),
                    (collection, artifactCollection) ->
                            collection.minus(artifactCollection.getArtifactFiles()),
                    (collection, artifactCollection) -> {
                        throw new RuntimeException(
                                "Can't do smart subtraction on a file collection");
                    });
        }

        return fileCollection;
    }

    @Override
    @NonNull
    public ArtifactCollection getArtifactCollection(
            @NonNull ConsumedConfigType configType,
            @NonNull ArtifactScope scope,
            @NonNull ArtifactType artifactType) {
        ArtifactCollection artifacts = computeArtifactCollection(configType, scope, artifactType);

        final VariantType type = getVariantConfiguration().getType();
        if (configType == RUNTIME_CLASSPATH
                && type.isFeatureSplit()
                && artifactType != ArtifactType.FEATURE_TRANSITIVE_DEPS) {
            artifacts =
                    new FilteredArtifactCollection(
                            globalScope.getProject(),
                            artifacts,
                            computeArtifactCollection(
                                            RUNTIME_CLASSPATH,
                                            MODULE,
                                            ArtifactType.FEATURE_TRANSITIVE_DEPS)
                                    .getArtifactFiles());
        }

        if (configType.needsTestedComponents()) {
            return handleTestedComponent(
                    artifacts,
                    configType,
                    scope,
                    artifactType,
                    (artifactResults, collection, variantName) ->
                            ArtifactCollectionWithExtraArtifact.makeExtraCollectionForTest(
                                    artifactResults,
                                    collection,
                                    getProject().getPath(),
                                    variantName),
                    SubtractingArtifactCollection::new,
                    (testArtifact, testedArtifact) -> {
                        return new AndroidTestResourceArtifactCollection(
                                testArtifact,
                                getVariantData()
                                        .getVariantDependency()
                                        .getIncomingRuntimeDependencies(),
                                getVariantData()
                                        .getVariantDependency()
                                        .getRuntimeClasspath()
                                        .getIncoming());
                    });
        }

        return artifacts;
    }

    @NonNull
    private Configuration getConfiguration(@NonNull ConsumedConfigType configType) {
        switch (configType) {
            case COMPILE_CLASSPATH:
                return getVariantData().getVariantDependency().getCompileClasspath();
            case RUNTIME_CLASSPATH:
                return getVariantData().getVariantDependency().getRuntimeClasspath();
            case ANNOTATION_PROCESSOR:
                return getVariantData()
                        .getVariantDependency()
                        .getAnnotationProcessorConfiguration();
            case METADATA_VALUES:
                return Preconditions.checkNotNull(
                        getVariantData().getVariantDependency().getMetadataValuesConfiguration());
            default:
                throw new RuntimeException("unknown ConfigType value " + configType);
        }
    }

    @NonNull
    private ArtifactCollection computeArtifactCollection(
            @NonNull ConsumedConfigType configType,
            @NonNull ArtifactScope scope,
            @NonNull ArtifactType artifactType) {

        Configuration configuration = getConfiguration(configType);

        Action<AttributeContainer> attributes =
                container -> container.attribute(ARTIFACT_TYPE, artifactType.getType());

        Spec<ComponentIdentifier> filter = getComponentFilter(scope);

        boolean lenientMode =
                Boolean.TRUE.equals(
                        globalScope.getProjectOptions().get(BooleanOption.IDE_BUILD_MODEL_ONLY));

        return configuration
                .getIncoming()
                .artifactView(
                        config -> {
                            config.attributes(attributes);
                            if (filter != null) {
                                config.componentFilter(filter);
                            }
                            // TODO somehow read the unresolved dependencies?
                            config.lenient(lenientMode);
                        })
                .getArtifacts();
    }

    @Nullable
    private static Spec<ComponentIdentifier> getComponentFilter(
            @NonNull AndroidArtifacts.ArtifactScope scope) {
        switch (scope) {
            case ALL:
                return null;
            case EXTERNAL:
                // since we want both Module dependencies and file based dependencies in this case
                // the best thing to do is search for non ProjectComponentIdentifier.
                return id -> !(id instanceof ProjectComponentIdentifier);
            case MODULE:
                return id -> id instanceof ProjectComponentIdentifier;
            default:
                throw new RuntimeException("unknown ArtifactScope value");
        }
    }

    /**
     * Returns the packaged local Jars
     *
     * @return a non null, but possibly empty set.
     */
    @NonNull
    @Override
    public FileCollection getLocalPackagedJars() {
        Configuration configuration = getVariantData().getVariantDependency().getRuntimeClasspath();

        // Get a list of local Jars dependencies.
        Callable<Collection<SelfResolvingDependency>> dependencies =
                () ->
                        configuration
                                .getAllDependencies()
                                .stream()
                                .filter((it) -> it instanceof SelfResolvingDependency)
                                .filter((it) -> !(it instanceof ProjectDependency))
                                .map((it) -> (SelfResolvingDependency) it)
                                .collect(ImmutableList.toImmutableList());

        // Create a file collection builtBy the dependencies.  The files are resolved later.
        return getGlobalScope()
                .getProject()
                .files(
                        TaskInputHelper.bypassFileCallable(
                                () -> {
                                    try {
                                        return dependencies
                                                .call()
                                                .stream()
                                                .flatMap((it) -> it.resolve().stream())
                                                .collect(Collectors.toList());
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                }))
                .builtBy(dependencies);
    }

    @NonNull
    @Override
    public FileCollection getProvidedOnlyClasspath() {
        FileCollection compile = getArtifactFileCollection(COMPILE_CLASSPATH, ALL, CLASSES);
        FileCollection pkg = getArtifactFileCollection(RUNTIME_CLASSPATH, ALL, CLASSES);
        return compile.minus(pkg);
    }

    /**
     * An intermediate directory for this variant.
     *
     * <p>Of the form build/intermediates/dirName/variant/
     */
    @NonNull
    private File intermediate(@NonNull String directoryName) {
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                directoryName,
                getVariantConfiguration().getDirName());
    }

    /**
     * An intermediate file for this variant.
     *
     * <p>Of the form build/intermediates/directoryName/variant/filename
     */
    @NonNull
    private File intermediate(@NonNull String directoryName, @NonNull String fileName) {
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                directoryName,
                getVariantConfiguration().getDirName(),
                fileName);
    }

    @Override
    @NonNull
    public File getIntermediateJarOutputFolder() {
        return new File(globalScope.getIntermediatesDir(), "/intermediate-jars/" +
                variantData.getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getProguardComponentsJarFile() {
        return new File(globalScope.getIntermediatesDir(), "multi-dex/" + getVariantConfiguration().getDirName()
                + "/componentClasses.jar");
    }

    @Override
    @NonNull
    public File getManifestKeepListProguardFile() {
        return new File(globalScope.getIntermediatesDir(), "multi-dex/" + getVariantConfiguration().getDirName()
                + "/manifest_keep.txt");
    }

    @Override
    @NonNull
    public File getMainDexListFile() {
        return new File(globalScope.getIntermediatesDir(), "multi-dex/" + getVariantConfiguration().getDirName()
                + "/maindexlist.txt");
    }

    @Override
    @NonNull
    public File getRenderscriptSourceOutputDir() {
        return new File(globalScope.getGeneratedDir(),
                "source/rs/" + variantData.getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getRenderscriptLibOutputDir() {
        return new File(globalScope.getIntermediatesDir(),
                "rs/" + variantData.getVariantConfiguration().getDirName() + "/lib");
    }

    @Override
    public void setResourceOutputDir(@NonNull File resourceOutputDir) {
        this.resourceOutputDir = resourceOutputDir;
    }

    @Override
    @NonNull
    public File getDefaultMergeResourcesOutputDir() {
        return FileUtils.join(
                getGlobalScope().getIntermediatesDir(),
                FD_RES,
                FD_MERGED,
                getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getCompiledResourcesOutputDir() {
        return FileUtils.join(
                getGlobalScope().getIntermediatesDir(),
                FD_RES,
                FD_COMPILED,
                getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getResourceBlameLogDir() {
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                StringHelper.toStrings(
                        "blame", "res", getDirectorySegments()));
    }

    @NonNull
    @Override
    public File getMergeNativeLibsOutputDir() {
        return FileUtils.join(globalScope.getIntermediatesDir(),
                "/jniLibs/" + getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getMergeShadersOutputDir() {
        return FileUtils.join(globalScope.getIntermediatesDir(),
                "/shaders/" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getBuildConfigSourceOutputDir() {
        return new File(globalScope.getBuildDir() + "/"  + FD_GENERATED + "/source/buildConfig/"
                + variantData.getVariantConfiguration().getDirName());
    }

    @NonNull
    private File getGeneratedResourcesDir(String name) {
        return FileUtils.join(
                globalScope.getGeneratedDir(),
                StringHelper.toStrings(
                        "res",
                        name,
                        getDirectorySegments()));
    }

    @NonNull
    @Override
    public File getGeneratedAssetsDir(@NonNull String name) {
        return FileUtils.join(
                globalScope.getGeneratedDir(),
                StringHelper.toStrings(
                        "assets",
                        name,
                        getDirectorySegments()));
    }

    @Override
    @NonNull
    public File getGeneratedResOutputDir() {
        return getGeneratedResourcesDir("resValues");
    }

    @Override
    @NonNull
    public File getGeneratedPngsOutputDir() {
        return getGeneratedResourcesDir("pngs");
    }

    @Override
    @NonNull
    public File getRenderscriptResOutputDir() {
        return getGeneratedResourcesDir("rs");
    }

    @NonNull
    @Override
    public File getRenderscriptObjOutputDir() {
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                StringHelper.toStrings(
                        "rs",
                        getDirectorySegments(),
                        "obj"));
    }

    @Override
    @NonNull
    public File getSourceFoldersJavaResDestinationDir() {
        return new File(globalScope.getIntermediatesDir(),
                "sourceFolderJavaResources/" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getAidlSourceOutputDir() {
        return new File(globalScope.getGeneratedDir(),
                "source/aidl/" + getVariantConfiguration().getDirName());
    }

    @Override
    @NonNull
    public File getIncrementalDir(String name) {
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                "incremental",
                name);
    }

    @NonNull
    @Override
    public File getAarClassesJar() {
        return intermediate("packaged-classes", FN_CLASSES_JAR);
    }

    @NonNull
    @Override
    public File getAarLibsDirectory() {
        return intermediate("packaged-classes", SdkConstants.LIBS_FOLDER);
    }

    @NonNull
    @Override
    public File getCoverageReportDir() {
        return new File(globalScope.getReportsDir(), "coverage/" + getDirName());
    }

    @Override
    @NonNull
    public File getClassOutputForDataBinding() {
        return new File(
                globalScope.getGeneratedDir(),
                "source/dataBinding/trigger/" + getVariantConfiguration().getDirName());
    }


    @Override
    @NonNull
    public File getLayoutInfoOutputForDataBinding() {
        return dataBindingIntermediate("layout-info");
    }

    @Override
    @NonNull
    public File getBuildFolderForDataBindingCompiler() {
        return dataBindingIntermediate("compiler");
    }

    @Override
    @NonNull
    public File getGeneratedClassListOutputFileForDataBinding() {
        return new File(dataBindingIntermediate("class-list"), "_generated.txt");
    }

    @NonNull
    @Override
    public File getBundleArtifactFolderForDataBinding() {
        return dataBindingIntermediate("bundle-bin");
    }

    private File dataBindingIntermediate(String name) {
        return intermediate("data-binding", name);
    }

    @Override
    @NonNull
    public File getProcessAndroidResourcesProguardOutputFile() {
        return new File(globalScope.getIntermediatesDir(),
                "/proguard-rules/" + getVariantConfiguration().getDirName() + "/aapt_rules.txt");
    }

    @Override
    @NonNull
    public File getGenerateSplitAbiResOutputDirectory() {
        return new File(
                globalScope.getIntermediatesDir(),
                FileUtils.join("splits", "res", "abi", getVariantConfiguration().getDirName()));
    }

    @Override
    @NonNull
    public File getSplitSupportDirectory() {
        return new File(
                globalScope.getIntermediatesDir(),
                "splits-support/" + getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getSplitDensityOrLanguagesPackagesOutputDirectory() {
        return new File(
                globalScope.getBuildDir(),
                FileUtils.join(
                        FD_OUTPUTS,
                        "splits",
                        "densityLanguage",
                        getVariantConfiguration().getDirName()));
    }

    @NonNull
    @Override
    public File getSplitAbiPackagesOutputDirectory() {
        return new File(
                globalScope.getBuildDir(),
                FileUtils.join(
                        FD_OUTPUTS, "splits", "abi", getVariantConfiguration().getDirName()));
    }

    @NonNull
    @Override
    public File getFullApkPackagesOutputDirectory() {
        return new File(
                globalScope.getBuildDir(),
                FileUtils.join(
                        FD_OUTPUTS, "splits", "full", getVariantConfiguration().getDirName()));
    }

    @NonNull
    @Override
    public File getInstantRunResourceApkFolder() {
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                "resources",
                "instant-run",
                getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getIntermediateDir(@NonNull InternalArtifactType taskOutputType) {
        return intermediate(taskOutputType.name().toLowerCase(Locale.US));
    }

    @NonNull
    @Override
    public File getMicroApkManifestFile() {
        return FileUtils.join(
                globalScope.getGeneratedDir(),
                "manifests",
                "microapk",
                getVariantConfiguration().getDirName(),
                FN_ANDROID_MANIFEST_XML);
    }

    @NonNull
    @Override
    public File getMicroApkResDirectory() {
        return FileUtils.join(
                globalScope.getGeneratedDir(),
                "res",
                "microapk",
                getVariantConfiguration().getDirName());
    }

    @NonNull
    @Override
    public File getManifestOutputDirectory() {
        final VariantType variantType = getVariantConfiguration().getType();

        if (variantType.isTestComponent()) {
            if (variantType.isApk()) { // ANDROID_TEST
                return FileUtils.join(
                        getGlobalScope().getIntermediatesDir(),
                        "manifest",
                        getVariantConfiguration().getDirName());
            }
        } else {
            return FileUtils.join(
                    getGlobalScope().getIntermediatesDir(),
                    "manifests",
                    "full",
                    getVariantConfiguration().getDirName());
        }

        throw new RuntimeException("getManifestOutputDirectory called for an unexpected variant.");
    }

    /**
     * Obtains the location where APKs should be placed.
     *
     * @return the location for APKs
     */
    @NonNull
    @Override
    public File getApkLocation() {
        String override = globalScope.getProjectOptions().get(StringOption.IDE_APK_LOCATION);
        File defaultLocation =
                getInstantRunBuildContext().isInInstantRunMode()
                        ? getDefaultInstantRunApkLocation()
                        : getDefaultApkLocation();

        File baseDirectory =
                override != null && !variantData.getType().isHybrid()
                        ? globalScope.getProject().file(override)
                        : defaultLocation;

        return new File(baseDirectory, getVariantConfiguration().getDirName());
    }

    /**
     * Obtains the default location for APKs.
     *
     * @return the default location for APKs
     */
    @NonNull
    private File getDefaultApkLocation() {
        return FileUtils.join(globalScope.getBuildDir(), FD_OUTPUTS, "apk");
    }

    @NonNull
    @Override
    public File getAarLocation() {
        return FileUtils.join(globalScope.getOutputsDir(), BuilderConstants.EXT_LIB_ARCHIVE);
    }

    @NonNull
    @Override
    public File getAnnotationProcessorOutputDir() {
        return FileUtils.join(
                globalScope.getGeneratedDir(),
                "source",
                "apt",
                getVariantConfiguration().getDirName());
    }

    // Tasks getters/setters.

    @Override
    public DefaultTask getAssembleTask() {
        return assembleTask;
    }

    @Override
    public void setAssembleTask(@NonNull DefaultTask assembleTask) {
        this.assembleTask = assembleTask;
    }

    @Override
    public DefaultTask getPreBuildTask() {
        return preBuildTask;
    }

    @Override
    public void setPreBuildTask(DefaultTask preBuildTask) {
        this.preBuildTask = preBuildTask;
    }

    @Override
    public Task getSourceGenTask() {
        return sourceGenTask;
    }

    @Override
    public void setSourceGenTask(Task sourceGenTask) {
        this.sourceGenTask = sourceGenTask;
    }

    @Override
    public Task getResourceGenTask() {
        return resourceGenTask;
    }

    @Override
    public void setResourceGenTask(Task resourceGenTask) {
        this.resourceGenTask = resourceGenTask;
    }

    @Override
    public Task getAssetGenTask() {
        return assetGenTask;
    }

    @Override
    public void setAssetGenTask(Task assetGenTask) {
        this.assetGenTask = assetGenTask;
    }

    @Override
    public CheckManifest getCheckManifestTask() {
        return checkManifestTask;
    }

    @Override
    public void setCheckManifestTask(CheckManifest checkManifestTask) {
        this.checkManifestTask = checkManifestTask;
    }

    @Override
    public RenderscriptCompile getRenderscriptCompileTask() {
        return renderscriptCompileTask;
    }

    @Override
    public void setRenderscriptCompileTask(RenderscriptCompile renderscriptCompileTask) {
        this.renderscriptCompileTask = renderscriptCompileTask;
    }

    @Override
    public AidlCompile getAidlCompileTask() {
        return aidlCompileTask;
    }

    @Override
    public void setAidlCompileTask(AidlCompile aidlCompileTask) {
        this.aidlCompileTask = aidlCompileTask;
    }

    @Override
    @Nullable
    public MergeSourceSetFolders getMergeAssetsTask() {
        return mergeAssetsTask;
    }

    @Override
    public void setMergeAssetsTask(@Nullable MergeSourceSetFolders mergeAssetsTask) {
        this.mergeAssetsTask = mergeAssetsTask;
    }

    @Override
    public GenerateBuildConfig getGenerateBuildConfigTask() {
        return generateBuildConfigTask;
    }

    @Override
    public void setGenerateBuildConfigTask(GenerateBuildConfig generateBuildConfigTask) {
        this.generateBuildConfigTask = generateBuildConfigTask;
    }

    @Override
    public Sync getProcessJavaResourcesTask() {
        return processJavaResourcesTask;
    }

    @Override
    public void setProcessJavaResourcesTask(Sync processJavaResourcesTask) {
        this.processJavaResourcesTask = processJavaResourcesTask;
    }

    @Override
    public void setMergeJavaResourcesTask(TransformTask mergeJavaResourcesTask) {
        this.mergeJavaResourcesTask = mergeJavaResourcesTask;
    }

    @Override
    @Nullable
    public JavaCompile getJavacTask() {
        return javacTask;
    }

    @Override
    public void setJavacTask(@Nullable JavaCompile javacTask) {
        this.javacTask = javacTask;
    }

    @Override
    public Task getCompileTask() {
        return compileTask;
    }

    @Override
    public void setCompileTask(Task compileTask) {
        this.compileTask = compileTask;
    }

    @Override
    @Nullable
    public DefaultTask getConnectedTask() {
        return this.connectedTask;
    }

    @Override
    public void setConnectedTask(DefaultTask connectedTask) {
        this.connectedTask = connectedTask;
    }

    @Override
    public GenerateApkDataTask getMicroApkTask() {
        return microApkTask;
    }

    @Override
    public void setMicroApkTask(GenerateApkDataTask microApkTask) {
        this.microApkTask = microApkTask;
    }

    @Override
    public Task getCoverageReportTask() {
        return coverageReportTask;
    }

    @Override
    public void setCoverageReportTask(Task coverageReportTask) {
        this.coverageReportTask = coverageReportTask;
    }

    @NonNull private final InstantRunBuildContext instantRunBuildContext;

    @Override
    @NonNull
    public InstantRunBuildContext getInstantRunBuildContext() {
        return instantRunBuildContext;
    }

    @NonNull
    @Override
    public ImmutableList<File> getInstantRunBootClasspath() {
        SdkHandler sdkHandler = getGlobalScope().getSdkHandler();
        AndroidBuilder androidBuilder = globalScope.getAndroidBuilder();
        IAndroidTarget androidBuilderTarget = androidBuilder.getTarget();

        File annotationsJar = sdkHandler.getSdkLoader().getSdkInfo(LOGGER).getAnnotationsJar();

        AndroidVersion targetDeviceVersion =
                DeploymentDevice.getDeploymentDeviceAndroidVersion(
                        getGlobalScope().getProjectOptions());

        if (targetDeviceVersion.equals(androidBuilderTarget.getVersion())) {
            // Compile SDK and the target device match, re-use the target that we have already
            // found earlier.
            return BootClasspathBuilder.computeFullBootClasspath(
                    androidBuilderTarget, annotationsJar);
        }

        IAndroidTarget targetToUse =
                getAndroidTarget(
                        sdkHandler, AndroidTargetHash.getPlatformHashString(targetDeviceVersion));

        if (targetToUse == null) {
            // The device platform is not installed, Studio should have done this already, so fail.
            throw new RuntimeException(
                    String.format(
                            ""
                                    + "In order to use Instant Run with this device running %1$S, "
                                    + "you must install platform %1$S in your SDK",
                            targetDeviceVersion.toString()));
        }

        return BootClasspathBuilder.computeFullBootClasspath(targetToUse, annotationsJar);
    }

    /**
     * Calls the sdklib machinery to construct the {@link IAndroidTarget} for the given hash string.
     *
     * @return appropriate {@link IAndroidTarget} or null if the matching platform package is not
     *         installed.
     */
    @Nullable
    private static IAndroidTarget getAndroidTarget(
            @NonNull SdkHandler sdkHandler,
            @NonNull String targetHash) {
        File sdkLocation = sdkHandler.getSdkFolder();
        ProgressIndicator progressIndicator = new LoggerProgressIndicatorWrapper(LOGGER);
        IAndroidTarget target = AndroidSdkHandler.getInstance(sdkLocation)
                .getAndroidTargetManager(progressIndicator)
                .getTargetFromHashString(targetHash, progressIndicator);
        if (target != null) {
            return target;
        }
        // reset the cached AndroidSdkHandler, next time a target is looked up,
        // this will force the re-parsing of the SDK.
        AndroidSdkHandler.resetInstance(sdkLocation);

        // and let's try immediately, it's possible the platform was installed since the SDK
        // handler was initialized in the this VM, since we reset the instance just above, it's
        // possible we find it.
        return AndroidSdkHandler.getInstance(sdkLocation)
                .getAndroidTargetManager(progressIndicator)
                .getTargetFromHashString(targetHash, progressIndicator);
    }

    @Override
    public void setExternalNativeBuildTask(@NonNull ExternalNativeBuildTask task) {
        this.externalNativeBuild = task;
    }

    @Nullable
    @Override
    public ExternalNativeJsonGenerator getExternalNativeJsonGenerator() {
        return externalNativeJsonGenerator;
    }

    @Override
    public void setExternalNativeJsonGenerator(@NonNull ExternalNativeJsonGenerator generator) {
        Preconditions.checkState(this.externalNativeJsonGenerator == null,
                "Unexpected overwrite of externalNativeJsonGenerator "
                        + "may result in information loss");
        this.externalNativeJsonGenerator = generator;
    }

    @Nullable
    @Override
    public ExternalNativeBuildTask getExternalNativeBuildTask() {
        return externalNativeBuild;
    }

    @Nullable
    @Override
    public InstantRunTaskManager getInstantRunTaskManager() {
        return instantRunTaskManager;
    }

    @Override
    public void setInstantRunTaskManager(InstantRunTaskManager instantRunTaskManager) {
        this.instantRunTaskManager = instantRunTaskManager;
    }

    @NonNull
    @Override
    public TransformVariantScope getTransformVariantScope() {
        return this;
    }

    @FunctionalInterface
    public interface TriFunction<T, U, V, R> {
        R apply(T t, U u, V v);
    }

    /**
     * adds or removes the tested artifact and dependencies to ensure the test build is correct.
     *
     * @param <T> the type of the collection
     * @param collection the collection to add or remove the artifact and dependencies.
     * @param configType the configuration from which to look at dependencies
     * @param artifactType the type of the artifact to add or remove
     * @param plusFunction a function that adds the tested artifact to the collection
     * @param minusFunction a function that removes the tested dependencies from the collection
     * @param resourceMinusFunction a function that keeps only the test resources in the collection
     * @return a new collection containing the result
     */
    @NonNull
    private <T> T handleTestedComponent(
            @NonNull final T collection,
            @NonNull final ConsumedConfigType configType,
            @NonNull final ArtifactScope artifactScope,
            @NonNull final ArtifactType artifactType,
            @NonNull final TriFunction<T, FileCollection, String, T> plusFunction,
            @NonNull final BiFunction<T, ArtifactCollection, T> minusFunction,
            @NonNull final BiFunction<T, ArtifactCollection, T> resourceMinusFunction) {
        // this only handles Android Test, not unit tests.
        VariantType variantType = getVariantConfiguration().getType();
        if (!variantType.isTestComponent()) {
            return collection;
        }

        T result = collection;

        // get the matching file collection for the tested variant, if any.
        if (variantData instanceof TestVariantData) {
            TestedVariantData tested = ((TestVariantData) variantData).getTestedVariantData();
            final VariantScope testedScope = tested.getScope();

            // we only add the tested component to the MODULE | ALL scopes.
            if (artifactScope == ArtifactScope.MODULE || artifactScope == ALL) {
                VariantSpec testedSpec =
                        testedScope.getPublishingSpec().getTestingSpec(variantType);

                // get the OutputPublishingSpec from the ArtifactType for this particular variant spec
                OutputSpec taskOutputSpec = testedSpec.getSpec(artifactType);

                if (taskOutputSpec != null) {
                    Collection<PublishedConfigType> publishedConfigs =
                            taskOutputSpec.getPublishedConfigTypes();

                    // check that we are querying for a config type that the tested artifact
                    // was published to.
                    if (publishedConfigs.contains(configType.getPublishedTo())) {
                        // if it's the case then we add the tested artifact.
                        final com.android.build.api.artifact.ArtifactType taskOutputType =
                                taskOutputSpec.getOutputType();
                        if (testedScope.hasOutput(taskOutputType)) {
                            result =
                                    plusFunction.apply(
                                            result,
                                            testedScope.getOutput(taskOutputType),
                                            testedScope.getFullVariantName());
                        }
                    }
                }
            }

            // We remove the transitive dependencies coming from the
            // tested app to avoid having the same artifact on each app and tested app.
            // This applies only to the package scope since we do want these in the compile
            // scope in order to compile.
            // We only do this for the AndroidTest.
            // We do have to however keep the Android resources.
            if (tested instanceof ApplicationVariantData
                    && configType == RUNTIME_CLASSPATH
                    && variantType.isTestComponent()
                    && variantType.isApk()) {
                if (artifactType == ArtifactType.ANDROID_RES) {
                    result =
                            resourceMinusFunction.apply(
                                    result,
                                    testedScope.getArtifactCollection(
                                            configType, artifactScope, artifactType));
                } else {
                    result =
                            minusFunction.apply(
                                    result,
                                    testedScope.getArtifactCollection(
                                            configType, artifactScope, artifactType));
                }
            }
        }

        return result;
    }

    @NonNull
    @Override
    public File getProcessResourcePackageOutputDirectory() {
        return FileUtils.join(getGlobalScope().getIntermediatesDir(), FD_RES, getDirName());
    }

    ProcessAndroidResources processAndroidResourcesTask;

    @Override
    public void setProcessResourcesTask(
            ProcessAndroidResources processAndroidResourcesAndroidTask) {
        this.processAndroidResourcesTask = processAndroidResourcesAndroidTask;
    }

    @Override
    public ProcessAndroidResources getProcessResourcesTask() {
        return processAndroidResourcesTask;
    }

    @Override
    public void setDataBindingExportBuildInfoTask(DataBindingExportBuildInfoTask task) {
        this.dataBindingExportBuildInfoTask = task;
    }

    @Override
    public DataBindingExportBuildInfoTask getDataBindingExportBuildInfoTask() {
        return dataBindingExportBuildInfoTask;
    }

    @Override
    @NonNull
    public OutputScope getOutputScope() {
        return variantData.getOutputScope();
    }

    @NonNull
    @Override
    public VariantDependencies getVariantDependencies() {
        return variantData.getVariantDependency();
    }

    @NonNull
    @Override
    public Java8LangSupport getJava8LangSupportType() {
        // in order of precedence
        if (!getGlobalScope()
                .getExtension()
                .getCompileOptions()
                .getTargetCompatibility()
                .isJava8Compatible()) {
            return Java8LangSupport.UNUSED;
        }

        if (globalScope.getProject().getPlugins().hasPlugin("me.tatarka.retrolambda")) {
            return Java8LangSupport.RETROLAMBDA;
        }

        CodeShrinker shrinker = getCodeShrinker();
        if (shrinker == R8) {
            if (globalScope.getProjectOptions().get(ENABLE_R8_DESUGARING)
                    && isValidJava8Flag(ENABLE_R8_DESUGARING, ENABLE_R8, ENABLE_DEX_ARCHIVE)) {
                return Java8LangSupport.R8;
            }
        } else {
            // D8 cannot be used if R8 is used
            if (globalScope.getProjectOptions().get(ENABLE_D8_DESUGARING)
                    && isValidJava8Flag(ENABLE_D8_DESUGARING, ENABLE_D8, ENABLE_DEX_ARCHIVE)) {
                return Java8LangSupport.D8;
            }
        }

        if (globalScope.getProjectOptions().get(BooleanOption.ENABLE_DESUGAR)) {
            return Java8LangSupport.DESUGAR;
        }

        BooleanOption missingFlag = shrinker == R8 ? ENABLE_R8_DESUGARING : ENABLE_D8_DESUGARING;
        globalScope
                .getErrorHandler()
                .reportError(
                        Type.GENERIC,
                        new EvalIssueException(
                                String.format(
                                        "Please add '%s=true' to your "
                                                + "gradle.properties file to enable Java 8 "
                                                + "language support.",
                                        missingFlag.name()),
                                getVariantConfiguration().getFullName()));
        return Java8LangSupport.INVALID;
    }

    private boolean isValidJava8Flag(
            @NonNull BooleanOption flag, @NonNull BooleanOption... dependsOn) {
        List<String> invalid = null;
        for (BooleanOption requiredFlag : dependsOn) {
            if (!globalScope.getProjectOptions().get(requiredFlag)) {
                if (invalid == null) {
                    invalid = Lists.newArrayList();
                }
                invalid.add("'" + requiredFlag.getPropertyName() + "= false'");
            }
        }

        if (invalid == null) {
            return true;
        } else {
            String template =
                    "Java 8 language support, as requested by '%s= true' in your "
                            + "gradle.properties file, is not supported when %s.";
            String msg =
                    String.format(
                            template,
                            flag.getPropertyName(),
                            invalid.stream().collect(Collectors.joining(",")));
            globalScope
                    .getErrorHandler()
                    .reportError(
                            Type.GENERIC,
                            new EvalIssueException(msg, getVariantConfiguration().getFullName()));
            return false;
        }
    }

    @NonNull
    @Override
    public ConfigurableFileCollection getTryWithResourceRuntimeSupportJar() {
        if (desugarTryWithResourcesRuntimeJar == null) {
            desugarTryWithResourcesRuntimeJar =
                    getProject()
                            .files(
                                    FileUtils.join(
                                            globalScope.getIntermediatesDir(),
                                            "processing-tools",
                                            "runtime-deps",
                                            variantData.getVariantConfiguration().getDirName(),
                                            "desugar_try_with_resources.jar"));
        }
        return desugarTryWithResourcesRuntimeJar;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).addValue(getFullVariantName()).toString();
    }

    @NonNull
    @Override
    public DexerTool getDexer() {
        if (globalScope.getProjectOptions().get(BooleanOption.ENABLE_D8)) {
            return DexerTool.D8;
        } else {
            return DexerTool.DX;
        }
    }

    @NonNull
    @Override
    public DexMergerTool getDexMerger() {
        if (globalScope.getProjectOptions().get(BooleanOption.ENABLE_D8)) {
            return DexMergerTool.D8;
        } else {
            return DexMergerTool.DX;
        }
    }

    @NonNull
    @Override
    public File getOutputProguardMappingFile() {
        return FileUtils.join(
                globalScope.getBuildDir(),
                FD_OUTPUTS,
                "mapping",
                "r8",
                getVariantConfiguration().getDirName(),
                "mapping.txt");
    }
}
