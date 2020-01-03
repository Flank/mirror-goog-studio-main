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
import com.android.build.gradle.internal.utils.IssueSubject
import com.android.builder.core.VariantTypeImpl
import com.android.builder.model.SyncIssue
import com.google.common.truth.Truth
import org.junit.Test

class VariantCombinatorTest: AbstractVariantInputModelTest<List<VariantConfiguration>>() {
    @Test
    fun `test default config`() {
        given {
            android {
                buildTypes {
                    create("debug").isDebuggable = true
                    create("release")
                }
            }
        }

        expect {
            listOf(
                VariantConfigurationImpl(
                    variantName = "debug",
                    buildType = "debug",
                    productFlavors = emptyList(),
                    isDebuggable = true
                ),
                VariantConfigurationImpl(
                    variantName = "release",
                    buildType = "release",
                    productFlavors = emptyList(),
                    isDebuggable = false
                )
            )
        }
    }

    @Test
    fun `test basic flavors`() {
        given {
            android {
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
            }
        }

        expect {
            listOf(
                VariantConfigurationImpl(
                    variantName = "flavor1Debug",
                    buildType = "debug",
                    productFlavors = listOf("one" to "flavor1"),
                    isDebuggable = true
                ),
                VariantConfigurationImpl(
                    variantName = "flavor2Debug",
                    buildType = "debug",
                    productFlavors = listOf("one" to "flavor2"),
                    isDebuggable = true
                ),
                VariantConfigurationImpl(
                    variantName = "flavor1Release",
                    buildType = "release",
                    productFlavors = listOf("one" to "flavor1")
                ),
                VariantConfigurationImpl(
                    variantName = "flavor2Release",
                    buildType = "release",
                    productFlavors = listOf("one" to "flavor2")
                )
            )
        }
    }

    @Test
    fun `test multiple dimensions flavors`() {
        given {
            android {
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
            }
        }

        withFlavorList {
            listOf("one", "two")
        }

        expect {
            listOf(
                VariantConfigurationImpl(
                    variantName = "flavor1FlavorADebug",
                    buildType = "debug",
                    productFlavors = listOf("one" to "flavor1", "two" to "flavorA"),
                    isDebuggable = true
                ),
                VariantConfigurationImpl(
                    variantName = "flavor1FlavorBDebug",
                    buildType = "debug",
                    productFlavors = listOf("one" to "flavor1", "two" to "flavorB"),
                    isDebuggable = true
                ),
                VariantConfigurationImpl(
                    variantName = "flavor2FlavorADebug",
                    buildType = "debug",
                    productFlavors = listOf("one" to "flavor2", "two" to "flavorA"),
                    isDebuggable = true
                ),
                VariantConfigurationImpl(
                    variantName = "flavor2FlavorBDebug",
                    buildType = "debug",
                    productFlavors = listOf("one" to "flavor2", "two" to "flavorB"),
                    isDebuggable = true
                ),
                VariantConfigurationImpl(
                    variantName = "flavor1FlavorARelease",
                    buildType = "release",
                    productFlavors = listOf("one" to "flavor1", "two" to "flavorA")
                ),
                VariantConfigurationImpl(
                    variantName = "flavor1FlavorBRelease",
                    buildType = "release",
                    productFlavors = listOf("one" to "flavor1", "two" to "flavorB")
                ),
                VariantConfigurationImpl(
                    variantName = "flavor2FlavorARelease",
                    buildType = "release",
                    productFlavors = listOf("one" to "flavor2", "two" to "flavorA")
                ),
                VariantConfigurationImpl(
                    variantName = "flavor2FlavorBRelease",
                    buildType = "release",
                    productFlavors = listOf("one" to "flavor2", "two" to "flavorB")
                )
            )
        }
    }

    @Test
    fun `test no build type or flavors`() {
        given {
            android {
            }
        }

        expect {
            listOf(
                VariantConfigurationImpl(
                    variantName = "main",
                    buildType = null,
                    productFlavors = emptyList(),
                    isDebuggable = false
                )
            )
        }
    }

    @Test
    fun `test basic flavors - no build types`() {
        given {
            android {
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
            }
        }

        expect {
            listOf(
                VariantConfigurationImpl(
                    variantName = "flavor1",
                    buildType = null,
                    productFlavors = listOf("one" to "flavor1")
                ),
                VariantConfigurationImpl(
                    variantName = "flavor2",
                    buildType = null,
                    productFlavors = listOf("one" to "flavor2")
                )
            )
        }
    }

