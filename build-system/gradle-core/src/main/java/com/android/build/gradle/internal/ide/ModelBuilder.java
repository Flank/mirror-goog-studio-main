/*
 * Copyright (C) 2012 The Android Open Source Project
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

import static com.android.builder.model.AndroidProject.ARTIFACT_MAIN;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.build.VariantOutput;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.TestAndroidConfig;
import com.android.build.gradle.internal.BuildTypeData;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.ProductFlavorData;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.VariantManager;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.dsl.CoreNdkOptions;
import com.android.build.gradle.internal.ide.DependenciesConverter.DependenciesImpl;
import com.android.build.gradle.internal.ide.level2.GlobalLibraryMapImpl;
import com.android.build.gradle.internal.incremental.BuildInfoWriterTask;
import com.android.build.gradle.internal.model.NativeLibraryFactory;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.scope.SplitFactory;
import com.android.build.gradle.internal.scope.SplitScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.internal.variant.TestedVariantData;
import com.android.builder.Version;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantType;
import com.android.builder.model.AaptOptions;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.ArtifactMetaData;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.JavaArtifact;
import com.android.builder.model.LintOptions;
import com.android.builder.model.NativeLibrary;
import com.android.builder.model.NativeToolchain;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SigningConfig;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.SourceProviderContainer;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.TestedTargetVariant;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.GlobalLibraryMap;
import com.android.ide.common.build.Split;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

/**
 * Builder for the custom Android model.
 */
public class ModelBuilder implements ToolingModelBuilder {

    @NonNull
    private final AndroidBuilder androidBuilder;
    @NonNull
    private final AndroidConfig config;
    @NonNull
    private final ExtraModelInfo extraModelInfo;
    @NonNull
    private final VariantManager variantManager;
    @NonNull
    private final TaskManager taskManager;
    @NonNull
    private final NdkHandler ndkHandler;
    @NonNull
    private Map<Abi, NativeToolchain> toolchains;
    @NonNull
    private NativeLibraryFactory nativeLibFactory;
    private final int projectType;
    private final int generation;
    private int modelLevel = AndroidProject.MODEL_LEVEL_0_ORIGINAL;
    private boolean modelWithFullDependency = false;

    public ModelBuilder(
            @NonNull AndroidBuilder androidBuilder,
            @NonNull VariantManager variantManager,
            @NonNull TaskManager taskManager,
            @NonNull AndroidConfig config,
            @NonNull ExtraModelInfo extraModelInfo,
            @NonNull NdkHandler ndkHandler,
            @NonNull NativeLibraryFactory nativeLibraryFactory,
            int projectType,
            int generation) {
        this.androidBuilder = androidBuilder;
        this.config = config;
        this.extraModelInfo = extraModelInfo;
        this.variantManager = variantManager;
        this.taskManager = taskManager;
        this.ndkHandler = ndkHandler;
        this.nativeLibFactory = nativeLibraryFactory;
        this.projectType = projectType;
        this.generation = generation;
    }

    public static void clearCaches() {
        ArtifactDependencyGraph.clearCaches();
    }

    @Override
    public boolean canBuild(String modelName) {
        // The default name for a model is the name of the Java interface.
        return modelName.equals(AndroidProject.class.getName()) || modelName.equals(
                GlobalLibraryMap.class.getName());
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        if (modelName.equals(AndroidProject.class.getName())) {
            return buildAndroidProject(project);
        }

        return buildGlobalLibraryMap(project);
    }

    private static Object buildGlobalLibraryMap(Project project) {
        return new GlobalLibraryMapImpl(ArtifactDependencyGraph.getGlobalLibMap());
    }

