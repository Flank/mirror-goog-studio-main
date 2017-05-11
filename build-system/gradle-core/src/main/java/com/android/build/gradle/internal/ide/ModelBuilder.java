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
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_FEATURE;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
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
import com.android.build.gradle.internal.dsl.CoreNdkOptions;
import com.android.build.gradle.internal.ide.level2.EmptyDependencyGraphs;
import com.android.build.gradle.internal.ide.level2.GlobalLibraryMapImpl;
import com.android.build.gradle.internal.incremental.BuildInfoWriterTask;
import com.android.build.gradle.internal.model.NativeLibraryFactory;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.TaskContainer;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.internal.variant.TestedVariantData;
import com.android.builder.Version;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantType;
import com.android.builder.model.AaptOptions;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ArtifactMetaData;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaArtifact;
import com.android.builder.model.LintOptions;
import com.android.builder.model.NativeLibrary;
import com.android.builder.model.NativeToolchain;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.SigningConfig;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.SourceProviderContainer;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.TestVariantBuildOutput;
import com.android.builder.model.TestedTargetVariant;
import com.android.builder.model.Variant;
import com.android.builder.model.VariantBuildOutput;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.GlobalLibraryMap;
import com.android.ide.common.build.ApkInfo;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
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
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

/**
 * Builder for the custom Android model.
 */
public class ModelBuilder implements ToolingModelBuilder {

    @NonNull
    static final DependenciesImpl EMPTY_DEPENDENCIES_IMPL =
            new DependenciesImpl(ImmutableList.of(), ImmutableList.of(), ImmutableList.of());

    @NonNull static final DependencyGraphs EMPTY_DEPENDENCY_GRAPH = new EmptyDependencyGraphs();

    @NonNull private final GlobalScope globalScope;
    @NonNull private final AndroidBuilder androidBuilder;
    @NonNull private final AndroidConfig config;
    @NonNull private final ExtraModelInfo extraModelInfo;
    @NonNull private final VariantManager variantManager;
    @NonNull private final TaskManager taskManager;
    @NonNull private final NdkHandler ndkHandler;
    @NonNull private Map<Abi, NativeToolchain> toolchains;
    @NonNull private NativeLibraryFactory nativeLibFactory;
    private final int projectType;
    private final int generation;
    private int modelLevel = AndroidProject.MODEL_LEVEL_0_ORIGINAL;
    private boolean modelWithFullDependency = false;

    private Set<SyncIssue> syncIssues = Sets.newLinkedHashSet();

    public ModelBuilder(
            @NonNull GlobalScope globalScope,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull VariantManager variantManager,
            @NonNull TaskManager taskManager,
            @NonNull AndroidConfig config,
            @NonNull ExtraModelInfo extraModelInfo,
            @NonNull NdkHandler ndkHandler,
            @NonNull NativeLibraryFactory nativeLibraryFactory,
            int projectType,
            int generation) {
        this.globalScope = globalScope;
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
        return modelName.equals(AndroidProject.class.getName())
                || modelName.equals(GlobalLibraryMap.class.getName())
                || modelName.equals(ProjectBuildOutput.class.getName());
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        if (modelName.equals(AndroidProject.class.getName())) {
            return buildAndroidProject(project);
        }
        if (modelName.equals(ProjectBuildOutput.class.getName())) {
            return buildMinimalisticModel();
        }

        return buildGlobalLibraryMap();
    }

