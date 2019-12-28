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

import com.android.build.api.variant.VariantFilter
import com.android.build.gradle.internal.VariantManager
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.variant.TestVariantInputModel
import com.android.build.gradle.internal.variant.VariantCombinator
import com.android.build.gradle.internal.variant.VariantModelImpl
import com.android.build.gradle.internal.variant.androidWithDefaults
import com.android.build.gradle.internal.variant2.createFakeDslScope
import com.android.builder.core.VariantTypeImpl
import com.android.builder.model.BuildType
import com.android.builder.model.ProductFlavor
import com.android.builder.model.SyncIssue
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito

/** Tests for the default variant DSL settings */
class DefaultVariantTest {

    private val dslScope: DslScope = createFakeDslScope()

    @get:Rule
    val projectDirectory = TemporaryFolder()

    lateinit var project: Project

    @Test
    fun `test defaults to debug`() {
        check(
            given = androidWithDefaults(dslScope) {
                buildTypes {
                    create("a")
                    create("z")
                }
            },
            expected = "debug"
        )
    }

    @Test
    fun `test removed all variants returns null`() {
        check(
            given = androidWithDefaults(dslScope) {
            },
            variantFilter = { variant ->
                variant.ignore = true
            },
            expected = null
        )
    }

    @Test
    fun `test debug removed defaults to first alphabetically`() {
        check(
            given = androidWithDefaults(dslScope) {
                buildTypes {
                    create("a")
                    create("z")
                }
            },
            variantFilter = { variant ->
                if (variant.buildType.name == "debug") {
                    variant.ignore = true
                }
            },
            expected = "a"
        )
    }

    @Test
    fun `test default variant build type explicit`() {
        check(
            given = androidWithDefaults(dslScope) {
                buildTypes {
                    create("dev") {
                        isDefault = true
                    }
                }
            },
            expected = "dev"
        )
    }

    @Test
    fun `test alphabetical flavor choice`() {
        check(
            given = androidWithDefaults(dslScope) {
                productFlavors {
                    create("f1") {
                        setDimension("dim")
                    }
                    create("f2") {
                        setDimension("dim")
                    }
                    create("f3") {
                        setDimension("dim")
                    }
                }
            },
            expected = "f1Debug"
        )
    }

    @Test
    fun `test explicit default flavor with single dimensions`() {
        check(
            given = androidWithDefaults(dslScope) {
                buildTypes {
                    create("a")
                    create("z") {
                        isDefault = true
                    }
                }

                productFlavors {
                    create("f1") {
                        setDimension("dim")
                    }
                    create("f2") {
                        setDimension("dim")
                        isDefault = true
                    }
                    create("f3") {
                        setDimension("dim")
                    }
                }
            },
            expected = "f2Z"
        )
    }

    @Test
    fun `test alphabetical default flavor with multiple dimensions`() {
        check(
            given = androidWithDefaults(dslScope) {
                productFlavors {
                    create("f1") {
                        setDimension("1")
                    }
                    create("f2") {
                        setDimension("1")
                    }
                    create("f3") {
                        setDimension("2")
                    }
                    create("f4") {
                        setDimension("2")
                    }
                }
            },
            expected = "f1F3Debug"
        )
    }

    @Test
    fun `test explicit default flavor with multiple dimensions`() {
        check(
            given = androidWithDefaults(dslScope) {
                productFlavors {
                    create("f1") {
                        setDimension("1")
                    }
                    create("f2") {
                        setDimension("1")
                        isDefault = true
                    }
                    create("f3") {
                        setDimension("2")
                    }
                    create("f4") {
                        setDimension("2")
                    }
                }
            },
            expected = "f2F3Debug"
        )
    }

    @Test
    fun `test removal default flavor with multiple dimensions`() {
        check(
            given = androidWithDefaults(dslScope) {
                productFlavors {
                    create("f1") {
                        setDimension("number")
                    }
                    create("f2") {
                        setDimension("number")
                    }
                    create("fa") {
                        setDimension("letter")
                    }
                    create("fb") {
                        setDimension("letter")
                    }
                }
            },
            variantFilter = { variant ->
                if (variant.name == "f1FaDebug") {
                    variant.ignore = true
                }
            },
            expected = "f1FbDebug"
        )
    }

