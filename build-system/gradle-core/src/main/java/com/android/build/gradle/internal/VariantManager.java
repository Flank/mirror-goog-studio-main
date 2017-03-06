/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.internal;

import static com.android.builder.core.BuilderConstants.LINT;
import static com.android.builder.core.VariantType.ANDROID_TEST;
import static com.android.builder.core.VariantType.LIBRARY;
import static com.android.builder.core.VariantType.UNIT_TEST;
import static org.gradle.api.internal.artifacts.ArtifactAttributes.ARTIFACT_FORMAT;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.AndroidConfig;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.TestAndroidConfig;
import com.android.build.gradle.TestedAndroidConfig;
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet;
import com.android.build.gradle.internal.api.ReadOnlyObjectProvider;
import com.android.build.gradle.internal.api.VariantFilter;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dependency.AarTransform;
import com.android.build.gradle.internal.dependency.BuildTypeAttr;
import com.android.build.gradle.internal.dependency.ExtractAarTransform;
import com.android.build.gradle.internal.dependency.JarTransform;
import com.android.build.gradle.internal.dependency.ProductFlavorAttr;
import com.android.build.gradle.internal.dependency.VariantAttr;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.dsl.CoreBuildType;
import com.android.build.gradle.internal.dsl.CoreProductFlavor;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.build.gradle.internal.variant.TaskContainer;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.internal.variant.TestedVariantData;
import com.android.build.gradle.internal.variant.VariantFactory;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.DefaultManifestParser;
import com.android.builder.core.DefaultProductFlavor;
import com.android.builder.core.ManifestAttributeSupplier;
import com.android.builder.core.VariantType;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.SigningConfig;
import com.android.builder.model.SyncIssue;
import com.android.builder.profile.ProcessProfileWriter;
import com.android.builder.profile.Recorder;
import com.android.builder.utils.FileCache;
import com.android.utils.StringHelper;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.internal.artifacts.ArtifactAttributes;
import org.gradle.internal.reflect.Instantiator;

/**
 * Class to create, manage variants.
 */
public class VariantManager implements VariantModel {

    private static final String MULTIDEX_VERSION = "1.0.1";

    protected static final String COM_ANDROID_SUPPORT_MULTIDEX =
            "com.android.support:multidex:" + MULTIDEX_VERSION;
    protected static final String COM_ANDROID_SUPPORT_MULTIDEX_INSTRUMENTATION =
            "com.android.support:multidex-instrumentation:" + MULTIDEX_VERSION;

    @NonNull
    private final Project project;
    @NonNull
    private final AndroidBuilder androidBuilder;
    @NonNull
    private final AndroidConfig extension;
    @NonNull
    private final VariantFactory variantFactory;
    @NonNull
    private final TaskManager taskManager;
    @NonNull
    private final Instantiator instantiator;
    @NonNull private final Recorder recorder;
    @NonNull private ProductFlavorData<CoreProductFlavor> defaultConfigData;
    @NonNull
    private final Map<String, BuildTypeData> buildTypes = Maps.newHashMap();
    @NonNull
    private final Map<String, ProductFlavorData<CoreProductFlavor>> productFlavors = Maps.newHashMap();
    @NonNull
    private final Map<String, SigningConfig> signingConfigs = Maps.newHashMap();

    @NonNull
    private final ReadOnlyObjectProvider readOnlyObjectProvider = new ReadOnlyObjectProvider();
    @NonNull
    private final VariantFilter variantFilter = new VariantFilter(readOnlyObjectProvider);

    @NonNull
    private final List<BaseVariantData<? extends BaseVariantOutputData>> variantDataList = Lists.newArrayList();
    @Nullable
    private CoreSigningConfig signingOverride;

    @NonNull Map<File, ManifestAttributeSupplier> manifestParserMap = new HashMap<>();

    public VariantManager(
            @NonNull Project project,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull AndroidConfig extension,
            @NonNull VariantFactory variantFactory,
            @NonNull TaskManager taskManager,
            @NonNull Instantiator instantiator,
            @NonNull Recorder recorder) {
        this.extension = extension;
        this.androidBuilder = androidBuilder;
        this.project = project;
        this.variantFactory = variantFactory;
        this.taskManager = taskManager;
        this.instantiator = instantiator;
        this.recorder = recorder;

        DefaultAndroidSourceSet mainSourceSet =
                (DefaultAndroidSourceSet) extension.getSourceSets().getByName(extension.getDefaultConfig().getName());

        DefaultAndroidSourceSet androidTestSourceSet = null;
        DefaultAndroidSourceSet unitTestSourceSet = null;
        if (variantFactory.hasTestScope()) {
            androidTestSourceSet =
                    (DefaultAndroidSourceSet) extension.getSourceSets()
                            .getByName(ANDROID_TEST.getPrefix());
            unitTestSourceSet =
                    (DefaultAndroidSourceSet) extension.getSourceSets()
                            .getByName(UNIT_TEST.getPrefix());
        }

        defaultConfigData = new ProductFlavorData<>(
                extension.getDefaultConfig(), mainSourceSet,
                androidTestSourceSet, unitTestSourceSet, project);
        signingOverride = createSigningOverride();
    }

