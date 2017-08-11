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
package com.android.build.gradle;

import static com.android.builder.model.SyncIssue.TYPE_GENERIC;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.Transform;
import com.android.build.api.variant.VariantFilter;
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.CompileOptions;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.SourceSetSourceProviderWrapper;
import com.android.build.gradle.internal.coverage.JacocoOptions;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.dsl.AdbOptions;
import com.android.build.gradle.internal.dsl.AndroidSourceSetFactory;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.DataBindingOptions;
import com.android.build.gradle.internal.dsl.DefaultConfig;
import com.android.build.gradle.internal.dsl.DexOptions;
import com.android.build.gradle.internal.dsl.ExternalNativeBuild;
import com.android.build.gradle.internal.dsl.LintOptions;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.dsl.Splits;
import com.android.build.gradle.internal.dsl.TestOptions;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.LibraryRequest;
import com.android.builder.model.SourceProvider;
import com.android.builder.sdk.TargetInfo;
import com.android.builder.testing.api.DeviceProvider;
import com.android.builder.testing.api.TestServer;
import com.android.repository.Revision;
import com.android.resources.Density;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.SourceSet;
import org.gradle.internal.reflect.Instantiator;

/**
 * Base 'android' extension for all android plugins.
 *
 * <p>This is never used directly. Instead,
 *
 * <ul>
 *   <li>Plugin {@code com.android.application} uses {@link AppExtension}.
 *   <li>Plugin {@code com.android.library} uses {@link LibraryExtension}.
 *   <li>Plugin {@code com.android.test} uses {@link TestExtension}.
 * </ul>
 */
// All the public methods are meant to be exposed in the DSL. We can't use lambdas in this class
// (yet), because the DSL reference generator doesn't understand them.
@SuppressWarnings({"UnnecessaryInheritDoc", "WeakerAccess", "unused", "Convert2Lambda"})
public abstract class BaseExtension implements AndroidConfig {

    /** Secondary dependencies for the custom transform. */
    private final List<List<Object>> transformDependencies = Lists.newArrayList();

    private final AndroidBuilder androidBuilder;

    private final SdkHandler sdkHandler;

    private final DefaultConfig defaultConfig;

    private final AaptOptions aaptOptions;

    private final LintOptions lintOptions;

    private final ExternalNativeBuild externalNativeBuild;

    private final DexOptions dexOptions;

    private final TestOptions testOptions;

    private final CompileOptions compileOptions;

    private final PackagingOptions packagingOptions;

    private final JacocoOptions jacoco;

    private final Splits splits;

    private final AdbOptions adbOptions;

    private final NamedDomainObjectContainer<ProductFlavor> productFlavors;

    private final NamedDomainObjectContainer<BuildType> buildTypes;

    private final NamedDomainObjectContainer<SigningConfig> signingConfigs;

    private final NamedDomainObjectContainer<BaseVariantOutput> buildOutputs;

    private final List<DeviceProvider> deviceProviderList = Lists.newArrayList();

    private final List<TestServer> testServerList = Lists.newArrayList();

    private final List<Transform> transforms = Lists.newArrayList();

    private final DataBindingOptions dataBinding;

    private final NamedDomainObjectContainer<AndroidSourceSet> sourceSetsContainer;

    private String target;

    @NonNull private Revision buildToolsRevision;

    private List<LibraryRequest> libraryRequests = Lists.newArrayList();

    private List<String> flavorDimensionList;

    private String resourcePrefix;

    private ExtraModelInfo extraModelInfo;

    private String defaultPublishConfig = "release";

    private Action<VariantFilter> variantFilter;

    protected Logger logger;

    private boolean isWritable = true;

    protected Project project;

    private final ProjectOptions projectOptions;

