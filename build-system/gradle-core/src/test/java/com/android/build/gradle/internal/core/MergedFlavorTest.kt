/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeProviderFactory
import com.android.build.gradle.internal.fixtures.FakeSyncIssueReporter
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.createDslServices
import com.android.build.gradle.internal.services.createProjectServices
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.core.AbstractProductFlavor
import com.android.builder.core.DefaultApiVersion
import com.android.builder.internal.ClassFieldImpl
import com.android.testutils.internal.CopyOfTester
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Test

@Suppress("DEPRECATION")
class MergedFlavorTest {

    private fun productFlavor(name: String): ProductFlavor =
        dslServices.newDecoratedInstance(ProductFlavor::class.java, name, dslServices)

    private val defaultFlavor: AbstractProductFlavor by lazy { productFlavor("default") }
    private val custom: AbstractProductFlavor by lazy {
        productFlavor("custom").also { custom ->
            custom.minSdkVersion = DefaultApiVersion(42)
            custom.targetSdkVersion = DefaultApiVersion(43)
            custom.renderscriptTargetApi = 17
            custom.versionCode = 44
            custom.versionName = "42.0"
            custom.applicationId = "com.forty.two"
            custom.testApplicationId = "com.forty.two.test"
            custom.testInstrumentationRunner = "com.forty.two.test.Runner"
            custom.setTestHandleProfiling(true)
            custom.setTestFunctionalTest(true)
            custom.addResourceConfiguration("hdpi")
            custom.addManifestPlaceholders(
                ImmutableMap.of<String, Any>("one", "oneValue", "two", "twoValue")
            )
            custom.addResValue("foo/one", ClassFieldImpl("foo", "one", "oneValue"))
            custom.addResValue("foo/two", ClassFieldImpl("foo", "two", "twoValue"))
            custom.addBuildConfigField(ClassFieldImpl("foo", "one", "oneValue"))
            custom.addBuildConfigField(ClassFieldImpl("foo", "two", "twoValue"))
            custom.versionNameSuffix = "custom"
            custom.applicationIdSuffix = "custom"
        }
    }
    private val custom2: AbstractProductFlavor by lazy {
        productFlavor("custom2").also { custom2 ->
            custom2.addResourceConfigurations("ldpi", "hdpi")
            custom2.addManifestPlaceholders(
                ImmutableMap.of<String, Any>("two", "twoValueBis", "three", "threeValue"))
            custom2.addResValue("foo/two", ClassFieldImpl("foo", "two", "twoValueBis"))
            custom2.addResValue("foo/three", ClassFieldImpl("foo", "three", "threeValue"))
            custom2.addBuildConfigField(ClassFieldImpl("foo", "two", "twoValueBis"))
            custom2.addBuildConfigField(ClassFieldImpl("foo", "three", "threeValue"))
            custom2.applicationIdSuffix = "custom2"
            custom2.versionNameSuffix = "custom2"
            custom2.applicationId = "com.custom2.app"
        }
    }
    private lateinit var dslServices: DslServices


    private fun initDslServices(enableLegacyApi: Boolean, throwOnError: Boolean = !enableLegacyApi) {
        val properties =
            ImmutableMap.builder<String, Any>()
        properties.put(
            BooleanOption.ENABLE_LEGACY_API.propertyName,
            enableLegacyApi
        )

        dslServices = createDslServices(
            createProjectServices(
                projectOptions = ProjectOptions(
                    ImmutableMap.of(),
                    FakeProviderFactory(FakeProviderFactory.factory, properties.build())
                ),
                issueReporter = FakeSyncIssueReporter(throwOnError = throwOnError)
            )
        )
    }

    @Test
    fun testClone() {
        initDslServices(true)
        val flavor = MergedFlavor.clone(custom, FakeGradleProperty("com.forty.two"), dslServices)
        assertThat(flavor.toString().substringAfter("{"))
                .isEqualTo(custom.toString().substringAfter("{"))
        CopyOfTester.assertAllGettersCalled(
            MergedFlavor::class.java,
            flavor,
            listOf("getApplicationId")
        ) { MergedFlavor.clone(it, FakeGradleProperty("com.forty.two"), dslServices) }
    }