    @NonNull
    @Override
    public ProductFlavorData<CoreProductFlavor> getDefaultConfig() {
        return defaultConfigData;
    }

    @Override
    @NonNull
    public Map<String, BuildTypeData> getBuildTypes() {
        return buildTypes;
    }

    @Override
    @NonNull
    public Map<String, ProductFlavorData<CoreProductFlavor>> getProductFlavors() {
        return productFlavors;
    }

    @Override
    @NonNull
    public Map<String, SigningConfig> getSigningConfigs() {
        return signingConfigs;
    }

    public void addSigningConfig(@NonNull SigningConfig signingConfig) {
        signingConfigs.put(signingConfig.getName(), signingConfig);
    }

    /**
     * Adds new BuildType, creating a BuildTypeData, and the associated source set,
     * and adding it to the map.
     * @param buildType the build type.
     */
    public void addBuildType(@NonNull CoreBuildType buildType) {
        String name = buildType.getName();
        checkName(name, "BuildType");

        if (productFlavors.containsKey(name)) {
            throw new RuntimeException("BuildType names cannot collide with ProductFlavor names");
        }

        DefaultAndroidSourceSet mainSourceSet = (DefaultAndroidSourceSet) extension.getSourceSets().maybeCreate(name);

        DefaultAndroidSourceSet unitTestSourceSet = null;
        if (variantFactory.hasTestScope()) {
            unitTestSourceSet = (DefaultAndroidSourceSet) extension
                    .getSourceSets().maybeCreate(
                            computeSourceSetName(buildType.getName(), UNIT_TEST));
        }

        BuildTypeData buildTypeData = new BuildTypeData(
                buildType, project, mainSourceSet, unitTestSourceSet);

        buildTypes.put(name, buildTypeData);
    }

    /**
     * Adds a new ProductFlavor, creating a ProductFlavorData and associated source sets,
     * and adding it to the map.
     *
     * @param productFlavor the product flavor
     */
    public void addProductFlavor(@NonNull CoreProductFlavor productFlavor) {
        String name = productFlavor.getName();
        checkName(name, "ProductFlavor");

        if (buildTypes.containsKey(name)) {
            throw new RuntimeException("ProductFlavor names cannot collide with BuildType names");
        }

        DefaultAndroidSourceSet mainSourceSet = (DefaultAndroidSourceSet) extension.getSourceSets().maybeCreate(
                productFlavor.getName());

        DefaultAndroidSourceSet androidTestSourceSet = null;
        DefaultAndroidSourceSet unitTestSourceSet = null;
        if (variantFactory.hasTestScope()) {
            androidTestSourceSet = (DefaultAndroidSourceSet) extension
                    .getSourceSets().maybeCreate(
                            computeSourceSetName(productFlavor.getName(), ANDROID_TEST));
            unitTestSourceSet = (DefaultAndroidSourceSet) extension
                    .getSourceSets().maybeCreate(
                            computeSourceSetName(productFlavor.getName(), UNIT_TEST));
        }

        ProductFlavorData<CoreProductFlavor> productFlavorData =
                new ProductFlavorData<>(
                        productFlavor,
                        mainSourceSet,
                        androidTestSourceSet,
                        unitTestSourceSet,
                        project);

        productFlavors.put(productFlavor.getName(), productFlavorData);
    }

    /**
     * Return a list of all created VariantData.
     */
    @NonNull
    public List<BaseVariantData<? extends BaseVariantOutputData>> getVariantDataList() {
        return variantDataList;
    }

    /**
     * Variant/Task creation entry point.
     *
     * Not used by gradle-experimental.
     */
    public void createAndroidTasks() {
        variantFactory.validateModel(this);
        variantFactory.preVariantWork(project);

        final TaskFactory tasks = new TaskContainerAdaptor(project.getTasks());
        if (variantDataList.isEmpty()) {
            recorder.record(
                    ExecutionType.VARIANT_MANAGER_CREATE_VARIANTS,
                    project.getPath(),
                    null /*variantName*/,
                    this::populateVariantDataList);
        }

        // Create top level test tasks.
        recorder.record(
                ExecutionType.VARIANT_MANAGER_CREATE_TESTS_TASKS,
                project.getPath(),
                null /*variantName*/,
                () -> taskManager.createTopLevelTestTasks(tasks, !productFlavors.isEmpty()));

        for (final BaseVariantData<? extends BaseVariantOutputData> variantData : variantDataList) {
            recorder.record(
                    ExecutionType.VARIANT_MANAGER_CREATE_TASKS_FOR_VARIANT,
                    project.getPath(),
                    variantData.getName(),
                    () -> createTasksForVariantData(tasks, variantData));
        }

        taskManager.createReportTasks(tasks, variantDataList);
    }

