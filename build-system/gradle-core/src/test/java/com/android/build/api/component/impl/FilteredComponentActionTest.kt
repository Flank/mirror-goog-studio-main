
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
package com.android.build.api.component.impl

import com.android.build.api.variant.VariantBuilder
import com.android.testutils.AbstractReturnGivenReturnExpectTest
import com.android.testutils.on
import org.gradle.api.Action
import org.junit.Test
import org.mockito.Mockito
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import kotlin.test.fail

/**
 * Tests for [FilteredComponentAction]
 */
class FilteredComponentActionTest: AbstractReturnGivenReturnExpectTest<Pair<FilteredComponentActionTest.FilterInfo, FilteredComponentActionTest.VariantInfo>, Boolean>() {

    @Test
    fun `matching name`() {
        given {
            filteredOperation {
                name = "foo"
            } on variant {
                name = "foo"
            }
        }

        expect { true }
    }

    @Test
    fun `non matching name`() {
        given {
            filteredOperation {
                name = "foo"
            } on variant {
                name = "bar"
            }
        }

        expect { false }
    }

    @Test
    fun `partial name`() {
        given {
            filteredOperation {
                name = "foo"
            } on variant {
                name = "foobar"
            }
        }

        expect { false }
    }

    @Test
    fun `matching pattern`() {
        given {
            filteredOperation {
                namePattern = Pattern.compile("foo.*")
            } on variant {
                name = "foo"
            }
        }

        expect { true }
    }

    @Test
    fun `not matching pattern`() {
        given {
            filteredOperation {
                namePattern = Pattern.compile("foo.*")
            } on variant {
                name = "bar"
            }
        }

        expect { false }
    }

    @Test
    fun `matching build type`() {
        given {
            filteredOperation {
                buildType = "debug"
            } on variant {
                buildType = "debug"
            }
        }

        expect { true }
    }

    @Test
    fun `not matching build type`() {
        given {
            filteredOperation {
                buildType = "debug"
            } on variant {
                buildType = "release"
            }
        }

        expect { false }
    }

    @Test
    fun `not matching build type when no-build-type variant`() {
        given {
            filteredOperation {
                buildType = "debug"
            } on variant {
                buildType = null
            }
        }

        expect { false }
    }

    @Test
    fun `matching single flavor`() {
        given {
            filteredOperation {
                productFlavors = listOf("one" to "flavor1")
            } on variant {
                productFlavors = listOf("one" to "flavor1")
            }
        }

        expect { true }
    }

    @Test
    fun `not matching single flavor`() {
        given {
            filteredOperation {
                productFlavors = listOf("one" to "flavor1")
            } on variant {
                productFlavors = listOf("one" to "flavor2")
            }
        }

        expect { false }
    }

    @Test
    fun `matching all flavors`() {
        given {
            filteredOperation {
                productFlavors = listOf("one" to "flavor1", "two" to "flavorA")
            } on variant {
                productFlavors = listOf("one" to "flavor1", "two" to "flavorA")
            }
        }

        expect { true }
    }

    @Test
    fun `matching no flavors`() {
        given {
            filteredOperation {
                productFlavors = listOf("one" to "flavor1", "two" to "flavorA")
            } on variant {
                productFlavors = listOf("one" to "flavor2", "two" to "flavorB")
            }
        }

        expect { false }
    }

    @Test
    fun `not matching all flavors`() {
        given {
            filteredOperation {
                productFlavors = listOf("one" to "flavor1", "two" to "flavorA")
            } on variant {
                productFlavors = listOf("one" to "flavor1", "two" to "flavorB")
            }
        }

        expect { false }
    }

    // ---------------------------------------------------------------------------------------------

    override fun defaultWhen(given: Pair<FilterInfo, VariantInfo>): Boolean? {
        val atomicBoolean = AtomicBoolean(false)

        val operation = with(given.first) {
            FilteredComponentAction(
                buildType = buildType,
                flavors = productFlavors ?: listOf(),
                namePattern = namePattern,
                name = name,
                action = Action {
                    atomicBoolean.set(true)
                })
        }

        val variant = with(given.second) {
            Mockito.mock(VariantBuilder::class.java).also { variant ->
                name?.let { Mockito.`when`(variant.name).thenReturn(it) }
                buildType?.let { Mockito.`when`(variant.buildType).thenReturn(it) }
                Mockito.`when`(variant.productFlavors).thenReturn(productFlavors)
            }
        }

        operation.executeFor(variant)

        // return whether the variant ran
        return atomicBoolean.get()
    }

    override fun compareResult(
        expected: Boolean?,
        actual: Boolean?,
        given: Pair<FilterInfo, VariantInfo>
    ) {
        val actualB = actual ?: throw RuntimeException("actual should not be null")
        val expectedB = expected ?: throw RuntimeException("expected should not be null")

        if (actualB != expectedB) {
            val header = if (expectedB) {
                "FilteredVariantOperation expected to run but did not."
            } else {
                "FilteredVariantOperation expected to not run but did."
            }

            fail("""$header
                |The following inputs were used:
                |- ${given.first}
                |- ${given.second}
            """.trimMargin())
        }
    }

    class FilterInfo(
        var name: String? = null,
        var namePattern: Pattern? = null,
        var buildType: String? = null,
        var productFlavors: List<Pair<String, String>>? = null


    ) {
        override fun toString(): String {
            return "OperationFilter(name=$name, namePattern=$namePattern, buildType=$buildType, productFlavors=$productFlavors)"
        }
    }

    private fun filteredOperation(action: FilterInfo.() -> Unit) = FilterInfo().also { action(it) }

    /**
     * Variant Info.
     *
     * Important to have some default that match the most common use case.
     */
    class VariantInfo(
        var name: String = "some-name",
        var buildType: String? = "some-build-type",
        var productFlavors: List<Pair<String, String>> = listOf()
    ) {
        override fun toString(): String {
            return "VariantInfo(name=$name, buildType=$buildType, productFlavors=$productFlavors)"
        }
    }

    private fun variant(action: VariantInfo.() -> Unit) = VariantInfo().also { action(it) }

}