    @Test
    fun testMergeOnDefault() {
        initDslServices(true)
        val flavor =
                MergedFlavor.mergeFlavors(defaultFlavor, ImmutableList.of(custom), FakeGradleProperty("com.forty.two"), dslServices)

        assertThat(flavor.minSdkVersion?.apiLevel).isEqualTo(42)
        assertThat(flavor.targetSdkVersion?.apiLevel).isEqualTo(43)
        assertThat(flavor.renderscriptTargetApi).isEqualTo(17)
        assertThat(flavor.versionCode).isEqualTo(44)
        assertThat(flavor.versionName).isEqualTo("42.0")
        assertThat(flavor.applicationId).isEqualTo("com.forty.two")
        assertThat(flavor.testApplicationId).isEqualTo("com.forty.two.test")
        assertThat(flavor.testInstrumentationRunner).isEqualTo("com.forty.two.test.Runner")
        assertThat(flavor.testHandleProfiling).isTrue()
        assertThat(flavor.testFunctionalTest).isTrue()
    }

    @Test
    fun testMergeOnCustom() {
        initDslServices(true)

        val flavor =
                MergedFlavor.mergeFlavors(custom, ImmutableList.of(defaultFlavor),FakeGradleProperty("com.forty.two"), dslServices)

        assertThat(flavor.minSdkVersion?.apiLevel).isEqualTo(42)
        assertThat(flavor.targetSdkVersion?.apiLevel).isEqualTo(43)
        assertThat(flavor.renderscriptTargetApi).isEqualTo(17)
        assertThat(flavor.versionCode).isEqualTo(44)
        assertThat(flavor.versionName).isEqualTo("42.0")
        assertThat(flavor.applicationId).isEqualTo("com.forty.two")
        assertThat(flavor.testApplicationId).isEqualTo("com.forty.two.test")
        assertThat(flavor.testInstrumentationRunner).isEqualTo("com.forty.two.test.Runner")
        assertThat(flavor.testHandleProfiling).isTrue()
        assertThat(flavor.testFunctionalTest).isTrue()
    }

    @Test
    fun testMergeDefaultOnDefault() {
        initDslServices(enableLegacyApi = false, throwOnError = false)
        val defaultFlavor2 = productFlavor("default2")

        val flavor =
                MergedFlavor
                        .mergeFlavors(
                                defaultFlavor2, ImmutableList.of(defaultFlavor), FakeGradleProperty("com.forty.two"), dslServices)

        assertThat(flavor.minSdkVersion).isNull()
        assertThat(flavor.targetSdkVersion).isNull()
        assertThat(flavor.renderscriptTargetApi).isNull()
        assertThat(flavor.versionCode).isNull()
        assertThat(flavor.versionName).isNull()
        assertThat(flavor.applicationId).isNull()
        assertThat(flavor.testApplicationId).isNull()
        assertThat(flavor.testInstrumentationRunner).isNull()
        assertThat(flavor.testHandleProfiling).isNull()
        assertThat(flavor.testFunctionalTest).isNull()
    }

    @Test
    fun testResourceConfigMerge() {
        initDslServices(true)
        val flavor = MergedFlavor.mergeFlavors(custom2, ImmutableList.of(custom), FakeGradleProperty("com.forty.two"), dslServices)

        val configs = flavor.resourceConfigurations
        assertThat(configs).containsExactly("hdpi", "ldpi")
    }

    @Test
    fun testManifestPlaceholdersMerge() {
        initDslServices(true)
        val flavor = MergedFlavor.mergeFlavors(custom2, ImmutableList.of(custom), FakeGradleProperty("com.forty.two"), dslServices)

        val manifestPlaceholders = flavor.manifestPlaceholders
        assertThat(manifestPlaceholders)
                .containsExactly("one", "oneValue", "two", "twoValue", "three", "threeValue")
    }