    /**
     * Create assemble task for VariantData.
     */
    private void createAssembleTaskForVariantData(
            TaskFactory tasks,
            final BaseVariantData<?> variantData) {
        final VariantScope variantScope = variantData.getScope();
        if (variantData.getType().isForTesting()) {
            variantScope.setAssembleTask(taskManager.createAssembleTask(tasks, variantData));
        } else {
            BuildTypeData buildTypeData =
                    buildTypes.get(variantData.getVariantConfiguration().getBuildType().getName());

            Preconditions.checkNotNull(buildTypeData.getAssembleTask());

            if (productFlavors.isEmpty()) {
                // Reuse assemble task for build type if there is no product flavor.
                variantScope.setAssembleTask(buildTypeData.getAssembleTask());
                buildTypeData
                        .getAssembleTask()
                        .configure(
                                tasks,
                                new Action<Task>() {
                                    @Override
                                    public void execute(Task task) {
                                        variantData.addTask(TaskContainer.TaskKind.ASSEMBLE, task);
                                    }
                                });
            } else {
                variantScope.setAssembleTask(taskManager.createAssembleTask(tasks, variantData));

                // setup the task dependencies
                // build type
                buildTypeData.getAssembleTask().dependsOn(tasks, variantScope.getAssembleTask());

                // each flavor
                GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();
                for (CoreProductFlavor flavor : variantConfig.getProductFlavors()) {
                    ProductFlavorData productFlavorData = productFlavors.get(flavor.getName());

                    AndroidTask<DefaultTask> flavorAssembleTask = productFlavorData.getAssembleTask();
                    if (flavorAssembleTask == null) {
                        flavorAssembleTask = taskManager.createAssembleTask(tasks, productFlavorData);
                        productFlavorData.setAssembleTask(flavorAssembleTask);
                    }
                    flavorAssembleTask.dependsOn(tasks, variantScope.getAssembleTask());
                }

                // assembleTask for this flavor(dimension), created on demand if needed.
                if (variantConfig.getProductFlavors().size() > 1) {
                    final String name = StringHelper.capitalize(variantConfig.getFlavorName());
                    final String variantAssembleTaskName = "assemble" + name;
                    if (!tasks.containsKey(variantAssembleTaskName)) {
                        tasks.create(variantAssembleTaskName, new Action<Task>() {
                            @Override
                            public void execute(Task task) {
                                task.setDescription(
                                        "Assembles all builds for flavor combination: " + name);
                                task.setGroup("Build");
                                task.dependsOn(variantScope.getAssembleTask().getName());

                            }
                        });
                    }
                    tasks.named("assemble", new Action<Task>() {
                        @Override
                        public void execute(Task task) {
                            task.dependsOn(variantAssembleTaskName);
                        }
                    });
                }
            }
        }
    }

