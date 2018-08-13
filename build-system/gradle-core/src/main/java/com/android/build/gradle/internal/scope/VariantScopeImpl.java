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
import static com.android.build.gradle.internal.scope.ArtifactPublishingUtil.publishArtifactToConfiguration;
import static com.android.build.gradle.internal.scope.CodeShrinker.PROGUARD;
import static com.android.build.gradle.internal.scope.CodeShrinker.R8;
import static com.android.build.gradle.options.BooleanOption.ENABLE_D8;
import static com.android.build.gradle.options.BooleanOption.ENABLE_D8_DESUGARING;
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
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
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
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType;
import com.android.build.gradle.internal.publishing.PublishingSpecs;
import com.android.build.gradle.internal.publishing.PublishingSpecs.OutputSpec;
import com.android.build.gradle.internal.publishing.PublishingSpecs.VariantSpec;
import com.android.build.gradle.internal.tasks.databinding.DataBindingCompilerArguments;
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
import com.android.sdklib.BuildToolInfo;
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
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.SelfResolvingDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;

/**
 * A scope containing data for a specific variant.
 */
public class VariantScopeImpl extends GenericVariantScopeImpl implements VariantScope {

    private static final ILogger LOGGER = LoggerWrapper.getLogger(VariantScopeImpl.class);
    private static final String PUBLISH_ERROR_MSG =
            "Publishing to %1$s with no %1$s configuration object. VariantType: %2$s";

    @NonNull private final PublishingSpecs.VariantSpec variantPublishingSpec;

    @NonNull private final GlobalScope globalScope;
    @NonNull private final BaseVariantData variantData;
    @NonNull private final TransformManager transformManager;
    @Nullable private Collection<File> ndkSoFolder;
    @NonNull private final Map<Abi, File> ndkDebuggableLibraryFolders = Maps.newHashMap();

    @NonNull private BuildArtifactsHolder buildArtifactsHolder;

    private final MutableTaskContainer taskContainer = new MutableTaskContainer();

    private InstantRunTaskManager instantRunTaskManager;

    private ConfigurableFileCollection desugarTryWithResourcesRuntimeJar;

    @Nullable private DataBindingCompilerArguments dataBindingCompilerArguments;

