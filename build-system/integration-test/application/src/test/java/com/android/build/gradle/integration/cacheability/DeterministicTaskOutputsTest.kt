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

package com.android.build.gradle.integration.cacheability

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.EmptyActivityProjectBuilder
import com.android.testutils.FileSnapshot
import com.google.common.truth.Expect
import org.junit.Rule
import org.junit.Test

/**
 * Test to ensure that the outputs of tasks are deterministic (and relocatable).
 */
class DeterministicTaskOutputsTest {

    companion object {

        /**
         * List of task outputs are currently either not deterministic or not relocatable (e.g.,
         * containing absolute paths).
         *
         * The goal is to reduce the size of list (tracked at bug 134654165).
         */
        private val INCONSISTENT_TASK_OUTPUTS = setOf(
            // The following task outputs need more investigation.
            "app/build/intermediates/blame/res/debug",
            "app/build/intermediates/incremental/debug-mergeJavaRes/merge-state",
            "app/build/intermediates/incremental/debug-mergeNativeLibs",
            "app/build/intermediates/incremental/mergeDebugAssets/merger.xml",
            "app/build/intermediates/incremental/mergeDebugJniLibFolders",
            "app/build/intermediates/incremental/mergeDebugResources",
            "app/build/intermediates/incremental/packageDebugResources",
            "app/build/intermediates/incremental/mergeDebugShaders",
            "app/build/intermediates/incremental/packageDebug/tmp/debug/dex-renamer-state.txt",
            "app/build/intermediates/incremental/packageDebug/tmp/debug/zip-cache",
            "app/build/intermediates/manifest_merge_blame_file/debug/manifest-merger-blame-debug-report.txt",
            "app/build/intermediates/merged_java_res/debug/out.jar",
            "app/build/intermediates/res/merged/debug",
            "app/build/outputs/apk/debug/app-debug.apk",
            "app/build/outputs/logs/manifest-merger-debug-report.txt",

            // Test reports contain timestamps, which make them different across builds.
            "app/build/reports/tests/testDebugUnitTest",
            "app/build/test-results/testDebugUnitTest/",

            // This is @LocalState for the dexing task
            "app/build/intermediates/dex_archive_input_jar_hashes/debug/out"
        )
    }

    @get:Rule
    var project1 = setUpTestProject("project1")

    @get:Rule
    var project2 = setUpTestProject("project2")

    private fun setUpTestProject(projectName: String): GradleTestProject {
        return with(EmptyActivityProjectBuilder()) {
            this.projectName = projectName
            build()
        }
    }

    @get:Rule
    val expect: Expect = Expect.create()

    @Test
    fun `check consistent outputs after building two identical projects`() {
        // Build the first project
        project1.executor().run("clean", "assembleDebug", "testDebugUnitTest")
        val snapshot1 = FileSnapshot.snapshot(
            fileToSnapshot = project1.getSubproject("app").buildDir,
            baseDir = project1.testDir
        )

        // Build the second project
        project2.executor().run("clean", "assembleDebug", "testDebugUnitTest")
        val snapshot2 = FileSnapshot.snapshot(
            fileToSnapshot = project2.getSubproject("app").buildDir,
            baseDir = project2.testDir
        )

        // Check that they have consistent outputs
        expect.that(snapshot1.directorySet).containsExactlyElementsIn(snapshot2.directorySet)
        for ((file, contents) in
        snapshot1.regularFileContentsMap.plus(snapshot2.regularFileContentsMap)) {
            if (INCONSISTENT_TASK_OUTPUTS.any { file.startsWith(it) }) {
                continue
            }

            val contents1 = snapshot1.regularFileContentsMap[file]
            val contents2 = snapshot2.regularFileContentsMap[file]
            if (contents1 == null) {
                expect.fail("${file.path} is not found in the first build")
            } else if (contents2 == null) {
                expect.fail("${file.path} is not found in the second build")
            } else {
                if (!contents.contentEquals(contents1)) {
                    expect.fail("${file.path} is not consistent across two builds")
                }
            }
        }
    }
}
