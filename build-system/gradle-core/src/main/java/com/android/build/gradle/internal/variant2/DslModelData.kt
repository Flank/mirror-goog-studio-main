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

import com.android.build.api.dsl.extension.EmbeddedTestProperties
import com.android.build.api.sourcesets.AndroidSourceSet
import com.android.build.gradle.internal.api.dsl.model.BuildTypeFactory
import com.android.build.gradle.internal.api.dsl.model.BuildTypeImpl
import com.android.build.gradle.internal.api.dsl.model.DefaultConfigImpl
import com.android.build.gradle.internal.api.dsl.model.ProductFlavorFactory
import com.android.build.gradle.internal.api.dsl.model.ProductFlavorImpl
import com.android.build.gradle.internal.api.dsl.options.SigningConfigFactory
import com.android.build.gradle.internal.api.dsl.options.SigningConfigImpl
import com.android.build.gradle.internal.api.sourcesets.AndroidSourceSetFactory
import com.android.build.gradle.internal.api.sourcesets.DefaultAndroidSourceSet
import com.android.builder.core.BuilderConstants
import com.android.builder.core.VariantType
import com.android.builder.errors.DeprecationReporter
import com.android.builder.errors.EvalIssueReporter
import com.android.builder.model.SyncIssue
import com.android.utils.StringHelper
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.internal.reflect.Instantiator

/**
 * Internal Store for items we want to control outside of the DSL classes.
 */