    @Test
    fun testResValuesMerge() {
        initDslServices(true)
        val flavor = MergedFlavor.mergeFlavors(custom2, ImmutableList.of(custom), FakeGradleProperty("com.forty.two"), dslServices)

        val resValues = flavor.resValues
        assertThat(resValues).hasSize(3)
        assertThat(resValues["foo/one"]?.value).isEqualTo("oneValue")
        assertThat(resValues["foo/two"]?.value).isEqualTo("twoValue")
        assertThat(resValues["foo/three"]?.value).isEqualTo("threeValue")
    }

    @Test
    fun testBuildConfigFieldMerge() {
        initDslServices(true)

        val flavor = MergedFlavor.mergeFlavors(custom2, ImmutableList.of(custom), FakeGradleProperty("com.forty.two"), dslServices)

        val buildConfigFields = flavor.buildConfigFields
        assertThat(buildConfigFields).hasSize(3)
        assertThat(buildConfigFields["one"]?.value).isEqualTo("oneValue")
        assertThat(buildConfigFields["two"]?.value).isEqualTo("twoValue")
        assertThat(buildConfigFields["three"]?.value).isEqualTo("threeValue")
    }

    @Test
    fun testMergeMultiple() {
        initDslServices(true)

        val custom3 = productFlavor("custom3")
        custom3.minSdkVersion = DefaultApiVersion(102)
        custom3.applicationIdSuffix = "custom3"
        custom3.versionNameSuffix = "custom3"

        val flavor =
                MergedFlavor.mergeFlavors(custom, ImmutableList.of(custom3, custom2), FakeGradleProperty("com.forty.two"), dslServices)

        assertThat(flavor.minSdkVersion).isEqualTo(
            DefaultApiVersion(
                102
            )
        )
        assertThat(flavor.versionNameSuffix).isEqualTo("customcustom3custom2")
        assertThat(flavor.applicationIdSuffix).isEqualTo("custom.custom3.custom2")
    }

    @Test
    fun testSecondDimensionOverwritesDefault() {
        initDslServices(true)

        val custom3 = productFlavor("custom3")
        custom3.minSdkVersion = DefaultApiVersion(102)

        val flavor =
                MergedFlavor.mergeFlavors(custom, ImmutableList.of(custom3, custom2), FakeGradleProperty("com.forty.two"), dslServices)
        assertThat(flavor.minSdkVersion).isEqualTo(
            DefaultApiVersion(
                102
            )
        )
        assertThat(flavor.versionNameSuffix).isEqualTo("customcustom2")
        assertThat(flavor.applicationIdSuffix).isEqualTo("custom.custom2")
    }

    @Test
    fun testSetVersionCodeError() {
        initDslServices(false)
        val flavor = MergedFlavor.clone(defaultFlavor, FakeGradleProperty("com.forty.two"), dslServices)
        try {
            flavor.versionCode = 123
            fail("Setting versionCode should result in RuntimeException from issueReporter")
        } catch (e : RuntimeException) {
            assertThat(e.message).isEqualTo(
                """versionCode cannot be set on a mergedFlavor directly.
                |versionCodeOverride can instead be set for variant outputs using the following syntax:
                |android {
                |    applicationVariants.all { variant ->
                |        variant.outputs.each { output ->
                |            output.versionCodeOverride = 123
                |        }
                |    }
                |}
            """.trimMargin())
        }
    }

    @Test
    fun testSetVersionNameError() {
        initDslServices(false)
        val flavor = MergedFlavor.clone(defaultFlavor, FakeGradleProperty("com.forty.two"), dslServices)
        try {
            flavor.versionName = "foo"
            fail("Setting versionName should result in RuntimeException from issueReporter")
        } catch (e : RuntimeException) {
            assertThat(e.message).isEqualTo(
                """versionName cannot be set on a mergedFlavor directly.
                |versionNameOverride can instead be set for variant outputs using the following syntax:
                |android {
                |    applicationVariants.all { variant ->
                |        variant.outputs.each { output ->
                |            output.versionNameOverride = "foo"
                |        }
                |    }
                |}
            """.trimMargin())
        }
    }

}
