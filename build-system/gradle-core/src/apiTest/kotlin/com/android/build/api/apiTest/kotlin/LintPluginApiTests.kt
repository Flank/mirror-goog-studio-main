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

package com.android.build.api.apiTest.kotlin

import com.android.build.api.apiTest.VariantApiBaseTest
import com.android.build.api.artifact.Artifact
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.tools.apk.analyzer.AaptInvoker
import com.android.tools.apk.analyzer.ApkAnalyzerImpl
import com.google.common.truth.Truth
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Test
import org.mockito.Mockito
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.util.Locale
import kotlin.test.assertNotNull

class LintPluginApiTests: VariantApiBaseTest(TestType.Script, ScriptingLanguage.Kotlin) {
    @Test
    fun lintOptionsCustomization() {
        given {
            addModule(":module") {
                buildFile =
                        // language=kotlin
                    """
            plugins {
                    kotlin("jvm")
                    id("com.android.lint")
            }

            lintLifecycle {
                finalizeDsl { lint -> lint.enable.plusAssign("StopShip") }
            }
            """.trimIndent()
                addSource("src/main/kotlin/com/example/foo/SomeClass.kt", """
                package com.example.foo

                class SomeClass {
                    // STOPSHIP
                    val foo = System.currentTimeMillis()
                }
                """.trimIndent())
            }
            addModule(":app") {
                buildFile =
                        // language=kotlin
                    """
            plugins {
                    id("com.android.application")
                    kotlin("android")
                    kotlin("android.extensions")
            }
            android {
                    ${testingElements.addCommonAndroidBuildLogic()}
            }
            dependencies {
                api(project(":module"))
            }
            """.trimIndent()
                testingElements.addManifest(this)
                addSource(
                    "src/main/kotlin/com/android/build/example/minimal/MainActivity.kt",
                    //language=kotlin
                    """
                    package com.android.build.example.minimal

                    import android.app.Activity

                    class MainActivity : Activity() {
                    }
                    """.trimIndent())
            }
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
        }
    }
}