    @Test
    fun `test removal default flavor with multiple dimensions and explicit choice`() {
        check(
            given = androidWithDefaults(dslScope) {
                productFlavors {
                    create("f1") {
                        setDimension("number")
                    }
                    create("f2") {
                        setDimension("number")
                        isDefault = true
                    }
                    create("fa") {
                        setDimension("letter")
                    }
                    create("fb") {
                        setDimension("letter")
                    }
                }
            },
            variantFilter = { variant ->
                if (variant.name == "f2FaDebug") {
                    variant.ignore = true
                }
            },
            expected = "f2FbDebug"
        )
    }

    @Test
    fun `test matching heuristic with filtering picks variant with most matching`() {
        check(
            given = androidWithDefaults(dslScope) {
                productFlavors {
                    create("f1") {
                        setDimension("number")
                    }
                    create("f2") {
                        setDimension("number")
                        isDefault = true
                    }
                    create("fa") {
                        setDimension("letter")
                    }
                    create("fb") {
                        setDimension("letter")
                        isDefault = true
                    }
                    create("fx") {
                        setDimension("something")
                    }
                    create("fy") {
                        setDimension("something")
                        isDefault = true
                    }
                }
            },
            variantFilter = { variant ->
                variant.ignore =
                    (variant.name == "f2FbFxDebug") ||
                            (variant.name == "f2FbFyDebug") ||
                            (variant.name == "f2FaFyDebug") ||
                            (variant.buildType.name == "release")
            },
            // Left to right comparison this would be f2FaFxDebug, (as f1 is first)
            // but f1FbFyDebug matches two of the user's settings, so the heuristic should prefer that.
            expected = "f1FbFyDebug"
        )
    }

    @Test
    fun `test ambiguous build type`() {
        check(
            given = androidWithDefaults(dslScope) {
                buildTypes {
                    create("a") {
                        isDefault = true
                    }
                    create("z") {
                        isDefault = true
                    }
                }
            },
            expected = "a",
            issueCheck = { issues ->
                assertThat(issues).hasSize(1)
                assertThat(issues.first().type).isEqualTo(SyncIssue.TYPE_AMBIGUOUS_BUILD_TYPE_DEFAULT)
                assertThat(issues.first().severity).isEqualTo(SyncIssue.SEVERITY_WARNING)
                assertThat(issues.first().message).isEqualTo(
                    "Ambiguous default build type: 'a', 'z'.\n" +
                            "Please only set `isDefault = true` " +
                            "for one build type."
                )
            }
        )
    }

    @Test
    fun `test ambiguous product flavor`() {
        check(
            given = androidWithDefaults(dslScope) {
                productFlavors {
                    create("f1") {
                        setDimension("1")
                        isDefault = true
                    }
                    create("f2") {
                        setDimension("1")
                        isDefault = true
                    }
                    create("f3") {
                        setDimension("1")
                    }
                }
            },
            expected = "f1Debug",
            issueCheck = { issues ->
                assertThat(issues).hasSize(1)
                assertThat(issues.first().type).isEqualTo(SyncIssue.TYPE_AMBIGUOUS_PRODUCT_FLAVOR_DEFAULT)
                assertThat(issues.first().severity).isEqualTo(SyncIssue.SEVERITY_WARNING)
                assertThat(issues.first().message).isEqualTo(
                    "Ambiguous default product flavors for flavor dimension '1': 'f1', 'f2'.\n" +
                            "Please only set `isDefault = true` " +
                            "for one product flavor in each flavor dimension."
                )
            }
        )
    }

