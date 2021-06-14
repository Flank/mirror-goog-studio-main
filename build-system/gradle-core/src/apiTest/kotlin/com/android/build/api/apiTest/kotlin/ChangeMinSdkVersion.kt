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

package com.android.build.api.apiTest.kotlin

import com.android.build.api.apiTest.VariantApiBaseTest
import com.android.build.gradle.options.BooleanOption
import com.android.tools.build.gradle.internal.profile.VariantMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildProfile
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertNotNull

class ChangeMinSdkVersion: VariantApiBaseTest(TestType.Script) {
    @Test
    fun changeMinSdkVersion() {
        given {
            tasksToInvoke.add(":app:assembleRelease")
            addModule(":app") {
                @Suppress("RemoveExplicitTypeArguments")
                buildFile =
                        // language=kotlin
                    """
            plugins {
                    id("com.android.application")
                    kotlin("android")
                    kotlin("android.extensions")
            }

            import com.android.build.api.variant.AndroidVersion

            android {
                ${testingElements.addCommonAndroidBuildLogic()}
            }
            androidComponents {
                beforeVariants(selector().withName("release")) { variantBuilder ->
                    variantBuilder.minSdk = 23
                }
            }
        """.trimIndent()
                testingElements.addManifest( this)
            }
        }
        withOptions(mapOf(BooleanOption.ENABLE_PROFILE_JSON to true))
        withDocs {
            index =
                    // language=markdown
                """
# Changing the minimum SDK version in Kotlin

This sample show how to change the minSdkVersion for a particular variant. Because the min SDK
version will impact the build flow, in particular how dexing is performed, it must be provided at
configuration time.

Changing the minSdkVersion through the beforeVariants API is not as straightforward as changing it in
the DSL directly and should only be done when a lot of build flavors and/or build types yield
multiple variants.

## To Run
./gradlew assembleRelease
expected result : An APK with minSdkVersion of 23
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
            onVariantStats { variant ->
                // check that debug variant minSdkVersion was unchanged and check that release
                // variant minSdkVersion was changed and the change event was recorded.
                if (variant.isDebug) {
                    Truth.assertThat(variant.minSdkVersion.apiLevel).isEqualTo(21)
                } else {
                    Truth.assertThat(variant.minSdkVersion.apiLevel).isEqualTo(23)
                    variant.variantApiAccess.variantAccessList.forEach {
                        println("op " + it.type)
                    }
                    val variantAccessList = variant.variantApiAccess.variantAccessList
                    Truth.assertThat(variantAccessList.size).isAtLeast(1)
                    // make sure our minSdkVersion reset has been recorded.
                    Truth.assertThat(variantAccessList.first().type).isEqualTo(
                        VariantMethodType.MIN_SDK_VERSION_VALUE_VALUE
                    )
                }
            }
        }
    }
}