    BaseExtension(
            @NonNull final Project project,
            @NonNull final ProjectOptions projectOptions,
            @NonNull Instantiator instantiator,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull SdkHandler sdkHandler,
            @NonNull NamedDomainObjectContainer<BuildType> buildTypes,
            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavors,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigs,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> buildOutputs,
            @NonNull ExtraModelInfo extraModelInfo,
            final boolean publishPackage) {
        this.androidBuilder = androidBuilder;
        this.sdkHandler = sdkHandler;
        this.buildTypes = buildTypes;
        //noinspection unchecked
        this.productFlavors = productFlavors;
        this.signingConfigs = signingConfigs;
        this.extraModelInfo = extraModelInfo;
        this.buildOutputs = buildOutputs;
        this.project = project;
        this.projectOptions = projectOptions;

        logger = Logging.getLogger(this.getClass());

        defaultConfig =
                instantiator.newInstance(
                        DefaultConfig.class,
                        BuilderConstants.MAIN,
                        project,
                        instantiator,
                        project.getLogger(),
                        extraModelInfo);

        aaptOptions = instantiator.newInstance(AaptOptions.class);
        dexOptions = instantiator.newInstance(DexOptions.class, extraModelInfo);
        lintOptions = instantiator.newInstance(LintOptions.class);
        externalNativeBuild = instantiator.newInstance(
                ExternalNativeBuild.class, instantiator, project);
        testOptions = instantiator.newInstance(TestOptions.class, instantiator);
        compileOptions = instantiator.newInstance(CompileOptions.class);
        packagingOptions = instantiator.newInstance(PackagingOptions.class);
        jacoco = instantiator.newInstance(JacocoOptions.class);
        adbOptions = instantiator.newInstance(AdbOptions.class);
        splits = instantiator.newInstance(Splits.class, instantiator);
        dataBinding = instantiator.newInstance(DataBindingOptions.class);

        sourceSetsContainer =
                project.container(
                        AndroidSourceSet.class,
                        new AndroidSourceSetFactory(instantiator, project, publishPackage));

        sourceSetsContainer.whenObjectAdded(
                new Action<AndroidSourceSet>() {
                    @Override
                    public void execute(AndroidSourceSet sourceSet) {
                        ConfigurationContainer configurations = project.getConfigurations();

                        final String implementationName =
                                sourceSet.getImplementationConfigurationName();
                        final String runtimeOnlyName = sourceSet.getRuntimeOnlyConfigurationName();
                        final String compileOnlyName = sourceSet.getCompileOnlyConfigurationName();

                        // deprecated configurations first.
                        final String compileName = sourceSet.getCompileConfigurationName();
                        // due to compatibility with other plugins and with Gradle sync,
                        // we have to keep 'compile' as resolvable.
                        // TODO Fix this in gradle sync.
                        Configuration compile =
                                createConfiguration(
                                        configurations,
                                        compileName,
                                        String.format(
                                                CONFIG_DESC_OLD,
                                                "Compile",
                                                sourceSet.getName(),
                                                implementationName),
                                        "compile".equals(compileName)
                                                || "testCompile"
                                                        .equals(compileName) /*canBeResolved*/);
                        compile.getAllDependencies()
                                .whenObjectAdded(
                                        new DeprecatedConfigurationAction(
                                                project, compile, implementationName));

                        String packageConfigDescription;
                        if (publishPackage) {
                            packageConfigDescription =
                                    String.format(
                                            CONFIG_DESC_OLD,
                                            "Publish",
                                            sourceSet.getName(),
                                            runtimeOnlyName);
                        } else {
                            packageConfigDescription =
                                    String.format(
                                            CONFIG_DESC_OLD,
                                            "Apk",
                                            sourceSet.getName(),
                                            runtimeOnlyName);
                        }

                        Configuration apk =
                                createConfiguration(
                                        configurations,
                                        sourceSet.getPackageConfigurationName(),
                                        packageConfigDescription);
                        apk.getAllDependencies()
                                .whenObjectAdded(
                                        new DeprecatedConfigurationAction(
                                                project, apk, runtimeOnlyName));

                        Configuration provided =
                                createConfiguration(
                                        configurations,
                                        sourceSet.getProvidedConfigurationName(),
                                        String.format(
                                                CONFIG_DESC_OLD,
                                                "Provided",
                                                sourceSet.getName(),
                                                compileOnlyName));
                        provided.getAllDependencies()
                                .whenObjectAdded(
                                        new DeprecatedConfigurationAction(
                                                project, provided, compileOnlyName));

                        // then the new configurations.
                        String apiName = sourceSet.getApiConfigurationName();
                        Configuration api =
                                createConfiguration(
                                        configurations,
                                        apiName,
                                        String.format(CONFIG_DESC, "API", sourceSet.getName()));
                        api.extendsFrom(compile);

                        Configuration implementation =
                                createConfiguration(
                                        configurations,
                                        implementationName,
                                        String.format(
                                                CONFIG_DESC,
                                                "Implementation only",
                                                sourceSet.getName()));
                        implementation.extendsFrom(api);

                        Configuration runtimeOnly =
                                createConfiguration(
                                        configurations,
                                        runtimeOnlyName,
                                        String.format(
                                                CONFIG_DESC, "Runtime only", sourceSet.getName()));
                        runtimeOnly.extendsFrom(apk);

                        Configuration compileOnly =
                                createConfiguration(
                                        configurations,
                                        compileOnlyName,
                                        String.format(
                                                CONFIG_DESC, "Compile only", sourceSet.getName()));
                        compileOnly.extendsFrom(provided);

                        // then the secondary configurations.
                        Configuration wearConfig =
                                createConfiguration(
                                        configurations,
                                        sourceSet.getWearAppConfigurationName(),
                                        "Link to a wear app to embed for object '"
                                                + sourceSet.getName()
                                                + "'.");

                        createConfiguration(
                                configurations,
                                sourceSet.getAnnotationProcessorConfigurationName(),
                                "Classpath for the annotation processor for '"
                                        + sourceSet.getName()
                                        + "'.");

                        sourceSet.setRoot(String.format("src/%s", sourceSet.getName()));
                    }
                });

        // Create the "special" configuration for test buddy APKs. It will be resolved by the test
        // running task, so that we can install all the found APKs before running tests.
        createConfiguration(
                project.getConfigurations(),
                SdkConstants.GRADLE_ANDROID_TEST_UTIL_CONFIGURATION,
                "Additional APKs used during instrumentation testing.",
                true);

        sourceSetsContainer.create(defaultConfig.getName());
        buildToolsRevision = AndroidBuilder.DEFAULT_BUILD_TOOLS_REVISION;
        setDefaultConfigValues();
    }