    private Object buildAndroidProject(Project project) {
        Integer modelLevelInt = AndroidGradleOptions.buildModelOnlyVersion(project);
        if (modelLevelInt != null) {
            modelLevel = modelLevelInt;
        }
        modelWithFullDependency = AndroidGradleOptions.buildModelWithFullDependencies(project);

        // FIXME: support model with full dependency
        Preconditions.checkState(
                !modelWithFullDependency, "modelWithFullDependency is not yet implemented");

        // Get the boot classpath. This will ensure the target is configured.
        List<String> bootClasspath = androidBuilder.getBootClasspathAsStrings(false);

        List<File> frameworkSource = Collections.emptyList();

        // List of extra artifacts, with all test variants added.
        List<ArtifactMetaData> artifactMetaDataList = Lists.newArrayList(
                extraModelInfo.getExtraArtifacts());

        for (VariantType variantType : VariantType.getTestingTypes()) {
            artifactMetaDataList.add(new ArtifactMetaDataImpl(
                    variantType.getArtifactName(),
                    true /*isTest*/,
                    variantType.getArtifactType()));
        }

        LintOptions lintOptions = com.android.build.gradle.internal.dsl.LintOptions.create(
                config.getLintOptions());

        AaptOptions aaptOptions = AaptOptionsImpl.create(config.getAaptOptions());

        List<SyncIssue> syncIssues = Lists.newArrayList(extraModelInfo.getSyncIssues().values());

        List<String> flavorDimensionList = config.getFlavorDimensionList() != null ?
                config.getFlavorDimensionList() : Lists.newArrayList();

        toolchains = createNativeToolchainModelMap(ndkHandler);

        ProductFlavorContainer defaultConfig = ProductFlavorContainerImpl
                .createProductFlavorContainer(
                        variantManager.getDefaultConfig(),
                        extraModelInfo.getExtraFlavorSourceProviders(
                                variantManager.getDefaultConfig().getProductFlavor().getName()));

        Collection<BuildTypeContainer> buildTypes = Lists.newArrayList();
        Collection<ProductFlavorContainer> productFlavors = Lists.newArrayList();
        Collection<Variant> variants = Lists.newArrayList();


        for (BuildTypeData btData : variantManager.getBuildTypes().values()) {
            buildTypes.add(BuildTypeContainerImpl.create(
                    btData,
                    extraModelInfo.getExtraBuildTypeSourceProviders(btData.getBuildType().getName())));
        }
        for (ProductFlavorData pfData : variantManager.getProductFlavors().values()) {
            productFlavors.add(ProductFlavorContainerImpl.createProductFlavorContainer(
                    pfData,
                    extraModelInfo.getExtraFlavorSourceProviders(pfData.getProductFlavor().getName())));
        }

        for (BaseVariantData<? extends BaseVariantOutputData> variantData : variantManager.getVariantDataList()) {
            if (!variantData.getType().isForTesting()) {
                variants.add(createVariant(variantData));
            }
        }

        return new DefaultAndroidProject(
                Version.ANDROID_GRADLE_PLUGIN_VERSION,
                project.getName(),
                defaultConfig,
                flavorDimensionList,
                buildTypes,
                productFlavors,
                variants,
                androidBuilder.getTarget() != null ? androidBuilder.getTarget().hashString() : "",
                bootClasspath,
                frameworkSource,
                cloneSigningConfigs(config.getSigningConfigs()),
                aaptOptions,
                artifactMetaDataList,
                findUnresolvedDependencies(syncIssues),
                syncIssues,
                config.getCompileOptions(),
                lintOptions,
                project.getBuildDir(),
                config.getResourcePrefix(),
                ImmutableList.copyOf(toolchains.values()),
                config.getBuildToolsVersion(),
                projectType,
                Version.BUILDER_MODEL_API_VERSION,
                generation);
    }

    /**
     * Create a map of ABI to NativeToolchain
     */
    public static Map<Abi, NativeToolchain> createNativeToolchainModelMap(
            @NonNull NdkHandler ndkHandler) {
        if (!ndkHandler.isConfigured()) {
            return ImmutableMap.of();
        }

        Map<Abi, NativeToolchain> toolchains = Maps.newHashMap();

        for (Abi abi : ndkHandler.getSupportedAbis()) {
            toolchains.put(
                    abi,
                    new NativeToolchainImpl(
                            ndkHandler.getToolchain().getName() + "-" + abi.getName(),
                            ndkHandler.getCCompiler(abi),
                            ndkHandler.getCppCompiler(abi)));
        }
        return toolchains;
    }

