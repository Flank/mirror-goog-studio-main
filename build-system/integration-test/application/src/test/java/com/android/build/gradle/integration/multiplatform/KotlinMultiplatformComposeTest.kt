/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.integration.multiplatform

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProjectBuilder
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.build.gradle.internal.TaskManager.Companion.COMPOSE_KOTLIN_COMPILER_EXTENSION_VERSION
import com.android.build.gradle.options.BooleanOption
import org.junit.Rule
import org.junit.Test

/** Check Compose works with KMP projects. */
class KotlinMultiplatformComposeTest {

    @get:Rule
    val project = createGradleProjectBuilder {
        rootProject {
            plugins.add(PluginType.ANDROID_LIB)
            plugins.add(PluginType.KOTLIN_MPP)
            android {
                setUpHelloWorld()
                minSdk = 24
            }
            dependencies {
                implementation("androidx.compose.ui:ui-tooling:$COMPOSE_KOTLIN_COMPILER_EXTENSION_VERSION")
                implementation("androidx.compose.material:material:$COMPOSE_KOTLIN_COMPILER_EXTENSION_VERSION")
            }
            appendToBuildFile {
                """
                    kotlin {
                        android()
                    }
                    android {
                        buildFeatures {
                            compose true
                        }
                        composeOptions {
                            useLiveLiterals false
                        }
                        kotlinOptions {
                            freeCompilerArgs += [
                              "-P", "plugin:androidx.compose.compiler.plugins.kotlin:suppressKotlinVersionCompatibilityCheck=true",
                            ]
                        }
                    }
                """.trimIndent()
            }
            addFile(
                "src/main/kotlin/com/Example.kt", """
                package foo

                import androidx.compose.foundation.layout.Column
                import androidx.compose.material.Text
                import androidx.compose.runtime.Composable

                @Composable
                fun MainView() {
                    Column {
                        Text(text = "Hello World")
                    }
                }
            """.trimIndent()
            )
        }
    }
        .withKotlinGradlePlugin(true)
        .withKotlinVersion("1.7.0")
        .create()

    /** Regression test for b/203594737. */
    @Test
    fun testLibraryBuilds() {
        // Allow dependency resolution at configuration and disable configuration cache
        // https://youtrack.jetbrains.com/issue/KT-49933 and https://youtrack.jetbrains.com/issue/KT-51940
        project.executor()
            .with(BooleanOption.USE_ANDROID_X, true)
            .with(BooleanOption.DISALLOW_DEPENDENCY_RESOLUTION_AT_CONFIGURATION, false)
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
            .run("assembleDebug")
    }
}
