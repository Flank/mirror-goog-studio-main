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

import static com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_ATTR_BUILD_TYPE;
import static com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_ATTR_FLAVOR_PREFIX;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.build.api.transform.Transform;
import com.android.build.api.variant.VariantFilter;
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.internal.CompileOptions;
import com.android.build.gradle.internal.ExtraModelInfo;
import com.android.build.gradle.internal.LoggingUtil;
import com.android.build.gradle.internal.SdkHandler;
import com.android.build.gradle.internal.SourceSetSourceProviderWrapper;
import com.android.build.gradle.internal.coverage.JacocoOptions;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.build.gradle.internal.dsl.AdbOptions;
import com.android.build.gradle.internal.dsl.AndroidSourceSetFactory;
import com.android.build.gradle.internal.dsl.BuildType;
import com.android.build.gradle.internal.dsl.DataBindingOptions;
import com.android.build.gradle.internal.dsl.DexOptions;
import com.android.build.gradle.internal.dsl.ExternalNativeBuild;
import com.android.build.gradle.internal.dsl.LintOptions;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.dsl.SigningConfig;
import com.android.build.gradle.internal.dsl.Splits;
import com.android.build.gradle.internal.dsl.TestOptions;
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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * <li>Plugin <code>com.android.application</code> uses {@link AppExtension}
 * <li>Plugin <code>com.android.library</code> uses {@link LibraryExtension}
 * <li>Plugin <code>com.android.test</code> uses {@link TestExtension}
 * <li>Plugin <code>com.android.atom</code> uses {@link AtomExtension}
 * <li>Plugin <code>com.android.instantapp</code> uses {@link InstantAppExtension}
 * </ul>
 */
// All the public methods are meant to be exposed in the DSL.
// We can't yet lambdas in this class (yet), because the DSL reference generator doesn't understand
// them.
@SuppressWarnings({"UnnecessaryInheritDoc", "WeakerAccess", "unused", "Convert2Lambda"})
public abstract class BaseExtension implements AndroidConfig {
    /** Secondary dependencies for the custom transform. */
    private final List<List<Object>> transformDependencies = Lists.newArrayList();

    private final AndroidBuilder androidBuilder;

    private final SdkHandler sdkHandler;

    private final ProductFlavor defaultConfig;

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

    private final List<DeviceProvider> deviceProviderList = Lists.newArrayList();

    private final List<TestServer> testServerList = Lists.newArrayList();

    private final List<Transform> transforms = Lists.newArrayList();

    private final DataBindingOptions dataBinding;

    private final NamedDomainObjectContainer<AndroidSourceSet> sourceSetsContainer;

    private String target;

    private Revision buildToolsRevision;

    private List<LibraryRequest> libraryRequests = Lists.newArrayList();

    private List<String> flavorDimensionList;

    private String resourcePrefix;

    private ExtraModelInfo extraModelInfo;

    private String defaultPublishConfig = "release";
    private Map<String, String> flavorMatchingStrategy;

    private Action<VariantFilter> variantFilter;

    protected Logger logger;

    private boolean isWritable = true;

    protected Project project;

