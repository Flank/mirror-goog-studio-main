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
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [StripDebugSymbolsTaskTest]
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
}
