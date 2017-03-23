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
import com.android.build.gradle.internal.dependency.AndroidTypeAttr;
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
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.TaskContainer;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.internal.variant.TestVariantFactory;
import com.android.build.gradle.internal.variant.TestedVariantData;
import com.android.build.gradle.internal.variant.VariantFactory;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.SigningOptions;
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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.wireless.android.sdk.stats.GradleBuildProfileSpan.ExecutionType;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
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

/**
 * Class to create, manage variants.
 */
public class VariantManager implements VariantModel {

    private static final String MULTIDEX_VERSION = "1.0.1";

    protected static final String COM_ANDROID_SUPPORT_MULTIDEX =
            "com.android.support:multidex:" + MULTIDEX_VERSION;
    protected static final String COM_ANDROID_SUPPORT_MULTIDEX_INSTRUMENTATION =
            "com.android.support:multidex-instrumentation:" + MULTIDEX_VERSION;

    @NonNull private final Project project;
    @NonNull private final ProjectOptions projectOptions;
    @NonNull private final AndroidBuilder androidBuilder;
    @NonNull private final AndroidConfig extension;
    @NonNull private final VariantFactory variantFactory;
    @NonNull private final TaskManager taskManager;
    @NonNull private final Recorder recorder;
    @NonNull private final ProductFlavorData<CoreProductFlavor> defaultConfigData;
    @NonNull private final Map<String, BuildTypeData> buildTypes;
    @NonNull private final VariantFilter variantFilter;
    @NonNull private final List<VariantScope> variantScopes;
    @NonNull private final Map<String, ProductFlavorData<CoreProductFlavor>> productFlavors;
    @NonNull private final Map<String, SigningConfig> signingConfigs;
    @NonNull private final Map<File, ManifestAttributeSupplier> manifestParserMap;
    @NonNull protected final GlobalScope globalScope;
    @Nullable private final CoreSigningConfig signingOverride;

    public VariantManager(
            @NonNull GlobalScope globalScope,
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull AndroidConfig extension,
            @NonNull VariantFactory variantFactory,
            @NonNull TaskManager taskManager,
            @NonNull Recorder recorder) {
        this.globalScope = globalScope;
        this.extension = extension;
        this.androidBuilder = androidBuilder;
        this.project = project;
        this.projectOptions = projectOptions;
        this.variantFactory = variantFactory;
        this.taskManager = taskManager;
        this.recorder = recorder;
        this.signingOverride = createSigningOverride();
        this.variantFilter = new VariantFilter(new ReadOnlyObjectProvider());
        this.buildTypes = Maps.newHashMap();
        this.variantScopes = Lists.newArrayList();
        this.productFlavors = Maps.newHashMap();
        this.signingConfigs = Maps.newHashMap();
        this.manifestParserMap = Maps.newHashMap();

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

        this.defaultConfigData =
                new ProductFlavorData<>(
                        extension.getDefaultConfig(),
                        mainSourceSet,
                        androidTestSourceSet,
                        unitTestSourceSet,
                        project);
    }