    BaseExtension(
            @NonNull final Project project,
            @NonNull Instantiator instantiator,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull SdkHandler sdkHandler,
            @NonNull NamedDomainObjectContainer<BuildType> buildTypes,
            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavors,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigs,
            @NonNull ExtraModelInfo extraModelInfo,
            final boolean publishPackage) {
        this.androidBuilder = androidBuilder;
        this.sdkHandler = sdkHandler;
        this.buildTypes = buildTypes;
        //noinspection unchecked
        this.productFlavors = (NamedDomainObjectContainer) productFlavors;
        this.signingConfigs = signingConfigs;
        this.extraModelInfo = extraModelInfo;
        this.project = project;

        logger = Logging.getLogger(this.getClass());

        defaultConfig = instantiator.newInstance(ProductFlavor.class, BuilderConstants.MAIN,
                project, instantiator, project.getLogger(), extraModelInfo);

        aaptOptions = instantiator.newInstance(AaptOptions.class);
        dexOptions = instantiator.newInstance(DexOptions.class, extraModelInfo);
        lintOptions = instantiator.newInstance(LintOptions.class);
        externalNativeBuild = instantiator.newInstance(
                ExternalNativeBuild.class, instantiator, project);
        testOptions = instantiator.newInstance(TestOptions.class);
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

                        final String compileName = sourceSet.getCompileConfigurationName();
                        // due to compatibility with other plugins and with Gradle sync,
                        // we have to keep 'compile' as resolvable.
                        // TODO Fix this in gradle sync.
                        createConfiguration(
                                configurations,
                                compileName,
                                "Classpath for compiling the " + sourceSet.getName() + " sources.",
                                "compile".equals(compileName) || "testCompile".equals(compileName) /*canBeResolved*/);

                        String packageConfigDescription;
                        if (publishPackage) {
                            packageConfigDescription =
                                    "Classpath only used when publishing '"
                                            + sourceSet.getName()
                                            + "'.";
                        } else {
                            packageConfigDescription =
                                    "Classpath packaged with the compiled '"
                                            + sourceSet.getName()
                                            + "' classes.";
                        }
                        createConfiguration(
                                configurations,
                                sourceSet.getPackageConfigurationName(),
                                packageConfigDescription,
                                false /*canBeResolved*/);

                        createConfiguration(
                                configurations,
                                sourceSet.getProvidedConfigurationName(),
                                "Classpath for only compiling the "
                                        + sourceSet.getName()
                                        + " sources.",
                                false /*canBeResolved*/);

                        Configuration wearConfig = createConfiguration(
                                configurations,
                                sourceSet.getWearAppConfigurationName(),
                                "Link to a wear app to embed for object '"
                                        + sourceSet.getName()
                                        + "'.",
                                false /*canBeResolved*/);

                        createConfiguration(
                                configurations,
                                sourceSet.getAnnotationProcessorConfigurationName(),
                                "Classpath for the annotation processor for '"
                                        + sourceSet.getName()
                                        + "'.",
                                false /*canBeResolved*/);

                        createConfiguration(
                                configurations,
                                sourceSet.getJackPluginConfigurationName(),
                                String.format(
                                        "Classpath for the '%s' Jack plugins.",
                                        sourceSet.getName()),
                                false /*canBeResolved*/);

                        sourceSet.setRoot(String.format("src/%s", sourceSet.getName()));
                    }
                });

        sourceSetsContainer.create(defaultConfig.getName());

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
     * @param configurations the configuration container to create the new configuration
     * @param configurationName the name of the configuration to create.
     * @param configurationDescription the configuration description.
     * @param canBeResolved Whether the configuration can be resolved directly.
     * @return the configuration
     *
     * @see Configuration#isCanBeResolved()
     */
    private Configuration createConfiguration(
            @NonNull ConfigurationContainer configurations,
            @NonNull String configurationName,
            @NonNull String configurationDescription,
            boolean canBeResolved) {
        logger.info("Creating configuration {}", configurationName);

        Configuration configuration = configurations.findByName(configurationName);
        if (configuration == null) {
            configuration = configurations.create(configurationName);
        }
        configuration.setVisible(false);
        configuration.setDescription(configurationDescription);
        configuration.setCanBeConsumed(false);
        configuration.setCanBeResolved(canBeResolved);

        return configuration;
    }

    /**
     * Sets the compile SDK version, based on full SDK version string, e.g.
     * <code>android-21</code> for Lollipop.
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
     * <strong>Required.</strong> Version of the build tools to use.
     *
     * <p>Value assigned to this property is parsed and stored in a normalized form, so reading it
     * back may give a slightly different string.
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
     * The default configuration, inherited by all product flavors (if any are defined).
     */
    public void defaultConfig(Action<ProductFlavor> action) {
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

    public void flavorMatchingStrategy(String name, String value) {
        if (flavorMatchingStrategy == null) {
            flavorMatchingStrategy = new HashMap<>();
        }

        if (!name.startsWith(VariantDependencies.CONFIG_ATTR_FLAVOR_PREFIX)) {
            name = CONFIG_ATTR_FLAVOR_PREFIX + name;
        }

        flavorMatchingStrategy.put(name, value);
    }


    @Override
    @NonNull
    public Map<String, String> getFlavorMatchingStrategy() {
        if (flavorMatchingStrategy == null) {
            return ImmutableMap.of();
        }

        return flavorMatchingStrategy;
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
    }

    public File getDefaultProguardFile(String name) {
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

    private boolean enforceUniquePackageName = true;

    public void enforceUniquePackageName(boolean value) {
        if (!value) {
            LoggingUtil.displayDeprecationWarning(logger, project, "Support for libraries with same package name is deprecated and will be removed in a future release.");
        }
        enforceUniquePackageName = value;
    }

    public void setEnforceUniquePackageName(boolean value) {
        enforceUniquePackageName(value);
    }

    @Override
    public boolean getEnforceUniquePackageName() {
        return enforceUniquePackageName;
    }

    /** {@inheritDoc} */
    @Override
    public ProductFlavor getDefaultConfig() {
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
                    SdkHandler.useCachedSdk(project));
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
}
