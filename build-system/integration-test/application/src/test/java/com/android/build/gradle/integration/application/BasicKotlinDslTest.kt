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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import org.junit.Rule
import org.junit.Test

/** Regression test for http://b/166468915. */
class BasicKotlinDslTest {

    @JvmField
    @Rule
    val project = GradleTestProject.builder().fromTestApp(MinimalSubProject.app("com.example.app"))
            .withPluginManagementBlock(true)
            .create()

    @Test
    fun testAbleToBuild() {
        project.buildFile.delete()

        project.file("build.gradle.kts").writeText("""
            apply(from = "../commonHeader.gradle")
            apply(from = "../commonLocalRepo.gradle")
            plugins {
                id("com.android.application")
            }

            android {
                compileSdkVersion(${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION})

                buildTypes {
                    debug {
                        isPseudoLocalesEnabled = true
                    }
                    release {
                        isMinifyEnabled = true
                    }
                }

            }
        """.trimIndent())

        // add at least one property, as that was triggering http://b/166468915
        project.file("gradle.properties").appendText("""
            android.debug.obsoleteApi=true
        """.trimIndent())

        project.executor().run("assembleDebug")
    }
}