    @VisibleForTesting
    ProjectBuildOutput buildMinimalisticModel() {

        ImmutableList.Builder<VariantBuildOutput> variantsOutput = ImmutableList.builder();

        // gather the testingVariants per testedVariant
        Multimap<VariantScope, VariantScope> sortedVariants = ArrayListMultimap.create();
        for (VariantScope variantScope : variantManager.getVariantScopes()) {
            boolean isForTesting = variantScope.getVariantData().getType().isForTesting();

            if (isForTesting && variantScope.getTestedVariantData() != null) {
                sortedVariants.put(variantScope.getTestedVariantData().getScope(), variantScope);
            }
        }

        for (VariantScope variantScope : variantManager.getVariantScopes()) {
            boolean isForTesting = variantScope.getVariantData().getType().isForTesting();

            if (!isForTesting) {
                Collection<VariantScope> testingVariants = sortedVariants.get(variantScope);
                Collection<TestVariantBuildOutput> testVariantBuildOutputs;
                if (testingVariants == null) {
                    testVariantBuildOutputs = ImmutableList.of();
                } else {
                    testVariantBuildOutputs =
                            testingVariants
                                    .stream()
                                    .map(
                                            testVariantScope ->
                                                    new DefaultTestVariantBuildOutput(
                                                            testVariantScope.getFullVariantName(),
                                                            getBuildOutputSupplier(
                                                                            testVariantScope
                                                                                    .getVariantData())
                                                                    .get(),
                                                            variantScope.getFullVariantName(),
                                                            testVariantScope
                                                                                    .getVariantData()
                                                                                    .getType()
                                                                            == VariantType
                                                                                    .ANDROID_TEST
                                                                    ? TestVariantBuildOutput
                                                                            .TestType.ANDROID_TEST
                                                                    : TestVariantBuildOutput
                                                                            .TestType.UNIT))
                                    .collect(Collectors.toList());
                }
                variantsOutput.add(
                        new DefaultVariantBuildOutput(
                                variantScope.getFullVariantName(),
                                getBuildOutputSupplier(variantScope.getVariantData()).get(),
                                testVariantBuildOutputs));
            }
        }

        return new DefaultProjectBuildOutput(variantsOutput.build());
    }

    private static Object buildGlobalLibraryMap() {
        return new GlobalLibraryMapImpl(ArtifactDependencyGraph.getGlobalLibMap());
    }

