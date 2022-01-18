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

package com.android.build.gradle.integration.library

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldLibraryApp
import com.android.build.gradle.internal.tasks.BundleLibraryJavaRes
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.util.jar.JarFile

/** Tests for [BundleLibraryJavaRes]. */
class BundleLibraryJavaResTest {

    @JvmField
    @Rule
    val project = GradleTestProject.builder().fromTestApp(HelloWorldLibraryApp.create()).create()

    @Test
    fun testTaskSkippedWhenNoJavaRes() {
        // first test that the task is skipped when there are no java resources.
        project.executor().run(":lib:bundleLibResDebug").run {
            assertThat(this.skippedTasks).containsAtLeastElementsIn(
                listOf(":lib:bundleLibResDebug")
            )
        }

        project.projectDir.resolve("lib/src/main/resources").mkdirs()
        project.executor().run(":lib:bundleLibResDebug").run {
            assertThat(this.skippedTasks).containsAtLeastElementsIn(
                listOf(":lib:bundleLibResDebug")
            )
        }

        project.projectDir.resolve("lib/src/main/resources/foo.txt").createNewFile()
        project.executor().run(":lib:bundleLibResDebug").run {
            assertThat(this.didWorkTasks).containsAtLeastElementsIn(
                listOf(":lib:bundleLibResDebug")
            )
        }

        project.projectDir.resolve("lib/src/main/resources/test_dir").mkdirs()
        project.executor().run(":lib:bundleLibResDebug").run {
            assertThat(this.didWorkTasks).containsAtLeastElementsIn(
                    listOf(":lib:bundleLibResDebug", ":lib:processDebugJavaRes")
            )
        }
        // ensure test_dir empty directory is packaged
        val resJar =
            project.projectDir.resolve("lib/build/intermediates/library_java_res/debug/res.jar")
        JarFile(resJar).use { assertThat(it.getJarEntry("test_dir")).isNotNull() }

        // then test that the task is up-to-date if nothing changes.
        project.executor().run(":lib:bundleLibResDebug").run {
            assertThat(this.upToDateTasks).containsAtLeastElementsIn(
                listOf(":lib:bundleLibResDebug")
            )
        }

        // then test that the task does work after the java resource is removed (since it must be
        // removed from the task's output).
        project.projectDir.resolve("lib/src/main/resources").deleteRecursively()
        project.executor().run(":lib:bundleLibResDebug").run {
            assertThat(this.didWorkTasks).containsAtLeastElementsIn(
                listOf(":lib:bundleLibResDebug")
            )
        }

        // finally test that the task is skipped if we build again with no java resources.
        project.executor().run(":lib:bundleLibResDebug").run {
            assertThat(this.skippedTasks).containsAtLeastElementsIn(
                listOf(":lib:bundleLibResDebug")
            )
        }
    }
}
