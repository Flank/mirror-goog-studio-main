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

package com.android.build.gradle.internal.tests

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.BasePlugin
import com.android.build.gradle.runAfterEvaluate
import com.android.build.gradle.internal.fixture.TestConstants
import com.android.build.gradle.internal.fixture.TestProjects
import com.android.build.gradle.internal.fixture.createAndConfig
import com.android.build.gradle.internal.multimapOf
import com.android.build.gradle.internal.multimapWithSingleKeyOf
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ListMultimap
import com.google.common.truth.Truth
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests that specific configurations properly extendFrom others.
 */
@RunWith(Parameterized::class)
class ConfigurationExtensionTest(val pluginType: TestProjects.Plugin) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "plugin_{0}")
        fun params() : Collection<TestProjects.Plugin> = listOf(
                TestProjects.Plugin.APP, TestProjects.Plugin.LIBRARY)
    }

    @get:Rule val projectDirectory = TemporaryFolder()

    private lateinit var project: Project
    private lateinit var plugin: BasePlugin
    private lateinit var android: BaseExtension
    private lateinit var configExtensionMap: ListMultimap<String, String>

    // basic relationship
    private val appBasics = multimapOf(
            "implementation" to "api",
            "api" to "compile",
            "runtimeOnly" to "apk",
            "compileOnly" to "provided")

    private val libBasics = multimapOf(
            "implementation" to "api",
            "api" to "compile",
            "runtimeOnly" to "publish",
            "compileOnly" to "provided")

    // variant to basic relationship
    private val compileToRaw = multimapWithSingleKeyOf(
            "lollipopDemoDebugCompileClasspath",
            "api",
            "compile",
            "implementation",
            "compileOnly",
            "lollipopImplementation",
            "debugImplementation",
            "demoImplementation",
            "lollipopDemoImplementation",
            "lollipopDemoDebugImplementation")

    private val runtimeToRaw = multimapWithSingleKeyOf(
            "lollipopDemoDebugRuntimeClasspath",
            "api",
            "compile",
            "implementation",
            "runtimeOnly",
            "lollipopImplementation",
            "debugImplementation",
            "demoImplementation",
            "lollipopDemoImplementation",
            "lollipopDemoDebugImplementation")

    private val testCompileToRaw = multimapWithSingleKeyOf(
            "lollipopDemoDebugUnitTestCompileClasspath",
            "compile",
            "testCompile",
            "testImplementation",
            "implementation",
            "lollipopImplementation",
            "testLollipopImplementation",
            "debugImplementation",
            "testDebugImplementation",
            "demoImplementation",
            "testDemoImplementation",
            "lollipopDemoImplementation",
            "testLollipopDemoImplementation",
            "lollipopDemoDebugImplementation",
            "testLollipopDemoDebugImplementation")
    private val testRuntimeToRaw = multimapWithSingleKeyOf(
            "lollipopDemoDebugUnitTestRuntimeClasspath",
            "compile",
            "testCompile",
            "testImplementation",
            "implementation",
            "lollipopImplementation",
            "testLollipopImplementation",
            "debugImplementation",
            "testDebugImplementation",
            "demoImplementation",
            "testDemoImplementation",
            "lollipopDemoImplementation",
            "testLollipopDemoImplementation",
            "lollipopDemoDebugImplementation",
            "testLollipopDemoDebugImplementation")

    // forbidden relationship
    private val forbiddenVariantToRaw = multimapOf(
            "lollipopDemoDebugCompileClasspath" to "runtimeOnly",
            "lollipopDemoDebugCompileClasspath" to "apk",
            "lollipopDemoDebugCompileClasspath" to "publish",
            "lollipopDemoDebugRuntimeClasspath" to "provided",
            "lollipopDemoDebugRuntimeClasspath" to "compileOnly")

    // test to prod relationship
    private val testToProd = multimapOf(
            // basic raw configs
            "testImplementation" to "implementation",
            "testRuntimeOnly" to "runtimeOnly",
            // flavor and build type configs
            "testDemoImplementation" to "demoImplementation",
            "testDebugImplementation" to "debugImplementation",
            "androidTestDemoImplementation" to "demoImplementation",
            // multi flavors and variant configs
            "testLollipopDemoImplementation" to "lollipopDemoImplementation",
            "androidTestLollipopDemoImplementation" to "lollipopDemoImplementation",
            "testLollipopDemoDebugImplementation" to "lollipopDemoDebugImplementation",
            "androidTestLollipopDemoDebugImplementation" to "lollipopDemoDebugImplementation")

    // forbidden relationship
    private val forbiddenTestToProd = multimapOf(
            "testCompileOnly" to "compileOnly",
            "testRuntimeOnly" to "provided")

    @Before
    fun setUp() {
        project = TestProjects.builder(projectDirectory.newFolder("project").toPath())
                .withPlugin(pluginType)
                .build()
        android = project.extensions?.getByType(pluginType.extensionClass) as BaseExtension
        android.setCompileSdkVersion(TestConstants.COMPILE_SDK_VERSION)
        android.buildToolsVersion = TestConstants.BUILD_TOOL_VERSION
        plugin = project.plugins.getPlugin(pluginType.pluginClass) as BasePlugin

        // manually call the DSL to configure the project.
        android.flavorDimensions("api", "mode")

        android.productFlavors.createAndConfig("demo") {
            setDimension("mode")
        }

        android.productFlavors.createAndConfig("full") {
            setDimension("mode")
        }

        android.productFlavors.createAndConfig("mnc") {
            setDimension("api")
        }

        android.productFlavors.createAndConfig("lollipop") {
            setDimension("api")
        }

        plugin.runAfterEvaluate()

        configExtensionMap = getConfigurationExtensions()

    }

    @Test
    fun testBasicRelationships() {
        checkValidExtensions(
                if (pluginType == TestProjects.Plugin.APP) appBasics else libBasics,
                configExtensionMap)
    }

    @Test
    fun testVariantToRawRelationships() {
        checkValidExtensions(compileToRaw, configExtensionMap)
        checkValidExtensions(runtimeToRaw, configExtensionMap)
    }

    @Test
    fun testTestToProductionRelationships() {
        checkValidExtensions(testToProd, configExtensionMap)
    }

    @Test
    fun testForbiddenRelationships() {
        checkInvalidExtensions(forbiddenVariantToRaw, configExtensionMap)
        checkInvalidExtensions(forbiddenTestToProd, configExtensionMap)
    }

    @Test
    fun testMainTestRelationships() {
        checkValidExtensions(testCompileToRaw, configExtensionMap)
        checkValidExtensions(testRuntimeToRaw, configExtensionMap)
    }

    private fun getConfigurationExtensions(): ListMultimap<String, String> {
        val map: ListMultimap<String, String> = ArrayListMultimap.create()
        for (config in project.configurations) {
            fillConfigMap(map, config.name, config.extendsFrom)
        }

        return map
    }

    private fun fillConfigMap(map: ListMultimap<String, String>, name: String, children: Set<Configuration>) {
        for (config in children) {
            map.put(name, config.name)
            fillConfigMap(map, name, config.extendsFrom)
        }
    }

    private fun checkValidExtensions(
            expected: ListMultimap<String, String>,
            actual: ListMultimap<String, String>) {

        for ((key, value) in expected.entries()) {
            Truth.assertThat(actual).containsEntry(key, value)
        }
    }

    private fun checkInvalidExtensions(
            expected: ListMultimap<String, String>,
            actual: ListMultimap<String, String>) {

        for ((key, value) in expected.entries()) {
            Truth.assertThat(actual).doesNotContainEntry(key, value)
        }
    }
}