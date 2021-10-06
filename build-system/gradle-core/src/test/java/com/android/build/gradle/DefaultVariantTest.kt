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
import com.android.build.api.component.impl.TestComponentImpl
import com.android.build.api.variant.impl.VariantImpl
import com.android.build.gradle.internal.core.VariantDslInfoBuilder
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.dsl.ApplicationBuildFeaturesImpl
import com.android.build.gradle.internal.scope.BuildFeatureValuesImpl
import com.android.build.gradle.internal.variant.AbstractVariantInputModelTest
import com.android.build.gradle.internal.variant.TestVariantInputModel
import com.android.build.gradle.internal.variant.DimensionCombinator
import com.android.build.gradle.internal.variant.VariantModelImpl
import com.android.builder.core.VariantTypeImpl
import com.android.builder.model.BuildType
import com.android.builder.model.ProductFlavor
import com.android.builder.model.SyncIssue
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito

/** Tests for the default variant DSL settings */
class DefaultVariantTest: AbstractVariantInputModelTest<String>() {

    @Test
    fun `test defaults to debug`() {
        useDefaultBuildTypes()

        given {
            android {
                buildTypes {
                    create("a")
                    create("z")
                }
            }
        }

        expect { "debug" }
    }

    @Test
    fun `test removed all variants returns null`() {
        useDefaultBuildTypes()

        given {
            android {
            }
        }

        withVariantFilter { variant ->
            variant.ignore = true
        }

        expect { null }
    }

    @Test
    fun `test debug removed defaults to first alphabetically`() {
        useDefaultBuildTypes()

        given {
            android {
                buildTypes {
                    create("a")
                    create("z")
                }
            }
        }

        withVariantFilter { variant ->
            if (variant.buildType.name == "debug") {
                variant.ignore = true
            }
        }

        expect { "a" }
    }

    @Test
    fun `test default variant build type explicit`() {
        useDefaultBuildTypes()

        given {
            android {
                buildTypes {
                    create("dev") {
                        isDefault = true
                    }
                }
            }
        }

        expect { "dev" }
    }

    @Test
    fun `test alphabetical flavor choice`() {
        useDefaultBuildTypes()

        given {
            android {
                productFlavors {
                    create("f1") {
                        dimension = "dim"
                    }
                    create("f2") {
                        dimension = "dim"
                    }
                    create("f3") {
                        dimension = "dim"
                    }
                }
            }
        }

        expect { "f1Debug" }
    }

    @Test
    fun `test explicit default flavor with single dimensions`() {
        useDefaultBuildTypes()

        given {
            android {
                buildTypes {
                    create("a")
                    create("z") {
                        isDefault = true
                    }
                }

                productFlavors {
                    create("f1") {
                        dimension = "dim"
                    }
                    create("f2") {
                        dimension = "dim"
                        isDefault = true
                    }
                    create("f3") {
                        dimension = "dim"
                    }
                }
            }
        }

        expect { "f2Z" }
    }

    @Test
    fun `test alphabetical default flavor with multiple dimensions`() {
        useDefaultBuildTypes()

        given {
            android {
                productFlavors {
                    create("f1") {
                        dimension = "1"
                    }
                    create("f2") {
                        dimension = "1"
                    }
                    create("f3") {
                        dimension = "2"
                    }
                    create("f4") {
                        dimension = "2"
                    }
                }
            }
        }

        expect { "f1F3Debug" }
    }

    @Test
    fun `test explicit default flavor with multiple dimensions`() {
        useDefaultBuildTypes()

        given {
            android {
                productFlavors {
                    create("f1") {
                        dimension = "1"
                    }
                    create("f2") {
                        dimension = "1"
                        isDefault = true
                    }
                    create("f3") {
                        dimension = "2"
                    }
                    create("f4") {
                        dimension = "2"
                    }
                }
            }
        }

        expect { "f2F3Debug" }
    }

    @Test
    fun `test removal default flavor with multiple dimensions`() {
        useDefaultBuildTypes()

        given {
            android {
                productFlavors {
                    create("f1") {
                        dimension = "number"
                    }
                    create("f2") {
                        dimension = "number"
                    }
                    create("fa") {
                        dimension = "letter"
                    }
                    create("fb") {
                        dimension = "letter"
                    }
                }
            }
        }

        withVariantFilter { variant ->
            if (variant.name == "f1FaDebug") {
                variant.ignore = true
            }
        }

        expect { "f1FbDebug" }
    }

    @Test
    fun `test removal default flavor with multiple dimensions and explicit choice`() {
        useDefaultBuildTypes()

        given {
            android {
                productFlavors {
                    create("f1") {
                        dimension = "number"
                    }
                    create("f2") {
                        dimension = "number"
                        isDefault = true
                    }
                    create("fa") {
                        dimension = "letter"
                    }
                    create("fb") {
                        dimension = "letter"
                    }
                }
            }
        }

        withVariantFilter { variant ->
            if (variant.name == "f2FaDebug") {
                variant.ignore = true
            }
        }

        expect { "f2FbDebug" }
    }

    @Test
    fun `test matching heuristic with filtering picks variant with most matching`() {
        useDefaultBuildTypes()

        given {
            android {
                productFlavors {
                    create("f1") {
                        dimension = "number"
                    }
                    create("f2") {
                        dimension = "number"
                        isDefault = true
                    }
                    create("fa") {
                        dimension = "letter"
                    }
                    create("fb") {
                        dimension = "letter"
                        isDefault = true
                    }
                    create("fx") {
                        dimension = "something"
                    }
                    create("fy") {
                        dimension = "something"
                        isDefault = true
                    }
                }
            }
        }

        withVariantFilter { variant ->
            variant.ignore =
                (variant.name == "f2FbFxDebug") ||
                        (variant.name == "f2FbFyDebug") ||
                        (variant.name == "f2FaFyDebug") ||
                        (variant.buildType.name == "release")
        }

        // Left to right comparison this would be f2FaFxDebug, (as f1 is first)
        // but f1FbFyDebug matches two of the user's settings, so the heuristic should prefer that.
        expect { "f1FbFyDebug" }
    }