class DslModelData(
        private val project: Project,
        val defaultConfig: DefaultConfigImpl,
        instantiator: Instantiator, //FIXME replace with ObjectFactory in 4.2
        deprecationReporter: DeprecationReporter,
        private val issueReporter: EvalIssueReporter) {

    // FIXME, provide an interface to create container so that it can be tested.
    val sourceSets: NamedDomainObjectContainer<DefaultAndroidSourceSet> = project.container(
            DefaultAndroidSourceSet::class.java,
            AndroidSourceSetFactory(instantiator, deprecationReporter, issueReporter))

    val productFlavors: NamedDomainObjectContainer<ProductFlavorImpl> = project.container(
            ProductFlavorImpl::class.java,
            ProductFlavorFactory(instantiator, deprecationReporter, issueReporter))

    val buildTypes: NamedDomainObjectContainer<BuildTypeImpl> = project.container(
            BuildTypeImpl::class.java,
            BuildTypeFactory(instantiator, deprecationReporter, issueReporter))

    val signingConfigs: NamedDomainObjectContainer<SigningConfigImpl> = project.container(
            SigningConfigImpl::class.java,
            SigningConfigFactory(instantiator, deprecationReporter, issueReporter))

    private val _flavorData: MutableMap<String, DimensionData<ProductFlavorImpl>> = mutableMapOf()
    private val _buildTypeData: MutableMap<String, DimensionData<BuildTypeImpl>> = mutableMapOf()

    private lateinit var _defaultConfigData: DimensionData<DefaultConfigImpl>
    private var afterEvaluatedComputation = false

    val flavorData: Map<String, DimensionData<ProductFlavorImpl>>
        get() {
            if (afterEvaluatedComputation) throw RuntimeException("Called before afterEvaluateCompute")
            return _flavorData
        }
    val buildTypeData: Map<String, DimensionData<BuildTypeImpl>>
        get() {
            if (afterEvaluatedComputation) throw RuntimeException("Called before afterEvaluateCompute")
            return _buildTypeData
        }

    val defaultConfigData: DimensionData<DefaultConfigImpl>
        get() {
            if (afterEvaluatedComputation) throw RuntimeException("Called before afterEvaluateCompute")
            return _defaultConfigData
        }

    init {
        productFlavors.whenObjectAdded { checkNewFlavor(it) }
        buildTypes.whenObjectAdded { checkNewBuildType(it) }

        // map whenObjectRemoved on the containers to throw an exception.
        val lambda: (Any) -> Unit = { UnsupportedOperationException("Removing objects is not supported.") }
        sourceSets.whenObjectRemoved(lambda)
        signingConfigs.whenObjectRemoved(lambda)
        buildTypes.whenObjectRemoved(lambda)
        productFlavors.whenObjectRemoved(lambda)
    }

    /**
     * Does afterEvaluation computation of source sets and flavor/build type data.
     */
    fun afterEvaluateCompute(
            hasTests: Boolean, // FIXME is this the same as testProp?
            testProp: EmbeddedTestProperties?) {

        computeDefaultConfigData(hasTests)

        // loop on flavors and build types.
        productFlavors.forEach { flavor ->
            val name = flavor.name
            val mainSourceSet = sourceSets.maybeCreate(name)

            var androidTestSourceSet: AndroidSourceSet? = null
            var unitTestSourceSet: AndroidSourceSet? = null
            if (hasTests) {
                androidTestSourceSet = sourceSets.maybeCreate(
                        computeSourceSetName(name, VariantType.ANDROID_TEST))
                unitTestSourceSet = sourceSets.maybeCreate(
                        computeSourceSetName(name, VariantType.UNIT_TEST))
            }

            _flavorData[name] = DimensionData(
                    flavor, mainSourceSet, androidTestSourceSet, unitTestSourceSet, project)
        }

        buildTypes.forEach { buildType ->
            val name = buildType.name
            val mainSourceSet = sourceSets.maybeCreate(name)

            var androidTestSourceSet: AndroidSourceSet? = null
            var unitTestSourceSet: AndroidSourceSet? = null
            if (hasTests) {
                if (buildType.name == testProp?.testBuildType) {
                    androidTestSourceSet = sourceSets.maybeCreate(
                            computeSourceSetName(buildType.name, VariantType.ANDROID_TEST))
                }

                unitTestSourceSet = sourceSets.maybeCreate(
                        computeSourceSetName(buildType.name, VariantType.UNIT_TEST))
            }

            _buildTypeData[name] = DimensionData(
                    buildType, mainSourceSet, androidTestSourceSet, unitTestSourceSet, project)
        }

        afterEvaluatedComputation = true
    }

    private fun computeDefaultConfigData(hasTests: Boolean) {
        val mainSourceSet = sourceSets.maybeCreate(BuilderConstants.MAIN)

        var androidTestSourceSet: AndroidSourceSet? = null
        var unitTestSourceSet: AndroidSourceSet? = null
        if (hasTests) {
            androidTestSourceSet = sourceSets.maybeCreate(
                    computeSourceSetName(BuilderConstants.MAIN, VariantType.ANDROID_TEST))
            unitTestSourceSet = sourceSets.maybeCreate(
                    computeSourceSetName(BuilderConstants.MAIN, VariantType.UNIT_TEST))
        }

        _defaultConfigData = DimensionData(
                defaultConfig, mainSourceSet, androidTestSourceSet, unitTestSourceSet, project)
    }

    /**
     * Checks a new product flavor for correctness
     */
    private fun checkNewFlavor(productFlavor: ProductFlavorImpl) {
        val name = productFlavor.name

        if (!checkName(name, "ProductFlavor")) {
            // don't want to keep going in case of sync
            return
        }

        if (buildTypes.stream().filter({bt -> bt.name == name }).findFirst().isPresent) {
            issueReporter.reportError(SyncIssue.TYPE_GENERIC,
                    "ProductFlavor names cannot collide with BuildType names: $name")

            // don't want to keep going in case of sync
            return
        }
    }

    /**
     * checks a new build type for correctness
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

        if (productFlavors.stream().filter({bt -> bt.name == name }).findFirst().isPresent) {
            issueReporter.reportError(SyncIssue.TYPE_GENERIC,
                    "BuildType names cannot collide with ProductFlavor names: $name")

            // don't want to keep going in case of sync
            return
        }
    }

    private fun checkName(name: String, displayName: String): Boolean {
        checkPrefix(name, displayName, VariantType.ANDROID_TEST.prefix)
        checkPrefix(name, displayName, VariantType.UNIT_TEST.prefix)

        if (BuilderConstants.LINT == name) {
            issueReporter.reportError(SyncIssue.TYPE_GENERIC,
                    "$displayName names cannot be ${BuilderConstants.LINT}")
            return false
        }

        return true
    }

    private fun checkPrefix(name: String, displayName: String, prefix: String) {
        if (name.startsWith(prefix)) {
            issueReporter.reportError(SyncIssue.TYPE_GENERIC,
                    "$displayName names cannot start with '$prefix'")
        }
    }
}

/**
 * Turns a string into a valid source set name for the given [VariantType], e.g.
 * "fooBarUnitTest" becomes "testFooBar".
 */
private fun computeSourceSetName(
        name: String,
        variantType: VariantType): String {
    var newName = name
    if (newName.endsWith(variantType.suffix)) {
        newName = newName.substring(0, newName.length - variantType.suffix.length)
    }

    if (!variantType.prefix.isEmpty()) {
        newName = variantType.prefix + StringHelper.capitalize(newName)
    }

    return newName
}

