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

import com.android.build.gradle.internal.api.dsl.extensions.BaseExtension2
import com.android.build.gradle.internal.api.dsl.model.BaseFlavorImpl
import com.android.build.gradle.internal.api.dsl.model.BuildTypeOrProductFlavorImpl
import com.android.build.gradle.internal.api.dsl.model.DefaultConfigImpl
import com.android.build.gradle.internal.api.dsl.model.FallbackStrategyImpl
import com.android.build.gradle.internal.api.dsl.model.ProductFlavorImpl
import com.android.build.gradle.internal.api.dsl.model.ProductFlavorOrVariantImpl
import com.android.build.gradle.internal.api.dsl.model.VariantPropertiesImpl
import com.android.build.gradle.internal.fixtures.FakeConfigurationContainer
import com.android.build.gradle.internal.fixtures.FakeContainerFactory
import com.android.build.gradle.internal.fixtures.FakeDeprecationReporter
import com.android.build.gradle.internal.fixtures.FakeEvalIssueReporter
import com.android.build.gradle.internal.fixtures.FakeFilesProvider
import com.android.build.gradle.internal.fixtures.FakeInstantiator
import com.android.build.gradle.internal.fixtures.FakeLogger
import com.android.build.gradle.internal.fixtures.FakeVariantFactory2
import com.android.builder.core.BuilderConstants
import com.android.builder.core.VariantType
import com.google.common.truth.Truth
import org.gradle.api.NamedDomainObjectContainer
import org.junit.Test

class DslModelDataImplTest {
    private val issueReporter = FakeEvalIssueReporter()
    private val deprecationReporter = FakeDeprecationReporter()

    @Test
    fun `default config data check`() {
        val modelData = createModelData(
                createFactories(BaseExtension2::class.java, VariantType.DEFAULT))

        val defaultConfigData = modelData.defaultConfigData
        Truth.assertThat(defaultConfigData).isNotNull()

        var sourceSet = defaultConfigData.getSourceSet(VariantType.DEFAULT)
        Truth.assertThat(sourceSet).isNotNull()
        Truth.assertThat(sourceSet?.name).isEqualTo(BuilderConstants.MAIN)

        sourceSet = defaultConfigData.getSourceSet(VariantType.ANDROID_TEST)
        Truth.assertThat(sourceSet).isNotNull()
        Truth.assertThat(sourceSet?.name).isEqualTo("androidTest")

        sourceSet = defaultConfigData.getSourceSet(VariantType.UNIT_TEST)
        Truth.assertThat(sourceSet).isNotNull()
        Truth.assertThat(sourceSet?.name).isEqualTo("test")

        // always assert we haven't hit any deprecation
        Truth.assertThat(deprecationReporter.deprecationWarnings).isEmpty()
    }

    @Test
    fun `default config data check when no tests`() {
        val modelData = createModelData(
                createFactories(
                        BaseExtension2::class.java,
                        VariantType.DEFAULT,
                        hasAndroidTests = false,
                        hasUnitTests = false))

        val defaultConfigData = modelData.defaultConfigData
        Truth.assertThat(defaultConfigData).isNotNull()

        var sourceSet = defaultConfigData.getSourceSet(VariantType.DEFAULT)
        Truth.assertThat(sourceSet).isNotNull()
        Truth.assertThat(sourceSet?.name).isEqualTo(BuilderConstants.MAIN)

        sourceSet = defaultConfigData.getSourceSet(VariantType.ANDROID_TEST)
        Truth.assertThat(sourceSet).isNull()

        sourceSet = defaultConfigData.getSourceSet(VariantType.UNIT_TEST)
        Truth.assertThat(sourceSet).isNull()

        // always assert we haven't hit any deprecation
        Truth.assertThat(deprecationReporter.deprecationWarnings).isEmpty()
    }

    @Test
    fun `flavor data check when flavor is added to container`() {
        val modelData = createModelData(
                createFactories(BaseExtension2::class.java, VariantType.DEFAULT))

        val flavorName = "free"
        modelData.productFlavors.create(flavorName)

        modelData.afterEvaluateCompute()

        val data = modelData.flavorData[flavorName]
        Truth.assertThat(data).isNotNull()

        var sourceSet = data?.getSourceSet(VariantType.DEFAULT)
        Truth.assertThat(sourceSet).isNotNull()
        Truth.assertThat(sourceSet?.name).isEqualTo(flavorName)

        sourceSet = data?.getSourceSet(VariantType.ANDROID_TEST)
        Truth.assertThat(sourceSet).isNotNull()
        Truth.assertThat(sourceSet?.name).isEqualTo("androidTestFree")

        sourceSet = data?.getSourceSet(VariantType.UNIT_TEST)
        Truth.assertThat(sourceSet).isNotNull()
        Truth.assertThat(sourceSet?.name).isEqualTo("testFree")

        // always assert we haven't hit any deprecation
        Truth.assertThat(deprecationReporter.deprecationWarnings).isEmpty()
    }