    @NonNull
    private VariantImpl createVariant(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData) {
        AndroidArtifact mainArtifact = createAndroidArtifact(ARTIFACT_MAIN, variantData);

        GradleVariantConfiguration variantConfiguration = variantData.getVariantConfiguration();

        String variantName = variantConfiguration.getFullName();

        List<AndroidArtifact> extraAndroidArtifacts = Lists.newArrayList(
                extraModelInfo.getExtraAndroidArtifacts(variantName));
        // Make sure all extra artifacts are serializable.
        List<JavaArtifact> clonedExtraJavaArtifacts =
                extraModelInfo.getExtraJavaArtifacts(variantName).stream()
                        .map(JavaArtifactImpl::clone)
                        .collect(Collectors.toList());

        if (variantData instanceof TestedVariantData) {
            for (VariantType variantType : VariantType.getTestingTypes()) {
                TestVariantData testVariantData = ((TestedVariantData) variantData).getTestVariantData(variantType);
                if (testVariantData != null) {
                    VariantType type = testVariantData.getType();
                    if (type != null) {
                        switch (type) {
                            case ANDROID_TEST:
                                extraAndroidArtifacts.add(createAndroidArtifact(
                                        variantType.getArtifactName(),
                                        testVariantData));
                                break;
                            case UNIT_TEST:
                                clonedExtraJavaArtifacts.add(createUnitTestsJavaArtifact(
                                        variantType,
                                        testVariantData));
                                break;
                            default:
                                throw new IllegalArgumentException(
                                        "Unsupported test variant type ${variantType}.");
                        }
                    }
                }
            }
        }

        // if the target is a codename, override the model value.
        ApiVersion sdkVersionOverride = null;

        // we know the getTargetInfo won't return null here.
        @SuppressWarnings("ConstantConditions")
        IAndroidTarget androidTarget = androidBuilder.getTargetInfo().getTarget();

        AndroidVersion version = androidTarget.getVersion();
        if (version.getCodename() != null) {
            sdkVersionOverride = ApiVersionImpl.clone(version);
        }

        // used for test only modules
        Collection<TestedTargetVariant> testTargetVariants = getTestTargetVariants();

        return new VariantImpl(
                variantName,
                variantConfiguration.getBaseName(),
                variantConfiguration.getBuildType().getName(),
                getProductFlavorNames(variantData),
                new ProductFlavorImpl(
                        variantConfiguration.getMergedFlavor(),
                        sdkVersionOverride,
                        sdkVersionOverride),
                mainArtifact,
                extraAndroidArtifacts,
                clonedExtraJavaArtifacts,
                testTargetVariants);
    }

    @NonNull
    private Collection<TestedTargetVariant> getTestTargetVariants() {
        if (config instanceof TestAndroidConfig) {
            TestAndroidConfig testConfig = (TestAndroidConfig) config;
            return ImmutableList.of(
                    new TestedTargetVariantImpl(
                            testConfig.getTargetProjectPath(), testConfig.getTargetVariant()));
        } else {
            return ImmutableList.of();
        }
    }

    private JavaArtifactImpl createUnitTestsJavaArtifact(
            @NonNull VariantType variantType,
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData) {
        SourceProviders sourceProviders = determineSourceProviders(variantData);

        List<File> extraGeneratedSourceFolders = variantData.getExtraGeneratedSourceFolders();

        DependenciesImpl dependencies;
        DependencyGraphs dependencyGraphs;

        if (modelLevel == AndroidProject.MODEL_LEVEL_2_DONT_USE) {
            dependencies = DependenciesConverter.getEmpty();

            dependencyGraphs =
                    ArtifactDependencyGraph.createLevel2DependencyGraph(variantData.getScope());
        } else {
            dependencies = ArtifactDependencyGraph.createDependencies(variantData.getScope());

            dependencyGraphs = DependenciesLevel2Converter.getEmpty();
        }

        return new JavaArtifactImpl(
                variantType.getArtifactName(),
                variantData.getScope().getAssembleTask().getName(),
                variantData.getScope().getCompileTask().getName(),
                Sets.newHashSet(taskManager.createMockableJar.getName()),
                extraGeneratedSourceFolders != null
                        ? extraGeneratedSourceFolders
                        : Collections.emptyList(),
                (variantData.javacTask != null)
                        ? variantData.javacTask.getDestinationDir()
                        : variantData.getScope().getJavaOutputDir(),
                variantData.getJavaResourcesForUnitTesting(),
                taskManager.getGlobalScope().getMockableAndroidJarFile(),
                dependencies,
                dependencyGraphs,
                sourceProviders.variantSourceProvider,
                sourceProviders.multiFlavorSourceProvider);
    }

    /**
     * Create a NativeLibrary for each ABI.
     */
    private Collection<NativeLibrary> createNativeLibraries(
            @NonNull Collection<Abi> abis,
            @NonNull VariantScope scope) {
        Collection<NativeLibrary> nativeLibraries = Lists.newArrayListWithCapacity(abis.size());
        for (Abi abi : abis) {
            NativeToolchain toolchain = toolchains.get(abi);
            if (toolchain == null) {
                continue;
            }
            Optional<NativeLibrary> lib = nativeLibFactory.create(scope, toolchain.getName(), abi);
            if (lib.isPresent()) {
                nativeLibraries.add(lib.get());
            }
        }
        return nativeLibraries;
    }