    /**
     * Create tasks for the specified variantData.
     */
    public void createTasksForVariantData(
            final TaskFactory tasks,
            final BaseVariantData<? extends BaseVariantOutputData> variantData) {

        final BuildTypeData buildTypeData = buildTypes.get(
                variantData.getVariantConfiguration().getBuildType().getName());
        if (buildTypeData.getAssembleTask() == null) {
            buildTypeData.setAssembleTask(taskManager.createAssembleTask(tasks, buildTypeData));
        }

        // Add dependency of assemble task on assemble build type task.
        tasks.named("assemble", new Action<Task>() {
            @Override
            public void execute(Task task) {
                assert buildTypeData.getAssembleTask() != null;
                task.dependsOn(buildTypeData.getAssembleTask().getName());
            }
        });

        VariantType variantType = variantData.getType();

        createAssembleTaskForVariantData(tasks, variantData);
        if (variantType.isForTesting()) {
            final GradleVariantConfiguration testVariantConfig = variantData.getVariantConfiguration();
            final BaseVariantData testedVariantData = (BaseVariantData) ((TestVariantData) variantData)
                    .getTestedVariantData();
            final VariantType testedVariantType = testedVariantData.getVariantConfiguration().getType();

            // Add the container of dependencies, the order of the libraries is important.
            // In descending order: build type (only for unit test), flavors, defaultConfig.
            List<DefaultAndroidSourceSet> testVariantSourceSets = Lists.newArrayListWithExpectedSize(
                    2 + testVariantConfig.getProductFlavors().size());

            DefaultAndroidSourceSet buildTypeConfigurationProvider =
                    buildTypes.get(testVariantConfig.getBuildType().getName())
                            .getTestSourceSet(variantType);
            if (buildTypeConfigurationProvider != null) {
                testVariantSourceSets.add(buildTypeConfigurationProvider);
            }

            for (CoreProductFlavor productFlavor : testVariantConfig.getProductFlavors()) {
                ProductFlavorData<CoreProductFlavor> data =
                        productFlavors.get(productFlavor.getName());
                testVariantSourceSets.add(data.getTestSourceSet(variantType));
            }

            // now add the default config
            testVariantSourceSets.add(defaultConfigData.getTestSourceSet(variantType));

            // If the variant being tested is a library variant, VariantDependencies must be
            // computed after the tasks for the tested variant is created.  Therefore, the
            // VariantDependencies is computed here instead of when the VariantData was created.
            VariantDependencies.Builder builder =
                    VariantDependencies.builder(
                                    project, androidBuilder.getErrorReporter(), testVariantConfig)
                            .setPublishVariant(false)
                            .setTestedVariantType(testedVariantType)
                            .addSourceSets(testVariantSourceSets)
                            .setFlavorSelection(extension.getFlavorSelection())
                            .addTestedVariant(
                                    testedVariantData.getVariantConfiguration(),
                                    testedVariantData.getVariantDependency());

            final VariantDependencies variantDep = builder.build();
            variantData.setVariantDependency(variantDep);

            if (variantType == VariantType.ANDROID_TEST &&
                    testVariantConfig.isLegacyMultiDexMode()) {
                project.getDependencies().add(
                        variantDep.getCompileClasspath().getName(), COM_ANDROID_SUPPORT_MULTIDEX_INSTRUMENTATION);
                project.getDependencies().add(
                        variantDep.getRuntimeClasspath().getName(), COM_ANDROID_SUPPORT_MULTIDEX_INSTRUMENTATION);
            }

            if (!AndroidGradleOptions.isImprovedDependencyResolutionEnabled(project)) {
                recorder.record(
                        ExecutionType.RESOLVE_DEPENDENCIES,
                        project.getPath(),
                        testVariantConfig.getFullName(),
                        () ->
                                taskManager.resolveDependencies(
                                        variantDep, null /*testedProjectPath*/));
            }

            switch (variantType) {
                case ANDROID_TEST:
                    taskManager.createAndroidTestVariantTasks(tasks, (TestVariantData) variantData);
                    break;
                case UNIT_TEST:
                    taskManager.createUnitTestVariantTasks(tasks, (TestVariantData) variantData);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown test type " + variantType);
            }
        } else {
            taskManager.createTasksForVariantData(tasks, variantData);
        }
    }

