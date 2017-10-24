/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.variant2

import com.android.build.api.dsl.model.BuildType
import com.android.build.api.dsl.model.DefaultConfig
import com.android.build.api.dsl.model.ProductFlavor
import com.android.build.api.dsl.options.SigningConfig
import com.android.build.api.sourcesets.AndroidSourceSet
import com.android.build.gradle.internal.api.dsl.extensions.BaseExtension2
import com.android.build.gradle.internal.api.dsl.model.BuildTypeFactory
import com.android.build.gradle.internal.api.dsl.model.BuildTypeImpl
import com.android.build.gradle.internal.api.dsl.model.DefaultConfigImpl
import com.android.build.gradle.internal.api.dsl.model.ProductFlavorFactory
import com.android.build.gradle.internal.api.dsl.model.ProductFlavorImpl
import com.android.build.gradle.internal.api.dsl.options.SigningConfigFactory
import com.android.build.gradle.internal.api.dsl.options.SigningConfigImpl
import com.android.build.gradle.internal.api.dsl.sealing.Sealable
import com.android.build.gradle.internal.api.dsl.sealing.SealableNamedDomainObjectContainer
import com.android.build.gradle.internal.api.sourcesets.AndroidSourceSetFactory
import com.android.build.gradle.internal.api.sourcesets.DefaultAndroidSourceSet
import com.android.build.gradle.internal.api.sourcesets.FilesProvider
import com.android.builder.core.BuilderConstants
import com.android.builder.core.VariantType
import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.builder.errors.EvalIssueReporter
import com.android.builder.errors.EvalIssueReporter.Type
import com.android.utils.StringHelper
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.logging.Logger
import org.gradle.internal.reflect.Instantiator
import java.util.function.BinaryOperator
import java.util.stream.Collectors

/**
 * Internal DSL model exposed to the extension objects.
 */
interface DslModelData {
    val defaultConfig: DefaultConfig
    val sourceSets: NamedDomainObjectContainer<AndroidSourceSet>
    val productFlavors: NamedDomainObjectContainer<ProductFlavor>
    val buildTypes: NamedDomainObjectContainer<BuildType>
    val signingConfigs: NamedDomainObjectContainer<SigningConfig>
}

/**
 * a Factory of [NamedDomainObjectContainer]
 *
 * This is to facilitate testing.
 */
interface ContainerFactory {
    /**
     * Creates a container
     *
     * @param itemClass the class of the items.
     * @param factory a factory to create items.
     */
    fun <T> createContainer(itemClass: Class<T>,
            factory: NamedDomainObjectFactory<T>): NamedDomainObjectContainer<T>
}

val CONFIG_DESC = "%s dependencies for '%s' sources."
val CONFIG_DESC_OLD = "%s dependencies for '%s' sources (deprecated: use '%s' instead)."

/**
 * Implementation of the DslModelData interface
 */