    @Test
    fun `build type data check when build type added to container`() {
        val modelData = createModelData(
                createFactories(BaseExtension2::class.java, VariantType.DEFAULT))

        val flavorName = "staging"
        modelData.buildTypes.create(flavorName)

        modelData.afterEvaluateCompute()

        val data = modelData.buildTypeData[flavorName]
        Truth.assertThat(data).isNotNull()

        var sourceSet = data?.getSourceSet(VariantType.DEFAULT)
        Truth.assertThat(sourceSet).isNotNull()
        Truth.assertThat(sourceSet?.name).isEqualTo(flavorName)

        sourceSet = data?.getSourceSet(VariantType.ANDROID_TEST)
        Truth.assertThat(sourceSet).isNotNull()
        Truth.assertThat(sourceSet?.name).isEqualTo("androidTestStaging")

        sourceSet = data?.getSourceSet(VariantType.UNIT_TEST)
        Truth.assertThat(sourceSet).isNotNull()
        Truth.assertThat(sourceSet?.name).isEqualTo("testStaging")

        // always assert we haven't hit any deprecation
        Truth.assertThat(deprecationReporter.deprecationWarnings).isEmpty()
    }

    @Test
    fun `name collisions between flavor, build type and default values`() {
        val modelData = createModelData(
                createFactories(BaseExtension2::class.java, VariantType.DEFAULT))

        // first validate collision against forbidden names
        val flavors = modelData.productFlavors
        validateCollision(
                flavors, "main","ProductFlavor names cannot be 'main'")
        validateCollision(
                flavors, "lint","ProductFlavor names cannot be 'lint'")
        validateCollision(
                flavors,
                VariantType.ANDROID_TEST.prefix,
                "ProductFlavor names cannot start with 'androidTest'")
        validateCollision(
                flavors,
                VariantType.ANDROID_TEST.prefix + "Foo",
                "ProductFlavor names cannot start with 'androidTest'")
        validateCollision(
                flavors,
                VariantType.UNIT_TEST.prefix,
                "ProductFlavor names cannot start with 'test'")
        validateCollision(
                flavors,
                VariantType.UNIT_TEST.prefix + "Foo",
                "ProductFlavor names cannot start with 'test'")

        // same with build types
        val buildTypes = modelData.buildTypes
        validateCollision(
                buildTypes, "main","BuildType names cannot be 'main'")
        validateCollision(
                buildTypes, "lint","BuildType names cannot be 'lint'")
        validateCollision(
                buildTypes,
                VariantType.ANDROID_TEST.prefix,
                "BuildType names cannot start with 'androidTest'")
        validateCollision(
                buildTypes,
                VariantType.ANDROID_TEST.prefix + "Foo",
                "BuildType names cannot start with 'androidTest'")
        validateCollision(
                buildTypes,
                VariantType.UNIT_TEST.prefix,
                "BuildType names cannot start with 'test'")
        validateCollision(
                buildTypes,
                VariantType.UNIT_TEST.prefix + "Foo",
                "BuildType names cannot start with 'test'")

        // create a flavor and then a build type of the same name
        flavors.create("foo")
        validateCollision(
                buildTypes,
                "foo",
                "BuildType names cannot collide with ProductFlavor names: foo")

        // and the other way around
        buildTypes.create("bar")
        validateCollision(
                flavors,
                "bar",
                "ProductFlavor names cannot collide with BuildType names: bar")
    }


    // ----

    private fun <T> validateCollision(
            container: NamedDomainObjectContainer<T>,
            itemName: String,
            expectedMessage: String) {
        container.create(itemName)
        Truth.assertThat(issueReporter.messages).containsExactly(expectedMessage)
        issueReporter.messages.clear()
    }

    private fun <E: BaseExtension2> createModelData(
            factories: List<VariantFactory2<E>>): DslModelDataImpl<E> {

        val baseFlavor = BaseFlavorImpl(deprecationReporter, issueReporter)
        val defaultConfig = DefaultConfigImpl(
                VariantPropertiesImpl(issueReporter),
                BuildTypeOrProductFlavorImpl(
                        deprecationReporter, issueReporter) { baseFlavor.postprocessing },
                ProductFlavorOrVariantImpl(issueReporter),
                FallbackStrategyImpl(deprecationReporter, issueReporter),
                baseFlavor,
                issueReporter)

        return DslModelDataImpl(
                defaultConfig,
                factories,
                FakeConfigurationContainer(),
                FakeFilesProvider(),
                FakeContainerFactory(),
                FakeInstantiator(),
                deprecationReporter,
                issueReporter,
                FakeLogger())
    }

    private fun <T: BaseExtension2> createFactories(
            itemClass: Class<T>,
            mainVariantType: VariantType,
            hasAndroidTests: Boolean = true,
            hasUnitTests: Boolean = true
    ): List<VariantFactory2<T>> {
        val list = mutableListOf<VariantFactory2<T>>()

        val testList = mutableListOf<VariantType>()
        if (hasAndroidTests) {
            testList.add(VariantType.ANDROID_TEST)
        }
        if (hasUnitTests) {
            testList.add(VariantType.UNIT_TEST)
        }

        list.add(FakeVariantFactory2<BaseExtension2>(mainVariantType, testList, null))

        if (hasAndroidTests) {
            list.add(
                    FakeVariantFactory2<BaseExtension2>(
                            VariantType.ANDROID_TEST, listOf(), mainVariantType))
        }

        if (hasUnitTests) {
            list.add(
                    FakeVariantFactory2<BaseExtension2>(
                            VariantType.UNIT_TEST, listOf(), mainVariantType))
        }

        return list
    }
}