    private void setDefaultConfigValues() {
        Set<Density> densities = Density.getRecommendedValuesForDevice();
        Set<String> strings = Sets.newHashSetWithExpectedSize(densities.size());
        for (Density density : densities) {
            strings.add(density.getResourceValue());
        }
        defaultConfig.getVectorDrawables().setGeneratedDensities(strings);
        defaultConfig.getVectorDrawables().setUseSupportLibrary(false);
    }

    /**
     * Disallow further modification on the extension.
     */
    public void disableWrite() {
        isWritable = false;
    }

    protected void checkWritability() {
        if (!isWritable) {
            throw new GradleException(
                    "Android tasks have already been created.\n" +
                            "This happens when calling android.applicationVariants,\n" +
                            "android.libraryVariants or android.testVariants.\n" +
                            "Once these methods are called, it is not possible to\n" +
                            "continue configuring the model.");
        }
    }

    /**
     * Creates a Configuration for a given source set.
     *
     * The configuration cannot be resolved
     *
     * @param configurations the configuration container to create the new configuration
     * @param name the name of the configuration to create.
     * @param description the configuration description.
     * @return the configuration
     *
     * @see Configuration#isCanBeResolved()
     */
    private Configuration createConfiguration(
            @NonNull ConfigurationContainer configurations,
            @NonNull String name,
            @NonNull String description) {
        return createConfiguration(configurations, name, description, false);
    }

    /**
     * Creates a Configuration for a given source set.
     *
     * @param configurations the configuration container to create the new configuration
     * @param name the name of the configuration to create.
     * @param description the configuration description.
     * @param canBeResolved Whether the configuration can be resolved directly.
     * @return the configuration
     *
     * @see Configuration#isCanBeResolved()
     */
    private Configuration createConfiguration(
            @NonNull ConfigurationContainer configurations,
            @NonNull String name,
            @NonNull String description,
            boolean canBeResolved) {
        logger.info("Creating configuration {}", name);

        Configuration configuration = configurations.findByName(name);
        if (configuration == null) {
            configuration = configurations.create(name);
        }
        configuration.setVisible(false);
        configuration.setDescription(description);
        configuration.setCanBeConsumed(false);
        configuration.setCanBeResolved(canBeResolved);

        return configuration;
    }

