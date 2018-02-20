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

import static com.android.build.gradle.internal.scope.InternalArtifactType.DATA_BINDING_BASE_CLASS_SOURCE_OUT;
import static com.android.build.gradle.internal.scope.InternalArtifactType.JAVAC;
import static com.android.build.gradle.options.BooleanOption.ENABLE_DATA_BINDING_V2;
import static com.android.builder.model.AndroidProject.ARTIFACT_MAIN;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.build.VariantOutput;
import com.android.build.api.artifact.ArtifactType;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.TestAndroidConfig;
import com.android.build.gradle.internal.BuildTypeData;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.ProductFlavorData;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.VariantManager;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.CoreNdkOptions;
import com.android.build.gradle.internal.dsl.TestOptions;
import com.android.build.gradle.internal.ide.level2.EmptyDependencyGraphs;
import com.android.build.gradle.internal.ide.level2.GlobalLibraryMapImpl;
import com.android.build.gradle.internal.incremental.BuildInfoWriterTask;
import com.android.build.gradle.internal.model.NativeLibraryFactory;
import com.android.build.gradle.internal.ndk.NdkHandler;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.publishing.PublishingSpecs;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.TaskContainer;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.internal.variant.TestedVariantData;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.SyncOptions;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.VariantType;
import com.android.builder.errors.EvalIssueReporter;
import com.android.builder.errors.EvalIssueReporter.Type;
import com.android.builder.model.AaptOptions;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ArtifactMetaData;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaArtifact;
import com.android.builder.model.LintOptions;
import com.android.builder.model.ModelBuilderParameter;
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
import com.android.builder.model.Version;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.GlobalLibraryMap;
import com.android.ide.common.build.ApkInfo;
import com.android.utils.Pair;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.invocation.Gradle;
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder;

/**
 * Builder for the custom Android model.
 */
public class ModelBuilder implements ParameterizedToolingModelBuilder<ModelBuilderParameter> {
    public static final String CURRENT_BUILD_NAME = "__current_build__";

    @NonNull
    static final DependenciesImpl EMPTY_DEPENDENCIES_IMPL =
            new DependenciesImpl(ImmutableList.of(), ImmutableList.of(), ImmutableList.of());

    @NonNull static final DependencyGraphs EMPTY_DEPENDENCY_GRAPH = new EmptyDependencyGraphs();
    @NonNull protected final GlobalScope globalScope;
    @NonNull private final AndroidBuilder androidBuilder;
    @NonNull protected final AndroidConfig config;
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
    /**
     * a map that goes from build name ({@link BuildIdentifier#getName()} to the root dir of the
     * build.
     */
    private ImmutableMap<String, String> buildMapping = null;

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
    public boolean canBuild(@NonNull String modelName) {
        // The default name for a model is the name of the Java interface.
        return modelName.equals(AndroidProject.class.getName())
                || modelName.equals(GlobalLibraryMap.class.getName())
                || modelName.equals(ProjectBuildOutput.class.getName())
                || modelName.equals(Variant.class.getName());
    }

    @Override
    public Object buildAll(@NonNull String modelName, @NonNull Project project) {
        // build a map from included build name to rootDir (as rootDir is the only thing
        // that we have access to on the tooling API side).
        initBuildMapping(project);

        if (modelName.equals(AndroidProject.class.getName())) {
            return buildAndroidProject(project, true);
        }
        if (modelName.equals(Variant.class.getName())) {
            throw new RuntimeException(
                    "Please use parameterized tooling API to obtain Variant model.");
        }
        return buildNonParameterizedModels(modelName);
    }