    public void configureDependencies() {
        final DependencyHandler dependencies = project.getDependencies();

        // register transforms.
        FileCache fileCache =
                MoreObjects.firstNonNull(
                        taskManager.getGlobalScope().getBuildCache(),
                        taskManager.getGlobalScope().getProjectLevelCache());

        final String explodedAarType = AndroidArtifacts.ArtifactType.EXPLODED_AAR.getType();
        dependencies.registerTransform(
                reg -> {
                    reg.getFrom().attribute(ARTIFACT_FORMAT, AndroidArtifacts.TYPE_AAR);
                    reg.getTo().attribute(ARTIFACT_FORMAT, explodedAarType);
                    reg.artifactTransform(
                            ExtractAarTransform.class, config -> config.params(project, fileCache));
                });


        for (String transformTarget : AarTransform.getTransformTargets()) {
            dependencies.registerTransform(
                    reg -> {
                        reg.getFrom().attribute(ARTIFACT_FORMAT, explodedAarType);
                        reg.getTo().attribute(ARTIFACT_FORMAT, transformTarget);
                        reg.artifactTransform(
                                AarTransform.class, config -> config.params(transformTarget));
                    });
        }

        for (String transformTarget : JarTransform.getTransformTargets()) {
            dependencies.registerTransform(
                    reg -> {
                        reg.getFrom().attribute(ARTIFACT_FORMAT, "jar");
                        reg.getTo().attribute(ARTIFACT_FORMAT, transformTarget);
                        reg.artifactTransform(JarTransform.class);
                    });
        }

        // default is created by the java base plugin, so mark it as not consumable here.
        // TODO we need to disable this because the apt plugin fails otherwise (for now at least).
        //project.getConfigurations().getByName("compile").setCanBeResolved(false);
        //project.getConfigurations().getByName("default").setCanBeConsumed(false);

        AttributesSchema schema = dependencies.getAttributesSchema();
        // default configure attribute resolution for the build type attribute
        schema.attribute(BuildTypeAttr.ATTRIBUTE).getCompatibilityRules().assumeCompatibleWhenMissing();
        // and for the Usage attribute
        schema.attribute(Usage.USAGE_ATTRIBUTE).getCompatibilityRules().assumeCompatibleWhenMissing();
        schema.attribute(VariantAttr.ATTRIBUTE)
                .getCompatibilityRules()
                .assumeCompatibleWhenMissing();

        // FIXME remove in next Gradle nightly.
        schema.getMatchingStrategy(ArtifactAttributes.ARTIFACT_FORMAT)
                .getCompatibilityRules()
                .add(
                        details -> {
                            if (details.getConsumerValue().equals("jar")
                                    && details.getProducerValue().equals("org.gradle.java.jar")) {
                                details.compatible();
                            }
                        });

        // same for flavors, both for user-declared flavors and for attributes created from
        // absent flavor matching
        // First gather the list of attributes
        final Set<Attribute<ProductFlavorAttr>> dimensionAttributes = new HashSet<>();

        List<String> flavorDimensionList = extension.getFlavorDimensionList();
        if (flavorDimensionList != null) {
            for (String dimension : flavorDimensionList) {
                dimensionAttributes.add(Attribute.of(dimension, ProductFlavorAttr.class));
            }
        }

        // this already contains the attribute name rather than just the dimension name.
        dimensionAttributes.addAll(extension.getFlavorSelection().keySet());

        // then set a default resolution strategy. It's fine if an attribute in the consumer is
        // missing from the producer
        for (Attribute<ProductFlavorAttr> dimensionAttribute : dimensionAttributes) {
            schema.attribute(dimensionAttribute).getCompatibilityRules().assumeCompatibleWhenMissing();
        }
    }

    /**
     * Create all variants.
     */
    public void populateVariantDataList() {
        configureDependencies();
        List<String> flavorDimensionList = extension.getFlavorDimensionList();

        if (productFlavors.isEmpty()) {
            createVariantDataForProductFlavors(Collections.emptyList());
        } else {
            // ensure that there is always a dimension
            if (flavorDimensionList == null || flavorDimensionList.isEmpty()) {
                androidBuilder.getErrorReporter().handleSyncError(
                        "",
                        SyncIssue.TYPE_GENERIC,
                        "Flavor dimension name is now required even with only one dimension."
                );
            } else if (flavorDimensionList.size() == 1) {
                // if there's only one dimension, auto-assign the dimension to all the flavors.
                String dimensionName = flavorDimensionList.get(0);
                for (ProductFlavorData<CoreProductFlavor> flavorData : productFlavors.values()) {
                    CoreProductFlavor flavor = flavorData.getProductFlavor();
                    if (flavor.getDimension() == null && flavor instanceof DefaultProductFlavor) {
                        ((DefaultProductFlavor) flavor).setDimension(dimensionName);
                    }
                }
            }

            // Create iterable to get GradleProductFlavor from ProductFlavorData.
            Iterable<CoreProductFlavor> flavorDsl =
                    Iterables.transform(
                            productFlavors.values(),
                            ProductFlavorData::getProductFlavor);

            // Get a list of all combinations of product flavors.
            List<ProductFlavorCombo<CoreProductFlavor>> flavorComboList =
                    ProductFlavorCombo.createCombinations(
                            flavorDimensionList,
                            flavorDsl);

            for (ProductFlavorCombo<CoreProductFlavor>  flavorCombo : flavorComboList) {
                //noinspection unchecked
                createVariantDataForProductFlavors(
                        (List<ProductFlavor>) (List) flavorCombo.getFlavorList());
            }
        }
    }