    /**
     * Registers a new variant.
     *
     * <p>Unfortunately VariantData and VariantScope are tangled together and are really parts of
     * the same, but we'll try to gradually shift all the immutable state to VariantScope and
     * pretend that there's only an edge from scope to data.
     */
    public void addVariant(BaseVariantData variantData) {
        variantScopes.add(variantData.getScope());
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

    /** Returns a list of all created {@link VariantScope}s. */
    @NonNull
    public List<VariantScope> getVariantScopes() {
        return variantScopes;
    }

    /**
     * Returns the {@link BaseVariantData} for every {@link VariantScope} known. Don't use this, get
     * the {@link VariantScope}s instead.
     *
     * @see #getVariantScopes()
     * @deprecated Kept only not to break the Kotlin plugin.
     */
    @NonNull
    @Deprecated
    public List<BaseVariantData> getVariantDataList() {
        List<BaseVariantData> result = Lists.newArrayListWithExpectedSize(variantScopes.size());
        for (VariantScope variantScope : variantScopes) {
            result.add(variantScope.getVariantData());
        }
        return result;
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
        if (variantScopes.isEmpty()) {
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

        for (final VariantScope variantScope : variantScopes) {
            recorder.record(
                    ExecutionType.VARIANT_MANAGER_CREATE_TASKS_FOR_VARIANT,
                    project.getPath(),
                    variantScope.getFullVariantName(),
                    () -> createTasksForVariantData(tasks, variantScope));
        }

        taskManager.createReportTasks(tasks, variantScopes);
    }

    /** Create assemble task for VariantData. */
    private void createAssembleTaskForVariantData(
            TaskFactory tasks, final BaseVariantData variantData) {
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

    /** Create tasks for the specified variant. */
    public void createTasksForVariantData(
            final TaskFactory tasks, final VariantScope variantScope) {
        BaseVariantData variantData = variantScope.getVariantData();
        VariantType variantType = variantData.getType();

        final BuildTypeData buildTypeData =
                buildTypes.get(variantScope.getVariantConfiguration().getBuildType().getName());
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

        createAssembleTaskForVariantData(tasks, variantData);
        if (variantType.isForTesting()) {
            final GradleVariantConfiguration testVariantConfig =
                    variantScope.getVariantConfiguration();
            final BaseVariantData testedVariantData =
                    (BaseVariantData) ((TestVariantData) variantData).getTestedVariantData();
            final VariantType testedVariantType = testedVariantData.getVariantConfiguration().getType();

            // FIXME: Remove this once we have proper tasks set for feature variants.
            if (testedVariantType == VariantType.FEATURE) {
                return;
            }

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
                            .setConsumeType(
                                    getConsumeType(
                                            testedVariantData.getVariantConfiguration().getType()))
                            .setTestedVariantType(testedVariantType)
                            .addSourceSets(testVariantSourceSets)
                            .setFlavorSelection(extension.getFlavorSelection());

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
            taskManager.createTasksForVariantScope(tasks, variantScope);
        }
    }

    @NonNull
    private AndroidTypeAttr getConsumeType(@NonNull VariantType type) {
        switch (type) {
            case DEFAULT:
                if (variantFactory instanceof TestVariantFactory) {
                    return AndroidTypeAttr.TYPE_APK;
                }
                return AndroidTypeAttr.TYPE_AAR;
            case LIBRARY:
                return AndroidTypeAttr.TYPE_AAR;
            case FEATURE:
                // FIXME this should disappear...
            case ATOM:
                // FIXME this should disappear...
            case INSTANTAPP:
                return AndroidTypeAttr.TYPE_FEATURE;
            case ANDROID_TEST:
            case UNIT_TEST:
                throw new IllegalStateException(
                        "Variant type '" + type + "' should not be publishing anything");
        }
        throw new IllegalStateException(
                "Unsupported VariantType requested in getConsumeType(): " + type);
    }

    @NonNull
    private static AndroidTypeAttr getPublishingType(@NonNull VariantType type) {
        switch (type) {
            case DEFAULT:
                return AndroidTypeAttr.TYPE_APK;
            case LIBRARY:
                return AndroidTypeAttr.TYPE_AAR;
            case FEATURE:
                // FIXME this should disappear...
            case ATOM:
                // FIXME this should disappear...
            case INSTANTAPP:
                return AndroidTypeAttr.TYPE_FEATURE;
            case ANDROID_TEST:
            case UNIT_TEST:
                throw new IllegalStateException(
                        "Variant type '" + type + "' should not be publishing anything");
        }
        throw new IllegalStateException(
                "Unsupported VariantType requested in getPublishingType(): " + type);
    }

    public void configureDependencies() {
        final DependencyHandler dependencies = project.getDependencies();

        // register transforms.
        FileCache fileCache =
                MoreObjects.firstNonNull(
                        globalScope.getBuildCache(), globalScope.getProjectLevelCache());

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
        // type for aar vs split.
        schema.attribute(AndroidTypeAttr.ATTRIBUTE)
                .getCompatibilityRules()
                .assumeCompatibleWhenMissing();
        schema.attribute(AndroidTypeAttr.ATTRIBUTE)
                .getCompatibilityRules()
                .add(
                        details -> {
                            final AndroidTypeAttr producerValue = details.getProducerValue();
                            final AndroidTypeAttr consumerValue = details.getConsumerValue();
                            if (producerValue.equals(consumerValue)) {
                                details.compatible();
                            } else {
                                if (AndroidTypeAttr.TYPE_AAR.equals(producerValue)
                                        && AndroidTypeAttr.TYPE_FEATURE.equals(consumerValue)) {
                                    details.compatible();
                                }
                            }
                        });
        schema.attribute(AndroidTypeAttr.ATTRIBUTE)
                .getDisambiguationRules()
                .add(
                        details -> {
                            // we should only get here, with both split and aar.
                            Set<AndroidTypeAttr> values = details.getCandidateValues();
                            if (values.size() == 2
                                    && values.contains(AndroidTypeAttr.TYPE_AAR)
                                    && values.contains(AndroidTypeAttr.TYPE_FEATURE)) {
                                details.closestMatch(AndroidTypeAttr.TYPE_FEATURE);
                            }
                        });

        // variant name as an attribute, this is to get the variant name on the consumption side.
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

    public Collection<BaseVariantData> createVariantData(
            @NonNull com.android.builder.model.BuildType buildType,
            @NonNull List<? extends ProductFlavor> productFlavorList) {
        ImmutableList.Builder<BaseVariantData> variantDataBuilder = new ImmutableList.Builder<>();
        for (VariantType variantType : variantFactory.getVariantConfigurationTypes()) {
            variantDataBuilder.add(
                    createVariantDataForVariantType(buildType, productFlavorList, variantType));
        }
        return variantDataBuilder.build();
    }

    private BaseVariantData createVariantDataForVariantType(
            @NonNull com.android.builder.model.BuildType buildType,
            @NonNull List<? extends ProductFlavor> productFlavorList,
            @NonNull VariantType variantType) {
        BuildTypeData buildTypeData = buildTypes.get(buildType.getName());

        final DefaultAndroidSourceSet sourceSet = defaultConfigData.getSourceSet();
        GradleVariantConfiguration variantConfig =
                GradleVariantConfiguration.getBuilderForExtension(extension)
                        .create(
                                globalScope.getProjectOptions(),
                                defaultConfigData.getProductFlavor(),
                                sourceSet,
                                getParser(sourceSet.getManifestFile()),
                                buildTypeData.getBuildType(),
                                buildTypeData.getSourceSet(),
                                variantType,
                                signingOverride);

        if (variantType == LIBRARY && variantConfig.isJackEnabled()) {
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
        BaseVariantData variantData =
                variantFactory.createVariantData(variantConfig, taskManager, recorder);

        VariantDependencies.Builder builder =
                VariantDependencies.builder(
                                project, androidBuilder.getErrorReporter(), variantConfig)
                        .setConsumeType(
                                getConsumeType(variantData.getVariantConfiguration().getType()))
                        .setPublishType(
                                getPublishingType(variantData.getVariantConfiguration().getType()))
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
                        globalScope,
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
        for (VariantType variantType : variantFactory.getVariantConfigurationTypes()) {
            createVariantDataForProductFlavorsAndVariantType(productFlavorList, variantType);
        }
    }

    private void createVariantDataForProductFlavorsAndVariantType(
            @NonNull List<ProductFlavor> productFlavorList, @NonNull VariantType variantType) {

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
            projectMatch =
                    variantType != VariantType.LIBRARY
                            && project.getPath().equals(restrictedProject);
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
                        variantType,
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
                BaseVariantData variantData =
                        createVariantDataForVariantType(
                                buildTypeData.getBuildType(), productFlavorList, variantType);
                addVariant(variantData);

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
                    addVariant(unitTestVariantData);

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
            addVariant(androidTestVariantData);
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
        SigningOptions signingOptions = SigningOptions.readSigningOptions(projectOptions);
        if (signingOptions != null) {
            com.android.build.gradle.internal.dsl.SigningConfig signingConfigDsl =
                    new com.android.build.gradle.internal.dsl.SigningConfig("externalOverride");

            signingConfigDsl.setStoreFile(new File(signingOptions.getStoreFile()));
            signingConfigDsl.setStorePassword(signingOptions.getStorePassword());
            signingConfigDsl.setKeyAlias(signingOptions.getKeyAlias());
            signingConfigDsl.setKeyPassword(signingOptions.getKeyPassword());

            if (signingOptions.getStoreType() != null) {
                signingConfigDsl.setStoreType(signingOptions.getStoreType());
            }

            if (signingOptions.getV1Enabled() != null) {
                signingConfigDsl.setV1SigningEnabled(signingOptions.getV1Enabled());
            }

            if (signingOptions.getV2Enabled() != null) {
                signingConfigDsl.setV2SigningEnabled(signingOptions.getV2Enabled());
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