    @Test
    fun `test multi dimension ambiguous product flavor`() {
        check(
            given = androidWithDefaults(dslScope) {
                productFlavors {
                    create("f1") {
                        setDimension("1")
                        isDefault = true
                    }
                    create("f2") {
                        setDimension("1")
                        isDefault = true
                    }
                    create("f3") {
                        setDimension("1")
                    }
                    create("f4") {
                        setDimension("2")
                        isDefault = true
                    }
                    create("f5") {
                        setDimension("2")
                        isDefault = true
                    }
                }
            },
            expected = "f1F4Debug",
            issueCheck = { issues ->
                assertThat(issues).hasSize(2)
                for (syncIssue in issues) {
                    assertThat(syncIssue.type).isEqualTo(SyncIssue.TYPE_AMBIGUOUS_PRODUCT_FLAVOR_DEFAULT)
                    assertThat(syncIssue.severity).isEqualTo(SyncIssue.SEVERITY_WARNING)
                }
                assertThat(issues.map { it.data }).containsExactly("1", "2")
                assertThat(issues.map { it.message }).containsExactly(
                    "Ambiguous default product flavors for flavor dimension '1': 'f1', 'f2'.\n" +
                            "Please only set `isDefault = true` " +
                            "for one product flavor in each flavor dimension.",
                    "Ambiguous default product flavors for flavor dimension '2': 'f4', 'f5'.\n" +
                            "Please only set `isDefault = true` " +
                            "for one product flavor in each flavor dimension."
                )
            }
        )
    }

    @Test
    fun `default respects tested build type`() {
        check(
            given = androidWithDefaults(dslScope) {
                buildTypes {
                    create("a")
                }
            },
            testBuildType = "a",
            expected = "a"
        )
    }

    private fun check(
        given: TestVariantInputModel,
        variantFilter: ((VariantFilter) -> Unit)? = null,
        testBuildType: String = "debug",
        expected: String?,
        issueCheck: (List<SyncIssue>) -> Unit = {
            Truth.assertThat(it).named("SyncIssues").isEmpty()
        }
    ) {
        val variantType = VariantTypeImpl.BASE_APK

        // gather the variant lists from the input model.
        val variantComputer = VariantCombinator(
            given, dslScope.issueReporter, variantType, given.implicitFlavorDimensions
        )

        // convert to mock VariantScope
        val variantScopes = mutableListOf<VariantScope>()

        for (variant in variantComputer.computeVariants()) {
            val flavors = variant.flavors.map { flavorName ->
                given.productFlavors[flavorName]!!.productFlavor
            }

            // run the filter
            var ignore = false
            if (variantFilter != null) {
                val variantInfo = VariantFilterImpl(
                    variant.name,
                    given.defaultConfig.productFlavor,
                    given.buildTypes[variant.buildType]!!.buildType,
                    flavors
                )

                variantFilter(variantInfo)

                ignore = variantInfo.ignore
            }

            // if not ignored, get the VariantScope
            // FIXME this should be simpler when we remove VariantData|Scope to use newer objects only.
            if (!ignore) {
                val variantScope = Mockito.mock(VariantScope::class.java)
                variantScopes.add(variantScope)

                Mockito.`when`(variantScope.fullVariantName).thenReturn(variant.name)
                Mockito.`when`(variantScope.type).thenReturn(variantType)

                val variantDslInfo = Mockito.mock(VariantDslInfo::class.java)
                Mockito.`when`(variantScope.variantDslInfo).thenReturn(variantDslInfo)
                Mockito.`when`(variantDslInfo.buildType).thenReturn(variant.buildType)
                Mockito.`when`(variantDslInfo.productFlavors).thenReturn(flavors)
            }
        }

        val variantManager = Mockito.mock(VariantManager::class.java).also {
            Mockito.`when`(it.variantScopes).thenReturn(variantScopes)
        }

        // finally get the computed default variant
        val actual = VariantModelImpl(
            given,
            { testBuildType },
            variantManager,
            dslScope.issueReporter
        ).defaultVariant

        // and validate it
        assertThat(actual).named("Name of the default Variant").isEqualTo(expected)

        // also check for errors
        issueCheck(dslScope.issueReporter.syncIssues)
    }

    private class VariantFilterImpl(
        override val name: String,
        override val defaultConfig: ProductFlavor,
        override val buildType: BuildType,
        override val flavors: List<ProductFlavor>
    ) : VariantFilter {
        override var ignore: Boolean = false
    }
}
