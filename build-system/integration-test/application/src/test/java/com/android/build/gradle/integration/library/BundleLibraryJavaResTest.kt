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
import com.android.build.gradle.integration.common.fixture.TemporaryProjectModification.doTest
import com.android.build.gradle.integration.common.fixture.app.HelloWorldLibraryApp
import com.android.build.gradle.internal.tasks.BundleLibraryJavaRes
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/** Tests for [BundleLibraryJavaRes]. */
class BundleLibraryJavaResTest {

    @JvmField
    @Rule
    val project = GradleTestProject.builder().fromTestApp(HelloWorldLibraryApp.create()).create()

    @Test
    fun testTaskSkippedWhenNoJavaRes() {
        // first test that the task is skipped when there are no java resources.
        val result1 = project.executor().run(":lib:bundleLibResDebug")
        assertThat(result1.skippedTasks).containsAtLeastElementsIn(
            listOf(":lib:bundleLibResDebug")
        )
        // then test that the task does work if we add a java resource.
        doTest(project) {
            it.addFile("lib/src/main/resources/foo.txt", "foo")
            val result2 = project.executor().run(":lib:bundleLibResDebug")
            assertThat(result2.didWorkTasks).containsAtLeastElementsIn(
                listOf(":lib:bundleLibResDebug")
            )
            // then test that the task is up-to-date if nothing changes.
            val result3 = project.executor().run(":lib:bundleLibResDebug")
            assertThat(result3.upToDateTasks).containsAtLeastElementsIn(
                listOf(":lib:bundleLibResDebug")
            )
        }
        // then test that the task does work after the java resource is removed (since it must be
        // removed from the task's output).
        val result4 = project.executor().run(":lib:bundleLibResDebug")
        assertThat(result4.didWorkTasks).containsAtLeastElementsIn(
            listOf(":lib:bundleLibResDebug")
        )
        // finally test that the task is skipped if we build again with no java resources.
        val result5 = project.executor().run(":lib:bundleLibResDebug")
        assertThat(result5.skippedTasks).containsAtLeastElementsIn(
            listOf(":lib:bundleLibResDebug")
        )
    }
}