class DslModelDataImpl<in E: BaseExtension2>(
        override val defaultConfig: DefaultConfigImpl,
        internal val variantFactories: List<VariantFactory2<E>>,
        private val configurationContainer: ConfigurationContainer,
        filesProvider: FilesProvider,
        containerFactory: ContainerFactory,
        instantiator: Instantiator, //FIXME replace with ObjectFactory in 4.2
        private val deprecationReporter: DeprecationReporter,
        private val issueReporter: EvalIssueReporter,
        private val logger: Logger): DslModelData, Sealable {

    // wrapped container for source sets.
    internal val _sourceSets: NamedDomainObjectContainer<DefaultAndroidSourceSet>

    // sealable container for source set.
    override val sourceSets: SealableNamedDomainObjectContainer<AndroidSourceSet, DefaultAndroidSourceSet>

    // wrapped container for product flavors
    internal val _productFlavors: NamedDomainObjectContainer<ProductFlavorImpl> =
            containerFactory.createContainer(
                    ProductFlavorImpl::class.java,
                    ProductFlavorFactory(instantiator, deprecationReporter, issueReporter))

    // sealable container for product flavors
    override val productFlavors: SealableNamedDomainObjectContainer<ProductFlavor, ProductFlavorImpl> =
            createSealableContainer(
                    ProductFlavor::class.java,
                    ProductFlavorImpl::class.java,
                    _productFlavors)

    // wrapped container for build type
    internal val _buildTypes: NamedDomainObjectContainer<BuildTypeImpl> =
            containerFactory.createContainer(
                    BuildTypeImpl::class.java,
                    BuildTypeFactory(instantiator, deprecationReporter, issueReporter))

    // sealable container for build type
    override val buildTypes: SealableNamedDomainObjectContainer<BuildType, BuildTypeImpl> =
            createSealableContainer(
                    BuildType::class.java,
                    BuildTypeImpl::class.java,
                    _buildTypes)

    // wrapped container for signing config
    internal val _signingConfigs: NamedDomainObjectContainer<SigningConfigImpl> =
            containerFactory.createContainer(
                    SigningConfigImpl::class.java,
                    SigningConfigFactory(instantiator, deprecationReporter, issueReporter))

    // sealable container for signing config
    override val signingConfigs: SealableNamedDomainObjectContainer<SigningConfig, SigningConfigImpl> =
            createSealableContainer(
                    SigningConfig::class.java,
                    SigningConfigImpl::class.java,
                    _signingConfigs)

    private val _flavorData: MutableMap<String, DimensionData<ProductFlavorImpl>> = mutableMapOf()
    private val _buildTypeData: MutableMap<String, DimensionData<BuildTypeImpl>> = mutableMapOf()

    private var afterEvaluatedComputation = false

    val flavorData: Map<String, DimensionData<ProductFlavorImpl>>
        get() {
            if (!afterEvaluatedComputation) throw RuntimeException("Called before afterEvaluateCompute")
            return _flavorData
        }
    val buildTypeData: Map<String, DimensionData<BuildTypeImpl>>
        get() {
            if (!afterEvaluatedComputation) throw RuntimeException("Called before afterEvaluateCompute")
            return _buildTypeData
        }

    val defaultConfigData: DimensionData<DefaultConfigImpl>

    private val mainVariantType: VariantType
    private val hasAndroidTests: Boolean
    private val hasUnitTests: Boolean

    init {
        // detact the test level support
        val variantTypes = variantFactories.stream().map({ it.generatedType}).collect(
                Collectors.toSet())

        mainVariantType = variantTypes
                .stream()
                .filter { !it.isForTesting }
                .reduce(toSingleItem())
                .orElseThrow { RuntimeException("No main variant type") }

        _sourceSets = containerFactory.createContainer(
                DefaultAndroidSourceSet::class.java,
                AndroidSourceSetFactory(
                        filesProvider,
                        mainVariantType == VariantType.LIBRARY,
                        instantiator,
                        deprecationReporter,
                        issueReporter))

        sourceSets = createSealableContainer(
                AndroidSourceSet::class.java, DefaultAndroidSourceSet::class.java, _sourceSets)

        hasAndroidTests = variantTypes.contains(VariantType.ANDROID_TEST)
        hasUnitTests = variantTypes.contains(VariantType.UNIT_TEST)

        // setup callback to generate source sets on the fly, as well as the associated
        // configurations
        _productFlavors.whenObjectAdded { checkNewFlavor(it) }
        _buildTypes.whenObjectAdded { checkNewBuildType(it) }
        _sourceSets.whenObjectAdded { handleNewSourceSet(it) }

        // map whenObjectRemoved on the containers to throw an exception.
        val lambda: (Any) -> Unit = { UnsupportedOperationException("Removing objects is not supported.") }
        _sourceSets.whenObjectRemoved(lambda)
        _signingConfigs.whenObjectRemoved(lambda)
        _buildTypes.whenObjectRemoved(lambda)
        _productFlavors.whenObjectRemoved(lambda)

        // and now create source set and dimension data for the default config
        createSourceSets(BuilderConstants.MAIN)
        defaultConfigData = createDimensionData(defaultConfig, { _ -> BuilderConstants.MAIN} )
    }

    /**
     * Does afterEvaluation computation of source sets and flavor/build type data.
     */
    fun afterEvaluateCompute() {
        // loop on flavors and build types.
        _productFlavors.forEach { flavor ->
            _flavorData[flavor.name] = createDimensionData(flavor, { it.name })
        }

        _buildTypes.forEach { buildType ->
            _buildTypeData[buildType.name] = createDimensionData(buildType, { it.name})
        }

        afterEvaluatedComputation = true
    }

    override fun seal() {
        defaultConfig.seal()
        sourceSets.seal()
        productFlavors.seal()
        signingConfigs.seal()
    }

    private fun <I, T: I> createSealableContainer(
            interfaceClass: Class<I>,
            itemClass: Class<T>,
            container: NamedDomainObjectContainer<T>
    ): SealableNamedDomainObjectContainer<I, T> {
        return SealableNamedDomainObjectContainer(container, itemClass, issueReporter)
    }

    private fun <T> createDimensionData(data: T, nameFun: (T) -> String): DimensionData<T> {
        val name = nameFun(data)

        return DimensionData(
                data,
                sourceSets.getByName(name), // this one must exist, so use getByName
                sourceSets.findByName(computeSourceSetName(name, VariantType.ANDROID_TEST)), // this one might not, so use findByName
                sourceSets.findByName(computeSourceSetName(name, VariantType.UNIT_TEST)), // this one might not, so use findByName
                configurationContainer)
    }

    /**
     * Callback for all new added product flavor.
     *
     * Checks its for correctness and creates its associated source sets.
     */
    private fun checkNewFlavor(productFlavor: ProductFlavorImpl) {
        val name = productFlavor.name

        if (!checkName(name, "ProductFlavor")) {
            // don't want to keep going in case of sync
            return
        }

        if (_buildTypes.any { it.name == name }) {
            issueReporter.reportError(Type.GENERIC,
                    "ProductFlavor names cannot collide with BuildType names: $name")

            // don't want to keep going in case of sync
            return
        }

        // create sourcesets
        createSourceSets(name)
    }

    /**
     * Callback for all new added build type.
     *
     * Checks its for correctness and creates its associated source sets.
     */
    private fun checkNewBuildType(buildType: BuildTypeImpl) {
        val name = buildType.name

        // setup the signing config for debug build type
        if (name == BuilderConstants.DEBUG) {
            val signingConfig = signingConfigs.findByName(BuilderConstants.DEBUG)
            buildType.signingConfig = signingConfig
        }

        if (!checkName(name, "BuildType")) {
            // don't want to keep going in case of sync
            return
        }

        if (_productFlavors.any { it.name == name }) {
            issueReporter.reportError(Type.GENERIC,
                    "BuildType names cannot collide with ProductFlavor names: $name")

            // don't want to keep going in case of sync
            return
        }

        // create sourcesets
        createSourceSets(name)
    }

    /** callback for creating sourcesets when a product flavor/build type is added. */
    private fun createSourceSets(name: String) {
        // safe to use the backing container directly since this is called on new flavor
        // or build type.
        _sourceSets.maybeCreate(name)

        if (hasAndroidTests) {
            _sourceSets.maybeCreate(computeSourceSetName(name, VariantType.ANDROID_TEST))
        }

        if (hasUnitTests) {
            _sourceSets.maybeCreate(computeSourceSetName(name, VariantType.UNIT_TEST))
        }
    }

    /**
     * Callback for all newly added sourcesets
     */
    private fun handleNewSourceSet(sourceSet: DefaultAndroidSourceSet) {
        // set the default location of the source set
        sourceSet.setRoot(String.format("src/%s", sourceSet.name))

        // create the associated configurations
        val implementationName = sourceSet.implementationConfigurationName
        val runtimeOnlyName = sourceSet.runtimeOnlyConfigurationName
        val compileOnlyName = sourceSet.compileOnlyConfigurationName

        // deprecated configurations first.
        val compileName = sourceSet._compileConfigurationName
        // due to compatibility with other plugins and with Gradle sync,
        // we have to keep 'compile' as resolvable.
        // TODO Fix this in gradle sync.
        val compile = createConfiguration(
                configurationContainer,
                compileName,
                String.format(
                        CONFIG_DESC_OLD,
                        "Compile",
                        sourceSet.name,
                        implementationName),
                "compile" == compileName || "testCompile" == compileName /*canBeResolved*/)
        compile.allDependencies
                .whenObjectAdded(
                        DeprecatedConfigurationAction(implementationName, compileName, deprecationReporter))

        val packageConfigDescription = if (mainVariantType == VariantType.LIBRARY) {
            String.format(
                    CONFIG_DESC_OLD,
                    "Publish",
                    sourceSet.name,
                    runtimeOnlyName)
        } else {
            String.format(
                    CONFIG_DESC_OLD,
                    "Apk",
                    sourceSet.name,
                    runtimeOnlyName)
        }

        val apkName = sourceSet._packageConfigurationName
        val apk = createConfiguration(
                configurationContainer, apkName, packageConfigDescription)
        apk.allDependencies
                .whenObjectAdded(
                        DeprecatedConfigurationAction(
                                runtimeOnlyName, apkName, deprecationReporter))

        val providedName = sourceSet._providedConfigurationName
        val provided = createConfiguration(
                configurationContainer,
                providedName,
                String.format(
                        CONFIG_DESC_OLD,
                        "Provided",
                        sourceSet.name,
                        compileOnlyName))
        provided.allDependencies
                .whenObjectAdded(
                        DeprecatedConfigurationAction(
                                compileOnlyName, providedName, deprecationReporter))

        // then the new configurations.
        val apiName = sourceSet.apiConfigurationName
        val api = createConfiguration(
                configurationContainer, apiName, String.format(CONFIG_DESC, "API", sourceSet.name))
        api.extendsFrom(compile)

        val implementation = createConfiguration(
                configurationContainer,
                implementationName,
                String.format(
                        CONFIG_DESC,
                        "Implementation only",
                        sourceSet.name))
        implementation.extendsFrom(api)

        val runtimeOnly = createConfiguration(
                configurationContainer,
                runtimeOnlyName,
                String.format(
                        CONFIG_DESC, "Runtime only", sourceSet.name))
        runtimeOnly.extendsFrom(apk)

        val compileOnly = createConfiguration(
                configurationContainer,
                compileOnlyName,
                String.format(
                        CONFIG_DESC, "Compile only", sourceSet.name))
        compileOnly.extendsFrom(provided)

        // then the secondary configurations.
        createConfiguration(
                configurationContainer,
                sourceSet.wearAppConfigurationName,
                "Link to a wear app to embed for object '"
                        + sourceSet.name
                        + "'.")

        createConfiguration(
                configurationContainer,
                sourceSet.annotationProcessorConfigurationName,
                "Classpath for the annotation processor for '"
                        + sourceSet.name
                        + "'.")
    }

    /**
     * Creates a Configuration for a given source set.
     *
     * @param configurationContainer the configuration container to create the new configuration
     * @param name the name of the configuration to create.
     * @param desc the configuration description.
     * @param canBeResolved Whether the configuration can be resolved directly.
     * @return the configuration
     *
     * @see Configuration.isCanBeResolved
     */
    private fun createConfiguration(
            configurationContainer: ConfigurationContainer,
            name: String,
            desc: String,
            canBeResolved: Boolean = false): Configuration {
        logger.debug("Creating configuration {}", name)

        val configuration = configurationContainer.maybeCreate(name)

        with(configuration) {
            isVisible = false
            description = desc
            isCanBeConsumed = false
            isCanBeResolved = canBeResolved
        }

        return configuration
    }

    private fun checkName(name: String, displayName: String): Boolean {
        if (!checkPrefix(name, displayName, VariantType.ANDROID_TEST.prefix)) {
            return false
        }
        if (!checkPrefix(name, displayName, VariantType.UNIT_TEST.prefix)) {
            return false
        }

        if (BuilderConstants.MAIN == name) {
            issueReporter.reportError(Type.GENERIC,
                    "$displayName names cannot be '${BuilderConstants.MAIN}'")
            return false
        }

        if (BuilderConstants.LINT == name) {
            issueReporter.reportError(Type.GENERIC,
                    "$displayName names cannot be '${BuilderConstants.LINT}'")
            return false
        }

        return true
    }

    private fun checkPrefix(name: String, displayName: String, prefix: String): Boolean {
        if (name.startsWith(prefix)) {
            issueReporter.reportError(Type.GENERIC,
                    "$displayName names cannot start with '$prefix'")
            return false
        }

        return true
    }
}

