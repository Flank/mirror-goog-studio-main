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

package com.android.build.gradle.integration.databinding

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.FileSnapshot
import com.google.common.truth.Expect
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Test to ensure that the outputs of data binding are deterministic (and relocatable).
 */
class DataBindingDeterminismTest {

    companion object {

        /**
         * List of task outputs are currently either not deterministic or not relocatable (e.g.,
         * containing absolute paths).
         *
         * The goal is to reduce the size of list (tracked at bug 134654165).
         */
        private val INCONSISTENT_TASK_OUTPUTS = setOf(
            // The following are not specific to data binding but to the Android Gradle plugin (this
            // is a subset of DeterministicTaskOutputsTest.INCONSISTENT_TASK_OUTPUTS).
            "build/intermediates/incremental/mergeDebugResources",
            "build/intermediates/manifest_merge_blame_file/debug/manifest-merger-blame-debug-report.txt",
            "build/intermediates/merged_res_blame_folder/debug/out",
            "build/intermediates/merged_res/debug",
            "build/intermediates/source_set_path_map/debug/file-map.txt",
            "build/outputs/logs/manifest-merger-debug-report.txt",

            // The following are specific to data binding.
            // Bug 129276057
            "build/intermediates/data_binding_layout_info_type_merge/debug/out/activity_main-layout.xml",
            "build/reports/configuration-cache/"
        )
    }

    @get:Rule
    val project1 = GradleTestProject.builder().fromTestProject("databinding")
        .withName("project1").create()

    @get:Rule
    val project2 = GradleTestProject.builder().fromTestProject("databinding")
        .withName("project2").create()

    @Before
    fun setUp() {
        for (project in listOf(project1, project2)) {
            // Add some bindable expressions to try to replicate bug 131659806
            TestFileUtils.appendToFile(
                File("${project.mainSrcDir}/android/databinding/testapp/User1.java"),
                """
                package android.databinding.testapp;

                import android.databinding.BaseObservable;
                import android.databinding.Bindable;

                public class User1 extends BaseObservable {
                    private String field11 = "User 1 Field 1";
                    private String field12 = "User 1 Field 2";

                    @Bindable
                    public String getField11() {
                        return field11;
                    }

                    @Bindable
                    public String getField12() {
                        return field12;
                    }
                }
                """.trimIndent()
            )
            TestFileUtils.appendToFile(
                File("${project.mainSrcDir}/android/databinding/testapp/User2.java"),
                """
                package android.databinding.testapp;

                import android.databinding.BaseObservable;
                import android.databinding.Bindable;

                public class User2 extends BaseObservable {
                    private String field21 = "User 2 Field 1";
                    private String field22 = "User 2 Field 2";

                    @Bindable
                    public String getField21() {
                        return field21;
                    }

                    @Bindable
                    public String getField22() {
                        return field22;
                    }
                }
                """.trimIndent()
            )

            // Normally the APK name wouldn't include the project name. However, because the current
            // test project has only one module located at the root project (as opposed to residing
            // in a subdirectory under the root project), the APK name in this test does include the
            // project name, which would break relocatability. To fix that, we need to apply the
            // following workaround to use a generic name for the APK that is independent of the
            // project name.
            TestFileUtils.appendToFile(project.buildFile, "archivesBaseName = 'project'")
        }
    }

    @get:Rule
    val expect: Expect = Expect.create()

    @Test
    fun `check consistent outputs after building two identical projects`() {
        // Build the first project
        project1.executor().run("clean", "compileDebugJavaWithJavac")
        val snapshot1 = FileSnapshot.snapshot(
            fileToSnapshot = project1.buildDir,
            baseDir = project1.projectDir
        )

        // Build the second project
        project2.executor().run("clean", "compileDebugJavaWithJavac")
        val snapshot2 = FileSnapshot.snapshot(
            fileToSnapshot = project2.buildDir,
            baseDir = project2.projectDir
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
