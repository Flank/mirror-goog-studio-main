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

import com.android.build.gradle.internal.utils.IssueSubject
import com.android.builder.core.VariantTypeImpl
import com.android.builder.model.SyncIssue
import com.google.common.truth.Truth
import org.junit.Test

class DimensionCombinatorTest : AbstractVariantInputModelTest<List<DimensionCombination>>() {
    @Test
    fun `test default config`() {
        given {
            android {
                buildTypes {
                    create("debug")
                    create("release")
                }
            }
        }

        expect {
            listOf(
                DimensionCombinationImpl(
                    buildType = "debug",
                    productFlavors = emptyList()
                ),
                DimensionCombinationImpl(
                    buildType = "release",
                    productFlavors = emptyList()
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
                        dimension = "one"
                    }
                    create("flavor2") {
                        dimension = "one"
                    }
                }
            }
        }

        expect {
            listOf(
                DimensionCombinationImpl(
                    buildType = "debug",
                    productFlavors = listOf("one" to "flavor1")
                ),
                DimensionCombinationImpl(
                    buildType = "debug",
                    productFlavors = listOf("one" to "flavor2")
                ),
                DimensionCombinationImpl(
                    buildType = "release",
                    productFlavors = listOf("one" to "flavor1")
                ),
                DimensionCombinationImpl(
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
                    create("debug")
                    create("release")
                }
                productFlavors {
                    create("flavor1") {
                        dimension = "one"
                    }
                    create("flavor2") {
                        dimension = "one"
                    }
                    create("flavorA") {
                        dimension = "two"
                    }
                    create("flavorB") {
                        dimension = "two"
                    }
                }
            }
        }

        withFlavorList {
            listOf("one", "two")
        }

        expect {
            listOf(
                DimensionCombinationImpl(
                    buildType = "debug",
                    productFlavors = listOf("one" to "flavor1", "two" to "flavorA")
                ),
                DimensionCombinationImpl(
                    buildType = "debug",
                    productFlavors = listOf("one" to "flavor1", "two" to "flavorB")
                ),
                DimensionCombinationImpl(
                    buildType = "debug",
                    productFlavors = listOf("one" to "flavor2", "two" to "flavorA")
                ),
                DimensionCombinationImpl(
                    buildType = "debug",
                    productFlavors = listOf("one" to "flavor2", "two" to "flavorB")
                ),
                DimensionCombinationImpl(
                    buildType = "release",
                    productFlavors = listOf("one" to "flavor1", "two" to "flavorA")
                ),
                DimensionCombinationImpl(
                    buildType = "release",
                    productFlavors = listOf("one" to "flavor1", "two" to "flavorB")
                ),
                DimensionCombinationImpl(
                    buildType = "release",
                    productFlavors = listOf("one" to "flavor2", "two" to "flavorA")
                ),
                DimensionCombinationImpl(
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
                DimensionCombinationImpl(
                    buildType = null,
                    productFlavors = emptyList()
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
                        dimension = "one"
                    }
                    create("flavor2") {
                        dimension = "one"
                    }
                }
            }
        }

        expect {
            listOf(
                DimensionCombinationImpl(
                    buildType = null,
                    productFlavors = listOf("one" to "flavor1")
                ),
                DimensionCombinationImpl(
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
                    create("debug")
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
                DimensionCombinationImpl(
                    buildType = "debug",
                    productFlavors = listOf("one" to "flavor1")
                ),
                DimensionCombinationImpl(
                    buildType = "debug",
                    productFlavors = listOf("one" to "flavor2")
                ),
                DimensionCombinationImpl(
                    buildType = "release",
                    productFlavors = listOf("one" to "flavor1")
                ),
                DimensionCombinationImpl(
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
                DimensionCombinationImpl(
                    buildType = "debug",
                    productFlavors = listOf(DimensionCombinator.FAKE_DIMENSION to "flavor1")
                ),
                DimensionCombinationImpl(
                    buildType = "debug",
                    productFlavors = listOf(DimensionCombinator.FAKE_DIMENSION to "flavor2")
                ),
                DimensionCombinationImpl(
                    buildType = "release",
                    productFlavors = listOf(DimensionCombinator.FAKE_DIMENSION to "flavor1")
                ),
                DimensionCombinationImpl(
                    buildType = "release",
                    productFlavors = listOf(DimensionCombinator.FAKE_DIMENSION to "flavor2")
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
                        dimension = "one"
                    }
                    create("flavor2") {
                        dimension = "one"
                    }
                }
            }
        }

        expect {
            listOf(
                DimensionCombinationImpl(
                    buildType = "debug",
                    productFlavors = listOf("one" to "flavor1")
                ),
                DimensionCombinationImpl(
                    buildType = "debug",
                    productFlavors = listOf("one" to "flavor2")
                ),
                DimensionCombinationImpl(
                    buildType = "release",
                    productFlavors = listOf("one" to "flavor1")
                ),
                DimensionCombinationImpl(
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

    override fun defaultWhen(given: TestVariantInputModel): List<DimensionCombination>? {
        // compute the flavor list if needed
        val expectedFlavorList = flavorList ?: given.productFlavors.values.asSequence().mapNotNull { it.productFlavor.dimension }.toSet().toList()

        val variantComputer = DimensionCombinator(given, dslServices.issueReporter, expectedFlavorList)

        return variantComputer.computeVariants()
    }

    override fun compareResult(
        expected: List<DimensionCombination>?,
        actual: List<DimensionCombination>?
    ) {
        Truth.assertThat(actual).containsExactlyElementsIn(expected)
    }
}
