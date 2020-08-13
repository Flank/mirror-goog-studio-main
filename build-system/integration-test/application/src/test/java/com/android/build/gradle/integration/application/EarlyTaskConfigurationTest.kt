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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.app.EmptyActivityProjectBuilder
import com.android.build.gradle.integration.common.utils.TestFileUtils
import org.junit.Rule
import org.junit.Test

/**
 * Tests that the build still succeeds when tasks are configured early. (Even though we expect tasks
 * to be configured lazily, users' build script or plugins may cause them to be configured early, as
 * in bug 139821728.)
 */
class EarlyTaskConfigurationTest {

    @get:Rule
    var project = EmptyActivityProjectBuilder().also { it.withUnitTest = true }.build()

    @Test // Regression test for bug 139821728
    fun `check that build succeeds when tasks are configured early`() {
        // Force tasks to be configured early
        TestFileUtils.appendToFile(
            project.buildFile,
            """
            allprojects {
                tasks.all { }
            }
            """.trimIndent()
        )

        // Check that the build succeeds
        project.executor().run("clean", "assembleDebug", "testDebugUnitTest")
    }
}
