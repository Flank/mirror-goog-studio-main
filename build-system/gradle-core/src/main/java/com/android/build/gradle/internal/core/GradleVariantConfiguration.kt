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
package com.android.build.gradle.internal.core

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.TestAndroidConfig
import com.android.build.gradle.api.JavaCompileOptions
import com.android.build.gradle.internal.core.MergedFlavor.Companion.clone
import com.android.build.gradle.internal.core.MergedFlavor.Companion.mergeFlavors
import com.android.build.gradle.internal.dsl.BaseFlavor
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.CoreExternalNativeBuildOptions
import com.android.build.gradle.internal.dsl.CoreNdkOptions
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.StringOption
import com.android.builder.core.BuilderConstants
import com.android.builder.core.DefaultApiVersion
import com.android.builder.core.DefaultManifestParser
import com.android.builder.core.ManifestAttributeSupplier
import com.android.builder.core.VariantAttributesProvider
import com.android.builder.core.VariantType
import com.android.builder.dexing.DexingType
import com.android.builder.errors.EvalIssueReporter
import com.android.builder.internal.ClassFieldImpl
import com.android.builder.model.ApiVersion
import com.android.builder.model.ClassField
import com.android.builder.model.InstantRun
import com.android.builder.model.SigningConfig
import com.android.builder.model.SourceProvider
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.AssetSet
import com.android.ide.common.resources.ResourceSet
import com.android.sdklib.AndroidVersion
import com.android.utils.appendCapitalized
import com.android.utils.combineAsCamelCase
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Joiner
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.common.collect.Sets
import java.io.File
import java.util.function.BooleanSupplier
import java.util.function.Function
import java.util.function.IntSupplier
import java.util.function.Supplier

/**
 * A Variant configuration.
 *
 *
 * Variants are made from the combination of:
 *
 *
 * - a build type (base interface BuildType), and its associated sources. - a default
 * configuration (base interface ProductFlavor), and its associated sources. - a optional list of
 * product flavors (base interface ProductFlavor) and their associated sources. - dependencies (both
 * jar and aar).
 */
