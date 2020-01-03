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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.TemporaryProjectModification.doTest
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.android.build.gradle.internal.tasks.StripDebugSymbolsTask
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [StripDebugSymbolsTask]
 */
class StripDebugSymbolsTaskTest {

    @get:Rule
    val project =
        GradleTestProject.builder().fromTestApp(MinimalSubProject.app("com.example.app")).create()

    /** Regression test for https://issuetracker.google.com/issues/130901899 */
    @Test
    fun testNoNdkLogsIfNoInputs() {
        project.executor().run("stripDebugDebugSymbols").stdout.use {scanner ->
            assertThat(scanner).doesNotContain("ANDROID_NDK_HOME")
        }
        doTest(project) {
            it.addFile("src/main/jniLibs/x86/foo.so", "foo")
            project.executor().run("stripDebugDebugSymbols").stdout.use { scanner ->
                assertThat(scanner).contains("ANDROID_NDK_HOME")
            }
        }
    }

    @Test
    fun testSingleStripDebugSymbolsWarning() {
        val expectedWarning =
            "Unable to strip the following libraries, packaging them as they are: bar.so, foo.so."
        doTest(project) {
            it.addFile("src/main/jniLibs/x86/foo.so", "foo")
            it.addFile("src/main/jniLibs/x86/bar.so", "bar")
            it.addFile("src/main/jniLibs/x86_64/foo.so", "foo")
            it.addFile("src/main/jniLibs/x86_64/bar.so", "bar")
            project.executor().run("stripDebugDebugSymbols").stdout.use { scanner ->
                assertThat(scanner).contains(expectedWarning)
                assertThat(scanner).doesNotContain("packaging it as is")
                assertThat(scanner).doesNotContain("Packaging it as is")
            }
        }
    }

    @Test
    fun testTaskSkippedWhenNoNativeLibs() {
        // first test that the task is skipped when there are no native libraries.
        val result1 = project.executor().run(":stripDebugDebugSymbols")
        assertThat(result1.skippedTasks).containsAtLeastElementsIn(
            listOf(":stripDebugDebugSymbols")
        )
        // then test that the task does work if we add a native library.
        doTest(project) {
            it.addFile("src/main/jniLibs/x86/foo.so", "foo")
            val result2 = project.executor().run(":stripDebugDebugSymbols")
            assertThat(result2.didWorkTasks).containsAtLeastElementsIn(
                listOf(":stripDebugDebugSymbols")
            )
            // then test that the task is up-to-date if nothing changes.
            val result3 = project.executor().run(":stripDebugDebugSymbols")
            assertThat(result3.upToDateTasks).containsAtLeastElementsIn(
                listOf(":stripDebugDebugSymbols")
            )
        }
        // then test that the task does work after the native library is removed (since it must be
        // removed from the task's output dir).
        val result4 = project.executor().run(":stripDebugDebugSymbols")
        assertThat(result4.didWorkTasks).containsAtLeastElementsIn(
            listOf(":stripDebugDebugSymbols")
        )
        // finally test that the task is skipped if we build again with no native libraries.
        val result5 = project.executor().run(":stripDebugDebugSymbols")
        assertThat(result5.skippedTasks).containsAtLeastElementsIn(
            listOf(":stripDebugDebugSymbols")
        )
    }
}