/**
 * Turns a string into a valid source set name for the given [VariantType], e.g.
 * "fooBarUnitTest" becomes "testFooBar".
 *
 * This does not support MAIN.
 */
private fun computeSourceSetName(
        name: String,
        variantType: VariantType): String {
    if (name == BuilderConstants.MAIN) {
        if (variantType.prefix.isEmpty()) {
            return name
        }
        return variantType.prefix
    }

    var newName = name
    if (newName.endsWith(variantType.suffix)) {
        newName = newName.substring(0, newName.length - variantType.suffix.length)
    }

    if (!variantType.prefix.isEmpty()) {
        newName = variantType.prefix + StringHelper.capitalize(newName)
    }

    return newName
}

internal class DeprecatedConfigurationAction(
        private val replacement: String,
        private val oldName: String,
        private val deprecationReporter: DeprecationReporter,
        private val deprecationTarget: DeprecationReporter.DeprecationTarget = DeprecationReporter.DeprecationTarget.EOY2018) : Action<Dependency> {
    private var warningPrintedAlready = false

    override fun execute(dependency: Dependency) {
        if (!warningPrintedAlready) {
            warningPrintedAlready = true
            deprecationReporter.reportDeprecatedConfiguration(
                    replacement, oldName, deprecationTarget)
        }
    }
}

/**
 * The goal of this operator is not to reduce anything but to ensure that
 * there is a single item in the list. If it gets called it means
 * that there are two object in the list that had the same name, and this is an error.
 *
 * @see .searchForSingleItemInList
 */
private fun <T> toSingleItem(): BinaryOperator<T> {
    return BinaryOperator { name1, _ -> throw IllegalArgumentException("Duplicate objects with name: " + name1) }
}