open class GradleVariantConfiguration @VisibleForTesting internal constructor(
    projectOptions: ProjectOptions,
    testedConfig: GradleVariantConfiguration?,
    defaultConfig: DefaultConfig,
    defaultSourceProvider: SourceProvider,
    mainManifestAttributeSupplier: ManifestAttributeSupplier?,
    buildType: BuildType,
    buildTypeSourceProvider: SourceProvider?,
    type: VariantType,
    signingConfigOverride: SigningConfig?,
    issueReporter: EvalIssueReporter,
    isInExecutionPhase: BooleanSupplier
) {
    /**
     * Full, unique name of the variant in camel case, including BuildType and Flavors (and Test)
     */
    private var mFullName: String? = null
    /**
     * Flavor Name of the variant, including all flavors in camel case (starting with a lower case).
     */
    private var mFlavorName: String? = null
    /**
     * Full, unique name of the variant, including BuildType, flavors and test, dash separated.
     * (similar to full name but with dashes)
     */
    private var mBaseName: String? = null
    /**
     * Unique directory name (can include multiple folders) for the variant, based on build type,
     * flavor and test. This always uses forward slashes ('/') as separator on all platform.
     */
    private var mDirName: String? = null
    private var mDirSegments: List<String>? = null
    val defaultConfig: DefaultConfig
    val defaultSourceSet: SourceProvider
    val buildType: BuildType
    /** The SourceProvider for the BuildType. Can be null.  */
    /** SourceProvider for the BuildType. Can be null  */
    val buildTypeSourceSet: SourceProvider?
    private val mFlavorDimensionNames: MutableList<String> =
        Lists.newArrayList()
    private val mFlavors: MutableList<ProductFlavor> =
        Lists.newArrayList()
    private val mFlavorSourceProviders: MutableList<SourceProvider> =
        Lists.newArrayList()
    /**
     * Returns the variant specific source provider
     *
     * @return the source provider or null if none has been provided.
     */
    /**
     * Sets the variant-specific source provider.
     *
     * @param sourceProvider the source provider for the product flavor
     * @return the config object
     */
    /** Variant specific source provider, may be null  */
    var variantSourceProvider: SourceProvider? = null
    /**
     * Sets the variant-specific source provider.
     *
     * @param sourceProvider the source provider for the product flavor
     * @return the config object
     */
    /** MultiFlavors specific source provider, may be null  */
    var multiFlavorSourceProvider: SourceProvider? = null
    val type: VariantType
    /**
     * Optional tested config in case this variant is used for testing another variant.
     *
     * @see VariantType.isTestComponent
     */
    val testedConfig: GradleVariantConfiguration?
    var mergedFlavor: MergedFlavor
        private set
    /** Variant-specific build Config fields.  */
    private val mBuildConfigFields: MutableMap<String, ClassField> =
        Maps.newTreeMap()
    /** Variant-specific res values.  */
    private val mResValues: MutableMap<String, ClassField> =
        Maps.newTreeMap()
    /**
     * Signing Override to be used instead of any signing config provided by Build Type or Product
     * Flavors.
     */
    private val mSigningConfigOverride: SigningConfig?
    /**
     * For reading the attributes from the main manifest file in the default source set, combining
     * the results with the current flavor.
     */
    private val mVariantAttributesProvider: VariantAttributesProvider
    /** For recording sync issues.  */
    protected val issueReporter: EvalIssueReporter
    private val projectOptions: ProjectOptions
    private val mergedNdkConfig = MergedNdkConfig()
    private val mergedExternalNativeBuildOptions =
        MergedExternalNativeBuildOptions()
    private val mergedJavaCompileOptions = MergedJavaCompileOptions()
    /**
     * Creates a [GradleVariantConfiguration] for a testing variant derived from this variant.
     */
    open fun getMyTestConfig(
        defaultSourceProvider: SourceProvider,
        mainManifestAttributeSupplier: ManifestAttributeSupplier?,
        buildTypeSourceProvider: SourceProvider?,
        type: VariantType,
        isInExecutionPhase: BooleanSupplier
    ): GradleVariantConfiguration? {
        return GradleVariantConfiguration(
            projectOptions,
            this,
            defaultConfig,
            defaultSourceProvider,
            mainManifestAttributeSupplier,
            buildType,
            buildTypeSourceProvider,
            type,
            signingConfig,
            issueReporter,
            isInExecutionPhase
        )
    }

    /**
     * Returns the full, unique name of the variant in camel case (starting with a lower case),
     * including BuildType, Flavors and Test (if applicable).
     *
     * @return the name of the variant
     */
    val fullName: String
        get() {
            if (mFullName == null) {
                mFullName = computeRegularVariantName(
                    flavorName,
                    buildType,
                    type
                )
            }
            return mFullName!!
        }

    /**
     * Returns a full name that includes the given splits name.
     *
     * @param splitName the split name
     * @return a unique name made up of the variant and split names.
     */
    fun computeFullNameWithSplits(splitName: String): String {
        val sb = StringBuilder()
        val flavorName = flavorName
        if (!flavorName.isEmpty()) {
            sb.append(flavorName)
            sb.appendCapitalized(splitName)
        } else {
            sb.append(splitName)
        }
        sb.appendCapitalized(buildType.name)
        if (type.isTestComponent) {
            sb.append(type.suffix)
        }
        return sb.toString()
    }

    /**
     * Returns the flavor name of the variant, including all flavors in camel case (starting with a
     * lower case). If the variant has no flavor, then an empty string is returned.
     *
     * @return the flavor name or an empty string.
     */
    val flavorName: String
        get() {
            if (mFlavorName == null) {
                mFlavorName = computeFlavorName(mFlavors)
            }
            return mFlavorName!!
        }

    /**
     * Returns the full, unique name of the variant, including BuildType, flavors and test, dash
     * separated. (similar to full name but with dashes)
     *
     * @return the name of the variant
     */
    val baseName: String
        get() {
            if (mBaseName == null) {
                val sb = StringBuilder()
                if (mFlavors.isNotEmpty()) {
                    for (pf in mFlavors) {
                        sb.append(pf.name).append('-')
                    }
                }
                sb.append(buildType.name)
                if (type.isTestComponent) {
                    sb.append('-').append(type.prefix)
                }
                mBaseName = sb.toString()
            }
            return mBaseName!!
        }

    /**
     * Returns a base name that includes the given splits name.
     *
     * @param splitName the split name
     * @return a unique name made up of the variant and split names.
     */
    fun computeBaseNameWithSplits(splitName: String): String {
        val sb = StringBuilder()
        if (mFlavors.isNotEmpty()) {
            for (pf in mFlavors) {
                sb.append(pf.name).append('-')
            }
        }
        sb.append(splitName).append('-')
        sb.append(buildType.name)
        if (type.isTestComponent) {
            sb.append('-').append(type.prefix)
        }
        return sb.toString()
    }

    /**
     * Returns a unique directory name (can include multiple folders) for the variant, based on
     * build type, flavor and test.
     *
     *
     * This always uses forward slashes ('/') as separator on all platform.
     *
     * @return the directory name for the variant
     */
    val dirName: String
        get() {
            if (mDirName == null) {
                mDirName = Joiner.on('/').join(directorySegments)
            }
            return mDirName!!
        }

    /**
     * Returns a unique directory name (can include multiple folders) for the variant, based on
     * build type, flavor and test.
     *
     * @return the directory name for the variant
     */
    val directorySegments: Collection<String?>
        get() {
            if (mDirSegments == null) {
                val builder =
                    ImmutableList.builder<String>()
                if (type.isTestComponent) {
                    builder.add(type.prefix)
                }
                if (!mFlavors.isEmpty()) {
                    builder.add(
                        combineAsCamelCase(
                            mFlavors, ProductFlavor::getName
                        )
                    )
                }
                builder.add(buildType.name)
                mDirSegments = builder.build()
            }
            return mDirSegments!!
        }

    /**
     * Returns a unique directory name (can include multiple folders) for the variant, based on
     * build type, flavor and test, and splits.
     *
     *
     * This always uses forward slashes ('/') as separator on all platform.
     *
     * @return the directory name for the variant
     */
    fun computeDirNameWithSplits(vararg splitNames: String): String {
        val sb = StringBuilder()
        if (type.isTestComponent) {
            sb.append(type.prefix).append("/")
        }
        if (!mFlavors.isEmpty()) {
            for (flavor in mFlavors) {
                sb.append(flavor.name)
            }
            sb.append('/')
        }
        for (splitName in splitNames) {
            sb.append(splitName).append('/')
        }
        sb.append(buildType.name)
        return sb.toString()
    }

    /**
     * Return the names of the applied flavors.
     *
     *
     * The list contains the dimension names as well.
     *
     * @return the list, possibly empty if there are no flavors.
     */
    val flavorNamesWithDimensionNames: List<String>
        get() {
            if (mFlavors.isEmpty()) {
                return emptyList()
            }
            val names: List<String>
            val count = mFlavors.size
            if (count > 1) {
                names =
                    Lists.newArrayListWithCapacity(count * 2)
                for (i in 0 until count) {
                    names.add(mFlavors[i].name)
                    names.add(mFlavorDimensionNames[i])
                }
            } else {
                names = listOf(mFlavors[0].name)
            }
            return names
        }

    /**
     * Add a new configured ProductFlavor.
     *
     *
     * If multiple flavors are added, the priority follows the order they are added when it comes
     * to resolving Android resources overlays (ie earlier added flavors supersedes latter added
     * ones).
     *
     * @param productFlavor the configured product flavor
     * @param sourceProvider the source provider for the product flavor
     * @param dimensionName the name of the dimension associated with the flavor
     */
    fun addProductFlavor(
        productFlavor: ProductFlavor,
        sourceProvider: SourceProvider,
        dimensionName: String
    ) {
        Preconditions.checkNotNull(
            productFlavor
        )
        Preconditions.checkNotNull(
            sourceProvider
        )
        Preconditions.checkNotNull(dimensionName)
        mFlavors.add(productFlavor)
        mFlavorSourceProviders.add(sourceProvider)
        mFlavorDimensionNames.add(dimensionName)
        mergedFlavor = mergeFlavors(defaultConfig, mFlavors, issueReporter)
        mVariantAttributesProvider.mergedFlavor = mergedFlavor
        // reset computed names to null so it will be recomputed.
        mFullName = null
        mFlavorName = null
        mVariantAttributesProvider.fullName = fullName
        mergeOptions()
    }

    fun hasFlavors(): Boolean {
        return mFlavors.isNotEmpty()
    }

    /** Returns the product flavors. Items earlier in the list override later items.  */
    val productFlavors: List<ProductFlavor>
        get() = mFlavors

    /**
     * Returns the list of SourceProviders for the flavors.
     *
     *
     * The list is ordered from higher priority to lower priority.
     *
     * @return the list of Source Providers for the flavors. Never null.
     */
    val flavorSourceProviders: List<SourceProvider>
        get() = mFlavorSourceProviders

    private val testedPackage: String
        private get() = if (testedConfig != null) testedConfig.applicationId else ""

    /**
     * Returns the original application ID before any overrides from flavors. If the variant is a
     * test variant, then the application ID is the one coming from the configuration of the tested
     * variant, and this call is similar to [.getApplicationId]
     *
     * @return the original application ID
     */
    open val originalApplicationId: String
        get() = mVariantAttributesProvider.getOriginalApplicationId(testedPackage)

    /**
     * Returns the application ID for this variant. This could be coming from the manifest or could
     * be overridden through the product flavors and/or the build type.
     *
     * @return the application ID
     */
    open val applicationId: String
        get() = mVariantAttributesProvider.getApplicationId(testedPackage)

    open val testApplicationId: String
        get() = mVariantAttributesProvider.getTestApplicationId(testedPackage)

    val testedApplicationId: String?
        get() {
            if (type.isTestComponent) {
                val tested = testedConfig!!
                return if (tested.type.isAar) {
                    applicationId
                } else {
                    tested.applicationId
                }
            }
            return null
        }

    /**
     * Returns the application id override value coming from the Product Flavor and/or the Build
     * Type. If the package/id is not overridden then this returns null.
     *
     * @return the id override or null
     */
    val idOverride: String?
        get() = mVariantAttributesProvider.idOverride

    /**
     * Returns the version name for this variant. This could be specified by the product flavors,
     * or, if not, it could be coming from the manifest. A suffix may be specified by the build
     * type.
     *
     * @return the version name or null if none defined
     */
    val versionName: String?
        get() {
            val override =
                projectOptions[StringOption.IDE_VERSION_NAME_OVERRIDE]
            return override ?: getVersionName(false)
        }

    /**
     * Returns the version name for this variant. This could be specified by the product flavors,
     * or, if not, it could be coming from the manifest. A suffix may be specified by the build
     * type.
     *
     * @param ignoreManifest whether or not the manifest is ignored when getting the version code
     * @return the version name or null if none defined
     */
    fun getVersionName(ignoreManifest: Boolean): String? {
        return mVariantAttributesProvider.getVersionName(ignoreManifest)
    }

    /**
     * Returns the version code for this variant. This could be specified by the product flavors,
     * or, if not, it could be coming from the manifest.
     *
     * @return the version code or -1 if there was none defined.
     */
    val versionCode: Int
        get() {
            val override =
                projectOptions[IntegerOption.IDE_VERSION_CODE_OVERRIDE]
            return override ?: getVersionCode(false)
        }

    /**
     * Returns the version code for this variant. This could be specified by the product flavors,
     * or, if not, it could be coming from the manifest.
     *
     * @param ignoreManifest whether or not the manifest is ignored when getting the version code
     * @return the version code or -1 if there was none defined.
     */
    fun getVersionCode(ignoreManifest: Boolean): Int {
        return mVariantAttributesProvider.getVersionCode(ignoreManifest)
    }

    val manifestVersionNameSupplier: Supplier<String?>
        get() = mVariantAttributesProvider.manifestVersionNameSupplier

    val manifestVersionCodeSupplier: IntSupplier
        get() = mVariantAttributesProvider.manifestVersionCodeSupplier

    /**
     * Returns the instrumentationRunner to use to test this variant, or if the variant is a test,
     * the one to use to test the tested variant.
     *
     * @return the instrumentation test runner name
     */
    val instrumentationRunner: String
        get() {
            var config: GradleVariantConfiguration = this
            if (type.isTestComponent) {
                config = testedConfig!!
            }
            val runner = config.mVariantAttributesProvider.instrumentationRunner
            if (runner != null) {
                return runner
            }
            return if (isLegacyMultiDexMode) {
                MULTIDEX_TEST_RUNNER
            } else DEFAULT_TEST_RUNNER
        }

    /**
     * Returns the instrumentationRunner arguments to use to test this variant, or if the variant is
     * a test, the ones to use to test the tested variant
     */
    val instrumentationRunnerArguments: Map<String, String>
        get() {
            var config: GradleVariantConfiguration = this
            if (type.isTestComponent) {
                config = testedConfig!!
            }
            return config.mergedFlavor.testInstrumentationRunnerArguments
        }

    /**
     * Returns handleProfiling value to use to test this variant, or if the variant is a test, the
     * one to use to test the tested variant.
     *
     * @return the handleProfiling value
     */
    val handleProfiling: Boolean
        get() {
            var config: GradleVariantConfiguration = this
            if (type.isTestComponent) {
                config = testedConfig!!
            }
            return config.mVariantAttributesProvider.handleProfiling ?: DEFAULT_HANDLE_PROFILING
        }

    /**
     * Returns functionalTest value to use to test this variant, or if the variant is a test, the
     * one to use to test the tested variant.
     *
     * @return the functionalTest value
     */
    val functionalTest: Boolean
        get() {
            var config: GradleVariantConfiguration = this
            if (type.isTestComponent) {
                config = testedConfig!!
            }
            return config.mVariantAttributesProvider.functionalTest ?: DEFAULT_FUNCTIONAL_TEST
        }

    /** Gets the test label for this variant  */
    val testLabel: String?
        get() = mVariantAttributesProvider.testLabel

    /** Reads the package name from the manifest. This is unmodified by the build type.  */
    val packageFromManifest: String
        get() = mVariantAttributesProvider.packageName// default to 1 for minSdkVersion.

    /**
     * Return the minSdkVersion for this variant.
     *
     *
     * This uses both the value from the manifest (if present), and the override coming from the
     * flavor(s) (if present).
     *
     * @return the minSdkVersion
     */
    val minSdkVersion: AndroidVersion
        get() {
            if (testedConfig != null) {
                return testedConfig.minSdkVersion
            }
            var minSdkVersion = mergedFlavor.minSdkVersion
            if (minSdkVersion == null) { // default to 1 for minSdkVersion.
                minSdkVersion =
                    DefaultApiVersion.create(Integer.valueOf(1))
            }
            return AndroidVersion(
                minSdkVersion.apiLevel,
                minSdkVersion.codename
            )
        }

    /** Returns the minSdkVersion as integer.  */
    val minSdkVersionValue: Int
        get() = minSdkVersion.featureLevel// default to -1 if not in build.gradle file.

    /**
     * Return the targetSdkVersion for this variant.
     *
     *
     * This uses both the value from the manifest (if present), and the override coming from the
     * flavor(s) (if present).
     *
     * @return the targetSdkVersion
     */
    val targetSdkVersion: ApiVersion
        get() {
            if (testedConfig != null) {
                return testedConfig.targetSdkVersion
            }
            var targetSdkVersion =
                mergedFlavor.targetSdkVersion
            if (targetSdkVersion == null) { // default to -1 if not in build.gradle file.
                targetSdkVersion =
                    DefaultApiVersion.create(Integer.valueOf(-1))
            }
            return targetSdkVersion
        }

    /** Returns whether the manifest file is required to exist.  */
    val isManifestFileRequired: Boolean
        get() = isManifestFileRequired(type)

    /**
     * Returns the path to the main manifest file. It may or may not exist.
     *
     *
     * Note: Avoid calling this method at configuration time because the final path to the
     * manifest file may change during that time.
     */
    val mainManifestFilePath: File
        get() = defaultSourceSet.manifestFile

    /**
     * Returns the path to the main manifest file if it exists, or `null` otherwise (e.g., the main
     * manifest file is not required to exist for a test variant or a test project).
     *
     *
     * Note: Avoid calling this method at configuration time because (1) the final path to the
     * manifest file may change during that time, and (2) this method performs I/O.
     */
    val mainManifestIfExists: File?
        get() {
            val mainManifest = mainManifestFilePath
            return if (mainManifest.isFile) {
                mainManifest
            } else null
        }// first the default source provider
    // the list of flavor must be reversed to use the right overlay order.
    // multiflavor specific overrides flavor
    // build type overrides flavors
    // variant specific overrides all

    /**
     * Returns a list of sorted SourceProvider in ascending order of importance. This means that
     * items toward the end of the list take precedence over those toward the start of the list.
     *
     * @return a list of source provider
     */
    val sortedSourceProviders: List<SourceProvider>
        get() {
            val providers: MutableList<SourceProvider> =
                Lists.newArrayListWithExpectedSize(
                    mFlavorSourceProviders.size + 4
                )

            // first the default source provider
            providers.add(defaultSourceSet)
            // the list of flavor must be reversed to use the right overlay order.
            for (n in mFlavorSourceProviders.indices.reversed()) {
                providers.add(mFlavorSourceProviders[n])
            }
            // multiflavor specific overrides flavor
            multiFlavorSourceProvider?.let(providers::add)
            // build type overrides flavors
            buildTypeSourceSet?.let(providers::add)
            // variant specific overrides all
            variantSourceProvider?.let(providers::add)

            return providers
        }

    val manifestOverlays: List<File>
        get() {
            val inputs = mutableListOf<File>()

            val gatherManifest: (SourceProvider) -> Unit = {
                val variantLocation = it.manifestFile
                if (variantLocation.isFile) {
                    inputs.add(variantLocation)
                }
            }

            variantSourceProvider?.let(gatherManifest)
            buildTypeSourceSet?.let(gatherManifest)
            multiFlavorSourceProvider?.let(gatherManifest)
            mFlavorSourceProviders.forEach(gatherManifest)

            return inputs
        }

    fun getSourceFiles(f: Function<SourceProvider, Collection<File>>): Set<File> {
        return sortedSourceProviders.flatMap {
            f.apply(it)
        }.toSet()
    }

    /**
     * Returns the dynamic list of [ResourceSet] for the source folders only.
     *
     *
     * The list is ordered in ascending order of importance, meaning the first set is meant to be
     * overridden by the 2nd one and so on. This is meant to facilitate usage of the list in a
     * Resource merger
     *
     * @return a list ResourceSet.
     */
    fun getResourceSets(validateEnabled: Boolean): List<ResourceSet> {
        val resourceSets: MutableList<ResourceSet> =
            Lists.newArrayList()
        val mainResDirs =
            defaultSourceSet.resDirectories
        // the main + generated res folders are in the same ResourceSet
        var resourceSet = ResourceSet(
            BuilderConstants.MAIN, ResourceNamespace.RES_AUTO, null, validateEnabled
        )
        resourceSet.addSources(mainResDirs)
        resourceSets.add(resourceSet)
        // the list of flavor must be reversed to use the right overlay order.
        for (n in mFlavorSourceProviders.indices.reversed()) {
            val sourceProvider = mFlavorSourceProviders[n]
            val flavorResDirs = sourceProvider.resDirectories

            // we need the same of the flavor config, but it's in a different list.
            // This is fine as both list are parallel collections with the same number of items.
            resourceSet = ResourceSet(
                sourceProvider.name,
                ResourceNamespace.RES_AUTO,
                null,
                validateEnabled
            )
            resourceSet.addSources(flavorResDirs)
            resourceSets.add(resourceSet)
        }
        // multiflavor specific overrides flavor
        multiFlavorSourceProvider?.let {
            val variantResDirs = it.resDirectories
            resourceSet = ResourceSet(
                flavorName, ResourceNamespace.RES_AUTO, null, validateEnabled
            )
            resourceSet.addSources(variantResDirs)
            resourceSets.add(resourceSet)
        }

        // build type overrides the flavors
        buildTypeSourceSet?.let {
            val typeResDirs = it.resDirectories
            resourceSet = ResourceSet(
                buildType.name,
                ResourceNamespace.RES_AUTO,
                null,
                validateEnabled
            )
            resourceSet.addSources(typeResDirs)
            resourceSets.add(resourceSet)
        }

        // variant specific overrides all
        variantSourceProvider?.let {
            val variantResDirs = it.resDirectories
            resourceSet = ResourceSet(
                fullName, ResourceNamespace.RES_AUTO, null, validateEnabled
            )
            resourceSet.addSources(variantResDirs)
            resourceSets.add(resourceSet)
        }

        return resourceSets
    }

    /**
     * Returns the dynamic list of [AssetSet] based on the configuration, for a particular
     * property of [SourceProvider].
     *
     *
     * The list is ordered in ascending order of importance, meaning the first set is meant to be
     * overridden by the 2nd one and so on. This is meant to facilitate usage of the list in an
     * asset merger
     *
     * @param function the function that return a collection of file based on the SourceProvider.
     * this is usually a method referenceo on SourceProvider
     * @return a list ResourceSet.
     */
    fun getSourceFilesAsAssetSets(
        function: Function<SourceProvider, Collection<File>>
    ): List<AssetSet> {
        val assetSets = mutableListOf<AssetSet>()

        val mainResDirs = function.apply(defaultSourceSet)
        // the main + generated asset folders are in the same AssetSet
        var assetSet = AssetSet(BuilderConstants.MAIN)
        assetSet.addSources(mainResDirs)
        assetSets.add(assetSet)
        // the list of flavor must be reversed to use the right overlay order.
        for (n in mFlavorSourceProviders.indices.reversed()) {
            val sourceProvider = mFlavorSourceProviders[n]
            val flavorResDirs = function.apply(sourceProvider)
            // we need the same of the flavor config, but it's in a different list.
// This is fine as both list are parallel collections with the same number of items.
            assetSet = AssetSet(mFlavors[n].name)
            assetSet.addSources(flavorResDirs)
            assetSets.add(assetSet)
        }

        // multiflavor specific overrides flavor
        multiFlavorSourceProvider?.let {
            val variantResDirs = function.apply(it)
            assetSet = AssetSet(flavorName)
            assetSet.addSources(variantResDirs)
            assetSets.add(assetSet)
        }

        // build type overrides flavors
        if (buildTypeSourceSet != null) {
            val typeResDirs = function.apply(buildTypeSourceSet)
            assetSet = AssetSet(buildType.name)
            assetSet.addSources(typeResDirs)
            assetSets.add(assetSet)
        }

        // variant specific overrides all
        variantSourceProvider?.let {
            val variantResDirs = function.apply(it)
            assetSet = AssetSet(fullName)
            assetSet.addSources(variantResDirs)
            assetSets.add(assetSet)
        }

        return assetSets
    }

    val renderscriptTarget: Int
        get() {
            val targetApi = mergedFlavor.renderscriptTargetApi ?: -1
            val minSdk = minSdkVersionValue
            return if (targetApi > minSdk) targetApi else minSdk
        }

    /**
     * Returns all the renderscript source folder from the main config, the flavors and the build
     * type.
     *
     * @return a list of folders.
     */
    val renderscriptSourceList: Collection<File>
        get() = getSourceFiles(
            Function { obj: SourceProvider -> obj.renderscriptDirectories }
        )

    val aidlSourceList: Collection<File>
        get() = getSourceFiles(
            Function { obj: SourceProvider -> obj.aidlDirectories }
        )

    val jniSourceList: Collection<File>
        get() = getSourceFiles(
            Function { obj: SourceProvider -> obj.cDirectories }
        )

    /**
     * Adds a variant-specific BuildConfig field.
     *
     * @param type the type of the field
     * @param name the name of the field
     * @param value the value of the field
     */
    fun addBuildConfigField(
        type: String, name: String, value: String
    ) {
        val classField: ClassField = ClassFieldImpl(type, name, value)
        mBuildConfigFields[name] = classField
    }

    /**
     * Adds a variant-specific res value.
     *
     * @param type the type of the field
     * @param name the name of the field
     * @param value the value of the field
     */
    fun addResValue(type: String, name: String, value: String) {
        val classField: ClassField = ClassFieldImpl(type, name, value)
        mResValues[name] = classField
    }// keep track of the names already added. This is because we show where the items
// come from so we cannot just put everything a map and let the new ones override the
// old ones.

    /**
     * Returns a list of items for the BuildConfig class.
     *
     *
     * Items can be either fields (instance of [com.android.builder.model.ClassField]) or
     * comments (instance of String).
     *
     * @return a list of items.
     */
    val buildConfigItems: List<Any>
        get() {
            val fullList: MutableList<Any> =
                Lists.newArrayList()
            // keep track of the names already added. This is because we show where the items
            // come from so we cannot just put everything a map and let the new ones override the
            // old ones.
            val usedFieldNames = mutableSetOf<String>()

            var list: Collection<ClassField> = mBuildConfigFields.values
            if (!list.isEmpty()) {
                fullList.add("Fields from the variant")
                fillFieldList(fullList, usedFieldNames, list)
            }
            list = buildType.buildConfigFields.values
            if (!list.isEmpty()) {
                fullList.add("Fields from build type: " + buildType.name)
                fillFieldList(fullList, usedFieldNames, list)
            }
            for (flavor in mFlavors) {
                list = flavor.buildConfigFields.values
                if (!list.isEmpty()) {
                    fullList.add("Fields from product flavor: " + flavor.name)
                    fillFieldList(fullList, usedFieldNames, list)
                }
            }
            list = defaultConfig.buildConfigFields.values
            if (!list.isEmpty()) {
                fullList.add("Fields from default config.")
                fillFieldList(fullList, usedFieldNames, list)
            }
            return fullList
        }// start from the lowest priority and just add it all. Higher priority fields
// will replace lower priority ones.

    /**
     * Return the merged build config fields for the variant.
     *
     *
     * This is made of the variant-specific fields overlaid on top of the build type ones, the
     * flavors ones, and the default config ones.
     *
     * @return a map of merged fields
     */
    val mergedBuildConfigFields: Map<String, ClassField>
        get() {
            val mergedMap: MutableMap<String, ClassField> = Maps.newHashMap()

            // start from the lowest priority and just add it all. Higher priority fields
            // will replace lower priority ones.
            mergedMap.putAll(defaultConfig.buildConfigFields)
            for (i in mFlavors.indices.reversed()) {
                mergedMap.putAll(mFlavors[i].buildConfigFields)
            }
            mergedMap.putAll(buildType.buildConfigFields)
            mergedMap.putAll(mBuildConfigFields)
            return mergedMap
        }

    /**
     * Return the merged res values for the variant.
     *
     *
     * This is made of the variant-specific fields overlaid on top of the build type ones, the
     * flavors ones, and the default config ones.
     *
     * @return a map of merged fields
     */
    val mergedResValues: Map<String, ClassField>
        get() {
            // start from the lowest priority and just add it all. Higher priority fields
            // will replace lower priority ones.
            val mergedMap: MutableMap<String, ClassField> = Maps.newHashMap()
            mergedMap.putAll(defaultConfig.resValues)
            for (i in mFlavors.indices.reversed()) {
                mergedMap.putAll(mFlavors[i].resValues)
            }
            mergedMap.putAll(buildType.resValues)
            mergedMap.putAll(mResValues)
            return mergedMap
        }

    /**
     * Returns a list of generated resource values.
     *
     *
     * Items can be either fields (instance of [com.android.builder.model.ClassField]) or
     * comments (instance of String).
     *
     * @return a list of items.
     */
    val resValues: List<Any>
        get() {
            val fullList: MutableList<Any> =
                Lists.newArrayList()
            // keep track of the names already added. This is because we show where the items
            // come from so we cannot just put everything a map and let the new ones override the
            // old ones.
            val usedFieldNames: MutableSet<String> = Sets.newHashSet()
            var list: Collection<ClassField> = mResValues.values
            if (!list.isEmpty()) {
                fullList.add("Values from the variant")
                fillFieldList(fullList, usedFieldNames, list)
            }
            list = buildType.resValues.values
            if (!list.isEmpty()) {
                fullList.add("Values from build type: " + buildType.name)
                fillFieldList(fullList, usedFieldNames, list)
            }
            for (flavor in mFlavors) {
                list = flavor.resValues.values
                if (!list.isEmpty()) {
                    fullList.add("Values from product flavor: " + flavor.name)
                    fillFieldList(fullList, usedFieldNames, list)
                }
            }
            list = defaultConfig.resValues.values
            if (!list.isEmpty()) {
                fullList.add("Values from default config.")
                fillFieldList(fullList, usedFieldNames, list)
            }
            return fullList
        }

    val signingConfig: SigningConfig?
        get() {
            if (type.isDynamicFeature) {
                return null
            }
            if (mSigningConfigOverride != null) {
                return mSigningConfigOverride
            }
            val signingConfig: SigningConfig? = buildType.signingConfig
            return signingConfig ?: mergedFlavor.signingConfig
        }

    val isSigningReady: Boolean
        get() {
            val signingConfig = signingConfig
            return signingConfig != null && signingConfig.isSigningReady
        }

    val isTestCoverageEnabled: Boolean
        get() = buildType.isTestCoverageEnabled// so far, blindly override the build type placeholders

    /**
     * Returns the merged manifest placeholders. All product flavors are merged first, then build
     * type specific placeholders are added and potentially overrides product flavors values.
     *
     * @return the merged manifest placeholders for a build variant.
     */
    val manifestPlaceholders: Map<String, Any>
        get() {
            val mergedFlavorsPlaceholders =
                mergedFlavor.manifestPlaceholders
            // so far, blindly override the build type placeholders
            mergedFlavorsPlaceholders.putAll(buildType.manifestPlaceholders)
            return mergedFlavorsPlaceholders
        }

    // Only require specific multidex opt-in for legacy multidex.
    val isMultiDexEnabled: Boolean
        get() {
            // Only require specific multidex opt-in for legacy multidex.
            return buildType.multiDexEnabled
                ?: mergedFlavor.multiDexEnabled
                ?: (minSdkVersion.featureLevel >= 21)
        }

    val multiDexKeepFile: File?
        get() {
            var value = buildType.multiDexKeepFile
            if (value != null) {
                return value
            }
            value = mergedFlavor.multiDexKeepFile
            return value
        }

    val multiDexKeepProguard: File?
        get() {
            var value = buildType.multiDexKeepProguard
            if (value != null) {
                return value
            }
            value = mergedFlavor.multiDexKeepProguard
            return value
        }

    val isLegacyMultiDexMode: Boolean
        get() = dexingType === DexingType.LEGACY_MULTIDEX

    // dynamic features can always be build in native multidex mode
    val dexingType: DexingType
        get() = if (type.isDynamicFeature) {
            if (buildType.multiDexEnabled != null
                || mergedFlavor.multiDexEnabled != null
            ) {
                issueReporter
                    .reportWarning(
                        EvalIssueReporter.Type.GENERIC,
                        "Native multidex is always used for dynamic features. Please "
                                + "remove 'multiDexEnabled true|false' from your "
                                + "build.gradle file."
                    )
            }
            // dynamic features can always be build in native multidex mode
            DexingType.NATIVE_MULTIDEX
        } else if (isMultiDexEnabled) {
            if (minSdkVersion.featureLevel < 21) DexingType.LEGACY_MULTIDEX else DexingType.NATIVE_MULTIDEX
        } else {
            DexingType.MONO_DEX
        }// default is false.

    /** Returns the renderscript support mode.  */
    val renderscriptSupportModeEnabled: Boolean
        get() {
            val value = mergedFlavor.renderscriptSupportModeEnabled
            return value ?: false
            // default is false.
        }// default is false.

    /** Returns the renderscript BLAS support mode.  */
    val renderscriptSupportModeBlasEnabled: Boolean
        get() {
            val value = mergedFlavor.renderscriptSupportModeBlasEnabled
            return value ?: false
            // default is false.
        }// default is false.

    /** Returns the renderscript NDK mode.  */
    val renderscriptNdkModeEnabled: Boolean
        get() {
            val value = mergedFlavor.renderscriptNdkModeEnabled
            return value ?: false
            // default is false.
        }

    /** Returns true if the variant output is a bundle.  */
    val isBundled: Boolean
        get() = type.isAar// Consider runtime API passed from the IDE only if multi-dex is enabled and the app is
// debuggable.

    /**
     * Returns the minimum SDK version for this variant, potentially overridden by a property passed
     * by the IDE.
     *
     * @see .getMinSdkVersion
     */
    val minSdkVersionWithTargetDeviceApi: AndroidVersion
        get() {
            val targetApiLevel =
                projectOptions[IntegerOption.IDE_TARGET_DEVICE_API]
            return if (targetApiLevel != null && isMultiDexEnabled && buildType.isDebuggable) { // Consider runtime API passed from the IDE only if multi-dex is enabled and the app is
// debuggable.
                val minVersion: Int =
                    if (targetSdkVersion.apiLevel > 1) Integer.min(
                        targetSdkVersion.apiLevel,
                        targetApiLevel
                    ) else targetApiLevel
                AndroidVersion(minVersion)
            } else {
                minSdkVersion
            }
        }

    /** Interface for building the [GradleVariantConfiguration] instances.  */
    interface Builder {
        /** Creates a variant configuration  */
        fun create(
            projectOptions: ProjectOptions,
            defaultConfig: DefaultConfig,
            defaultSourceProvider: SourceProvider,
            mainManifestAttributeSupplier: ManifestAttributeSupplier?,
            buildType: BuildType,
            buildTypeSourceProvider: SourceProvider?,
            type: VariantType,
            signingConfigOverride: SigningConfig?,
            issueReporter: EvalIssueReporter,
            isInExecutionPhase: BooleanSupplier
        ): GradleVariantConfiguration
    }

    /** Builder for non-testing variant configurations  */
    private class VariantConfigurationBuilder :
        Builder {
        override fun create(
            projectOptions: ProjectOptions,
            defaultConfig: DefaultConfig,
            defaultSourceProvider: SourceProvider,
            mainManifestAttributeSupplier: ManifestAttributeSupplier?,
            buildType: BuildType,
            buildTypeSourceProvider: SourceProvider?,
            type: VariantType,
            signingConfigOverride: SigningConfig?,
            issueReporter: EvalIssueReporter,
            isInExecutionPhase: BooleanSupplier
        ): GradleVariantConfiguration {
            return GradleVariantConfiguration(
                projectOptions,
                null /*testedConfig*/,
                defaultConfig,
                defaultSourceProvider,
                mainManifestAttributeSupplier,
                buildType,
                buildTypeSourceProvider,
                type,
                signingConfigOverride,
                issueReporter,
                isInExecutionPhase
            )
        }
    }

    /**
     * Creates a [GradleVariantConfiguration] for a testing module variant.
     *
     *
     * The difference from the regular modules is how the original application id,
     * and application id are resolved. Our build process supports the absence of manifest
     * file for these modules, and that is why the value resolution for these attributes
     * is different.
     */
    private class TestModuleConfigurationBuilder :
        Builder {
        override fun create(
            projectOptions: ProjectOptions,
            defaultConfig: DefaultConfig,
            defaultSourceProvider: SourceProvider,
            mainManifestAttributeSupplier: ManifestAttributeSupplier?,
            buildType: BuildType,
            buildTypeSourceProvider: SourceProvider?,
            type: VariantType,
            signingConfigOverride: SigningConfig?,
            issueReporter: EvalIssueReporter,
            isInExecutionPhase: BooleanSupplier
        ): GradleVariantConfiguration {
            return object : GradleVariantConfiguration(
                projectOptions,
                null /*testedConfig*/,
                defaultConfig,
                defaultSourceProvider,
                mainManifestAttributeSupplier,
                buildType,
                buildTypeSourceProvider,
                type,
                signingConfigOverride,
                issueReporter,
                isInExecutionPhase
            ) {
                override val applicationId: String
                    get() {
                        val applicationId = mergedFlavor.testApplicationId
                        if (applicationId != null && applicationId.isNotEmpty()) {
                            return applicationId
                        }

                        return super.applicationId
                    }

                override val originalApplicationId: String
                    get() = applicationId

                override val testApplicationId: String
                    get() = applicationId

                override fun getMyTestConfig(
                    defaultSourceProvider: SourceProvider,
                    mainManifestAttributeSupplier: ManifestAttributeSupplier?,
                    buildTypeSourceProvider: SourceProvider?,
                    type: VariantType,
                    isInExecutionPhase: BooleanSupplier
                ): GradleVariantConfiguration? {
                    throw UnsupportedOperationException("Test modules have no test variants.")
                }
            }
        }
    }

    /**
     * Merge Gradle specific options from build types, product flavors and default config.
     */
    private fun mergeOptions() {
        computeMergedOptions(
            mergedJavaCompileOptions,
            { javaCompileOptions },
            { javaCompileOptions }
        )
        computeMergedOptions(
            mergedNdkConfig,
            { ndkConfig },
            { ndkConfig }
        )
        computeMergedOptions(
            mergedExternalNativeBuildOptions,
            { externalNativeBuildOptions },
            { externalNativeBuildOptions }
        )
    }

    val ndkConfig: CoreNdkOptions
        get() = mergedNdkConfig

    val externalNativeBuildOptions: CoreExternalNativeBuildOptions
        get() = mergedExternalNativeBuildOptions

    /**
     * Returns the ABI filters associated with the artifact, or null if there are no filters.
     *
     * If the list contains values, then the artifact only contains these ABIs and excludes
     * others.
     */
    val supportedAbis: Set<String>?
        get() = mergedNdkConfig.abiFilters

    /**
     * Merge a specific option in GradleVariantConfiguration.
     *
     *
     * It is assumed that merged option type with a method to reset and append is created for the
     * option being merged.
     *
     *
     * The order of priority is BuildType, ProductFlavors, and default config. ProductFlavor
     * added earlier has higher priority than ProductFlavor added later.
     *
     * @param mergedOption The merged option store in the GradleVariantConfiguration.
     * @param productFlavorOptionGetter A Function to return the option from a ProductFlavor.
     * @param getBuildTypeOption A Function to return the option from a BuildType.
     * @param reset A method to return 'option' to its default state.
     * @param append A BiConsumer to combine two options into one. Option in second input argument
     * takes priority and overwrite option in the first input argument.
     * @param <CoreOptionsT> The core type of the option being merge.
     * @param <MergedOptionsT> The merge option type.
    </MergedOptionsT></CoreOptionsT> */
    private fun <CoreOptionsT, MergedOptionsT: MergedOptions<CoreOptionsT>> computeMergedOptions(
        mergedOption: MergedOptionsT,
        getFlavorOption: BaseFlavor.() -> CoreOptionsT?,
        getBuildTypeOption: BuildType.() -> CoreOptionsT?
    ) {
        mergedOption.reset()

        val defaultOption = defaultConfig.getFlavorOption()
        if (defaultOption != null) {
            mergedOption.append(defaultOption)
        }
        // reverse loop for proper order
        for (i in mFlavors.indices.reversed()) {
            val flavorOption = mFlavors[i].getFlavorOption()
            if (flavorOption != null) {
                mergedOption.append(flavorOption)
            }
        }
        val buildTypeOption = buildType.getBuildTypeOption()
        if (buildTypeOption != null) {
            mergedOption.append(buildTypeOption)
        }
    }

    val javaCompileOptions: JavaCompileOptions
        get() = mergedJavaCompileOptions

    /** Returns a status code indicating whether Instant Run is supported and why.  */
    fun getInstantRunSupportStatus(globalScope: GlobalScope): Int {
        return InstantRun.STATUS_REMOVED
    }

    // add the lower priority one, to override them with the higher priority ones.
    // cant use merge flavor as it's not a prop on the base class.
    // reverse loop for proper order
    val defautGlslcArgs: List<String>
        get() {
            val optionMap: MutableMap<String, String> =
                Maps.newHashMap()
            // add the lower priority one, to override them with the higher priority ones.
            for (option in defaultConfig.shaders.glslcArgs) {
                optionMap[getKey(option)] = option
            }
            // cant use merge flavor as it's not a prop on the base class.
            // reverse loop for proper order
            for (i in mFlavors.indices.reversed()) {
                for (option in mFlavors[i].shaders.glslcArgs) {
                    optionMap[getKey(option)] = option
                }
            }
            // then the build type
            for (option in buildType.shaders.glslcArgs) {
                optionMap[getKey(option)] = option
            }
            return Lists.newArrayList(optionMap.values)
        }// global
    // scoped.
    // 3. the build type, global
    // 3b. the build type, scoped.
    // now add the full value list.
// first add to a temp map to resolve overridden values
    // we're going to go from lower priority, to higher priority elements, and for each
// start with the non scoped version, and then add the scoped version.
// 1. default config, global.
    // 1b. default config, scoped.
    // 2. the flavors.
// cant use merge flavor as it's not a prop on the base class.
// reverse loop for proper order

    // first collect all possible keys.
    val scopedGlslcArgs: Map<String, List<String>>
        get() {
            val scopedArgs: MutableMap<String, List<String>> =
                Maps.newHashMap()
            // first collect all possible keys.
            val keys = scopedGlslcKeys
            for (key in keys) { // first add to a temp map to resolve overridden values
                val optionMap: MutableMap<String, String> =
                    Maps.newHashMap()
                // we're going to go from lower priority, to higher priority elements, and for each
                // start with the non scoped version, and then add the scoped version.
                // 1. default config, global.
                for (option in defaultConfig.shaders.glslcArgs) {
                    optionMap[getKey(option)] = option
                }
                // 1b. default config, scoped.
                for (option in defaultConfig.shaders.scopedGlslcArgs[key]) {
                    optionMap[getKey(option)] = option
                }
                // 2. the flavors.
                // cant use merge flavor as it's not a prop on the base class.
                // reverse loop for proper order
                for (i in mFlavors.indices.reversed()) { // global
                    for (option in mFlavors[i].shaders.glslcArgs) {
                        optionMap[getKey(option)] = option
                    }
                    // scoped.
                    for (option in mFlavors[i].shaders.scopedGlslcArgs[key]) {
                        optionMap[getKey(option)] = option
                    }
                }
                // 3. the build type, global
                for (option in buildType.shaders.glslcArgs) {
                    optionMap[getKey(option)] = option
                }
                // 3b. the build type, scoped.
                for (option in buildType.shaders.scopedGlslcArgs[key]) {
                    optionMap[getKey(option)] = option
                }
                // now add the full value list.
                scopedArgs[key] = ImmutableList.copyOf(optionMap.values)
            }
            return scopedArgs
        }

    private val scopedGlslcKeys: Set<String>
        private get() {
            val keys: MutableSet<String> =
                Sets.newHashSet()
            keys.addAll(defaultConfig.shaders.scopedGlslcArgs.keySet())
            for (flavor in mFlavors) {
                keys.addAll(flavor.shaders.scopedGlslcArgs.keySet())
            }
            keys.addAll(buildType.shaders.scopedGlslcArgs.keySet())
            return keys
        }

    companion object {
        /**
         * Returns the full, unique name of the variant in camel case (starting with a lower case),
         * including BuildType, Flavors and Test (if applicable).
         *
         *
         * This is to be used for the normal variant name. In case of Feature plugin, the library
         * side will be called the same as for library plugins, while the feature side will add
         * 'feature' to the name.
         *
         * @param flavorName the flavor name, as computed by [.computeFlavorName]
         * @param buildType the build type
         * @param type the variant type
         * @return the name of the variant
         */
        @JvmStatic
        fun computeRegularVariantName(
            flavorName: String,
            buildType: com.android.builder.model.BuildType,
            type: VariantType
        ): String {
            val sb = StringBuilder()
            if (!flavorName.isEmpty()) {
                sb.append(flavorName)
                sb.appendCapitalized(buildType.name)
            } else {
                sb.append(buildType.name)
            }
            if (type.isTestComponent) {
                sb.append(type.suffix)
            }
            return sb.toString()
        }

        /**
         * Returns the flavor name for a variant composed of the given flavors, including all flavor
         * names in camel case (starting with a lower case).
         *
         *
         * If the flavor list is empty, then an empty string is returned.
         *
         * @param flavors the list of flavors
         * @return the flavor name or an empty string.
         */
        @JvmStatic
        fun computeFlavorName(
            flavors: List<com.android.builder.model.ProductFlavor>
        ): String {
            return if (flavors.isEmpty()) {
                ""
            } else {
                combineAsCamelCase(flavors, com.android.builder.model.ProductFlavor::getName)
            }
        }

        private const val DEFAULT_TEST_RUNNER = "android.test.InstrumentationTestRunner"
        private const val MULTIDEX_TEST_RUNNER =
            "com.android.test.runner.MultiDexTestRunner"
        private const val DEFAULT_HANDLE_PROFILING = false
        private const val DEFAULT_FUNCTIONAL_TEST = false

        /** Returns whether the manifest file is required to exist for the given variant type.  */
        @JvmStatic
        fun isManifestFileRequired(variantType: VariantType): Boolean { // The manifest file is not required to exist for a test variant or a test project
            return !variantType.isForTesting
        }

        /**
         * Fills a list of Object from a given list of ClassField only if the name isn't in a set. Each
         * new item added adds its name to the list.
         *
         * @param outList the out list
         * @param usedFieldNames the list of field names already in the list
         * @param list the list to copy items from
         */
        private fun fillFieldList(
            outList: MutableList<Any>,
            usedFieldNames: MutableSet<String>,
            list: Collection<ClassField>
        ) {
            for (f in list) {
                val name = f.name
                if (!usedFieldNames.contains(name)) {
                    usedFieldNames.add(f.name)
                    outList.add(f)
                }
            }
        }

        /** Depending on the extension, gets appropriate variant configuration builder  */
        @JvmStatic
        fun getBuilderForExtension(extension: BaseExtension): Builder {
            return if (extension is TestAndroidConfig) { // if this is the test module
                TestModuleConfigurationBuilder()
            } else { // if this is non-test variant
                VariantConfigurationBuilder()
            }
        }

        private fun getKey(fullOption: String): String {
            val pos = fullOption.lastIndexOf('=')
            return if (pos == -1) {
                fullOption
            } else fullOption.substring(0, pos)
        }
    }

    init {
        Preconditions.checkNotNull(defaultConfig)
        Preconditions.checkNotNull(
            defaultSourceProvider
        )
        Preconditions.checkNotNull(
            buildType
        )
        Preconditions.checkNotNull(type)
        Preconditions.checkArgument(
            !type.isTestComponent || testedConfig != null,
            "You have to specify the tested variant for this variant type."
        )
        Preconditions.checkArgument(
            type.isTestComponent || testedConfig == null,
            "This variant type doesn't need a tested variant."
        )
        this.defaultConfig =
            Preconditions.checkNotNull(defaultConfig)
        defaultSourceSet =
            Preconditions.checkNotNull(
                defaultSourceProvider
            )
        this.buildType =
            Preconditions.checkNotNull(
                buildType
            )
        buildTypeSourceSet = buildTypeSourceProvider
        this.type =
            Preconditions.checkNotNull(
                type
            )
        this.testedConfig = testedConfig
        mSigningConfigOverride = signingConfigOverride
        this.issueReporter = issueReporter
        mergedFlavor = clone(this.defaultConfig, this.issueReporter)
        val manifestParser =
            mainManifestAttributeSupplier
                ?: DefaultManifestParser(
                    defaultSourceSet.manifestFile,
                    isInExecutionPhase,
                    isManifestFileRequired(type),
                    issueReporter
                )
        mVariantAttributesProvider = VariantAttributesProvider(
            mergedFlavor,
            this.buildType,
            type.isTestComponent,
            manifestParser,
            defaultSourceSet.manifestFile,
            fullName
        )
        mergeOptions()
        this.projectOptions = projectOptions
    }
}