    /**
     * Sets the compile SDK version, based on full SDK version string, e.g. {@code android-21} for
     * Lollipop.
     */
    public void compileSdkVersion(String version) {
        checkWritability();
        this.target = version;
    }

    /**
     * Sets the compile SDK version, based on API level, e.g. 21 for Lollipop.
     */
    public void compileSdkVersion(int apiLevel) {
        compileSdkVersion("android-" + apiLevel);
    }

    public void setCompileSdkVersion(int apiLevel) {
        compileSdkVersion(apiLevel);
    }

    public void setCompileSdkVersion(String target) {
        compileSdkVersion(target);
    }

    /**
     * Request the use a of Library. The library is then added to the classpath.
     * @param name the name of the library.
     */
    public void useLibrary(String name) {
        useLibrary(name, true);
    }

    /**
     * Request the use a of Library. The library is then added to the classpath.
     * @param name the name of the library.
     * @param required if using the library requires a manifest entry, the  entry will
     * indicate that the library is not required.
     */
    public void useLibrary(String name, boolean required) {
        libraryRequests.add(new LibraryRequest(name, required));
    }

    public void buildToolsVersion(String version) {
        checkWritability();
        //The underlying Revision class has the maven artifact semantic,
        // so 20 is not the same as 20.0. For the build tools revision this
        // is not the desired behavior, so normalize e.g. to 20.0.0.
        buildToolsRevision = Revision.parseRevision(version, Revision.Precision.MICRO);
    }

    /**
     * Version of the build tools to use.
     *
     * <p>Each released plugin has a fixed default, so builds are reproducible.
     *
     * <table>
     *     <caption>Versions of build tools</caption>
     *     <tr><th>Gradle plugin</th><th>Minimum build tools</th><th>Default build tools</th></tr>
     *     <tr><td>2.0.x</td><td>21.1.1</td><td>-</td></tr>
     *     <tr><td>2.1.x</td><td>23.0.2</td><td>-</td></tr>
     *     <tr><td>2.2.x</td><td>23.0.2</td><td>-</td></tr>
     *     <tr><td>2.3.x</td><td>23.0.2</td><td>-</td></tr>
     *     <tr><td>2.4.x</td><td>25.0.0</td><td>25.0.2</td></tr>
     *     <tr><td>3.0.x</td><td>25.0.0</td><td>26.0.0</td></tr>
     * </table>
     *
     * <p>The value assigned to this property is parsed and stored in a normalized form, so reading
     * it back may give a slightly different string.
     */
    @Override
    public String getBuildToolsVersion() {
        return buildToolsRevision.toString();
    }

    public void setBuildToolsVersion(String version) {
        buildToolsVersion(version);
    }

    /**
     * Configures build types.
     */
    public void buildTypes(Action<? super NamedDomainObjectContainer<BuildType>> action) {
        checkWritability();
        action.execute(buildTypes);
    }

    /**
     * Configures product flavors.
     */
    public void productFlavors(Action<? super NamedDomainObjectContainer<ProductFlavor>> action) {
        checkWritability();
        action.execute(productFlavors);
    }

    /**
     * Configures signing configs.
     */
    public void signingConfigs(Action<? super NamedDomainObjectContainer<SigningConfig>> action) {
        checkWritability();
        action.execute(signingConfigs);
    }

    /**
     * Specifies names of flavor dimensions. Order in which the dimensions are specified matters,
     * as it indicates the merging priority that decreases from the first to the last dimensions.
     * The default product flavor has the lowest priority. In case there are properties that are
     * defined in multiple product flavors, the one with the higher priority is selected.
     *
     * <p>See <a href="https://developer.android.com/studio/build/build-variants.html#flavor-dimensions">Multi-flavor variants</a>.
     */
    public void flavorDimensions(String... dimensions) {
        checkWritability();
        flavorDimensionList = Arrays.asList(dimensions);
    }

    /**
     * Configures source sets.
     *
     * <p>Note that the Android plugin uses its own implementation of source sets,
     * {@link AndroidSourceSet}.
     */
    public void sourceSets(Action<NamedDomainObjectContainer<AndroidSourceSet>> action) {
        checkWritability();
        action.execute(sourceSetsContainer);
    }

