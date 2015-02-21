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
package com.android.build.gradle

import com.android.SdkConstants
import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.TestVariant
import com.android.build.gradle.internal.CompileOptions
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.LoggingUtil
import com.android.build.gradle.internal.SdkHandler
import com.android.build.gradle.internal.SourceSetSourceProviderWrapper
import com.android.build.gradle.internal.coverage.JacocoExtension
import com.android.build.gradle.internal.dsl.AaptOptions
import com.android.build.gradle.internal.dsl.AndroidSourceSetFactory
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.DexOptions
import com.android.build.gradle.internal.dsl.GroupableProductFlavor
import com.android.build.gradle.internal.dsl.LintOptions
import com.android.build.gradle.internal.dsl.PackagingOptions
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.dsl.Splits
import com.android.build.gradle.internal.test.TestOptions
import com.android.builder.core.AndroidBuilder
import com.android.builder.core.BuilderConstants
import com.android.builder.model.SourceProvider
import com.android.builder.sdk.TargetInfo
import com.android.builder.testing.api.DeviceProvider
import com.android.builder.testing.api.TestServer
import com.android.sdklib.repository.FullRevision
import com.google.common.collect.Lists
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.SourceSet
import org.gradle.internal.reflect.Instantiator

import static com.android.builder.core.VariantType.ANDROID_TEST
import static com.android.builder.core.VariantType.UNIT_TEST

/**
 * Base 'android' extension for all android plugins.
 *
 * <p>This is never used directly. Instead,
 *<ul>
 * <li>Plugin 'com.android.application' uses {@link AppExtension}</li>
 * <li>Plugin 'com.android.library' uses {@link LibraryExtension}</li>
 * </ul>
 */
public abstract class BaseExtension {

    private String target
    private FullRevision buildToolsRevision

    /** Default config, shared by all flavors. */
    final ProductFlavor defaultConfig

    /** Options for aapt, tool for packaging resources. */
    final AaptOptions aaptOptions

    /** Lint options. */
    final LintOptions lintOptions

    /** Dex options. */
    final DexOptions dexOptions

    /** Options for running tests. */
    final TestOptions testOptions

    /** Compile options */
    final CompileOptions compileOptions

    /** Packaging options. */
    final PackagingOptions packagingOptions

    /** JaCoCo options. */
    final JacocoExtension jacoco

    /** APK splits */
    final Splits splits

    /** All product flavors used by this project. */
    final NamedDomainObjectContainer<GroupableProductFlavor> productFlavors

    /** Build types used by this project. */
    final NamedDomainObjectContainer<BuildType> buildTypes

    /** Signing configs used by this project. */
    final NamedDomainObjectContainer<SigningConfig> signingConfigs

    private ExtraModelInfo extraModelInfo

    protected Project project

    /** A prefix to be used when creating new resources. Used by Studio */
    String resourcePrefix

    List<String> flavorDimensionList
    String testBuildType = "debug"

    private String defaultPublishConfig = "release"
    private boolean publishNonDefault = false
    private boolean useNewNativePlugin = false

    private Closure<Void> variantFilter

    private final DefaultDomainObjectSet<TestVariant> testVariantList =
        new DefaultDomainObjectSet<TestVariant>(TestVariant.class)

    private final List<DeviceProvider> deviceProviderList = Lists.newArrayList();
    private final List<TestServer> testServerList = Lists.newArrayList();

    private final AndroidBuilder androidBuilder

    private final SdkHandler sdkHandler

    protected Logger logger

    private boolean isWritable = true;

    /**
     * The source sets container.
     */
    final NamedDomainObjectContainer<AndroidSourceSet> sourceSetsContainer

