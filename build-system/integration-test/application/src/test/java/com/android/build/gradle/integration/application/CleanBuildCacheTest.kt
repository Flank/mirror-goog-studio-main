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

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.app.EmptyActivityProjectBuilder
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.options.StringOption
import com.android.build.gradle.tasks.CleanBuildCache
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Integration test for [CleanBuildCache].  */
class CleanBuildCacheTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    @get:Rule
    val project = EmptyActivityProjectBuilder().addAndroidLibrary().build()

    @Test
    fun test() {
        val buildCacheDir = tmpDir.root
        File(buildCacheDir, "some_cache_contents").createNewFile()

        val result = project.executor().with(StringOption.BUILD_CACHE_DIR, buildCacheDir.path)
            .run("cleanBuildCache")

        assertThat(buildCacheDir).doesNotExist()
        result.stdout.use {
            ScannerSubject.assertThat(it).contains(
                "The Android Gradle plugin's build cache has been deprecated."
            )
        }
    }

    /** Regression test for http://b/158468794. */
    @Test
    fun testConfigureOnDemand() {
        // force-configure all tasks
        project.buildFile.appendText("\n" +
            """
            tasks.register("mytask") {
                project.rootProject.subprojects { project.rootProject.evaluationDependsOn(it.path) }
            }
            """.trimIndent()
        )
        // running "tasks" is incompatible
        project.executor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
            .withArgument("--configure-on-demand")
            .run(":tasks")
    }
}