    /**
     * Create a VariantData for a specific combination of BuildType and ProductFlavor list.
     */
    public BaseVariantData<? extends BaseVariantOutputData> createVariantData(
            @NonNull com.android.builder.model.BuildType buildType,
            @NonNull List<? extends ProductFlavor> productFlavorList) {
        BuildTypeData buildTypeData = buildTypes.get(buildType.getName());

        final DefaultAndroidSourceSet sourceSet = defaultConfigData.getSourceSet();
        GradleVariantConfiguration variantConfig =
                GradleVariantConfiguration.getBuilderForExtension(extension)
                        .create(
                                project,
                                defaultConfigData.getProductFlavor(),
                                sourceSet,
                                getParser(sourceSet.getManifestFile()),
                                buildTypeData.getBuildType(),
                                buildTypeData.getSourceSet(),
                                variantFactory.getVariantConfigurationType(),
                                signingOverride);

        if (variantConfig.getType() == LIBRARY && variantConfig.isJackEnabled()) {
            project.getLogger().warn(
                    "{}, {}: Jack compiler is not supported in library projects, falling back to javac.",
                    project.getPath(),
                    variantConfig.getFullName());
        }

        // sourceSetContainer in case we are creating variant specific sourceSets.
        NamedDomainObjectContainer<AndroidSourceSet> sourceSetsContainer = extension
                .getSourceSets();

        // We must first add the flavors to the variant config, in order to get the proper
        // variant-specific and multi-flavor name as we add/create the variant providers later.
        for (ProductFlavor productFlavor : productFlavorList) {
            ProductFlavorData<CoreProductFlavor> data = productFlavors.get(
                    productFlavor.getName());

            String dimensionName = productFlavor.getDimension();
            if (dimensionName == null) {
                dimensionName = "";
            }

            variantConfig.addProductFlavor(
                    data.getProductFlavor(),
                    data.getSourceSet(),
                    dimensionName);
        }

        createCompoundSourceSets(productFlavorList, variantConfig, sourceSetsContainer);

        // Add the container of dependencies.
        // The order of the libraries is important, in descending order:
        // variant-specific, build type, multi-flavor, flavor1, flavor2, ..., defaultConfig.
        // variant-specific if the full combo of flavors+build type. Does not exist if no flavors.
        // multi-flavor is the combination of all flavor dimensions. Does not exist if <2 dimension.
        final List<DefaultAndroidSourceSet> variantSourceSets =
                Lists.newArrayListWithExpectedSize(productFlavorList.size() + 4);

        // 1. add the variant-specific if applicable.
        if (!productFlavorList.isEmpty()) {
            variantSourceSets.add((DefaultAndroidSourceSet) variantConfig.getVariantSourceProvider());
        }

        // 2. the build type.
        variantSourceSets.add(buildTypeData.getSourceSet());

        // 3. the multi-flavor combination
        if (productFlavorList.size() > 1) {
            variantSourceSets.add((DefaultAndroidSourceSet) variantConfig.getMultiFlavorSourceProvider());
        }

        // 4. the flavors.
        for (ProductFlavor productFlavor : productFlavorList) {
            variantSourceSets.add(productFlavors.get(productFlavor.getName()).getSourceSet());
        }

        // 5. The defaultConfig
        variantSourceSets.add(defaultConfigData.getSourceSet());

        // Done. Create the variant and get its internal storage object.
        BaseVariantData<?> variantData =
                variantFactory.createVariantData(variantConfig, taskManager, recorder);

        VariantDependencies.Builder builder =
                VariantDependencies.builder(
                                project, androidBuilder.getErrorReporter(), variantConfig)
                        .setPublishVariant(true)
                        .setFlavorSelection(extension.getFlavorSelection())
                        .addSourceSets(variantSourceSets);

        final VariantDependencies variantDep = builder.build();
        variantData.setVariantDependency(variantDep);

        if (variantConfig.isLegacyMultiDexMode()) {
            project.getDependencies().add(
                    variantDep.getCompileClasspath().getName(), COM_ANDROID_SUPPORT_MULTIDEX);
            project.getDependencies().add(
                    variantDep.getRuntimeClasspath().getName(), COM_ANDROID_SUPPORT_MULTIDEX);
        }

        if (variantConfig.getRenderscriptSupportModeEnabled()) {
            File renderScriptSupportJar = androidBuilder.getRenderScriptSupportJar();

            final ConfigurableFileCollection fileCollection = project.files(renderScriptSupportJar);
            project.getDependencies()
                    .add(variantDep.getCompileClasspath().getName(), fileCollection);
            project.getDependencies()
                    .add(variantDep.getRuntimeClasspath().getName(), fileCollection);
        }


        final String testedProjectPath = extension instanceof TestAndroidConfig ?
                ((TestAndroidConfig) extension).getTargetProjectPath() :
                null;

        if (!AndroidGradleOptions.isImprovedDependencyResolutionEnabled(project)) {
            recorder.record(
                    ExecutionType.RESOLVE_DEPENDENCIES,
                    project.getPath(),
                    variantConfig.getFullName(),
                    () -> taskManager.resolveDependencies(variantDep, testedProjectPath));
        }

        return variantData;
    }