    private static final class ConfigurationSplitCreator implements SplitScope.SplitCreator {

        private final String baseName;
        private final String fullName;
        private final String dirName;

        ConfigurationSplitCreator(String baseName, String fullName, String dirName) {
            this.baseName = baseName;
            this.fullName = fullName;
            this.dirName = dirName;
        }

        @Nullable
        @Override
        public Split create(
                @NonNull VariantOutput.OutputType outputType,
                @NonNull Collection<FilterData> filters) {
            String filterName = SplitFactory.getFilterNameForSplits(filters);
            return new SplitFactory.DefaultSplit(
                    outputType,
                    filterName,
                    baseName,
                    fullName,
                    dirName,
                    ImmutableList.copyOf(filters));
        }
    }


    private AndroidArtifact createAndroidArtifact(
            @NonNull String name,
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData) {
        VariantScope scope = variantData.getScope();
        GradleVariantConfiguration variantConfiguration = variantData.getVariantConfiguration();

        SigningConfig signingConfig = variantConfiguration.getSigningConfig();
        String signingConfigName = null;
        if (signingConfig != null) {
            signingConfigName = signingConfig.getName();
        }

        SourceProviders sourceProviders = determineSourceProviders(variantData);

        ConfigurationSplitCreator splitCreator =
                new ConfigurationSplitCreator(
                        variantConfiguration.getBaseName(),
                        variantConfiguration.getFullName(),
                        variantConfiguration.getDirName());

        // get the outputs
        SerializableSupplier<Collection<SplitScope.SplitOutput>> splitOutputsProxy = null;
        SerializableSupplier<Collection<SplitScope.SplitOutput>> manifestsProxy = null;
        switch (variantData.getType()) {
            case DEFAULT:
                splitOutputsProxy =
                        new SplitOutputsSupplier(
                                variantData.getSplitScope(),
                                splitCreator,
                                ImmutableList.of(
                                        VariantScope.TaskOutputType.APK,
                                        VariantScope.TaskOutputType.ABI_PACKAGED_SPLIT,
                                        VariantScope.TaskOutputType
                                                .DENSITY_OR_LANGUAGE_PACKAGED_SPLIT),
                                ImmutableList.of(
                                        new File(
                                                variantData
                                                        .getScope()
                                                        .getGlobalScope()
                                                        .getApkLocation(),
                                                variantData
                                                        .getVariantConfiguration()
                                                        .getDirName())));

                manifestsProxy =
                        new SplitOutputsSupplier(
                                variantData.getSplitScope(),
                                splitCreator,
                                ImmutableList.of(VariantScope.TaskOutputType.MERGED_MANIFESTS),
                                ImmutableList.of(
                                        variantData.getScope().getManifestOutputDirectory()));
                break;
            case LIBRARY:
                Split mainSplit =
                        splitCreator.create(VariantOutput.OutputType.MAIN, ImmutableList.of());
                splitOutputsProxy =
                        new SerializableSupplier.Default<>(
                                ImmutableList.of(
                                        new SplitScope.SplitOutput(
                                                VariantScope.TaskOutputType.AAR,
                                                mainSplit,
                                                scope.getOutputBundleFile())));
                manifestsProxy =
                        new SerializableSupplier.Default<>(
                                ImmutableList.of(
                                        new SplitScope.SplitOutput(
                                                VariantScope.TaskOutputType.MERGED_MANIFESTS,
                                                mainSplit,
                                                new File(
                                                        scope.getManifestOutputDirectory(),
                                                        SdkConstants.ANDROID_MANIFEST_XML))));
                break;
            case ATOM:
            case INSTANTAPP:
                splitOutputsProxy =
                        new SplitOutputsSupplier(
                                variantData.getSplitScope(),
                                splitCreator,
                                ImmutableList.of(VariantScope.TaskOutputType.APKB),
                                ImmutableList.of(
                                        new File(
                                                variantData
                                                        .getScope()
                                                        .getGlobalScope()
                                                        .getApkLocation(),
                                                variantData
                                                        .getVariantConfiguration()
                                                        .getDirName())));
                manifestsProxy =
                        new SplitOutputsSupplier(
                                variantData.getSplitScope(),
                                splitCreator,
                                ImmutableList.of(VariantScope.TaskOutputType.MERGED_MANIFESTS),
                                ImmutableList.of(
                                        variantData.getScope().getManifestOutputDirectory()));
                break;
            case ANDROID_TEST:
                splitOutputsProxy =
                        new SplitOutputsSupplier(
                                variantData.getSplitScope(),
                                splitCreator,
                                ImmutableList.of(VariantScope.TaskOutputType.APK),
                                ImmutableList.of(
                                        new File(
                                                variantData
                                                        .getScope()
                                                        .getGlobalScope()
                                                        .getApkLocation(),
                                                variantData
                                                        .getVariantConfiguration()
                                                        .getDirName())));
                manifestsProxy =
                        new SplitOutputsSupplier(
                                variantData.getSplitScope(),
                                splitCreator,
                                ImmutableList.of(VariantScope.TaskOutputType.MERGED_MANIFESTS),
                                ImmutableList.of(
                                        variantData.getScope().getManifestOutputDirectory()));
                break;
            default:
                throw new RuntimeException("Unhandled build type " + variantData.getType());
        }

        CoreNdkOptions ndkConfig = variantData.getVariantConfiguration().getNdkConfig();
        Collection<NativeLibrary> nativeLibraries = ImmutableList.of();
        if (ndkHandler.isConfigured()) {
            if (config.getSplits().getAbi().isEnable()) {
                nativeLibraries = createNativeLibraries(
                        config.getSplits().getAbi().isUniversalApk()
                                ? ndkHandler.getSupportedAbis()
                                : createAbiList(config.getSplits().getAbiFilters()),
                        scope);
            } else {
                if (ndkConfig.getAbiFilters() == null || ndkConfig.getAbiFilters().isEmpty()) {
                    nativeLibraries = createNativeLibraries(
                            ndkHandler.getSupportedAbis(),
                            scope);
                } else {
                    nativeLibraries = createNativeLibraries(
                            createAbiList(ndkConfig.getAbiFilters()),
                            scope);
                }
            }
        }

        InstantRunImpl instantRun = new InstantRunImpl(
                BuildInfoWriterTask.ConfigAction.getBuildInfoFile(scope),
                variantConfiguration.getInstantRunSupportStatus());

        VariantDependencies variantDependency = variantData.getVariantDependency();

        DependenciesImpl dependencies;
        DependencyGraphs dependencyGraphs;

        if (modelLevel == AndroidProject.MODEL_LEVEL_2_DONT_USE) {
            dependencies = DependenciesConverter.getEmpty();

            dependencyGraphs = ArtifactDependencyGraph.createLevel2DependencyGraph(scope);
        } else {
            dependencies = ArtifactDependencyGraph.createDependencies(scope);

            dependencyGraphs = DependenciesLevel2Converter.getEmpty();
        }

        return new AndroidArtifactImpl(
                name,
                variantData.assembleVariantTask == null
                        ? scope.getTaskName("assemble")
                        : variantData.assembleVariantTask.getName(),
                variantConfiguration.isSigningReady() || variantData.outputsAreSigned,
                signingConfigName,
                variantConfiguration.getApplicationId(),
                // TODO: Need to determine the tasks' name when the tasks may not be created
                // in component plugin.
                scope.getSourceGenTask() == null
                        ? scope.getTaskName("generate", "Sources")
                        : scope.getSourceGenTask().getName(),
                scope.getCompileTask() == null
                        ? scope.getTaskName("compile", "Sources")
                        : scope.getCompileTask().getName(),
                getGeneratedSourceFolders(variantData),
                getGeneratedResourceFolders(variantData),
                (variantData.javacTask != null)
                        ? variantData.javacTask.getDestinationDir()
                        : scope.getJavaOutputDir(),
                scope.getVariantData().getJavaResourcesForUnitTesting(),
                dependencies,
                dependencyGraphs,
                sourceProviders.variantSourceProvider,
                sourceProviders.multiFlavorSourceProvider,
                variantConfiguration.getSupportedAbis(),
                nativeLibraries,
                variantConfiguration.getMergedBuildConfigFields(),
                variantConfiguration.getMergedResValues(),
                instantRun,
                splitOutputsProxy,
                manifestsProxy);
    }

