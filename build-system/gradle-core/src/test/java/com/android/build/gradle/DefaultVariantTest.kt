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

package com.android.build.gradle

import com.android.build.gradle.internal.errors.SyncIssueHandler
import com.android.build.gradle.internal.fixture.TestConstants
import com.android.build.gradle.internal.fixture.TestProjects
import com.android.build.gradle.internal.ide.SyncIssueImpl
import com.android.build.gradle.internal.plugins.AppPlugin
import com.android.build.gradle.internal.plugins.LibraryPlugin
import com.android.builder.errors.EvalIssueException
import com.android.builder.model.SyncIssue
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth.assertThat
import groovy.util.Eval
import org.gradle.api.Project
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertFailsWith

/** Tests for the default variant DSL settings */
class DefaultVariantTest {

    @get:Rule
    val projectDirectory = TemporaryFolder()

    lateinit var project: Project

    @Test
    fun `test defaults to debug`() {
        assertThat(
            computeDefaultVariant(
                """
                buildTypes {
                    a
                    z
                }
                """
            )
        ).isEqualTo("debug")
    }

    @Test
    fun `test removed all variants returns null`() {
        assertThat(
            computeDefaultVariant(
                """
                variantFilter { variant -> variant.ignore = true }
                """
            )
        ).isNull()
    }

    @Test
    fun `test debug removed defaults to first alphabetically`() {
        assertThat(
            computeDefaultVariant(
                """
                buildTypes {
                    a
                    z
                }
                variantFilter { variant ->
                    if (variant.buildType.name == 'debug') {
                        variant.ignore = true
                    }
                }
                """
            )
        ).isEqualTo("a")
    }

    @Test
    fun `test default variant build type explicit`() {
        assertThat(
            computeDefaultVariant(
                """
                buildTypes {
                    dev { isDefault = true }
                }
                """
            )
        ).isEqualTo("dev")
    }

    @Test
    fun `test alphabetical flavor choice`() {
        assertThat(
            computeDefaultVariant(
                """
                flavorDimensions 'dim'
                productFlavors {
                    f1
                    f2
                    f3
                }"""
            )
        ).isEqualTo("f1Debug")
    }

    @Test
    fun `test explicit default flavor with single dimensions`() {
        assertThat(
            computeDefaultVariant(
                """
                flavorDimensions 'dim'
                buildTypes {
                    a
                    z { isDefault = true }
                }
                productFlavors {
                    f1
                    f2 { isDefault = true }
                    f3
                }"""
            )
        ).isEqualTo("f2Z")
    }

    @Test
    fun `test alphabetical default flavor with multiple dimensions`() {
        assertThat(
            computeDefaultVariant(
                """
                flavorDimensions '1', '2'
                productFlavors {
                    f1 { dimension = '1' }
                    f2 { dimension = '1'}
                    f3 { dimension = '2'}
                    f4 { dimension = '2' }
                }"""
            )
        ).isEqualTo("f1F3Debug")
    }

    @Test
    fun `test explicit default flavor with multiple dimensions`() {
        assertThat(
            computeDefaultVariant(
                """
                flavorDimensions '1', '2'
                productFlavors {
                    f1 { dimension = '1' }
                    f2 { dimension = '1'; isDefault = true}
                    f3 { dimension = '2'}
                    f4 { dimension = '2' }
                }"""
            )
        ).isEqualTo("f2F3Debug")
    }


    @Test
    fun `test removal default flavor with multiple dimensions`() {
        assertThat(
            computeDefaultVariant(
                """
                flavorDimensions 'number', 'letter'
                productFlavors {
                    f1 { dimension = 'number' }
                    f2 { dimension = 'number' }
                    fa { dimension = 'letter' }
                    fb { dimension = 'letter' }
                }
                variantFilter { variant ->
                    if (variant.name == 'f1FaDebug') {
                        variant.ignore = true
                    }
                }
                """
            )
        ).isEqualTo("f1FbDebug")
    }