    BaseExtension(
            @NonNull ProjectInternal project,
            @NonNull Instantiator instantiator,
            @NonNull AndroidBuilder androidBuilder,
            @NonNull SdkHandler sdkHandler,
            @NonNull NamedDomainObjectContainer<BuildType> buildTypes,
            @NonNull NamedDomainObjectContainer<GroupableProductFlavor> productFlavors,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigs,
            @NonNull ExtraModelInfo extraModelInfo,
            boolean isLibrary) {
        this.androidBuilder = androidBuilder
        this.sdkHandler = sdkHandler
        this.buildTypes = buildTypes
        this.productFlavors = productFlavors
        this.signingConfigs = signingConfigs
        this.extraModelInfo = extraModelInfo
        this.project = project

        logger = Logging.getLogger(this.class)

        defaultConfig = instantiator.newInstance(ProductFlavor, BuilderConstants.MAIN,
                project, instantiator, project.getLogger())

        aaptOptions = instantiator.newInstance(AaptOptions)
        dexOptions = instantiator.newInstance(DexOptions)
        lintOptions = instantiator.newInstance(LintOptions)
        testOptions = instantiator.newInstance(TestOptions)
        compileOptions = instantiator.newInstance(CompileOptions)
        packagingOptions = instantiator.newInstance(PackagingOptions)
        jacoco = instantiator.newInstance(JacocoExtension)
        splits = instantiator.newInstance(Splits, instantiator)

        sourceSetsContainer = project.container(AndroidSourceSet,
                new AndroidSourceSetFactory(instantiator, project, isLibrary))

        sourceSetsContainer.whenObjectAdded { AndroidSourceSet sourceSet ->
            ConfigurationContainer configurations = project.getConfigurations()

            createConfiguration(
                    configurations,
                    sourceSet.getCompileConfigurationName(),
                    "Classpath for compiling the ${sourceSet.name} sources.")

            String packageConfigDescription
            if (isLibrary) {
                packageConfigDescription = "Classpath only used when publishing '${sourceSet.name}'."
            } else {
                packageConfigDescription = "Classpath packaged with the compiled '${sourceSet.name}' classes."
            }
            createConfiguration(
                    configurations,
                    sourceSet.getPackageConfigurationName(),
                    packageConfigDescription)

            createConfiguration(
                    configurations,
                    sourceSet.getProvidedConfigurationName(),
                    "Classpath for only compiling the ${sourceSet.name} sources.")

            createConfiguration(
                    configurations,
                    sourceSet.getWearAppConfigurationName(),
                    "Link to a wear app to embed for object '${sourceSet.name}'.")

            sourceSet.setRoot(String.format("src/%s", sourceSet.getName()))
        }

        sourceSetsContainer.create(defaultConfig.name)
        sourceSetsContainer.create(ANDROID_TEST.prefix)
        sourceSetsContainer.create(UNIT_TEST.prefix)
    }

    /**
     * Disallow further modification on the extension.
     */
    public void disableWrite() {
        isWritable = false
    }

    private checkWritability() {
        if (!isWritable) {
            throw new GradleException(
                    "Android tasks have already been created.\n" +
                            "This happens when calling android.applicationVariants,\n" +
                            "android.libraryVariants or android.testVariants.\n" +
                            "Once these methods are called, it is not possible to\n" +
                            "continue configuring the model.")
        }
    }

    protected void createConfiguration(
            @NonNull ConfigurationContainer configurations,
            @NonNull String configurationName,
            @NonNull String configurationDescription) {
        logger.info("Creating configuration ${configurationName}.")

        Configuration configuration = configurations.findByName(configurationName)
        if (configuration == null) {
            configuration = configurations.create(configurationName)
        }
        configuration.setVisible(false);
        configuration.setDescription(configurationDescription)
    }

    /**
     * Sets the compile SDK version, based on full SDK version string, e.g.
     * <code>android-21</code> for Lollipop.
     */
    void compileSdkVersion(String version) {
        checkWritability()
        this.target = version
    }

    /**
     * Sets the compile SDK version, based on API level, e.g. 21 for Lollipop.
     */
    void compileSdkVersion(int apiLevel) {
        compileSdkVersion("android-" + apiLevel)
    }

    void setCompileSdkVersion(int apiLevel) {
        compileSdkVersion(apiLevel)
    }

    void setCompileSdkVersion(String target) {
        compileSdkVersion(target)
    }

    void buildToolsVersion(String version) {
        checkWritability()
        buildToolsRevision = FullRevision.parseRevision(version)
    }

    /**
     * <strong>Required.</strong> Version of the build tools to use.
     *
     * <p>Value assigned to this property is parsed and stored in a normalized form, so reading it
     * back may give a slightly different string.
     */
    String getBuildToolsVersion() {
        return buildToolsRevision.toString()
    }

    void setBuildToolsVersion(String version) {
        buildToolsVersion(version)
    }

    /**
     * Configures the build types.
     */
    void buildTypes(Action<? super NamedDomainObjectContainer<BuildType>> action) {
        checkWritability()
        action.execute(buildTypes)
    }

    /**
     * Configures the product flavors.
     */
    void productFlavors(Action<? super NamedDomainObjectContainer<GroupableProductFlavor>> action) {
        checkWritability()
        action.execute(productFlavors)
    }

    /**
     * Configures the signing configs.
     */
    void signingConfigs(Action<? super NamedDomainObjectContainer<SigningConfig>> action) {
        checkWritability()
        action.execute(signingConfigs)
    }

