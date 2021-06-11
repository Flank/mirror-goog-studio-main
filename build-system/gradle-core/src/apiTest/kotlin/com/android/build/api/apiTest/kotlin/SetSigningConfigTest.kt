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
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.ArtifactAccess
import com.google.wireless.android.sdk.stats.VariantPropertiesAccess
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SetSigningConfigTest: VariantApiBaseTest(TestType.Script) {
    @Test
    fun setSigningConfig() {
        given {
            expectFailure()
            tasksToInvoke.addAll(listOf("clean", ":app:assembleFlavor1Special"))
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
            import org.gradle.api.DefaultTask
            import org.gradle.api.file.DirectoryProperty
            import org.gradle.api.tasks.InputFiles
            import org.gradle.api.tasks.TaskAction
            import com.android.build.api.variant.BuiltArtifactsLoader
            import com.android.build.api.artifact.SingleArtifact
            import org.gradle.api.provider.Property
            import org.gradle.api.tasks.Internal
            android {
                ${testingElements.addCommonAndroidBuildLogic()}
                signingConfigs {

                        create("default") {
                                keyAlias = "pretend"
                                keyPassword = "some password"
                                storeFile = file("/path/to/supposedly/existing/keystore.jks")
                                storePassword = "some keystore password"
                        }
                        create("other") {
                                keyAlias = "invalid"
                                keyPassword = "some password"
                                storeFile = file("/path/to/some/other/keystore.jks")
                                storePassword = "some keystore password"
                        }
                }
                flavorDimensions("version")
                    buildTypes {
                            create("special")
                    }
                    productFlavors {
                            create("flavor1") {
                                    dimension = "version"
                                    signingConfig = signingConfigs.getByName("default")
                            }
                            create("flavor2") {
                                    dimension = "version"
                                    signingConfig = signingConfigs.getByName("default")
                            }
                    }
            }

            androidComponents {
                onVariants(selector()
                        .withFlavor("version" to "flavor1")
                        .withBuildType("special")
                ) { variant ->
                        variant.signingConfig?.setConfig(android.signingConfigs.getByName("other"))
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
# artifacts.get in Kotlin
This sample shows how to reset the variant's [com.android.build.api.variant.SigningConfig] using one
 of the DSL [com.android.build.api.dsl.SigningConfig] named element present in the android's
[signingConfigs] block.

In this example, we define 2 signing configurations : default and other.
The 'default' configuration is the default signing configuration used for all the variants signing.
However, using the Variant API, the 'flavor1Special' variant will use the 'other' signing
configuration.

## To Run
./gradlew :app:assembleFlavor1Special
expected result : "Got an APK...." message.
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("Keystore file '/path/to/some/other/keystore.jks' not found for signing config 'other'")
            Truth.assertThat(output).contains("FAILURE: Build failed with an exception.")
            var assertedStats = false
            super.onVariantStats {
                if (it.variantApiAccess.variantPropertiesAccessCount == 4) {
                    assertedStats = true
                    val accessType = it.variantApiAccess.variantPropertiesAccessList[3].type
                    Truth.assertThat(accessType).isEqualTo(
                        VariantPropertiesMethodType.SIGNING_CONFIG_SET_CONFIG_VALUE
                    )
                }
            }
            assertTrue(assertedStats)
        }
    }
}
