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

package com.android.build.gradle.internal.variant

import com.android.build.api.variant.VariantConfiguration
import com.android.build.api.variant.impl.VariantConfigurationImpl
import com.android.build.gradle.DefaultVariantTest
import com.android.build.gradle.internal.utils.IssueSubject
import com.android.builder.core.VariantTypeImpl
import com.android.builder.model.SyncIssue
import com.google.common.truth.Truth
import org.junit.Test

class VariantCombinatorTest {
    @Test
    fun `test default config`() {
        check(
            given = android {
                buildTypes {
                    create("debug").isDebuggable = true
                    create("release")
                }
            },
            expected = listOf(
                VariantConfigurationImpl(
                    variantName = "debug",
                    buildType = "debug",
                    flavors = emptyList(),
                    isDebuggable = true
                ),
                VariantConfigurationImpl(
                    variantName = "release",
                    buildType = "release",
                    flavors = emptyList(),
                    isDebuggable = false
                )
            )
        )
    }

    @Test
    fun `test basic flavors`() {
        check(
            given = android {
                buildTypes {
                    create("debug").isDebuggable = true
                    create("release")
                }
                productFlavors {
                    create("flavor1") {
                        // FIXME once we clear up the ProductFlavor inheritance
                        setDimension("one")
                    }
                    create("flavor2") {
                        // FIXME once we clear up the ProductFlavor inheritance
                        setDimension("one")
                    }
                }
            },
            expected = listOf(
                VariantConfigurationImpl(
                    variantName = "flavor1Debug",
                    buildType = "debug",
                    flavors = listOf("flavor1"),
                    isDebuggable = true
                ),
                VariantConfigurationImpl(
                    variantName = "flavor2Debug",
                    buildType = "debug",
                    flavors = listOf("flavor2"),
                    isDebuggable = true
                ),
                VariantConfigurationImpl(
                    variantName = "flavor1Release",
                    buildType = "release",
                    flavors = listOf("flavor1")
                ),
                VariantConfigurationImpl(
                    variantName = "flavor2Release",
                    buildType = "release",
                    flavors = listOf("flavor2")
                )
            )
        )
    }

    @Test
    fun `test multiple dimensions flavors`() {
        check(
            given = android {
                buildTypes {
                    create("debug").isDebuggable = true
                    create("release")
                }
                productFlavors {
                    create("flavor1") {
                        // FIXME once we clear up the ProductFlavor inheritance
                        setDimension("one")
                    }
                    create("flavor2") {
                        // FIXME once we clear up the ProductFlavor inheritance
                        setDimension("one")
                    }
                    create("flavorA") {
                        // FIXME once we clear up the ProductFlavor inheritance
                        setDimension("two")
                    }
                    create("flavorB") {
                        // FIXME once we clear up the ProductFlavor inheritance
                        setDimension("two")
                    }
                }
            },
            flavorList = listOf("one", "two"),
            expected = listOf(
                VariantConfigurationImpl(
                    variantName = "flavor1FlavorADebug",
                    buildType = "debug",
                    flavors = listOf("flavor1", "flavorA"),
                    isDebuggable = true
                ),
                VariantConfigurationImpl(
                    variantName = "flavor1FlavorBDebug",
                    buildType = "debug",
                    flavors = listOf("flavor1", "flavorB"),
                    isDebuggable = true
                ),
                VariantConfigurationImpl(
                    variantName = "flavor2FlavorADebug",
                    buildType = "debug",
                    flavors = listOf("flavor2", "flavorA"),
                    isDebuggable = true
                ),
                VariantConfigurationImpl(
                    variantName = "flavor2FlavorBDebug",
                    buildType = "debug",
                    flavors = listOf("flavor2", "flavorB"),
                    isDebuggable = true
                ),
                VariantConfigurationImpl(
                    variantName = "flavor1FlavorARelease",
                    buildType = "release",
                    flavors = listOf("flavor1", "flavorA")
                ),
                VariantConfigurationImpl(
                    variantName = "flavor1FlavorBRelease",
                    buildType = "release",
                    flavors = listOf("flavor1", "flavorB")
                ),
                VariantConfigurationImpl(
                    variantName = "flavor2FlavorARelease",
                    buildType = "release",
                    flavors = listOf("flavor2", "flavorA")
                ),
                VariantConfigurationImpl(
                    variantName = "flavor2FlavorBRelease",
                    buildType = "release",
                    flavors = listOf("flavor2", "flavorB")
                )
            )
        )
    }

    @Test
    fun `test no build type or flavors`() {
        check(
            given = android { },
            expected = listOf(
                VariantConfigurationImpl(
                    variantName = "main",
                    buildType = null,
                    flavors = emptyList(),
                    isDebuggable = false
                )
            )
        )
    }