    /**
     * All source sets. Note that the Android plugin uses its own implementation of
     * source sets, {@link AndroidSourceSet}.
     */
    @Override
    public NamedDomainObjectContainer<AndroidSourceSet> getSourceSets() {
        return sourceSetsContainer;
    }

    /**
     * All build outputs for all variants, can be used by users to customize a build output.
     *
     * @return a container for build outputs.
     */
    @Override
    public NamedDomainObjectContainer<BaseVariantOutput> getBuildOutputs() {
        return buildOutputs;
    }

    /** The default configuration, inherited by all product flavors (if any are defined). */
    public void defaultConfig(Action<DefaultConfig> action) {
        checkWritability();
        action.execute(defaultConfig);
    }

    /**
     * Configures aapt options.
     */
    public void aaptOptions(Action<AaptOptions> action) {
        checkWritability();
        action.execute(aaptOptions);
    }

    /**
     * Configures dex options.
     */
    public void dexOptions(Action<DexOptions> action) {
        checkWritability();
        action.execute(dexOptions);
    }

    /**
     * Configures lint options.
     */
    public void lintOptions(Action<LintOptions> action) {
        checkWritability();
        action.execute(lintOptions);
    }

    /**
     * Configures external native build options.
     */
    public void externalNativeBuild(Action<ExternalNativeBuild> action) {
        checkWritability();
        action.execute(externalNativeBuild);
    }

    /** Configures test options. */
    public void testOptions(Action<TestOptions> action) {
        checkWritability();
        action.execute(testOptions);
    }

    /**
     * Configures compile options.
     */
    public void compileOptions(Action<CompileOptions> action) {
        checkWritability();
        action.execute(compileOptions);
    }

    /**
     * Configures packaging options.
     */
    public void packagingOptions(Action<PackagingOptions> action) {
        checkWritability();
        action.execute(packagingOptions);
    }

    /**
     * Configures JaCoCo options.
     */
    public void jacoco(Action<JacocoOptions> action) {
        checkWritability();
        action.execute(jacoco);
    }

    /**
     * Configures adb options.
     */
    public void adbOptions(Action<AdbOptions> action) {
        checkWritability();
        action.execute(adbOptions);
    }

    /**
     * Configures APK splits.
     */
    public void splits(Action<Splits> action) {
        checkWritability();
        action.execute(splits);
    }

    /**
     * Configures data binding options.
     */
    public void dataBinding(Action<DataBindingOptions> action) {
        checkWritability();
        action.execute(dataBinding);
    }

    /** {@inheritDoc} */
    @Override
    public DataBindingOptions getDataBinding() {
        return dataBinding;
    }

    public void deviceProvider(DeviceProvider deviceProvider) {
        checkWritability();
        deviceProviderList.add(deviceProvider);
    }

    @Override
    @NonNull
    public List<DeviceProvider> getDeviceProviders() {
        return deviceProviderList;
    }

    public void testServer(TestServer testServer) {
        checkWritability();
        testServerList.add(testServer);
    }

    @Override
    @NonNull
    public List<TestServer> getTestServers() {
        return testServerList;
    }

    public void registerTransform(@NonNull Transform transform, Object... dependencies) {
        transforms.add(transform);
        transformDependencies.add(Arrays.asList(dependencies));
    }

    @Override
    @NonNull
    public List<Transform> getTransforms() {
        return ImmutableList.copyOf(transforms);
    }

    @Override
    @NonNull
    public List<List<Object>> getTransformsDependencies() {
        return ImmutableList.copyOf(transformDependencies);
    }

    /** {@inheritDoc} */
    @Override
    public NamedDomainObjectContainer<ProductFlavor> getProductFlavors() {
        return productFlavors;
    }

    /** {@inheritDoc} */
    @Override
    public NamedDomainObjectContainer<BuildType> getBuildTypes() {
        return buildTypes;
    }

    /** {@inheritDoc} */
    @Override
    public NamedDomainObjectContainer<SigningConfig> getSigningConfigs() {
        return signingConfigs;
    }

    public void defaultPublishConfig(String value) {
        setDefaultPublishConfig(value);
    }

    /**
     * Name of the configuration used to build the default artifact of this project, used for
     * publishing to Maven
     *
     * <p>See <a href="https://developer.android.com/studio/build/dependencies.html">
     * Add Build Dependencies</a>
     */
    @Override
    public String getDefaultPublishConfig() {
        return defaultPublishConfig;
    }