    // Build parameterized model. This method is invoked if model is obtained by
    // BuildController::findModel(Model var1, Class<T> var2, Class<P> var3, Action<? super P> var4).
    @Override
    public Object buildAll(
            @NonNull String modelName,
            @NonNull ModelBuilderParameter parameter,
            @NonNull Project project) {
        // build a map from included build name to rootDir (as rootDir is the only thing
        // that we have access to on the tooling API side).
        initBuildMapping(project);
        if (modelName.equals(AndroidProject.class.getName())) {
            return buildAndroidProject(project, parameter.getShouldBuildVariant());
        }
        if (modelName.equals(Variant.class.getName())) {
            return buildVariant(parameter.getVariantName());
        }
        return buildNonParameterizedModels(modelName);
    }

    @NonNull
    private Object buildNonParameterizedModels(@NonNull String modelName) {
        if (modelName.equals(ProjectBuildOutput.class.getName())) {
            return buildMinimalisticModel();
        }
        return buildGlobalLibraryMap();
    }

    @Override
    @NonNull
    public Class<ModelBuilderParameter> getParameterType() {
        return ModelBuilderParameter.class;
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

    private Object buildAndroidProject(Project project, boolean shouldBuildVariant) {
        // Cannot be injected, as the project might not be the same as the project used to construct
        // the model builder e.g. when lint explicitly builds the model.
        ProjectOptions projectOptions = new ProjectOptions(project);
        Integer modelLevelInt = SyncOptions.buildModelOnlyVersion(projectOptions);
        if (modelLevelInt != null) {
            modelLevel = modelLevelInt;
        }

        if (modelLevel < AndroidProject.MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD) {
            throw new RuntimeException(
                    "This Gradle plugin requires a newer IDE able to request IDE model level 3. For Android Studio this means version 3.0+");
        }

        modelWithFullDependency =
                projectOptions.get(BooleanOption.IDE_BUILD_MODEL_FEATURE_FULL_DEPENDENCIES);

        // Get the boot classpath. This will ensure the target is configured.
        List<String> bootClasspath;
        if (androidBuilder.getTargetInfo() != null) {
            bootClasspath = androidBuilder.getBootClasspathAsStrings(false);
        } else {
            // SDK not set up, error will be reported as a sync issue.
            bootClasspath = Collections.emptyList();
        }
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

        syncIssues.addAll(extraModelInfo.getSyncIssueHandler().getSyncIssues());

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
        Collection<String> variantNames = Lists.newArrayList();

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
                variantNames.add(variantScope.getFullVariantName());
                if (shouldBuildVariant) {
                    variants.add(createVariant(variantScope.getVariantData()));
                }
            }
        }

