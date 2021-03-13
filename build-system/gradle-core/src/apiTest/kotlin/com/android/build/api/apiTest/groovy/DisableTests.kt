/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.api.apiTest.groovy

import com.android.build.api.apiTest.VariantApiBaseTest
import com.google.common.truth.Truth
import org.junit.Test
import kotlin.test.assertNotNull

class DisableTests: VariantApiBaseTest(TestType.Script, ScriptingLanguage.Groovy) {
    @Test
    fun disableUnitTest() {
        given {
            tasksToInvoke.add("tasks")

            addModule(":app") {
                buildFile = """
            plugins {
                id 'com.android.application'
            }

            android {
                ${testingElements.addCommonAndroidBuildLogic()}
            }

            androidComponents {
                beforeVariants(selector().all(), { variantBuilder ->
                    variantBuilder.enableUnitTest = false
                })
                onVariants(selector().withName("debug"), { variant ->
                    if (variant.unitTest != null) {
                        throw new RuntimeException("UnitTest is active while it was deactivated")
                    }
                    if (variant.androidTest == null) {
                        throw new RuntimeException("AndroidTest is not active, it should be")
                    }
                })
            }
                """.trimIndent()

                testingElements.addManifest(this)
            }
        }
        withDocs {
            index =
                    // language=markdown
                """
# Test get operation

This sample shows how to use the get operation, which provides the final version of the artifact.
It shows the location of the apk for the all variants.

## To Run
./gradlew debugDisplayApks
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
        }
    }

    @Test
    fun disableAndroidTest() {
        given {
            tasksToInvoke.add("tasks")

            addModule(":app") {
                buildFile = """
            plugins {
                id 'com.android.application'
            }

            android {
                ${testingElements.addCommonAndroidBuildLogic()}
            }

            androidComponents {
                beforeVariants(selector().withName("debug"), { variantBuilder ->
                    variantBuilder.enableAndroidTest = false
                })
                onVariants(selector().withName("debug"), { variant ->
                    if (variant.unitTest == null) {
                        throw new RuntimeException("Unit test is not active, it should be")
                    }
                    if (variant.androidTest != null) {
                        throw new RuntimeException("AndroidTest is active while it was deactivated")
                    }
                })
            }
                """.trimIndent()

                testingElements.addManifest(this)
            }
        }
        withDocs {
            index =
                    // language=markdown
                """
# Test get operation

This sample shows how to use the get operation, which provides the final version of the artifact.
It shows the location of the apk for the all variants.

## To Run
./gradlew debugDisplayApks
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
        }
    }
}