    public void flavorDimensions(String... dimensions) {
        checkWritability()
        flavorDimensionList = Arrays.asList(dimensions)
    }

    /**
     * Configures the source sets. Note that the Android plugin uses its own implementation of
     * source sets, {@link AndroidSourceSet}.
     */
    void sourceSets(Action<NamedDomainObjectContainer<AndroidSourceSet>> action) {
        checkWritability()
        action.execute(sourceSetsContainer)
    }

    /**
     * All source sets. Note that the Android plugin uses its own implementation of
     * source sets, {@link AndroidSourceSet}.
     */
    NamedDomainObjectContainer<AndroidSourceSet> getSourceSets() {
        sourceSetsContainer
    }

    /**
     * The default configuration, inherited by all build flavors (if any are defined).
     */
    void defaultConfig(Action<ProductFlavor> action) {
        checkWritability()
        action.execute(defaultConfig)
    }

    /**
     * Configures aapt options.
     */
    void aaptOptions(Action<AaptOptions> action) {
        checkWritability()
        action.execute(aaptOptions)
    }

    /**
     * Configures dex options.
     * @param action
     */
    void dexOptions(Action<DexOptions> action) {
        checkWritability()
        action.execute(dexOptions)
    }

    /**
     * Configure lint options.
     */
    void lintOptions(Action<LintOptions> action) {
        checkWritability()
        action.execute(lintOptions)
    }

    /** Configures the test options. */
    void testOptions(Action<TestOptions> action) {
        checkWritability()
        action.execute(testOptions)
    }

    /**
     * Configures compile options.
     */
    void compileOptions(Action<CompileOptions> action) {
        checkWritability()
        action.execute(compileOptions)
    }

    /**
     * Configures packaging options.
     */
    void packagingOptions(Action<PackagingOptions> action) {
        checkWritability()
        action.execute(packagingOptions)
    }

    /**
     * Configures JaCoCo options.
     */
    void jacoco(Action<JacocoExtension> action) {
        checkWritability()
        action.execute(jacoco)
    }

    /**
     * Configures APK splits.
     */
    void splits(Action<Splits> action) {
        checkWritability()
        action.execute(splits)
    }

    void deviceProvider(DeviceProvider deviceProvider) {
        checkWritability()
        deviceProviderList.add(deviceProvider)
    }

    @NonNull
    List<DeviceProvider> getDeviceProviders() {
        return deviceProviderList
    }

    void testServer(TestServer testServer) {
        checkWritability()
        testServerList.add(testServer)
    }

    @NonNull
    List<TestServer> getTestServers() {
        return testServerList
    }

    public void defaultPublishConfig(String value) {
        setDefaultPublishConfig(value)
    }

    public void publishNonDefault(boolean value) {
        publishNonDefault = value
    }

    /**
     * Name of the configuration used to build the default artifact of this project.
     *
     * <p>See <a href="http://tools.android.com/tech-docs/new-build-system/user-guide#TOC-Referencing-a-Library">
     * Referencing a Library</a>
     */
    public String getDefaultPublishConfig() {
        return defaultPublishConfig
    }

    public void setDefaultPublishConfig(String value) {
        defaultPublishConfig = value
    }

    /**
     * Whether to publish artifacts for all configurations, not just the default one.
     *
     * <p>See <a href="http://tools.android.com/tech-docs/new-build-system/user-guide#TOC-Referencing-a-Library">
     * Referencing a Library</a>
     */
    public boolean getPublishNonDefault() {
        return publishNonDefault
    }

    /**
     * Sets a variant filter to control which variant are excluded. The closure is passed a single
     * object of type {@link com.android.build.gradle.internal.api.VariantFilter}
     * @param filter the filter as a closure
     */
    void variantFilter(Closure<Void> filter) {
        setVariantFilter(filter)
    }

    /**
     * Sets a variant filter to control which variant are excluded. The closure is passed a single
     * object of type {@link com.android.build.gradle.internal.api.VariantFilter}
     * @param filter the filter as a closure
     */
    void setVariantFilter(Closure<Void> filter) {
        variantFilter = filter
    }

    /**
     * A variant filter to control which variant are excluded. The filter is a closure which
     * is passed a single object of type {@link com.android.build.gradle.internal.api.VariantFilter}
     */
    public Closure<Void> getVariantFilter() {
        return variantFilter;
    }

    void resourcePrefix(String prefix) {
        resourcePrefix = prefix
    }