    @Test
    fun `test removal default flavor with multiple dimensions and explicit choice`() {
        assertThat(
            computeDefaultVariant(
                """
                flavorDimensions 'number', 'letter'
                productFlavors {
                    f1 { dimension = 'number' }
                    f2 { dimension = 'number'; isDefault = true }
                    fa { dimension = 'letter' }
                    fb { dimension = 'letter' }
                }
                variantFilter { variant ->
                    if (variant.name == 'f2FaDebug') {
                        variant.ignore = true
                    }
                }
                """
            )
        ).isEqualTo("f2FbDebug")
    }

    @Test
    fun `test matching heuristic with filtering picks variant with most matching`() {
        assertThat(
            computeDefaultVariant(
                """
                flavorDimensions 'number', 'letter', 'something'
                productFlavors {
                    f1 { dimension = 'number' }
                    f2 { dimension = 'number'; isDefault = true }
                    fa { dimension = 'letter' }
                    fb { dimension = 'letter'; isDefault = true  }
                    fx { dimension = 'something' }
                    fy { dimension = 'something'; isDefault = true }
                }
                variantFilter { variant ->
                    variant.ignore =
                            (variant.name == 'f2FbFxDebug') ||
                            (variant.name == 'f2FbFyDebug') ||
                            (variant.name == 'f2FaFyDebug') ||
                            (variant.buildType.name == 'release')
                }
                """
            )
        ).isEqualTo("f1FbFyDebug")
        // Left to right comparison this would be f2FaFxDebug, (as f1 is first)
        // but f1FbFyDebug matches two of the user's settings, so the heuristic should prefer that.
    }

    @Test
    fun `test late change`() {
        assertThat(computeDefaultVariant("")).isEqualTo("debug")

        // Simulate the user trying to modify the setting too late, for example
        // in afterEvaluate or during execution.
        assertThat(assertFailsWith<IllegalStateException> {
            Eval.me(
                "project",
                project,
                """
                project.android {
                    buildTypes {
                        release { isDefault = true }
                    }
                }
                """
            )
        }).hasMessageThat().contains("The value for this property is final")
    }


    @Test
    fun `test ambiguous build type`() {
        val result = computeDefaultVariantWithSyncIssues(
            """
                buildTypes {
                    a { isDefault = true }
                    z { isDefault = true }
                }
                """
        )
        assertThat(result.defaultVariant).isEqualTo("a")
        assertThat(result.syncIssues).hasSize(1)
        assertThat(result.syncIssues.first().type).isEqualTo(SyncIssue.TYPE_AMBIGUOUS_BUILD_TYPE_DEFAULT)
        assertThat(result.syncIssues.first().severity).isEqualTo(SyncIssue.SEVERITY_WARNING)
        assertThat(result.syncIssues.first().message).isEqualTo(
            "Ambiguous default build type: 'a', 'z'.\n" +
                    "Please only set `isDefault = true` " +
                    "for one build type.")
    }


    @Test
    fun `test ambiguous product flavor`() {
        val result = computeDefaultVariantWithSyncIssues(
            """
                flavorDimensions '1'
                productFlavors {
                    f1 { isDefault = true}
                    f2 { isDefault = true}
                    f3
                }"""
        )
        assertThat(result.defaultVariant).isEqualTo("f1Debug")
        assertThat(result.syncIssues).hasSize(1)
        assertThat(result.syncIssues.first().type).isEqualTo(SyncIssue.TYPE_AMBIGUOUS_PRODUCT_FLAVOR_DEFAULT)
        assertThat(result.syncIssues.first().severity).isEqualTo(SyncIssue.SEVERITY_WARNING)
        assertThat(result.syncIssues.first().message).isEqualTo(
            "Ambiguous default product flavors for flavor dimension '1': 'f1', 'f2'.\n" +
                    "Please only set `isDefault = true` " +
                    "for one product flavor in each flavor dimension.")
    }