    @Test
    fun `test basic flavors - no build types`() {
        check(
            given = android {
                productFlavors {
                    create("flavor1") {
                        // FIXME once we clear up the ProductFlavor inheritance
                        setDimension("one")
                    }
                    create("flavor2") {
                        // FIXME once we clear up the ProductFlavor inheritance
                        setDimension("one")
                    }
                }
            },
            expected = listOf(
                VariantConfigurationImpl(
                    variantName = "flavor1",
                    buildType = null,
                    flavors = listOf("flavor1")
                ),
                VariantConfigurationImpl(
                    variantName = "flavor2",
                    buildType = null,
                    flavors = listOf("flavor2")
                )
            )
        )
    }

    @Test
    fun `test missing dimension in flavors`() {
        check(
            given = android {
                buildTypes {
                    create("debug").isDebuggable = true
                    create("release")
                }
                productFlavors {
                    create("flavor1") {
                        // no dimension set
                    }
                    create("flavor2") {
                        // no dimension set
                    }
                }
            },
            flavorList = listOf("one"),
            expected = listOf(
                VariantConfigurationImpl(
                    variantName = "flavor1Debug",
                    buildType = "debug",
                    flavors = listOf("flavor1"),
                    isDebuggable = true
                ),
                VariantConfigurationImpl(
                    variantName = "flavor2Debug",
                    buildType = "debug",
                    flavors = listOf("flavor2"),
                    isDebuggable = true
                ),
                VariantConfigurationImpl(
                    variantName = "flavor1Release",
                    buildType = "release",
                    flavors = listOf("flavor1")
                ),
                VariantConfigurationImpl(
                    variantName = "flavor2Release",
                    buildType = "release",
                    flavors = listOf("flavor2")
                )
            )
        )
    }

    @Test
    fun `test missing dimension list with no dimension in flavors`() {
        check(
            given = android {
                buildTypes {
                    create("debug").isDebuggable = true
                    create("release")
                }
                productFlavors {
                    create("flavor1") {
                        // no dimension set
                    }
                    create("flavor2") {
                        // no dimension set
                    }
                }
            },
            expected = listOf(
                VariantConfigurationImpl(
                    variantName = "flavor1Debug",
                    buildType = "debug",
                    flavors = listOf("flavor1"),
                    isDebuggable = true
                ),
                VariantConfigurationImpl(
                    variantName = "flavor2Debug",
                    buildType = "debug",
                    flavors = listOf("flavor2"),
                    isDebuggable = true
                ),
                VariantConfigurationImpl(
                    variantName = "flavor1Release",
                    buildType = "release",
                    flavors = listOf("flavor1")
                ),
                VariantConfigurationImpl(
                    variantName = "flavor2Release",
                    buildType = "release",
                    flavors = listOf("flavor2")
                )
            ),
            issueCheck = { issues: List<SyncIssue> ->
                Truth.assertThat(issues).named("issues").hasSize(1)
                val issue = issues.single()
                IssueSubject.assertThat(issue).hasType(SyncIssue.TYPE_UNNAMED_FLAVOR_DIMENSION)
                IssueSubject.assertThat(issue)
                    .hasMessage("All flavors must now belong to a named flavor dimension. Learn more at https://d.android.com/r/tools/flavorDimensions-missing-error-message.html")
            }

        )
    }

    @Test
    fun `test missing dimension list`() {
        check(
            given = android {
                buildTypes {
                    create("debug").isDebuggable = true
                    create("release")
                }
                productFlavors {
                    create("flavor1") {
                        // FIXME once we clear up the ProductFlavor inheritance
                        setDimension("one")
                    }
                    create("flavor2") {
                        // FIXME once we clear up the ProductFlavor inheritance
                        setDimension("one")
                    }
                }
            },
            expected = listOf(
                VariantConfigurationImpl(
                    variantName = "flavor1Debug",
                    buildType = "debug",
                    flavors = listOf("flavor1"),
                    isDebuggable = true
                ),
                VariantConfigurationImpl(
                    variantName = "flavor2Debug",
                    buildType = "debug",
                    flavors = listOf("flavor2"),
                    isDebuggable = true
                ),
                VariantConfigurationImpl(
                    variantName = "flavor1Release",
                    buildType = "release",
                    flavors = listOf("flavor1")
                ),
                VariantConfigurationImpl(
                    variantName = "flavor2Release",
                    buildType = "release",
                    flavors = listOf("flavor2")
                )
            )
        )
    }

    private fun check(
        given: VariantModel,
        flavorList: List<String>? = null,
        expected: List<VariantConfiguration>,
        issueCheck: (List<SyncIssue>) -> Unit = { Truth.assertThat(it).named("SyncIssues").isEmpty() }
    ) {
        val errorReporter = DefaultVariantTest.FakeSyncIssueHandler()
        // compute the flavor list if needed
        val expectedFlavorList = flavorList ?: given.productFlavors.values.asSequence().mapNotNull { it.productFlavor.dimension }.toSet().toList()

        val variantComputer = VariantCombinator(
            given, errorReporter, VariantTypeImpl.BASE_APK, expectedFlavorList
        )

        val actual = variantComputer.computeVariants()

        Truth.assertThat(actual).containsExactlyElementsIn(expected)

        // also check for errors
        issueCheck(errorReporter.syncIssues)
    }
}

