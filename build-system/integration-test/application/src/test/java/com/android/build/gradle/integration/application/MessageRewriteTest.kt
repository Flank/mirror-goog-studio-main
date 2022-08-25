/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.build.gradle.integration.common.fixture.TemporaryProjectModification
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.options.BooleanOption
import com.android.utils.FileUtils
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.Scanner

/** Tests the error message rewriting logic.  */
class MessageRewriteTest {

    @get:Rule
    val project = GradleTestProject.builder().fromTestProject("flavoredlib").create()

    @Test
    fun invalidAppLayoutFile() {
        project.execute("assembleDebug")
        TemporaryProjectModification.doTest(project) {
            it.replaceInFile("app/src/main/res/layout/main.xml", "</LinearLayout>", "")
            val result = project.executor()
                .with(BooleanOption.IDE_INVOKED_FROM_IDE, true)
                .expectFailure()
                .run("assembleF1Debug")
            checkPathInOutput(
                    FileUtils.join("app", "src", "main", "res", "layout", "main.xml"),
                result.stdout)
        }
    }

    @Test
    fun nonExistentResourceReferenceInAppLayout() {
        TemporaryProjectModification.doTest(project) {
            it.replaceInFile("app/src/main/res/layout/main.xml","@string/app_string", "@string/agloe")
            val result = project.executor()
                .expectFailure()
                .run("assembleDebug")
            checkPathInOutput(
                    FileUtils.join("app", "src", "main", "res", "layout", "main.xml"), result.stderr)
        }
    }

    @Test
    fun nonExistentResourceReferenceInAppValues() {
        TemporaryProjectModification.doTest(project) {
            it.replaceInFile("app/src/main/res/values/strings.xml", "string", "")
            val result = project.executor()
                .expectFailure()
                .run("assembleDebug")
            checkPathInOutput(
                    FileUtils.join("app", "src", "main", "res", "values", "strings.xml"), result.stderr)
        }
    }

    @Test
    fun nonExistentResourceReferenceInLibLayout() {
        TemporaryProjectModification.doTest(project) {
            it.replaceInFile(
                "lib/src/flavor1/res/layout/lib_main.xml",
                "@string/lib_string",
                "@string/agloe"
            )
            val result = project.executor()
                .expectFailure()
                .run("assembleDebug")
            // b/206624424 - Errors in libraries currently (and incorrectly) rewrite as
            // the packaged res for full builds and merged intermediate filepaths for incremental
            // builds.
            checkPathInOutput(
                FileUtils.join(
                    "lib",
                    "build",
                    "intermediates",
                    "packaged_res",
                    "flavor1Debug",
                    "layout",
                    "lib_main.xml"
                ),
                result.stderr
            )
        }
    }

    private fun checkPathInOutput(path: String, output: Scanner) =
        output.use { out -> ScannerSubject.assertThat(out).contains(path) }
}