    @Test
    fun `test missing dimension in flavors`() {
        given {
            android {
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
            }
        }

        withFlavorList { listOf("one") }

        expect {
            listOf(
                VariantConfigurationImpl(
                    variantName = "flavor1Debug",
                    buildType = "debug",
                    productFlavors = listOf("one" to "flavor1"),
                    isDebuggable = true
                ),
                VariantConfigurationImpl(
                    variantName = "flavor2Debug",
                    buildType = "debug",
                    productFlavors = listOf("one" to "flavor2"),
                    isDebuggable = true
                ),
                VariantConfigurationImpl(
                    variantName = "flavor1Release",
                    buildType = "release",
                    productFlavors = listOf("one" to "flavor1")
                ),
                VariantConfigurationImpl(
                    variantName = "flavor2Release",
                    buildType = "release",
                    productFlavors = listOf("one" to "flavor2")
                )
            )
        }
    }

    @Test
    fun `test missing dimension list with no dimension in flavors`() {
        given {
            android {
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
            }
        }

        withIssueChecker { issues: List<SyncIssue> ->
            Truth.assertThat(issues).named("issues").hasSize(1)
            val issue = issues.single()
            IssueSubject.assertThat(issue).hasType(SyncIssue.TYPE_UNNAMED_FLAVOR_DIMENSION)
            IssueSubject.assertThat(issue)
                .hasMessage("All flavors must now belong to a named flavor dimension. Learn more at https://d.android.com/r/tools/flavorDimensions-missing-error-message.html")
        }

        expect {
            listOf(
                VariantConfigurationImpl(
                    variantName = "flavor1Debug",
                    buildType = "debug",
                    productFlavors = listOf(VariantCombinator.FAKE_DIMENSION to "flavor1"),
                    isDebuggable = true
                ),
                VariantConfigurationImpl(
                    variantName = "flavor2Debug",
                    buildType = "debug",
                    productFlavors = listOf(VariantCombinator.FAKE_DIMENSION to "flavor2"),
                    isDebuggable = true
                ),
                VariantConfigurationImpl(
                    variantName = "flavor1Release",
                    buildType = "release",
                    productFlavors = listOf(VariantCombinator.FAKE_DIMENSION to "flavor1")
                ),
                VariantConfigurationImpl(
                    variantName = "flavor2Release",
                    buildType = "release",
                    productFlavors = listOf(VariantCombinator.FAKE_DIMENSION to "flavor2")
                )
            )
        }
    }

    @Test
    fun `test missing dimension list`() {
        given {
            android {
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
            }
        }

        expect {
            listOf(
                VariantConfigurationImpl(
                    variantName = "flavor1Debug",
                    buildType = "debug",
                    productFlavors = listOf("one" to "flavor1"),
                    isDebuggable = true
                ),
                VariantConfigurationImpl(
                    variantName = "flavor2Debug",
                    buildType = "debug",
                    productFlavors = listOf("one" to "flavor2"),
                    isDebuggable = true
                ),
                VariantConfigurationImpl(
                    variantName = "flavor1Release",
                    buildType = "release",
                    productFlavors = listOf("one" to "flavor1")
                ),
                VariantConfigurationImpl(
                    variantName = "flavor2Release",
                    buildType = "release",
                    productFlavors = listOf("one" to "flavor2")
                )
            )
        }
    }

    private var flavorList: List<String>? = null

    private fun withFlavorList(action: () -> List<String>) {
        flavorList = action()
    }

    override fun defaultWhen(given: TestVariantInputModel): List<VariantConfiguration>? {
        // compute the flavor list if needed
        val expectedFlavorList = flavorList ?: given.productFlavors.values.asSequence().mapNotNull { it.productFlavor.dimension }.toSet().toList()

        val variantComputer = VariantCombinator(
            given, dslScope.issueReporter, VariantTypeImpl.BASE_APK, expectedFlavorList
        )

        return variantComputer.computeVariants()
    }

    override fun compareResult(
        expected: List<VariantConfiguration>?,
        actual: List<VariantConfiguration>?,
        given: TestVariantInputModel
    ) {
        Truth.assertThat(actual).containsExactlyElementsIn(expected)
    }
}