    private Object buildAndroidProject(Project project) {
        Integer modelLevelInt = AndroidGradleOptions.buildModelOnlyVersion(project);
        if (modelLevelInt != null) {
            modelLevel = modelLevelInt;
        }

        if (modelLevel < AndroidProject.MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD) {
            throw new RuntimeException("This Gradle plugin requires Studio 3.0 minimum");
        }

        modelWithFullDependency = AndroidGradleOptions.buildModelWithFullDependencies(project);

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

        syncIssues.addAll(extraModelInfo.getSyncIssues().values());

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

        for (VariantScope variantScope : variantManager.getVariantScopes()) {
            if (!variantScope.getVariantData().getType().isForTesting()) {
                variants.add(createVariant(variantScope.getVariantData()));
            }
        }

        return new DefaultAndroidProject(
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
                syncIssues,
                config.getCompileOptions(),
                lintOptions,
                project.getBuildDir(),
                config.getResourcePrefix(),
                ImmutableList.copyOf(toolchains.values()),
                config.getBuildToolsVersion(),
                projectType,
                Version.BUILDER_MODEL_API_VERSION,
                generation,
                projectType == PROJECT_TYPE_FEATURE && config.getBaseFeature());
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
    private VariantImpl createVariant(@NonNull BaseVariantData variantData) {
        AndroidArtifact mainArtifact = createAndroidArtifact(ARTIFACT_MAIN, variantData);

        GradleVariantConfiguration variantConfiguration = variantData.getVariantConfiguration();

        String variantName = variantConfiguration.getFullName();

        List<AndroidArtifact> extraAndroidArtifacts = Lists.newArrayList(
                extraModelInfo.getExtraAndroidArtifacts(variantName));
        // Make sure all extra artifacts are serializable.
        List<JavaArtifact> clonedExtraJavaArtifacts =
                extraModelInfo
                        .getExtraJavaArtifacts(variantName)
                        .stream()
                        .map(
                                javaArtifact ->
                                        JavaArtifactImpl.clone(
                                                javaArtifact, modelLevel, modelWithFullDependency))
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

        // used for test only modules
        Collection<TestedTargetVariant> testTargetVariants = getTestTargetVariants(variantData);

        return new VariantImpl(
                variantName,
                variantConfiguration.getBaseName(),
                variantConfiguration.getBuildType().getName(),
                getProductFlavorNames(variantData),
                new ProductFlavorImpl(variantConfiguration.getMergedFlavor()),
                mainArtifact,
                extraAndroidArtifacts,
                clonedExtraJavaArtifacts,
                testTargetVariants);
    }

    @NonNull
    private Collection<TestedTargetVariant> getTestTargetVariants(BaseVariantData variantData) {
        if (config instanceof TestAndroidConfig) {
            TestAndroidConfig testConfig = (TestAndroidConfig) config;

            // to get the target variant we need to get the result of the dependency resolution
            ArtifactCollection apkArtifacts =
                    variantData
                            .getScope()
                            .getArtifactCollection(
                                    AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                                    AndroidArtifacts.ArtifactScope.ALL,
                                    AndroidArtifacts.ArtifactType.MANIFEST_METADATA);

            // there should really be only one.
            ResolvedArtifactResult result = Iterables.getOnlyElement(apkArtifacts.getArtifacts());
            String variant = ArtifactDependencyGraph.getVariant(result);

            return ImmutableList.of(
                    new TestedTargetVariantImpl(testConfig.getTargetProjectPath(), variant));
        } else {
            return ImmutableList.of();
        }
    }

    private JavaArtifactImpl createUnitTestsJavaArtifact(
            @NonNull VariantType variantType, @NonNull BaseVariantData variantData) {
        SourceProviders sourceProviders = determineSourceProviders(variantData);

        List<File> extraGeneratedSourceFolders = variantData.getExtraGeneratedSourceFolders();

        Dependencies dependencies;
        DependencyGraphs dependencyGraphs;

        ArtifactDependencyGraph graph = new ArtifactDependencyGraph();

        if (modelLevel >= AndroidProject.MODEL_LEVEL_4_NEW_DEP_MODEL) {
            dependencies = EMPTY_DEPENDENCIES_IMPL;

            dependencyGraphs =
                    graph.createLevel2DependencyGraph(
                            variantData.getScope(), modelWithFullDependency);
        } else {
            dependencies = graph.createDependencies(variantData.getScope());

            dependencyGraphs = EMPTY_DEPENDENCY_GRAPH;
        }

        List<String> failures = graph.collectFailures();
        if (!failures.isEmpty()) {
            handleUnresolvedDependencies(failures);
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
                globalScope.getMockableAndroidJarFile(),
                dependencies,
                dependencyGraphs,
                sourceProviders.variantSourceProvider,
                sourceProviders.multiFlavorSourceProvider);
    }

    private void handleUnresolvedDependencies(@NonNull List<String> failures) {
        for (String failure : failures) {
            syncIssues.add(
                    new SyncIssueImpl(
                            SyncIssue.TYPE_UNRESOLVED_DEPENDENCY,
                            SyncIssue.SEVERITY_ERROR,
                            failure,
                            String.format("Unable to resolve dependency '%s'", failure)));
        }
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

    private AndroidArtifact createAndroidArtifact(
            @NonNull String name, @NonNull BaseVariantData variantData) {
        VariantScope scope = variantData.getScope();
        GradleVariantConfiguration variantConfiguration = variantData.getVariantConfiguration();

        SigningConfig signingConfig = variantConfiguration.getSigningConfig();
        String signingConfigName = null;
        if (signingConfig != null) {
            signingConfigName = signingConfig.getName();
        }

        SourceProviders sourceProviders = determineSourceProviders(variantData);

        // get the outputs
        BuildOutputSupplier<Collection<BuildOutput>> splitOutputsProxy =
                getBuildOutputSupplier(variantData);
        BuildOutputSupplier<Collection<BuildOutput>> manifestsProxy =
                getManifestsSupplier(variantData);

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

        DependenciesImpl dependencies;
        DependencyGraphs dependencyGraphs;

        ArtifactDependencyGraph graph = new ArtifactDependencyGraph();
        if (modelLevel >= AndroidProject.MODEL_LEVEL_4_NEW_DEP_MODEL) {
            dependencies = EMPTY_DEPENDENCIES_IMPL;

            dependencyGraphs = graph.createLevel2DependencyGraph(scope, modelWithFullDependency);
        } else {
            dependencies = graph.createDependencies(scope);

            dependencyGraphs = EMPTY_DEPENDENCY_GRAPH;
        }

        List<String> failures = graph.collectFailures();
        if (!failures.isEmpty()) {
            handleUnresolvedDependencies(failures);
        }

        return new AndroidArtifactImpl(
                name,
                scope.getGlobalScope().getProjectBaseName()
                        + "-"
                        + variantConfiguration.getBaseName(),
                variantData.getTaskByKind(TaskContainer.TaskKind.ASSEMBLE) == null
                        ? scope.getTaskName("assemble")
                        : variantData.getTaskByKind(TaskContainer.TaskKind.ASSEMBLE).getName(),
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

    private static BuildOutputSupplier<Collection<BuildOutput>> getBuildOutputSupplier(
            BaseVariantData variantData) {
        switch (variantData.getType()) {
            case DEFAULT:
            case FEATURE:
                return new BuildOutputsSupplier(
                        ImmutableList.of(
                                VariantScope.TaskOutputType.APK,
                                VariantScope.TaskOutputType.ABI_PACKAGED_SPLIT,
                                VariantScope.TaskOutputType.DENSITY_OR_LANGUAGE_PACKAGED_SPLIT),
                        ImmutableList.of(
                                new File(
                                        variantData.getScope().getGlobalScope().getApkLocation(),
                                        variantData.getVariantConfiguration().getDirName())));
            case INSTANTAPP:
                return new BuildOutputsSupplier(
                        ImmutableList.of(VariantScope.TaskOutputType.INSTANTAPP_BUNDLE),
                        ImmutableList.of(
                                new File(
                                        variantData.getScope().getGlobalScope().getApkLocation(),
                                        variantData.getVariantConfiguration().getDirName())));
            case LIBRARY:
                ApkInfo mainApkInfo =
                        ApkInfo.of(VariantOutput.OutputType.MAIN, ImmutableList.of(), 0);
                return BuildOutputSupplier.of(
                        ImmutableList.of(
                                new BuildOutput(
                                        VariantScope.TaskOutputType.AAR,
                                        mainApkInfo,
                                        variantData.getScope().getOutputBundleFile())));
            case ANDROID_TEST:
                return new BuildOutputsSupplier(
                        ImmutableList.of(VariantScope.TaskOutputType.APK),
                        ImmutableList.of(
                                new File(
                                        variantData.getScope().getGlobalScope().getApkLocation(),
                                        variantData.getVariantConfiguration().getDirName())));
            case UNIT_TEST:
                return (BuildOutputSupplier<Collection<BuildOutput>>)
                        () ->
                                ImmutableList.of(
                                        new BuildOutput(
                                                VariantScope.TaskOutputType.JAVAC,
                                                ApkInfo.of(
                                                        VariantOutput.OutputType.MAIN,
                                                        ImmutableList.of(),
                                                        variantData
                                                                .getVariantConfiguration()
                                                                .getVersionCode()),
                                                variantData.getScope().getJavaOutputDir()));
            default:
                throw new RuntimeException("Unhandled build type " + variantData.getType());
        }
    }

    private static BuildOutputSupplier<Collection<BuildOutput>> getManifestsSupplier(
            BaseVariantData variantData) {

        switch (variantData.getType()) {
            case DEFAULT:
            case ANDROID_TEST:
            case FEATURE:
                return new BuildOutputsSupplier(
                        ImmutableList.of(VariantScope.TaskOutputType.MERGED_MANIFESTS),
                        ImmutableList.of(variantData.getScope().getManifestOutputDirectory()));
            case LIBRARY:
                ApkInfo mainApkInfo =
                        ApkInfo.of(VariantOutput.OutputType.MAIN, ImmutableList.of(), 0);
                return BuildOutputSupplier.of(
                        ImmutableList.of(
                                new BuildOutput(
                                        VariantScope.TaskOutputType.MERGED_MANIFESTS,
                                        mainApkInfo,
                                        new File(
                                                variantData.getScope().getManifestOutputDirectory(),
                                                SdkConstants.ANDROID_MANIFEST_XML))));
            case INSTANTAPP:
                return new BuildOutputsSupplier(ImmutableList.of(), ImmutableList.of());
            default:
                throw new RuntimeException("Unhandled build type " + variantData.getType());
        }
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

    private static SourceProviders determineSourceProviders(@NonNull BaseVariantData variantData) {
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
    private static List<String> getProductFlavorNames(@NonNull BaseVariantData variantData) {
        return variantData.getVariantConfiguration().getProductFlavors().stream()
                .map((Function<ProductFlavor, String>) ProductFlavor::getName)
                .collect(Collectors.toList());
    }

    @NonNull
    private static List<File> getGeneratedSourceFolders(@Nullable BaseVariantData variantData) {
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
    private static List<File> getGeneratedResourceFolders(@Nullable BaseVariantData variantData) {
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
}