    @Test
    fun `test multi dimension ambiguous product flavor`() {
        val result = computeDefaultVariantWithSyncIssues(
            """
                flavorDimensions '1', '2'
                productFlavors {
                    f1 { dimension='1'; isDefault = true}
                    f2 { dimension='1'; isDefault = true}
                    f3 { dimension='1' }
                    f4 { dimension='2'; isDefault = true}
                    f5 { dimension='2'; isDefault = true}
                }"""
        )
        assertThat(result.defaultVariant).isEqualTo("f1F4Debug")
        assertThat(result.syncIssues).hasSize(2)
        for (syncIssue in result.syncIssues) {
            assertThat(syncIssue.type).isEqualTo(SyncIssue.TYPE_AMBIGUOUS_PRODUCT_FLAVOR_DEFAULT)
            assertThat(syncIssue.severity).isEqualTo(SyncIssue.SEVERITY_WARNING)
        }
        assertThat(result.syncIssues.map { it.data }).containsExactly("1", "2")
        assertThat(result.syncIssues.map { it.message }).containsExactly(
            "Ambiguous default product flavors for flavor dimension '1': 'f1', 'f2'.\n" +
                    "Please only set `isDefault = true` " +
                    "for one product flavor in each flavor dimension.",
            "Ambiguous default product flavors for flavor dimension '2': 'f4', 'f5'.\n" +
                    "Please only set `isDefault = true` " +
                    "for one product flavor in each flavor dimension.")
    }

    @Test
    fun `test feature plugin defaults to debug`() {
        assertThat(
            computeDefaultVariant(
                dsl = """
                    buildTypes {
                        a
                        z
                    }
                    """,
                pluginType = TestProjects.Plugin.FEATURE
            )
        ).isEqualTo("debug")
    }

    @Test
    fun `default respects tested build type`() {
        assertThat(
            computeDefaultVariant(
                dsl = """
                    buildTypes {
                        a
                    }
                    testBuildType = 'a'
                    """
            )
        ).isEqualTo("a")
    }

    private class Result(val defaultVariant: String?, val syncIssues: Set<SyncIssue>)

    private fun computeDefaultVariant(dsl: String, pluginType: TestProjects.Plugin = TestProjects.Plugin.APP): String? {
        val result = computeDefaultVariantWithSyncIssues(dsl, pluginType)
        assertThat(result.syncIssues).isEmpty()
        return result.defaultVariant
    }

    private fun computeDefaultVariantWithSyncIssues(dsl: String, pluginType: TestProjects.Plugin = TestProjects.Plugin.APP) : Result {
        project = TestProjects.builder(projectDirectory.newFolder("project").toPath())
            .withPlugin(pluginType)
            .build()
        project.extensions.getByType(TestedExtension::class.java).apply {
            setCompileSdkVersion(TestConstants.COMPILE_SDK_VERSION)
        }
        Eval.me(
            "project",
            project, "project.android {\n $dsl\n }\n"
        )
        val plugin = when (pluginType) {
            TestProjects.Plugin.APP -> project.plugins.getPlugin(AppPlugin::class.java)
            TestProjects.Plugin.LIBRARY -> project.plugins.getPlugin(LibraryPlugin::class.java)
            TestProjects.Plugin.FEATURE -> project.plugins.getPlugin(FeaturePlugin::class.java)
        }

        plugin.variantManager.populateVariantDataList()

        val syncIssuesHandler = FakeSyncIssueHandler()
        val defaultVariant =
            plugin.variantManager.getDefaultVariant(syncIssuesHandler)
        return Result(defaultVariant, ImmutableSet.copyOf(syncIssuesHandler.syncIssues))
    }

    class FakeSyncIssueHandler: SyncIssueHandler() {
        override val syncIssues: ImmutableList<SyncIssue>
            get() = ImmutableList.copyOf(_syncIssues)

        private val _syncIssues = mutableListOf<SyncIssue>()

        override fun hasSyncIssue(type: Type): Boolean {
            return _syncIssues.any { issue -> issue.type == type.type }
        }

        override fun lockHandler() {
            throw UnsupportedOperationException("lockHandler not implemented.")
        }

        override fun reportIssue(
            type: Type,
            severity: Severity,
            exception: EvalIssueException
        ) {
            val issue = SyncIssueImpl(type, severity, exception)
            _syncIssues.add(issue)
        }
    }
}