    private static Collection<Abi> createAbiList(Collection<String> abiNames) {
        ImmutableList.Builder<Abi> builder = ImmutableList.builder();
        for (String abiName : abiNames) {
            Abi abi = Abi.getByName(abiName);
            if (abi != null) {
                builder.add(abi);
            }
        }
        return builder.build();
    }

    private static SourceProviders determineSourceProviders(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData) {
        SourceProvider variantSourceProvider =
                variantData.getVariantConfiguration().getVariantSourceProvider();
        SourceProvider multiFlavorSourceProvider =
                variantData.getVariantConfiguration().getMultiFlavorSourceProvider();

        return new SourceProviders(
                variantSourceProvider != null ?
                        new SourceProviderImpl(variantSourceProvider) :
                        null,
                multiFlavorSourceProvider != null ?
                        new SourceProviderImpl(multiFlavorSourceProvider) :
                        null);
    }

    @NonNull
    private static List<String> getProductFlavorNames(
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData) {
        return variantData.getVariantConfiguration().getProductFlavors().stream()
                .map((Function<ProductFlavor, String>) ProductFlavor::getName)
                .collect(Collectors.toList());
    }

    @NonNull
    private static List<File> getGeneratedSourceFolders(
            @Nullable BaseVariantData<? extends BaseVariantOutputData> variantData) {
        if (variantData == null) {
            return Collections.emptyList();
        }

        List<File> extraFolders = variantData.getExtraGeneratedSourceFolders();

        List<File> folders;
        if (extraFolders != null) {
            folders = Lists.newArrayListWithExpectedSize(5 + extraFolders.size());
            folders.addAll(extraFolders);
        } else {
            folders = Lists.newArrayListWithExpectedSize(5);
        }

        VariantScope scope = variantData.getScope();

        // The R class is only generated by the first output.
        folders.add(scope.getRClassSourceOutputDir());

        folders.add(scope.getAidlSourceOutputDir());
        folders.add(scope.getBuildConfigSourceOutputDir());
        Boolean ndkMode = variantData.getVariantConfiguration().getMergedFlavor().getRenderscriptNdkModeEnabled();
        if (ndkMode == null || !ndkMode) {
            folders.add(scope.getRenderscriptSourceOutputDir());
        }
        folders.add(scope.getAnnotationProcessorOutputDir());

        return folders;
    }

