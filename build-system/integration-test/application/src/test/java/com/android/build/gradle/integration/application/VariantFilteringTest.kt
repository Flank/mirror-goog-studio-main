/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.builder.model.AndroidProject
import com.android.testutils.AbstractReturnGivenBuildResultTest
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Tests to validate the different filtering mechanisms  */
@RunWith(Parameterized::class)
class VariantFilteringTest(private val useModelV2: Boolean)
    : AbstractReturnGivenBuildResultTest<String,
        VariantFilteringTest.VariantBuilder,
        List<VariantFilteringTest.VariantInfo>>() {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "useModelV2_{0}")
        fun useModelV2() = arrayOf(true, false)
    }

    @get:Rule
    val project =
        GradleTestProject.builder().fromTestProject("emptyApp").create()

    @Test
    fun `filtering via old api on abi and flavor names`() {
        given {
            """
                |    flavorDimensions "abi", "api"
                |    productFlavors {
                |        x86 {
                |            dimension "abi"
                |        }
                |        mips {
                |            dimension "abi"
                |        }
                |        arm {
                |            dimension "abi"
                |        }
                |        cupcake {
                |            dimension "api"
                |        }
                |        gingerbread {
                |            dimension "api"
                |        }
                |    }
                |    variantFilter {
                |        String abi = it.flavors.get(0).name
                |        if ("cupcake".equals(it.flavors.get(1).name) && ("x86".equals(abi) || "mips".equals(abi))) {
                |            it.ignore = true
                |        }
                |    }
            """
        }

        expect {
            variant { name = "x86GingerbreadDebug" }
            variant {
                name = "x86GingerbreadRelease"
                androidTest = false
            }
            variant { name = "mipsGingerbreadDebug" }
            variant {
                name = "mipsGingerbreadRelease"
                androidTest = false
            }
            variant { name = "armGingerbreadDebug" }
            variant {
                name = "armGingerbreadRelease"
                androidTest = false
            }

            variant { name = "armCupcakeDebug" }
            variant {
                name = "armCupcakeRelease"
                androidTest = false
            }
        }
    }

    @Test
    fun `filtering via old api on build type names`() {
        given {
            """
                |    variantFilter {
                |        if (it.buildType.name.equals("debug")) {
                |            it.ignore = true
                |        }
                |    }
            """
        }

        expect {
            variant { name = "release"
                androidTest = false
            }
        }
    }

    @Test
    fun `filtering using generic callback on build type names`() {
        withAndroidComponents {
            """
                |    beforeVariants(selector().all(), {
                |        if (buildType.equals("debug")) {
                |            enabled = false
                |        }
                |    })
            """
        }

        expect {
            variant {
                name = "release"
                androidTest = false
            }
        }
    }

    @Test
    fun `filtering using buildtype callback`() {
        given {""}
        withAndroidComponents {
            """
                |    beforeVariants(selector().withBuildType("debug"), {
                |        enabled = false
                |    })
            """
        }

        expect {
            variant {
                name = "release"
                androidTest = false
            }
        }
    }

    @Test
    fun `filtering using flavor callback`() {
        given {
            """
                |    flavorDimensions "one"
                |    productFlavors {
                |        flavor1 {
                |            dimension "one"
                |        }
                |        flavor2 {
                |            dimension "one"
                |        }
                |    }
            """
        }

        withAndroidComponents {
            """
                |    beforeVariants(selector().withFlavor(new kotlin.Pair("one", "flavor1")), {
                |        enabled = false
                |    })
            """
        }

        expect {
            variant { name = "flavor2Debug" }
            variant {
                name = "flavor2Release"
                androidTest = false
            }
        }
    }

    @Test
    fun `filtering using multiple flavor callbacks`() {
        given {
            """
                |    flavorDimensions "one", "two"
                |    productFlavors {
                |        flavor1 {
                |            dimension "one"
                |        }
                |        flavor2 {
                |            dimension "one"
                |        }
                |        flavorA{
                |            dimension "two"
                |        }
                |        flavorB {
                |            dimension "two"
                |        }
                |    }
            """
        }

        withAndroidComponents {
            """
                |    beforeVariants(selector()
                |          .withFlavor(new kotlin.Pair("one", "flavor1"))
                |          .withFlavor(new kotlin.Pair("two", "flavorA")), {
                |        enabled = false
                |    })
            """
        }


        expect {
            variant { name = "flavor1FlavorBDebug" }
            variant {
                name = "flavor1FlavorBRelease"
                androidTest = false
            }
            variant { name = "flavor2FlavorADebug" }
            variant {
                name = "flavor2FlavorARelease"
                androidTest = false
            }
            variant { name = "flavor2FlavorBDebug" }
            variant {
                name = "flavor2FlavorBRelease"
                androidTest = false
            }
        }
    }

    @Test
    fun `filtering using flavor callback then build type callback`() {
        given {
            """
                |    flavorDimensions "one"
                |    productFlavors {
                |        flavor1 {
                |            dimension "one"
                |        }
                |        flavor2 {
                |            dimension "one"
                |        }
                |    }
            """
        }

        withAndroidComponents {
            """
                |    beforeVariants(selector()
                |          .withFlavor(new kotlin.Pair("one", "flavor1"))
                |          .withBuildType("debug"), {
                |        enabled = false
                |    })
            """
        }

        expect {
            variant {
                name = "flavor1Release"
                androidTest = false
            }
            variant { name = "flavor2Debug" }
            variant {
                name = "flavor2Release"
                androidTest = false
            }
        }
    }

    @Test
    fun `filtering using build-type callback then flavor callback`() {
        given {
            """
                |    flavorDimensions "one"
                |    productFlavors {
                |        flavor1 {
                |            dimension "one"
                |        }
                |        flavor2 {
                |            dimension "one"
                |        }
                |    }
            """
        }

        withAndroidComponents {
            """
                |    beforeVariants(selector()
                |          .withBuildType("debug")
                |          .withFlavor(new kotlin.Pair("one", "flavor1")), {
                |        enabled = false
                |    })
            """
        }

        expect {
            variant {
                name = "flavor1Release"
                androidTest = false
            }
            variant { name = "flavor2Debug" }
            variant {
                name = "flavor2Release"
                androidTest = false
            }
        }
    }

    @Test
    fun `filtering using multiple flavor callback then build-type callback`() {
        given {
            """
                |    flavorDimensions "one", "two"
                |    productFlavors {
                |        flavor1 {
                |            dimension "one"
                |        }
                |        flavor2 {
                |            dimension "one"
                |        }
                |        flavorA{
                |            dimension "two"
                |        }
                |        flavorB {
                |            dimension "two"
                |        }
                |    }
            """
        }

        withAndroidComponents {
            """
                |    beforeVariants(selector()
                |          .withFlavor(new kotlin.Pair("one", "flavor1"))
                |          .withFlavor(new kotlin.Pair("two", "flavorA"))
                |          .withBuildType("debug"), {
                |        enabled = false
                |    })
            """
        }

        expect {
            variant {
                name = "flavor1FlavorARelease"
                androidTest = false
            }
            variant { name = "flavor1FlavorBDebug" }
            variant {
                name = "flavor1FlavorBRelease"
                androidTest = false
            }
            variant { name = "flavor2FlavorADebug" }
            variant {
                name = "flavor2FlavorARelease"
                androidTest = false
            }
            variant { name = "flavor2FlavorBDebug" }
            variant {
                name = "flavor2FlavorBRelease"
                androidTest = false
            }
        }
    }

    @Test
    fun `filtering using build-type callback then multiple flavor callback`() {
        given {
            """
                |    flavorDimensions "one", "two"
                |    productFlavors {
                |        flavor1 {
                |            dimension "one"
                |        }
                |        flavor2 {
                |            dimension "one"
                |        }
                |        flavorA{
                |            dimension "two"
                |        }
                |        flavorB {
                |            dimension "two"
                |        }
                |    }
            """
        }

        withAndroidComponents {
            """
                |    beforeVariants(selector()
                |          .withBuildType("debug")
                |          .withFlavor(new kotlin.Pair("one", "flavor1"))
                |          .withFlavor(new kotlin.Pair("two", "flavorA")), {
                |        enabled = false
                |    })
            """
        }

        expect {
            variant {
                name = "flavor1FlavorARelease"
                androidTest = false
            }
            variant { name = "flavor1FlavorBDebug" }
            variant {
                name = "flavor1FlavorBRelease"
                androidTest = false
            }

            variant { name = "flavor2FlavorADebug" }
            variant {
                name = "flavor2FlavorARelease"
                androidTest = false
            }
            variant { name = "flavor2FlavorBDebug" }
            variant {
                name = "flavor2FlavorBRelease"
                androidTest = false
            }
        }
    }

    @Test
    fun `filtering using name callback`() {
        given {
            """
                |    flavorDimensions "one"
                |    productFlavors {
                |        flavor1 {
                |            dimension "one"
                |        }
                |        flavor2 {
                |            dimension "one"
                |        }
                |    }
            """
        }

        withAndroidComponents {
            """
                |    beforeVariants(selector()
                |          .withName("flavor1Debug"), {
                |        enabled = false
                |    })
            """
        }

        expect {
            variant {
                name = "flavor1Release"
                androidTest = false
            }
            variant { name = "flavor2Debug" }
            variant {
                name = "flavor2Release"
                androidTest = false
            }
        }
    }

    @Test
    fun `unit-test filtering using buildtype callback`() {
        withAndroidComponents {
            """
                |    beforeVariants(selector()
                |          .withBuildType("debug"), {
                |        unitTestEnabled = false
                |    })
            """
        }

        expect {
            variant {
                name = "release"
                androidTest = false
            }
            variant {
                name = "debug"
                unitTest = false
            }
        }
    }

    @Test
    fun `android-test filtering using buildtype callback`() {
        withAndroidComponents {
            """
                |    beforeVariants(selector()
                |          .withBuildType("debug"), {
                |        androidTestEnabled = false
                |    })
            """
        }

        expect {
            variant {
                name = "release"
                androidTest = false
            }
            variant {
                name = "debug"
                androidTest = false
            }
        }
    }

    @Test
    fun `test-fixtures filtering using buildtype callback`() {
        // TestFixtures feature is not supported in model v1
        if (!useModelV2) {
            return
        }
        given {
            """
                |    testFixtures {
                |        it.enable true
                |    }
            """
        }

        withAndroidComponents {
            """
                |    beforeVariants(selector()
                |          .withBuildType("debug"), {
                |        enableTestFixtures = false
                |    })
            """
        }

        expect {
            variant {
                name = "release"
                testFixtures = true
                androidTest = false
            }
            variant {
                name = "debug"
                testFixtures = false
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    var androidComponentsBlock: (() -> String)? = null
    fun withAndroidComponents(action: () -> String) {
        androidComponentsBlock = action
        state = TestState.GIVEN
    }

    override fun noGivenData(): String {
        // it's ok to not have any given data, if there is some androidComponents customization.
        if (androidComponentsBlock!=null)
            return ""
        else
            throw RuntimeException("No given data")
    }

    override fun defaultWhen(given: String): List<VariantInfo>? {
        project.buildFile.appendText(
            """
                |android {
                |${given.trimMargin()}
                |}
            """.trimMargin())
        this.androidComponentsBlock?.let {
            project.buildFile.appendText(
            """
                |
                |androidComponents {
                |${it().trimMargin()}
                |}
            """.trimMargin())
        }


        if (useModelV2) {
            return project.modelV2().fetchModels().container.singleAndroidProject.variants.map {
                VariantInfo(
                    it.name,
                    unitTest = it.unitTestArtifact != null,
                    androidTest = it.androidTestArtifact != null,
                    testFixtures = it.testFixturesArtifact != null,
                )
            }
        } else {
            return project.model().fetchAndroidProjects().onlyModel.variants.map {
                VariantInfo(
                    it.name,
                    unitTest = it.extraJavaArtifacts.any { it.name == AndroidProject.ARTIFACT_UNIT_TEST },
                    androidTest = it.extraAndroidArtifacts.any { it.name == AndroidProject.ARTIFACT_ANDROID_TEST })
            }
        }
    }

    override fun compareResult(expected: List<VariantInfo>?, actual: List<VariantInfo>?, given: String) {
        Truth.assertThat(actual).containsExactlyElementsIn(expected)
    }

    override fun instantiateResulBuilder(): VariantBuilder = VariantBuilder()

    class VariantBuilder: ResultBuilder<List<VariantInfo>> {
        private val variants = mutableListOf<VariantInfo>()

        fun variant(action: VariantInfo.() -> Unit) {
            variants.add(VariantInfo().also { action(it) })
        }

        override fun toResult(): List<VariantInfo> {
            return variants
        }
    }

    data class VariantInfo(
        var name: String = "",
        var unitTest: Boolean = true,
        var androidTest: Boolean = true,
        var testFixtures: Boolean = false,
    )
}