    @Test
    fun `test ambiguous build type`() {
        useDefaultBuildTypes()

        given {
            android {
                buildTypes {
                    create("a") {
                        isDefault = true
                    }
                    create("z") {
                        isDefault = true
                    }
                }
            }
        }

        withIssueChecker {
                issues ->
            assertThat(issues).hasSize(1)
            assertThat(issues.first().type).isEqualTo(SyncIssue.TYPE_AMBIGUOUS_BUILD_TYPE_DEFAULT)
            assertThat(issues.first().severity).isEqualTo(SyncIssue.SEVERITY_WARNING)
            assertThat(issues.first().message).isEqualTo(
                "Ambiguous default build type: 'a', 'z'.\n" +
                        "Please only set `isDefault = true` " +
                        "for one build type."
            )
        }

        expect { "a" }
    }

    @Test
    fun `test ambiguous product flavor`() {
        useDefaultBuildTypes()
        given {
            android {
                productFlavors {
                    create("f1") {
                        dimension = "1"
                        isDefault = true
                    }
                    create("f2") {
                        dimension = "1"
                        isDefault = true
                    }
                    create("f3") {
                        dimension = "1"
                    }
                }
            }
        }

        withIssueChecker { issues ->
            assertThat(issues).hasSize(1)
            assertThat(issues.first().type).isEqualTo(SyncIssue.TYPE_AMBIGUOUS_PRODUCT_FLAVOR_DEFAULT)
            assertThat(issues.first().severity).isEqualTo(SyncIssue.SEVERITY_WARNING)
            assertThat(issues.first().message).isEqualTo(
                "Ambiguous default product flavors for flavor dimension '1': 'f1', 'f2'.\n" +
                        "Please only set `isDefault = true` " +
                        "for one product flavor in each flavor dimension."
            )
        }

        expect { "f1Debug" }
    }

    @Test
    fun `test multi dimension ambiguous product flavor`() {
        useDefaultBuildTypes()
        given {
            android {
                productFlavors {
                    create("f1") {
                        dimension = "1"
                        isDefault = true
                    }
                    create("f2") {
                        dimension = "1"
                        isDefault = true
                    }
                    create("f3") {
                        dimension = "1"
                    }
                    create("f4") {
                        dimension = "2"
                        isDefault = true
                    }
                    create("f5") {
                        dimension = "2"
                        isDefault = true
                    }
                }
            }
        }

        withIssueChecker { issues ->
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

        expect { "f1F4Debug" }
    }

    @Test
    fun `default respects tested build type`() {
        useDefaultBuildTypes()

        given {
            android {
                buildTypes {
                    create("a")
                }
            }
        }

        withTestBuildType { "a" }

        expect { "a" }
    }

    private var variantFilter: ((VariantFilter) -> Unit)? = null
    private var testBuildType: String? = "debug"

    private fun withVariantFilter(action : ((VariantFilter) -> Unit)?) {
        checkState(TestState.GIVEN)
        variantFilter = action
    }

    private fun withTestBuildType(action: () -> String?) {
        checkState(TestState.GIVEN)
        testBuildType = action()
    }

    override fun compareResult(expected: String?, actual: String?) {
        assertThat(actual).named("Name of the default Variant").isEqualTo(expected)
    }

    override fun defaultWhen(given: TestVariantInputModel): String? {
        val variantType = VariantTypeImpl.BASE_APK

        // gather the variant lists from the input model.
        val variantComputer = DimensionCombinator(
            given, dslServices.issueReporter, given.implicitFlavorDimensions
        )

        // convert to mock VariantScope
        val components = mutableListOf<VariantImpl>()

        for (variant in variantComputer.computeVariants()) {
            val name = VariantDslInfoBuilder.computeName(variant, variantType)

            val flavors = variant.productFlavors.map {
                (given.productFlavors[it.second] ?: error("Cant find flavor ${it.second}")).productFlavor
            }

            // run the filter
            var ignore = false
            if (variantFilter != null) {
                val variantInfo = VariantFilterImpl(
                    name,
                    given.defaultConfigData.defaultConfig,
                    given.buildTypes[variant.buildType]!!.buildType,
                    flavors
                )

                variantFilter?.invoke(variantInfo)

                ignore = variantInfo.ignore
            }

            // if not ignored, get the VariantScope
            // FIXME this should be simpler when we remove VariantData|Scope to use newer objects only.
            if (!ignore) {
                val component = Mockito.mock(VariantImpl::class.java)
                components.add(component)

                Mockito.`when`(component.name).thenReturn(name)
                Mockito.`when`(component.variantType).thenReturn(variantType)
                Mockito.`when`(component.buildType).thenReturn(variant.buildType)
                Mockito.`when`(component.productFlavors).thenReturn(variant.productFlavors)

                val variantDslInfo = Mockito.mock(VariantDslInfo::class.java)
                Mockito.`when`(component.variantDslInfo).thenReturn(variantDslInfo)
                Mockito.`when`(variantDslInfo.productFlavorList).thenReturn(flavors)
            }
        }

        // finally get the computed default variant
        return VariantModelImpl(
            given,
            { testBuildType },
            { components },
            { listOf() },
            {
                BuildFeatureValuesImpl(
                    dslServices.newInstance(ApplicationBuildFeaturesImpl::class.java),
                    dslServices.projectOptions
                )
            },
            dslServices.issueReporter
        ).defaultVariant
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