    private static void createCompoundSourceSets(
            @NonNull List<? extends ProductFlavor> productFlavorList,
            GradleVariantConfiguration variantConfig,
            NamedDomainObjectContainer<AndroidSourceSet> sourceSetsContainer) {
        if (!productFlavorList.isEmpty() && !variantConfig.getType().isSingleBuildType()) {
            DefaultAndroidSourceSet variantSourceSet =
                    (DefaultAndroidSourceSet) sourceSetsContainer.maybeCreate(
                            computeSourceSetName(
                                    variantConfig.getFullName(),
                                    variantConfig.getType()));
            variantConfig.setVariantSourceProvider(variantSourceSet);
        }

        if (productFlavorList.size() > 1) {
            DefaultAndroidSourceSet multiFlavorSourceSet =
                    (DefaultAndroidSourceSet) sourceSetsContainer.maybeCreate(
                            computeSourceSetName(
                                    variantConfig.getFlavorName(),
                                    variantConfig.getType()));
            variantConfig.setMultiFlavorSourceProvider(multiFlavorSourceSet);
        }
    }

    /**
     * Turns a string into a valid source set name for the given {@link VariantType}, e.g.
     * "fooBarUnitTest" becomes "testFooBar".
     */
    @NonNull
    private static String computeSourceSetName(
            @NonNull String name,
            @NonNull VariantType variantType) {
        if (name.endsWith(variantType.getSuffix())) {
            name = name.substring(0, name.length() - variantType.getSuffix().length());
        }

        if (!variantType.getPrefix().isEmpty()) {
            name = variantType.getPrefix() + StringHelper.capitalize(name);
        }

        return name;
    }

    /**
     * Create a TestVariantData for the specified testedVariantData.
     */
    public TestVariantData createTestVariantData(
            BaseVariantData testedVariantData,
            VariantType type) {
        CoreBuildType buildType = testedVariantData.getVariantConfiguration().getBuildType();
        BuildTypeData buildTypeData = buildTypes.get(buildType.getName());

        GradleVariantConfiguration testedConfig = testedVariantData.getVariantConfiguration();
        List<? extends CoreProductFlavor> productFlavorList = testedConfig.getProductFlavors();

        // handle test variant
        // need a suppress warning because ProductFlavor.getTestSourceSet(type) is annotated
        // to return @Nullable and the constructor is @NonNull on this parameter,
        // but it's never the case on defaultConfigData
        // The constructor does a runtime check on the instances so we should be safe.
        final DefaultAndroidSourceSet testSourceSet = defaultConfigData.getTestSourceSet(type);
        @SuppressWarnings("ConstantConditions")
        GradleVariantConfiguration testVariantConfig =
                testedConfig.getMyTestConfig(
                        testSourceSet,
                        testSourceSet != null ? getParser(testSourceSet.getManifestFile()) : null,
                        buildTypeData.getTestSourceSet(type),
                        type);


        for (CoreProductFlavor productFlavor : productFlavorList) {
            ProductFlavorData<CoreProductFlavor> data = productFlavors
                    .get(productFlavor.getName());

            String dimensionName = productFlavor.getDimension();
            if (dimensionName == null) {
                dimensionName = "";
            }
            // same supress warning here.
            //noinspection ConstantConditions
            testVariantConfig.addProductFlavor(
                    data.getProductFlavor(),
                    data.getTestSourceSet(type),
                    dimensionName);
        }

        createCompoundSourceSets(
                productFlavorList,
                testVariantConfig,
                extension.getSourceSets());

        // create the internal storage for this variant.
        TestVariantData testVariantData =
                new TestVariantData(
                        extension,
                        taskManager,
                        testVariantConfig,
                        (TestedVariantData) testedVariantData,
                        androidBuilder.getErrorReporter(),
                        recorder);
        // link the testVariant to the tested variant in the other direction
        ((TestedVariantData) testedVariantData).setTestVariantData(testVariantData, type);

        return testVariantData;
    }