    /**
     * Returns the list of test variants. Since the collections is built after evaluation,
     * it should be used with Groovy's <code>all</code> iterator to process future items.
     */
    @NonNull
    public DefaultDomainObjectSet<TestVariant> getTestVariants() {
        return testVariantList
    }

    abstract void addVariant(BaseVariant variant)

    void addTestVariant(TestVariant testVariant) {
        testVariantList.add(testVariant)
    }

    public void registerArtifactType(@NonNull String name,
                                     boolean isTest,
                                     int artifactType) {
        extraModelInfo.registerArtifactType(name, isTest, artifactType)
    }

    public void registerBuildTypeSourceProvider(
            @NonNull String name,
            @NonNull BuildType buildType,
            @NonNull SourceProvider sourceProvider) {
        extraModelInfo.registerBuildTypeSourceProvider(name, buildType, sourceProvider)
    }

    public void registerProductFlavorSourceProvider(
            @NonNull String name,
            @NonNull ProductFlavor productFlavor,
            @NonNull SourceProvider sourceProvider) {
        extraModelInfo.registerProductFlavorSourceProvider(name, productFlavor, sourceProvider)
    }

    public void registerJavaArtifact(
            @NonNull String name,
            @NonNull BaseVariant variant,
            @NonNull String assembleTaskName,
            @NonNull String javaCompileTaskName,
            @NonNull Iterable<String> ideSetupTaskNames,
            @NonNull Configuration configuration,
            @NonNull File classesFolder,
            @Nullable SourceProvider sourceProvider) {
        extraModelInfo.registerJavaArtifact(name, variant, assembleTaskName,
                javaCompileTaskName, ideSetupTaskNames,
                configuration, classesFolder, sourceProvider)
    }

    public void registerMultiFlavorSourceProvider(
            @NonNull String name,
            @NonNull String flavorName,
            @NonNull SourceProvider sourceProvider) {
        extraModelInfo.registerMultiFlavorSourceProvider(name, flavorName, sourceProvider)
    }

    @NonNull
    public SourceProvider wrapJavaSourceSet(@NonNull SourceSet sourceSet) {
        return new SourceSetSourceProviderWrapper(sourceSet)
    }

    /**
     * <strong>Required.</strong> Compile SDK version.
     *
     * <p>Setter can be called with a string like "android-21" or a number.
     *
     * <p>Value assigned to this property is parsed and stored in a normalized form, so reading it
     * back may give a slightly different string.
     */
    public String getCompileSdkVersion() {
        return target
    }

    public FullRevision getBuildToolsRevision() {
        return buildToolsRevision
    }

    public File getSdkDirectory() {
        return sdkHandler.getSdkFolder()
    }

    public List<File> getBootClasspath() {
        ensureTargetSetup()
        return androidBuilder.getBootClasspath()
    }

    public File getAdbExe() {
        return sdkHandler.getSdkInfo().adb
    }

    public File getDefaultProguardFile(String name) {
        File sdkDir = sdkHandler.getAndCheckSdkFolder()
        return new File(sdkDir,
                SdkConstants.FD_TOOLS + File.separatorChar
                        + SdkConstants.FD_PROGUARD + File.separatorChar
                        + name);
    }

    // ---------------
    // TEMP for compatibility
    // STOPSHIP Remove in 1.0

    // by default, we do not generate pure splits
    boolean generatePureSplits = false;

    void generatePureSplits(boolean flag) {
        if (flag) {
            logger.warn("Pure splits are not supported by PlayStore yet.")
        }
        this.generatePureSplits = flag;
    }

    private boolean enforceUniquePackageName = true

    public void enforceUniquePackageName(boolean value) {
        if (!value) {
            LoggingUtil.displayDeprecationWarning(logger, project, "Support for libraries with same package name is deprecated and will be removed in 1.0")
        }
        enforceUniquePackageName = value
    }

    public void setEnforceUniquePackageName(boolean value) {
        enforceUniquePackageName(value)
    }

    public getEnforceUniquePackageName() {
        return enforceUniquePackageName
    }

    public boolean getUseNewNativePlugin() {
        return useNewNativePlugin
    }

    public void setUseNewNativePlugin(boolean value) {
        useNewNativePlugin = value
    }

    private void ensureTargetSetup() {
        // check if the target has been set.
        TargetInfo targetInfo = androidBuilder.getTargetInfo()
        if (targetInfo == null) {
            sdkHandler.initTarget(
                    getCompileSdkVersion(),
                    buildToolsRevision,
                    androidBuilder)
        }
    }
}
