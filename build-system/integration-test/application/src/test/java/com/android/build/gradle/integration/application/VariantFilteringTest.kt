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
import com.android.testutils.AbstractGivenExpectReturnTest
import com.android.testutils.AbstractGivenExpectTest
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

/** Tests to validate the different filtering mechanisms  */
class VariantFilteringTest: AbstractGivenExpectReturnTest<String, List<String>>() {
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
            listOf(
                "x86GingerbreadDebug",
                "x86GingerbreadRelease",
                "mipsGingerbreadDebug",
                "mipsGingerbreadRelease",
                "armGingerbreadDebug",
                "armGingerbreadRelease",
                "armCupcakeDebug",
                "armCupcakeRelease"
            )
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
            listOf("release")
        }
    }

    @Test
    fun `filtering via new api using generic callback on build type names`() {
        given {
            """
                |    onVariants {
                |        if (buildType.equals("debug")) {
                |            enabled = false
                |        }
                |    }
            """
        }

        expect {
            listOf("release")
        }
    }

    @Test
    fun `filtering via new api using buildtype callback`() {
        given {
            """
                |    onVariants.withBuildType("debug") {
                |        it.enabled = false
                |    }
            """
        }

        expect {
            listOf("release")
        }
    }

    @Test
    fun `filtering via new api using flavor callback`() {
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
                |    onVariants.withFlavor(new kotlin.Pair("one", "flavor1")) {
                |        enabled = false
                |    }
            """
        }

        expect {
            listOf("flavor2Debug", "flavor2Release")
        }
    }

    @Test
    fun `filtering via new api using multiple flavor callbacks`() {
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
                |    onVariants
                |            .withFlavor(new kotlin.Pair("one", "flavor1"))
                |            .withFlavor(new kotlin.Pair("two", "flavorA")) {
                |        enabled = false
                |    }
            """
        }

        expect {
            listOf(
                "flavor1FlavorBDebug",
                "flavor1FlavorBRelease",
                "flavor2FlavorADebug",
                "flavor2FlavorARelease",
                "flavor2FlavorBDebug",
                "flavor2FlavorBRelease"
            )
        }
    }

    @Test
    fun `filtering via new api using flavor callback then build type callback`() {
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
                |    onVariants
                |            .withFlavor(new kotlin.Pair("one", "flavor1"))
                |            .withBuildType("debug") {
                |        enabled = false
                |    }
            """
        }

        expect {
            listOf("flavor1Release", "flavor2Debug", "flavor2Release")
        }
    }

    @Test
    fun `filtering via new api using build-type callback then flavor callback`() {
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
                |    onVariants
                |            .withBuildType("debug")
                |            .withFlavor(new kotlin.Pair("one", "flavor1")) {
                |        enabled = false
                |    }
            """
        }

        expect {
            listOf("flavor1Release", "flavor2Debug", "flavor2Release")
        }
    }

    @Test
    fun `filtering via new api using multiple flavor callback then build-type callback`() {
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
                |    onVariants
                |            .withFlavor(new kotlin.Pair("one", "flavor1"))
                |            .withFlavor(new kotlin.Pair("two", "flavorA"))
                |            .withBuildType("debug") {
                |        enabled = false
                |    }
            """
        }

        expect {
            listOf(
                "flavor1FlavorARelease",
                "flavor1FlavorBDebug",
                "flavor1FlavorBRelease",
                "flavor2FlavorADebug",
                "flavor2FlavorARelease",
                "flavor2FlavorBDebug",
                "flavor2FlavorBRelease"
            )
        }
    }

    @Test
    fun `filtering via new api using build-type callback then multiple flavor callback`() {
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
                |    onVariants
                |            .withBuildType("debug")
                |            .withFlavor(new kotlin.Pair("one", "flavor1"))
                |            .withFlavor(new kotlin.Pair("two", "flavorA")) {
                |        enabled = false
                |    }
            """
        }

        expect {
            listOf(
                "flavor1FlavorARelease",
                "flavor1FlavorBDebug",
                "flavor1FlavorBRelease",
                "flavor2FlavorADebug",
                "flavor2FlavorARelease",
                "flavor2FlavorBDebug",
                "flavor2FlavorBRelease"
            )
        }
    }

    @Test
    fun `filtering via new api using name callback`() {
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
                |    onVariants.withName("flavor1Debug") {
                |        enabled = false
                |    }
            """
        }

        expect {
            listOf("flavor1Release","flavor2Debug", "flavor2Release")
        }
    }

    // ---------------------------------------------------------------------------------------------


    override fun defaultWhen(given: String): List<String>? {
        project.buildFile.appendText(
            """
                |android {
                |${given.trimMargin()}
                |}
            """.trimMargin()
        )

        return project.model().fetchAndroidProjects().onlyModel.variants.map { it.name }
    }

    override fun compareResult(expected: List<String>?, actual: List<String>?, given: String) {
        Truth.assertThat(actual).containsExactlyElementsIn(expected)
    }
}