    @NonNull
    private static List<File> getGeneratedResourceFolders(
            @Nullable BaseVariantData<? extends BaseVariantOutputData> variantData) {
        if (variantData == null) {
            return Collections.emptyList();
        }

        List<File> result;

        final FileCollection extraResFolders = variantData.getExtraGeneratedResFolders();
        Set<File> extraFolders = extraResFolders != null ? extraResFolders.getFiles() : null;
        if (extraFolders != null && !extraFolders.isEmpty()) {
            result = Lists.newArrayListWithCapacity(extraFolders.size() + 2);
            result.addAll(extraFolders);
        } else {
            result = Lists.newArrayListWithCapacity(2);
        }

        VariantScope scope = variantData.getScope();

        result.add(scope.getRenderscriptResOutputDir());
        result.add(scope.getGeneratedResOutputDir());

        return result;
    }

    @NonNull
    private static Collection<SigningConfig> cloneSigningConfigs(
            @NonNull Collection<? extends SigningConfig> signingConfigs) {
        return signingConfigs.stream()
                .map((Function<SigningConfig, SigningConfig>)
                        SigningConfigImpl::createSigningConfig)
                .collect(Collectors.toList());
    }

    @Nullable
    private static SourceProviderContainer getSourceProviderContainer(
            @NonNull Collection<SourceProviderContainer> items,
            @NonNull String name) {
        for (SourceProviderContainer item : items) {
            if (name.equals(item.getArtifactName())) {
                return item;
            }
        }

        return null;
    }

    private static class SourceProviders {
        protected SourceProviderImpl variantSourceProvider;
        protected SourceProviderImpl multiFlavorSourceProvider;

        public SourceProviders(
                SourceProviderImpl variantSourceProvider,
                SourceProviderImpl multiFlavorSourceProvider) {
            this.variantSourceProvider = variantSourceProvider;
            this.multiFlavorSourceProvider = multiFlavorSourceProvider;
        }
    }

    /**
     * Return the unresolved dependencies in SyncIssues
     */
    private static Collection<String> findUnresolvedDependencies(
            @NonNull Collection<SyncIssue> syncIssues) {
        return syncIssues.stream()
                .filter(issue -> issue.getType() == SyncIssue.TYPE_UNRESOLVED_DEPENDENCY)
                .map(SyncIssue::getData).collect(Collectors.toList());
    }
}