        return new DefaultAndroidProject(
                project.getName(),
                defaultConfig,
                flavorDimensionList,
                buildTypes,
                productFlavors,
                variants,
                variantNames,
                androidBuilder.getTargetInfo() != null
                        ? androidBuilder.getTarget().hashString()
                        : "",
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
                isBaseSplit(),
                getDynamicFeatures());
    }

    protected boolean isBaseSplit() {
        return false;
    }

    @NonNull
    protected Collection<String> getDynamicFeatures() {
        return ImmutableList.of();
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
    private VariantImpl buildVariant(@Nullable String variantName) {
        if (variantName == null) {
            throw new IllegalArgumentException("Variant name cannot be null.");
        }
        for (VariantScope variantScope : variantManager.getVariantScopes()) {
            if (!variantScope.getVariantData().getType().isForTesting()
                    && variantScope.getFullVariantName().equals(variantName)) {
                return createVariant(variantScope.getVariantData());
            }
        }
        throw new IllegalArgumentException(
                String.format("Variant with name '%s' doesn't exist.", variantName));
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

            // while there should be single result, if the variant matching is broken, then
            // we need to support this.
            if (apkArtifacts.getArtifacts().size() == 1) {
                ResolvedArtifactResult result =
                        Iterables.getOnlyElement(apkArtifacts.getArtifacts());
                String variant = ArtifactDependencyGraph.getVariant(result);

                return ImmutableList.of(
                        new TestedTargetVariantImpl(testConfig.getTargetProjectPath(), variant));
            } else if (!apkArtifacts.getFailures().isEmpty()) {
                VariantScope variantScope = variantData.getScope();

                // probably there was an error...
                syncIssues.addAll(
                        new DependencyFailureHandler()
                                .addErrors(
                                        variantScope.getGlobalScope().getProject().getPath()
                                                + "@"
                                                + variantScope.getFullVariantName()
                                                + "/testTarget",
                                        apkArtifacts.getFailures())
                                .collectIssues());
            }
        }

        return ImmutableList.of();
    }

    private JavaArtifactImpl createUnitTestsJavaArtifact(
            @NonNull VariantType variantType, @NonNull BaseVariantData variantData) {
        SourceProviders sourceProviders = determineSourceProviders(variantData);

        final VariantScope scope = variantData.getScope();
        Pair<Dependencies, DependencyGraphs> result =
                getDependencies(
                        scope,
                        buildMapping,
                        extraModelInfo,
                        syncIssues,
                        modelLevel,
                        modelWithFullDependency);

        Set<File> additionalTestClasses = new HashSet<>();
        additionalTestClasses.addAll(variantData.getAllPreJavacGeneratedBytecode().getFiles());
        additionalTestClasses.addAll(variantData.getAllPostJavacGeneratedBytecode().getFiles());
        if (scope.hasOutput(InternalArtifactType.UNIT_TEST_CONFIG_DIRECTORY)) {
            additionalTestClasses.add(
                    scope.getOutput(InternalArtifactType.UNIT_TEST_CONFIG_DIRECTORY)
                            .getSingleFile());
        }
        // The separately compile R class, if applicable.
        VariantScope testedScope = Objects.requireNonNull(scope.getTestedVariantData()).getScope();
        if (testedScope.hasOutput(InternalArtifactType.COMPILE_ONLY_NOT_NAMESPACED_R_CLASS_JAR)) {
            additionalTestClasses.add(
                    testedScope
                            .getOutput(InternalArtifactType.COMPILE_ONLY_NOT_NAMESPACED_R_CLASS_JAR)
                            .getSingleFile());
        }

        return new JavaArtifactImpl(
                variantType.getArtifactName(),
                scope.getAssembleTask().getName(),
                scope.getCompileTask().getName(),
                Sets.newHashSet(taskManager.createMockableJar.getName()),
                getGeneratedSourceFoldersForUnitTests(variantData),
                Iterables.getOnlyElement(scope.getBuildArtifactsHolder().getArtifactFiles(JAVAC)),
                additionalTestClasses,
                variantData.getJavaResourcesForUnitTesting(),
                globalScope.getMockableAndroidJarFile(),
                result.getFirst(),
                result.getSecond(),
                sourceProviders.variantSourceProvider,
                sourceProviders.multiFlavorSourceProvider);
    }

    /** Gather the dependency graph for the specified <code>variantScope</code>. */
    @NonNull
    public static Pair<Dependencies, DependencyGraphs> getDependencies(
            @NonNull VariantScope variantScope,
            @NonNull ImmutableMap<String, String> buildMapping,
            @NonNull ExtraModelInfo extraModelInfo,
            @NonNull Set<SyncIssue> syncIssues,
            int modelLevel,
            boolean modelWithFullDependency) {
        Pair<Dependencies, DependencyGraphs> result;

        // If there is a missing flavor dimension then we don't even try to resolve dependencies
        // as it may fail due to improperly setup configuration attributes.
        if (extraModelInfo.getSyncIssueHandler().hasSyncIssue(Type.UNNAMED_FLAVOR_DIMENSION)) {
            result = Pair.of(EMPTY_DEPENDENCIES_IMPL, EMPTY_DEPENDENCY_GRAPH);
        } else {
            final Project project = variantScope.getGlobalScope().getProject();
            ArtifactDependencyGraph graph = new ArtifactDependencyGraph();
            // can't use ProjectOptions as this is likely to change from the initialization of
            // ProjectOptions due to how lint dynamically add/remove this property.
            boolean downloadSources =
                    !project.hasProperty(AndroidProject.PROPERTY_BUILD_MODEL_DISABLE_SRC_DOWNLOAD)
                            || !Boolean.TRUE.equals(
                                    project.getProperties()
                                            .get(
                                                    AndroidProject
                                                            .PROPERTY_BUILD_MODEL_DISABLE_SRC_DOWNLOAD));

            if (modelLevel >= AndroidProject.MODEL_LEVEL_4_NEW_DEP_MODEL) {
                result =
                        Pair.of(
                                EMPTY_DEPENDENCIES_IMPL,
                                graph.createLevel4DependencyGraph(
                                        variantScope,
                                        modelWithFullDependency,
                                        downloadSources,
                                        buildMapping,
                                        syncIssues::add));
            } else {
                result =
                        Pair.of(
                                graph.createDependencies(
                                        variantScope,
                                        downloadSources,
                                        buildMapping,
                                        syncIssues::add),
                                EMPTY_DEPENDENCY_GRAPH);
            }
        }

        return result;
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
        BuildOutputSupplier<Collection<EarlySyncBuildOutput>> splitOutputsProxy =
                getBuildOutputSupplier(variantData);
        BuildOutputSupplier<Collection<EarlySyncBuildOutput>> manifestsProxy =
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

        Pair<Dependencies, DependencyGraphs> dependencies =
                getDependencies(
                        scope,
                        buildMapping,
                        extraModelInfo,
                        syncIssues,
                        modelLevel,
                        modelWithFullDependency);

        Set<File> additionalTestClasses = new HashSet<>();
        additionalTestClasses.addAll(variantData.getAllPreJavacGeneratedBytecode().getFiles());
        additionalTestClasses.addAll(variantData.getAllPostJavacGeneratedBytecode().getFiles());

        List<File> additionalRuntimeApks = new ArrayList<>();
        TestOptionsImpl testOptions = null;

        if (variantData.getType().isForTesting()) {
            Configuration testHelpers =
                    scope.getGlobalScope()
                            .getProject()
                            .getConfigurations()
                            .findByName(SdkConstants.GRADLE_ANDROID_TEST_UTIL_CONFIGURATION);

            // This may be the case with the experimental plugin.
            if (testHelpers != null) {
                additionalRuntimeApks.addAll(testHelpers.getFiles());
            }

            DeviceProviderInstrumentTestTask.checkForNonApks(
                    additionalRuntimeApks,
                    message ->
                            syncIssues.add(
                                    new SyncIssueImpl(
                                            Type.GENERIC,
                                            EvalIssueReporter.Severity.ERROR,
                                            null,
                                            message)));

            TestOptions testOptionsDsl = scope.getGlobalScope().getExtension().getTestOptions();
            testOptions =
                    new TestOptionsImpl(
                            testOptionsDsl.getAnimationsDisabled(),
                            testOptionsDsl.getExecutionEnum());
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
                Iterables.getOnlyElement(scope.getBuildArtifactsHolder().getArtifactFiles(JAVAC)),
                additionalTestClasses,
                scope.getVariantData().getJavaResourcesForUnitTesting(),
                dependencies.getFirst(),
                dependencies.getSecond(),
                additionalRuntimeApks,
                sourceProviders.variantSourceProvider,
                sourceProviders.multiFlavorSourceProvider,
                variantConfiguration.getSupportedAbis(),
                nativeLibraries,
                variantConfiguration.getMergedBuildConfigFields(),
                variantConfiguration.getMergedResValues(),
                instantRun,
                splitOutputsProxy,
                manifestsProxy,
                testOptions,
                scope.getConnectedTask() == null ? null : scope.getConnectedTask().getName());
    }

    private static BuildOutputSupplier<Collection<EarlySyncBuildOutput>> getBuildOutputSupplier(
            BaseVariantData variantData) {
        final VariantScope variantScope = variantData.getScope();

        switch (variantData.getType()) {
            case APK:
            case FEATURE:
                return new BuildOutputsSupplier(
                        ImmutableList.of(
                                InternalArtifactType.APK,
                                InternalArtifactType.ABI_PACKAGED_SPLIT,
                                InternalArtifactType.DENSITY_OR_LANGUAGE_PACKAGED_SPLIT),
                        ImmutableList.of(variantScope.getApkLocation()));
            case LIBRARY:
                ApkInfo mainApkInfo =
                        ApkInfo.of(VariantOutput.OutputType.MAIN, ImmutableList.of(), 0);
                return BuildOutputSupplier.of(
                        ImmutableList.of(
                                new EarlySyncBuildOutput(
                                        InternalArtifactType.AAR,
                                        mainApkInfo.getType(),
                                        mainApkInfo.getFilters(),
                                        mainApkInfo.getVersionCode(),
                                        variantScope
                                                .getOutput(InternalArtifactType.AAR)
                                                .getSingleFile())));
            case ANDROID_TEST:
                return new BuildOutputsSupplier(
                        ImmutableList.of(InternalArtifactType.APK),
                        ImmutableList.of(variantScope.getApkLocation()));
            case UNIT_TEST:
                return (BuildOutputSupplier<Collection<EarlySyncBuildOutput>>)
                        () -> {
                            final BaseVariantData testedVariantData =
                                    variantScope.getTestedVariantData();
                            //noinspection ConstantConditions
                            final VariantScope testedVariantScope = testedVariantData.getScope();

                            PublishingSpecs.VariantSpec testedSpec =
                                    testedVariantScope
                                            .getPublishingSpec()
                                            .getTestingSpec(
                                                    variantScope
                                                            .getVariantConfiguration()
                                                            .getType());

                            // get the OutputPublishingSpec from the ArtifactType for this particular variant spec
                            PublishingSpecs.OutputSpec taskOutputSpec =
                                    testedSpec.getSpec(AndroidArtifacts.ArtifactType.CLASSES);
                            // now get the output type
                            ArtifactType testedOutputType = taskOutputSpec.getOutputType();

                            return ImmutableList.of(
                                    new EarlySyncBuildOutput(
                                            JAVAC,
                                            VariantOutput.OutputType.MAIN,
                                            ImmutableList.of(),
                                            variantData.getVariantConfiguration().getVersionCode(),
                                            variantScope
                                                    .getOutput(testedOutputType)
                                                    // We used to call .getSingleFile() but Kotlin projects
                                                    // currently have 2 output dirs specified for test classes.
                                                    // This supplier is going away in beta3, so this is obsolete
                                                    // in any case.
                                                    .iterator()
                                                    .next()));
                        };
            case INSTANTAPP:
            default:
                throw new RuntimeException("Unhandled build type " + variantData.getType());
        }
    }

    private static BuildOutputSupplier<Collection<EarlySyncBuildOutput>> getManifestsSupplier(
            BaseVariantData variantData) {

        switch (variantData.getType()) {
            case APK:
            case ANDROID_TEST:
            case FEATURE:
                return new BuildOutputsSupplier(
                        ImmutableList.of(InternalArtifactType.MERGED_MANIFESTS),
                        ImmutableList.of(variantData.getScope().getManifestOutputDirectory()));
            case LIBRARY:
                ApkInfo mainApkInfo =
                        ApkInfo.of(VariantOutput.OutputType.MAIN, ImmutableList.of(), 0);
                return BuildOutputSupplier.of(
                        ImmutableList.of(
                                new EarlySyncBuildOutput(
                                        InternalArtifactType.MERGED_MANIFESTS,
                                        mainApkInfo.getType(),
                                        mainApkInfo.getFilters(),
                                        mainApkInfo.getVersionCode(),
                                        new File(
                                                variantData.getScope().getManifestOutputDirectory(),
                                                SdkConstants.ANDROID_MANIFEST_XML))));
            case INSTANTAPP:
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
    private static List<File> getGeneratedSourceFoldersForUnitTests(
            @Nullable BaseVariantData variantData) {
        if (variantData == null) {
            return Collections.emptyList();
        }

        List<File> folders = Lists.newArrayList(variantData.getExtraGeneratedSourceFolders());
        folders.add(variantData.getScope().getAnnotationProcessorOutputDir());
        return folders;
    }

    @NonNull
    private static List<File> getGeneratedSourceFolders(@Nullable BaseVariantData variantData) {
        if (variantData == null) {
            return Collections.emptyList();
        }
        VariantScope scope = variantData.getScope();
        GlobalScope globalScope = variantData.getScope().getGlobalScope();

        boolean useDataBindingV2 = globalScope.getProjectOptions().get(ENABLE_DATA_BINDING_V2);
        boolean addDataBindingSources =
                globalScope.getExtension().getDataBinding().isEnabled()
                        && useDataBindingV2
                        && scope.hasOutput(DATA_BINDING_BASE_CLASS_SOURCE_OUT);
        List<File> extraFolders = getGeneratedSourceFoldersForUnitTests(variantData);

        // Set this to the number of folders you expect to add explicitly in the code below.
        int additionalFolders = 4;
        if (addDataBindingSources) {
            additionalFolders += 1;
        }
        List<File> folders =
                Lists.newArrayListWithExpectedSize(additionalFolders + extraFolders.size());
        folders.addAll(extraFolders);

        // The R class is only generated by the first output.
        folders.add(scope.getRClassSourceOutputDir());

        folders.add(scope.getAidlSourceOutputDir());
        folders.add(scope.getBuildConfigSourceOutputDir());
        Boolean ndkMode = variantData.getVariantConfiguration().getMergedFlavor().getRenderscriptNdkModeEnabled();
        if (ndkMode == null || !ndkMode) {
            folders.add(scope.getRenderscriptSourceOutputDir());
        }
        if (addDataBindingSources) {
            FileCollection output = scope.getOutput(DATA_BINDING_BASE_CLASS_SOURCE_OUT);
            folders.add(output.getSingleFile());
        }
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

    private void initBuildMapping(@NonNull Project project) {
        if (buildMapping == null) {
            buildMapping = computeBuildMapping(project.getGradle());
        }
    }

    @NonNull
    public static ImmutableMap<String, String> computeBuildMapping(@NonNull Gradle gradle) {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();

        // Get the root dir for current build.
        // This is necessary to handle the case when dependency comes from the same build with consumer,
        // i.e. when BuildIdentifier.isCurrentBuild returns true. In that case, BuildIdentifier.getName
        // returns ":" instead of the actual build name.
        String currentBuildPath = gradle.getRootProject().getProjectDir().getAbsolutePath();
        builder.put(CURRENT_BUILD_NAME, currentBuildPath);

        Gradle rootGradleProject = gradle;
        // first, ensure we are starting from the root Gradle object.
        //noinspection ConstantConditions
        while (rootGradleProject.getParent() != null) {
            rootGradleProject = rootGradleProject.getParent();
        }

        // get the root dir for the top project if different from current project.
        if (rootGradleProject != gradle) {
            builder.put(
                    rootGradleProject.getRootProject().getName(),
                    rootGradleProject.getRootProject().getProjectDir().getAbsolutePath());
        }

        for (IncludedBuild includedBuild : rootGradleProject.getIncludedBuilds()) {
            String includedBuildPath = includedBuild.getProjectDir().getAbsolutePath();
            // current build has been added with key CURRENT_BUIlD_NAME, avoid redundant entry.
            if (!includedBuildPath.equals(currentBuildPath)) {
                builder.put(includedBuild.getName(), includedBuildPath);
            }
        }

        return builder.build();
    }
}