    /**
     * Creates VariantData for a specified list of product flavor.
     *
     * This will create VariantData for all build types of the given flavors.
     *
     * @param productFlavorList the flavor(s) to build.
     */
    private void createVariantDataForProductFlavors(
            @NonNull List<ProductFlavor> productFlavorList) {

        BuildTypeData testBuildTypeData = null;
        if (extension instanceof TestedAndroidConfig) {
            TestedAndroidConfig testedExtension = (TestedAndroidConfig) extension;

            testBuildTypeData = buildTypes.get(testedExtension.getTestBuildType());
            if (testBuildTypeData == null) {
                throw new RuntimeException(String.format(
                        "Test Build Type '%1$s' does not exist.", testedExtension.getTestBuildType()));
            }
        }

        BaseVariantData variantForAndroidTest = null;

        CoreProductFlavor defaultConfig = defaultConfigData.getProductFlavor();

        Action<com.android.build.api.variant.VariantFilter> variantFilterAction =
                extension.getVariantFilter();

        final String restrictedProject = AndroidGradleOptions.getRestrictVariantProject(project);
        final boolean restrictVariants = restrictedProject != null;

        // compare the project name if the type is not a lib.
        final boolean projectMatch;
        final String restrictedVariantName;
        if (restrictVariants) {
            projectMatch = variantFactory.getVariantConfigurationType() != VariantType.LIBRARY &&
                    project.getPath().equals(restrictedProject);
            restrictedVariantName = AndroidGradleOptions.getRestrictVariantName(project);
        } else {
            projectMatch = false;
            restrictedVariantName = null;
        }

        for (BuildTypeData buildTypeData : buildTypes.values()) {
            boolean ignore = false;

            if (restrictVariants || variantFilterAction != null) {
                variantFilter.reset(
                        defaultConfig,
                        buildTypeData.getBuildType(),
                        variantFactory.getVariantConfigurationType(),
                        productFlavorList);

                if (restrictVariants) {
                    if (projectMatch) {
                        // get the app project, compare to this one, and if a match only accept
                        // the variant being built.
                        ignore = !variantFilter.getName().equals(restrictedVariantName);
                    }
                } else {
                    // variantFilterAction != null always true here.
                    variantFilterAction.execute(variantFilter);
                    ignore = variantFilter.isIgnore();
                }
            }

            if (!ignore) {
                BaseVariantData<?> variantData = createVariantData(
                        buildTypeData.getBuildType(),
                        productFlavorList);
                variantDataList.add(variantData);

                GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();
                ProcessProfileWriter.addVariant(project.getPath(), variantData.getName())
                        .setIsDebug(variantConfig.getBuildType().isDebuggable())
                        .setUseJack(variantConfig.isJackEnabled())
                        .setMinifyEnabled(variantConfig.isMinifyEnabled())
                        .setUseMultidex(variantConfig.isMultiDexEnabled())
                        .setUseLegacyMultidex(variantConfig.isLegacyMultiDexMode())
                        .setVariantType(variantData.getType().getAnalyticsVariantType());

                if (variantFactory.hasTestScope()) {
                    TestVariantData unitTestVariantData = createTestVariantData(
                            variantData,
                            UNIT_TEST);
                    variantDataList.add(unitTestVariantData);

                    if (buildTypeData == testBuildTypeData) {
                        if (variantConfig.isMinifyEnabled() && variantConfig.isJackEnabled()) {
                            androidBuilder.getErrorReporter().handleSyncError(
                                    variantConfig.getFullName(),
                                    SyncIssue.TYPE_JACK_IS_NOT_SUPPORTED,
                                    "Minifying the variant used for tests is not supported when "
                                            + "using Jack.");
                        }
                        variantForAndroidTest = variantData;
                    }
                }
            }
        }

        if (variantForAndroidTest != null) {
            TestVariantData androidTestVariantData = createTestVariantData(
                    variantForAndroidTest,
                    ANDROID_TEST);
            variantDataList.add(androidTestVariantData);
        }
    }

    private static void checkName(@NonNull String name, @NonNull String displayName) {
        checkPrefix(name, displayName, ANDROID_TEST.getPrefix());
        checkPrefix(name, displayName, UNIT_TEST.getPrefix());

        if (LINT.equals(name)) {
            throw new RuntimeException(String.format(
                    "%1$s names cannot be %2$s", displayName, LINT));
        }
    }

    private static void checkPrefix(String name, String displayName, String prefix) {
        if (name.startsWith(prefix)) {
            throw new RuntimeException(String.format(
                    "%1$s names cannot start with '%2$s'", displayName, prefix));
        }
    }

    private CoreSigningConfig createSigningOverride() {
        AndroidGradleOptions.SigningOptions signingOptions =
                AndroidGradleOptions.getSigningOptions(project);
        if (signingOptions != null) {
            com.android.build.gradle.internal.dsl.SigningConfig signingConfigDsl =
                    new com.android.build.gradle.internal.dsl.SigningConfig("externalOverride");

            signingConfigDsl.setStoreFile(new File(signingOptions.storeFile));
            signingConfigDsl.setStorePassword(signingOptions.storePassword);
            signingConfigDsl.setKeyAlias(signingOptions.keyAlias);
            signingConfigDsl.setKeyPassword(signingOptions.keyPassword);

            if (signingOptions.storeType != null) {
                signingConfigDsl.setStoreType(signingOptions.storeType);
            }

            if (signingOptions.v1Enabled != null) {
                signingConfigDsl.setV1SigningEnabled(signingOptions.v1Enabled);
            }

            if (signingOptions.v2Enabled != null) {
                signingConfigDsl.setV2SigningEnabled(signingOptions.v2Enabled);
            }

            return signingConfigDsl;
        }
        return null;
    }

    @NonNull
    private ManifestAttributeSupplier getParser(@NonNull File file) {
        return manifestParserMap.computeIfAbsent(file, DefaultManifestParser::new);
    }
}
