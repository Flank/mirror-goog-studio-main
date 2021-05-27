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

package com.android.build.api.apiTest.buildsrc

import com.android.build.api.apiTest.VariantApiBaseTest
import com.google.common.truth.Truth
import org.junit.Test
import kotlin.test.assertNotNull

class VariantFilterTest:  VariantApiBaseTest(
    TestType.BuildSrc
) {
    @Test
    fun testVariantFilteringOnBuildType() {
        given {
            addBuildSrc {
                addSource("src/main/kotlin/CustomPlugin.kt",
                    // language=kotlin
                    """
                        import com.android.build.api.variant.ApplicationAndroidComponentsExtension
                        import com.android.build.api.variant.LibraryAndroidComponentsExtension
                        import com.android.build.gradle.AppPlugin
                        import com.android.build.gradle.LibraryPlugin
                        import org.gradle.api.Plugin
                        import org.gradle.api.Project

                        class CustomPlugin: Plugin<Project> {
                            override fun apply(project: Project) {
                                project.plugins.withType(AppPlugin::class.java) {
                                    val extension = project.extensions.getByName("androidComponents") as ApplicationAndroidComponentsExtension
                                    extension.beforeVariants {
                                        // disable all unit tests for apps (only using instrumentation tests)
                                        it.enableUnitTest = false
                                    }
                                }
                                project.plugins.withType(LibraryPlugin::class.java) {
                                    val extension = project.extensions.getByName("androidComponents") as LibraryAndroidComponentsExtension
                                    extension.beforeVariants(extension.selector().withBuildType("debug")) {
                                        // Disable instrumentation for debug
                                        it.enableAndroidTest = false
                                    }
                                    extension.beforeVariants(extension.selector().withBuildType("release")) {
                                        // disable all unit tests for apps (only using instrumentation tests)
                                        it.enableUnitTest = false
                                    }
                                }
                            }
                        }
                    """.trimIndent())
                buildFile =
                    """
                    dependencies {
                        implementation("com.android.tools.build:gradle:$agpVersion")
                    }
                    """.trimIndent()
            }
            addModule(":app") {
                buildFile =
                    """
                    plugins {
                            id("com.android.application")
                            kotlin("android")
                            kotlin("android.extensions")
                    }

                    apply<CustomPlugin>()

                    android { ${testingElements.addCommonAndroidBuildLogic()}
                    }
                    """.trimIndent()
                testingElements.addManifest(this)
                testingElements.addMainActivity(this)
                addSource("src/test/java/ExampleUnitTest.kt",
                """
                    import org.junit.Test

                    import org.junit.Assert.*

                    /**
                     * Example local unit test, which will execute on the development machine (host).
                     *
                     * See [testing documentation](http://d.android.com/tools/testing).
                     */
                    class ExampleUnitTest {
                        @Test
                        fun addition_isCorrect() {
                            assertEquals(4, 2 + 2)
                        }
                    }
                """.trimIndent())
            }
            addSource("src/androidTest/kotlin/ExampleInstrumentedTest.kt",
                """
                import androidx.test.platform.app.InstrumentationRegistry
                import androidx.test.ext.junit.runners.AndroidJUnit4

                import org.junit.Test
                import org.junit.runner.RunWith

                import org.junit.Assert.*

                /**
                 * Instrumented test, which will execute on an Android device.
                 *
                 * See [testing documentation](http://d.android.com/tools/testing).
                 */
                @RunWith(AndroidJUnit4::class)
                class ExampleInstrumentedTest {
                    @Test
                    fun useAppContext() {
                        // Context of the app under test.
                        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
                        assertEquals("com.example.appandlib", appContext.packageName)
                    }
                }
                """.trimIndent())
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
        }
    }
}
