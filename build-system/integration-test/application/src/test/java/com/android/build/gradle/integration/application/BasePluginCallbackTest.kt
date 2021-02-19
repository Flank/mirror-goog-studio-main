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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import org.junit.Rule
import org.junit.Test

class BasePluginCallbackTest {

    @JvmField
    @Rule
    val project =
            GradleTestProjectBuilder().fromTestApp(MinimalSubProject.app("com.example")).create()

    @Test
    fun testCallback() {
        val currentBuildFile = project.buildFile.readText()
        project.buildFile.writeText("""
            pluginManager.withPlugin("com.android.base") {
                if (extensions.findByName("android") == null) {
                    throw new RuntimeException("Extension is not initialized yet.")
                }
            }

            $currentBuildFile
        """.trimIndent())

        project.executor().run("help")
    }
}
