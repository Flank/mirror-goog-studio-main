/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import org.junit.Rule
import org.junit.Test

class SourceSetFilteringTest {
    @JvmField
    @Rule
    var project = builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .create()

    /** Regression test for b/155215177. */
    @Test
    fun testDynamicFilteringIsApplied() {
        project.mainSrcDir.resolve("test/DoesNotCompile.java").also {
            it.parentFile.mkdirs()
            it.writeText("""this is not a valid source file""")
        }
        project.mainSrcDir.resolve("test/Another.java")
            .writeText("""this is not a valid source file""")
        project.buildFile.appendText(
            """

            android.sourceSets.main.java {
              exclude { f ->
                f.file.path.endsWith("DoesNotCompile.java")
              }
              exclude "**/Another.java"
            }
        """.trimIndent()
        )

        project.executor().run("assembleDebug")
    }
}