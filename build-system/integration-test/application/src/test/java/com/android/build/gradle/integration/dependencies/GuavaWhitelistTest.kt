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

package com.android.build.gradle.integration.dependencies

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GuavaWhitelistTest {

    @get:Rule
    var project = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .create()

    @Before
    @Throws(Exception::class)
    fun setUp() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                dependencies {
                    implementation "com.google.guava:listenablefuture:1.0"
                    androidTestImplementation "com.google.guava:guava:27.0.1-android"
                }
            """.trimIndent()
        )
    }

    @Test
    @Throws(Exception::class)
    fun `check AndroidTest does not fail dependency resolution`() {
        project.execute("assembleDebugAndroidTest")
    }
}