    public void setDefaultPublishConfig(String value) {
        defaultPublishConfig = value;
    }

    public void setPublishNonDefault(boolean publishNonDefault) {
        logger.warn("publishNonDefault is deprecated and has no effect anymore. All variants are now published.");
    }

    public void buildTypeMatching(@NonNull String consumer, @NonNull String... alternates) {
        // STOPSHIP
        extraModelInfo.handleSyncError(
                null,
                TYPE_GENERIC,
                "buildTypeMatching has been removed. Use buildTypes.<name>.fallbacks ...");
    }

    public void productFlavorMatching(
            @NonNull String consumerDimension,
            @NonNull String consumer,
            @NonNull String... alternates) {
        // STOPSHIP
        extraModelInfo.handleSyncError(
                null,
                TYPE_GENERIC,
                "productFlavorMatching has been removed. Use productFlavors.<name>.fallbacks ...");
    }

    public void variantFilter(Action<VariantFilter> filter) {
        setVariantFilter(filter);
    }

    public void setVariantFilter(Action<VariantFilter> filter) {
        variantFilter = filter;
    }

    /**
     * Callback to control which variants should be excluded.
     *
     * <p>The {@link Action} is passed a single object of type {@link VariantFilter}.
     * It should set the {@link VariantFilter#setIgnore(boolean)} flag to filter out the
     * given variant.
     */
    @Override
    public Action<VariantFilter> getVariantFilter() {
        return variantFilter;
    }

    /** {@inheritDoc} */
    @Override
    public AdbOptions getAdbOptions() {
        return adbOptions;
    }

    /** {@inheritDoc} */
    @Override
    public String getResourcePrefix() {
        return resourcePrefix;
    }

    /**
     * Returns the names of flavor dimensions.
     *
     * <p>See <a href="https://developer.android.com/studio/build/build-variants.html#flavor-dimensions">Multi-flavor variants</a>.
     */
    @Override
    public List<String> getFlavorDimensionList() {
        return flavorDimensionList;
    }

    /** {@inheritDoc} */
    @Override
    public boolean getGeneratePureSplits() {
        return generatePureSplits;
    }

    public void resourcePrefix(String prefix) {
        resourcePrefix = prefix;
    }

    public abstract void addVariant(BaseVariant variant);

    public void registerArtifactType(@NonNull String name,
            boolean isTest,
            int artifactType) {
        extraModelInfo.registerArtifactType(name, isTest, artifactType);
    }

    public void registerBuildTypeSourceProvider(
            @NonNull String name,
            @NonNull BuildType buildType,
            @NonNull SourceProvider sourceProvider) {
        extraModelInfo.registerBuildTypeSourceProvider(name, buildType, sourceProvider);
    }

    public void registerProductFlavorSourceProvider(
            @NonNull String name,
            @NonNull ProductFlavor productFlavor,
            @NonNull SourceProvider sourceProvider) {
        extraModelInfo.registerProductFlavorSourceProvider(name, productFlavor, sourceProvider);
    }

    public void registerJavaArtifact(
            @NonNull String name,
            @NonNull BaseVariant variant,
            @NonNull String assembleTaskName,
            @NonNull String javaCompileTaskName,
            @NonNull Collection<File> generatedSourceFolders,
            @NonNull Iterable<String> ideSetupTaskNames,
            @NonNull Configuration configuration,
            @NonNull File classesFolder,
            @NonNull File javaResourceFolder,
            @Nullable SourceProvider sourceProvider) {
        extraModelInfo.registerJavaArtifact(name, variant, assembleTaskName,
                javaCompileTaskName, generatedSourceFolders, ideSetupTaskNames,
                configuration, classesFolder, javaResourceFolder, sourceProvider);
    }

    public void registerMultiFlavorSourceProvider(
            @NonNull String name,
            @NonNull String flavorName,
            @NonNull SourceProvider sourceProvider) {
        extraModelInfo.registerMultiFlavorSourceProvider(name, flavorName, sourceProvider);
    }

    @NonNull
    public static SourceProvider wrapJavaSourceSet(@NonNull SourceSet sourceSet) {
        return new SourceSetSourceProviderWrapper(sourceSet);
    }

