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

package com.android.build.gradle.internal.core

import com.android.build.api.component.impl.ComponentIdentityImpl
import com.android.build.gradle.internal.variant.DimensionCombinationImpl
import com.android.builder.core.VariantType
import com.android.builder.core.VariantTypeImpl
import com.android.testutils.AbstractBuildGivenBuildExpectTest
import org.junit.Test

class VariantBuilderComputeNameTest :
    AbstractBuildGivenBuildExpectTest<VariantBuilderComputeNameTest.GivenBuilder, VariantBuilderComputeNameTest.ResultBuilder>() {

    @Test
    fun `app with build-type but not flavors`() {
        given {
            buildType = "debug"
        }

        expect {
            name = "debug"
            baseName = "debug"
            fullNameWithSplit = "splitDebug"
        }
    }

    @Test
    fun `app with build-type and one flavor`() {
        given {
            buildType = "debug"
            flavors = listOf("one" to "flavor1")
        }

        expect {
            name = "flavor1Debug"
            baseName = "flavor1-debug"
            fullNameWithSplit = "flavor1SplitDebug"
        }
    }

    @Test
    fun `app with no build-types and one flavor`() {
        given {
            buildType = null
            flavors = listOf("one" to "flavor1")
        }

        expect {
            name = "flavor1"
            baseName = "flavor1"
            fullNameWithSplit = "flavor1Split"
        }
    }

    @Test
    fun `app with no build-types and no flavors`() {
        given {
            buildType = null
        }

        expect {
            name = "main"
            baseName = "main"
            fullNameWithSplit = "split"
        }
    }

    @Test
    fun `androidTest with build-type but not flavors`() {
        given {
            variantType = VariantTypeImpl.ANDROID_TEST
            buildType = "debug"
        }

        expect {
            name = "debugAndroidTest"
            baseName = "debug-androidTest"
            fullNameWithSplit = "splitDebugAndroidTest"
        }
    }

    @Test
    fun `androidTest with build-type and one flavor`() {
        given {
            variantType = VariantTypeImpl.ANDROID_TEST
            buildType = "debug"
            flavors = listOf("one" to "flavor1")
        }

        expect {
            name = "flavor1DebugAndroidTest"
            baseName = "flavor1-debug-androidTest"
            fullNameWithSplit = "flavor1SplitDebugAndroidTest"
        }
    }

    @Test
    fun `androidTest with no build-types and one flavor`() {
        given {
            variantType = VariantTypeImpl.ANDROID_TEST
            buildType = null
            flavors = listOf("one" to "flavor1")
        }

        expect {
            name = "flavor1AndroidTest"
            baseName = "flavor1-androidTest"
            fullNameWithSplit = "flavor1SplitAndroidTest"
        }
    }

    @Test
    fun `androidTest with no build-types and no flavors`() {
        given {
            variantType = VariantTypeImpl.ANDROID_TEST
            buildType = null
        }

        expect {
            name = "androidTest"
            baseName = "androidTest"
            fullNameWithSplit = "splitAndroidTest"
        }
    }

    @Test
    fun `unitTest with build-type but not flavors`() {
        given {
            variantType = VariantTypeImpl.UNIT_TEST
            buildType = "debug"
        }

        expect {
            name = "debugUnitTest"
            baseName = "debug-test"
            fullNameWithSplit = "splitDebugUnitTest"
        }
    }

    @Test
    fun `unitTest with build-type and one flavor`() {
        given {
            variantType = VariantTypeImpl.UNIT_TEST
            buildType = "debug"
            flavors = listOf("one" to "flavor1")
        }

        expect {
            name = "flavor1DebugUnitTest"
            baseName = "flavor1-debug-test"
            fullNameWithSplit = "flavor1SplitDebugUnitTest"
        }
    }

    @Test
    fun `unitTest with no build-types and one flavor`() {
        given {
            variantType = VariantTypeImpl.UNIT_TEST
            buildType = null
            flavors = listOf("one" to "flavor1")
        }

        expect {
            name = "flavor1UnitTest"
            baseName = "flavor1-test"
            fullNameWithSplit = "flavor1SplitUnitTest"
        }
    }

    @Test
    fun `unitTest with no build-types and no flavors`() {
        given {
            variantType = VariantTypeImpl.UNIT_TEST
            buildType = null
        }

        expect {
            name = "test"
            baseName = "test"
            fullNameWithSplit = "splitUnitTest"
        }
    }



    // ---------------------------------------------------------------------------------------------

    override fun instantiateGiven() = GivenBuilder()
    override fun instantiateResult() = ResultBuilder()

    override fun defaultWhen(given: GivenBuilder): ResultBuilder {
        val varCombo = DimensionCombinationImpl(given.buildType, given.flavors)
        var flavorName: String = ""

        return ResultBuilder().also {
            it.name = VariantDslInfoBuilder.computeName(varCombo, given.variantType) {
                flavorName = it
            }
            it.baseName = VariantDslInfoBuilder.computeBaseName(varCombo, given.variantType)
            it.fullNameWithSplit = VariantDslInfoBuilder.computeFullNameWithSplits(
                ComponentIdentityImpl(
                    it.name,
                    flavorName,
                    given.buildType,
                    given.flavors
                ),
                given.variantType,"split"
            )
        }

    }

    fun variant(action: GivenBuilder.() -> Unit): GivenBuilder = GivenBuilder().also { action(it) }

    class GivenBuilder {
        var variantType: VariantType = VariantTypeImpl.BASE_APK
        var buildType: String? = "debug"
        var flavors: List<Pair<String, String>> = listOf()
    }

    data class ResultBuilder(
        var name: String = "",
        var baseName: String = "",
        var fullNameWithSplit: String = ""
    )
}