    private FileCollection bootClasspath;

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
    }

    protected Project getProject() {
        return globalScope.getProject();
    }

    @Override
    @NonNull
    public PublishingSpecs.VariantSpec getPublishingSpec() {
        return variantPublishingSpec;
    }

    @NonNull
    @Override
    public MutableTaskContainer getTaskContainer() {
        return taskContainer;
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
        Provider<File> file =
                getProject().provider(() -> Iterables.getOnlyElement(artifact.getFiles()));

        Preconditions.checkState(!configTypes.isEmpty());

        // FIXME this needs to be parameterized based on the variant's publishing type.
        final VariantDependencies variantDependency = getVariantData().getVariantDependency();

        for (PublishedConfigType configType : PublishedConfigType.values()) {
            if (configTypes.contains(configType)) {
                Configuration config = variantDependency.getElements(configType);
                Preconditions.checkNotNull(
                        config, String.format(PUBLISH_ERROR_MSG, configType, getType()));
                publishArtifactToConfiguration(config, file, artifact, artifactType);
            }
        }
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
        if (variantData.getType().isForTesting()
                || getInstantRunBuildContext().isInInstantRunMode()) {
            return false;
        }

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

        // TODO: support resource shrinking for multi-apk applications http://b/78119690
        if (variantData.getType().isFeatureSplit() || globalScope.hasDynamicFeatures()) {
            globalScope
                    .getErrorHandler()
                    .reportError(
                            Type.GENERIC,
                            new EvalIssueException(
                                    "Resource shrinker cannot be used for multi-apk applications"));
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

    @Override
    public boolean consumesFeatureJars() {
        return getVariantData().getType().isBaseModule()
                && getVariantConfiguration().getBuildType().isMinifyEnabled()
                && globalScope.hasDynamicFeatures();
    }

    @Override
    public boolean getNeedsMainDexListForBundle() {
        return getVariantData().getType().isBaseModule()
                && globalScope.hasDynamicFeatures()
                && getVariantConfiguration().getDexingType().getNeedsMainDexList();
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
                shrinkerForBuildType = useProguard ? PROGUARD : R8;
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
                default:
                    throw new AssertionError("Unknown value " + chosenShrinker);
            }
        }
    }

    @NonNull
    @Override
    public List<File> getProguardFiles() {
        List<File> result = getExplicitProguardFiles();

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
    public List<File> getExplicitProguardFiles() {
        return gatherProguardFiles(
                PostprocessingOptions::getProguardFiles, BaseConfig::getProguardFiles);
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

        List<File> result =
                new ArrayList<>(baseConfigGetter.apply(variantConfiguration.getDefaultConfig()));

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
        return CodeShrinker.PROGUARD;
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

    @Override
    public boolean getNeedsMainDexList() {
        return getDexingType().getNeedsMainDexList();
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

        if (globalScope.getExtension().getAaptOptions().getNamespaced()) {
            mainCollection =
                    mainCollection.plus(
                            buildArtifactsHolder
                                    .getFinalArtifactFiles(
                                            InternalArtifactType
                                                    .COMPILE_ONLY_NAMESPACED_R_CLASS_JAR)
                                    .get());
            mainCollection =
                    mainCollection.plus(
                            getArtifactFileCollection(
                                    configType, ALL, COMPILE_ONLY_NAMESPACED_R_CLASS_JAR));
            mainCollection =
                    mainCollection.plus(getArtifactFileCollection(configType, ALL, SHARED_CLASSES));

            if (globalScope
                    .getProjectOptions()
                    .get(BooleanOption.CONVERT_NON_NAMESPACED_DEPENDENCIES)) {
                FileCollection namespacedClasses =
                        buildArtifactsHolder
                                .getFinalArtifactFiles(InternalArtifactType.NAMESPACED_CLASSES_JAR)
                                .get();
                mainCollection = mainCollection.plus(namespacedClasses);

                FileCollection namespacedRClasses =
                        buildArtifactsHolder
                                .getFinalArtifactFiles(
                                        InternalArtifactType
                                                .COMPILE_ONLY_NAMESPACED_DEPENDENCIES_R_JAR)
                                .get();
                mainCollection = mainCollection.plus(namespacedRClasses);
            }

            BaseVariantData tested = getTestedVariantData();
            if (tested != null) {
                mainCollection =
                        mainCollection.plus(
                                tested.getScope()
                                        .getArtifacts()
                                        .getFinalArtifactFiles(
                                                InternalArtifactType
                                                        .COMPILE_ONLY_NAMESPACED_R_CLASS_JAR)
                                        .get());
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

    @NonNull
    @Override
    public File getIncrementalRuntimeSupportJar() {
        return new File(globalScope.getIntermediatesDir(), "/incremental-runtime-classes/" +
                variantData.getVariantConfiguration().getDirName() + "/instant-run.jar");
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
        return getArtifactFileCollection(configType, scope, artifactType, null);
    }

    @Override
    @NonNull
    public FileCollection getArtifactFileCollection(
            @NonNull ConsumedConfigType configType,
            @NonNull ArtifactScope scope,
            @NonNull ArtifactType artifactType,
            @Nullable Map<Attribute<String>, String> attributeMap) {
        ArtifactCollection artifacts =
                computeArtifactCollection(configType, scope, artifactType, attributeMap);

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
                                                    ArtifactType.FEATURE_TRANSITIVE_DEPS,
                                                    attributeMap)
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
                    (testArtifact, testedArtifact) ->
                            new AndroidTestResourceArtifactCollection(
                                    testArtifact,
                                    getVariantData()
                                            .getVariantDependency()
                                            .getIncomingRuntimeDependencies(),
                                    getVariantData()
                                            .getVariantDependency()
                                            .getRuntimeClasspath()
                                            .getIncoming()));
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
        return computeArtifactCollection(configType, scope, artifactType, null);
    }

    @NonNull
    private ArtifactCollection computeArtifactCollection(
            @NonNull ConsumedConfigType configType,
            @NonNull ArtifactScope scope,
            @NonNull ArtifactType artifactType,
            @Nullable Map<Attribute<String>, String> attributeMap) {

        Configuration configuration = getConfiguration(configType);

        Action<AttributeContainer> attributes =
                container -> {
                    container.attribute(ARTIFACT_TYPE, artifactType.getType());
                    if (attributeMap != null) {
                        for (Attribute<String> attribute : attributeMap.keySet()) {
                            container.attribute(attribute, attributeMap.get(attribute));
                        }
                    }
                };

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
                        (Callable<Collection<File>>)
                                () ->
                                        dependencies
                                                .call()
                                                .stream()
                                                .flatMap((it) -> it.resolve().stream())
                                                .collect(Collectors.toList()))
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
    public File getRenderscriptLibOutputDir() {
        return new File(globalScope.getIntermediatesDir(),
                "rs/" + variantData.getVariantConfiguration().getDirName() + "/lib");
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
        return FileUtils.join(
                globalScope.getIntermediatesDir(),
                "proguard-rules",
                getVariantConfiguration().getDirName(),
                SdkConstants.FN_AAPT_RULES);
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
                        if (testedScope.getArtifacts().hasArtifact(taskOutputType)) {
                            result =
                                    plusFunction.apply(
                                            result,
                                            testedScope
                                                    .getArtifacts()
                                                    .getFinalArtifactFiles(taskOutputType)
                                                    .get(),
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

    @Override
    public void setDataBindingCompilerArguments(
            @NonNull DataBindingCompilerArguments dataBindingCompilerArguments) {
        this.dataBindingCompilerArguments = dataBindingCompilerArguments;
    }

    @Override
    @Nullable
    public DataBindingCompilerArguments getDataBindingCompilerArguments() {
        return dataBindingCompilerArguments;
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
                    && isValidJava8Flag(ENABLE_R8_DESUGARING, ENABLE_R8)) {
                return Java8LangSupport.R8;
            }
        } else {
            // D8 cannot be used if R8 is used
            if (globalScope.getProjectOptions().get(ENABLE_D8_DESUGARING)
                    && isValidJava8Flag(ENABLE_D8_DESUGARING, ENABLE_D8)) {
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

    @NonNull
    @Override
    public FileCollection getBootClasspath() {
        if (bootClasspath != null) {
            return bootClasspath;
        }

        File coreLambdaStubsJar =
                new File(
                        globalScope
                                .getAndroidBuilder()
                                .getBuildToolInfo()
                                .getPath(BuildToolInfo.PathId.CORE_LAMBDA_STUBS));
        bootClasspath =
                getProject()
                        .files(
                                globalScope.getAndroidBuilder().getBootClasspath(false),
                                coreLambdaStubsJar);

        return bootClasspath;
    }
}