    /**
     * <strong>Required.</strong> Compile SDK version.
     *
     * <p>Your code will be compiled against the android.jar from this API level. You should
     * generally use the most up-to-date SDK version here. Use the Lint tool to make sure you don't
     * use APIs not available in earlier platform version without checking.
     *
     * <p>Setter can be called with a string like "android-21" or a number.
     *
     * <p>Value assigned to this property is parsed and stored in a normalized form, so reading it
     * back may give a slightly different string.
     */
    @Override
    public String getCompileSdkVersion() {
        return target;
    }

    @NonNull
    @Override
    public Revision getBuildToolsRevision() {
        return buildToolsRevision;
    }

    @Override
    public Collection<LibraryRequest> getLibraryRequests() {
        return libraryRequests;
    }

    /**
     * Returns the SDK directory used.
     */
    public File getSdkDirectory() {
        return sdkHandler.getSdkFolder();
    }

    /**
     * ReturnS the NDK directory used.
     */
    public File getNdkDirectory() {
        return sdkHandler.getNdkFolder();
    }

    public List<File> getBootClasspath() {
        ensureTargetSetup();
        return androidBuilder.getBootClasspath(false);
    }

    /**
     * The adb executable from the compile SDK.
     */
    public File getAdbExecutable() {
        return sdkHandler.getSdkInfo().getAdb();
    }

    @Deprecated
    public File getAdbExe() {
        return getAdbExecutable();
        // test
    }

    public File getDefaultProguardFile(String name) {
        if (!ProguardFiles.KNOWN_FILE_NAMES.contains(name)) {
            extraModelInfo.handleSyncError(
                    null, TYPE_GENERIC, ProguardFiles.UNKNOWN_FILENAME_MESSAGE);
        }
        return ProguardFiles.getDefaultProguardFile(name, project);
    }

    // ---------------
    // TEMP for compatibility

    // by default, we do not generate pure splits
    boolean generatePureSplits = false;

    public void generatePureSplits(boolean flag) {
        setGeneratePureSplits(flag);
    }

    public void setGeneratePureSplits(boolean flag) {
        if (flag) {
            logger.warn("Pure splits are not supported by PlayStore yet.");
        }
        this.generatePureSplits = flag;
    }

    /** {@inheritDoc} */
    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    /** {@inheritDoc} */
    @Override
    public AaptOptions getAaptOptions() {
        return aaptOptions;
    }

    /** {@inheritDoc} */
    @Override
    public CompileOptions getCompileOptions() {
        return compileOptions;
    }

    /** {@inheritDoc} */
    @Override
    public DexOptions getDexOptions() {
        return dexOptions;
    }

    /** {@inheritDoc} */
    @Override
    public JacocoOptions getJacoco() {
        return jacoco;
    }

    /** {@inheritDoc} */
    @Override
    public LintOptions getLintOptions() {
        return lintOptions;
    }

    /** {@inheritDoc} */
    @Override
    public ExternalNativeBuild getExternalNativeBuild() {
        return externalNativeBuild;
    }

    /** {@inheritDoc} */
    @Override
    public PackagingOptions getPackagingOptions() {
        return packagingOptions;
    }

    /** {@inheritDoc} */
    @Override
    public Splits getSplits() {
        return splits;
    }

    /** {@inheritDoc} */
    @Override
    public TestOptions getTestOptions() {
        return testOptions;
    }

    private void ensureTargetSetup() {
        // check if the target has been set.
        TargetInfo targetInfo = androidBuilder.getTargetInfo();
        if (targetInfo == null) {
            sdkHandler.initTarget(
                    getCompileSdkVersion(),
                    buildToolsRevision,
                    libraryRequests,
                    androidBuilder,
                    SdkHandler.useCachedSdk(projectOptions));
        }
    }

    // For compatibility with LibraryExtension.
    @Override
    public Boolean getPackageBuildConfig() {
        throw new GradleException("packageBuildConfig is not supported.");
    }

    @Override
    public Collection<String> getAidlPackageWhiteList() {
        throw new GradleException("aidlPackageWhiteList is not supported.");
    }

    // For compatibility with FeatureExtension.
    @Override
    public Boolean getBaseFeature() {
        throw new GradleException("baseFeature is not supported.");
    }
}
