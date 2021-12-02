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

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.generators.ResValueGenerator
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GenerateResValuesIntegrationTest {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(
            MultiModuleTestProject.builder()
                .subproject(
                    MinimalSubProject.app().withFile(
                        "src/main/res/values/strings.xml",
                        """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <!-- The below string is a used to demonstrate resource merging. -->
                    <string name="some_string" translatable="false">hello world</string>
                </resources>""".trimIndent()
                    )
                )
                .build()
        )
        .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
        .create()

    @Before
    fun setup() {
        TestFileUtils.appendToFile(
            project.getSubproject(":app").buildFile,
            //language=groovy
            """
                android {
                    def vMajor = 0
                    def vMinor = 1
                    defaultConfig {
                        versionCode vMajor+vMinor
                        versionName vMajor+"."+vMinor
                        resValue "string", "app_name", "TEST APP " + versionName + " (" + versionCode + ")"
                    }
                }
            """.trimIndent()
        )
    }

    @Test
    fun testResValueIsUpdatedOnCleanAndIncrementalReleaseBuilds() {
        val app = project.getSubproject(":app")
        val executor = project.executor()
        executor.run("clean", "assembleRelease")

        val generatedResValueReleaseXml =
            FileUtils.join(
                app.generatedDir,
                SdkConstants.RES_FOLDER,
                "resValues",
                "release",
                SdkConstants.FD_RES_VALUES,
                ResValueGenerator.RES_VALUE_FILENAME_XML
            )
        assertThat(generatedResValueReleaseXml).contentWithUnixLineSeparatorsIsExactly(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>

                    <!-- Automatically generated file. DO NOT MODIFY -->

                    <!-- Value from default config. -->
                    <string name="app_name" translatable="false">TEST APP 0.1 (1)</string>

                </resources>""".trimIndent()
        )

        val incrementalMergedFile = app.getIntermediateFile(
            "incremental", "release", "mergeReleaseResources", "merged.dir", "values", "values.xml"
        )
        assertThat(incrementalMergedFile).contentWithUnixLineSeparatorsIsExactly(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="app_name" translatable="false">TEST APP 0.1 (1)</string>
                    <string name="some_string" translatable="false">hello world</string>
                </resources>""".trimIndent()
        )

        // Make a modification to resValues.
        TestFileUtils.searchAndReplace(
            app.buildFile,
            "def vMajor = 0",
            "def vMajor = 1"
        )

        // Run incremental build.
        val incrementalReleaseBuild = executor.run("assembleRelease")

        assertThat(incrementalReleaseBuild.upToDateTasks)
            .doesNotContain(":app:mergeReleaseResources")
        assertThat(incrementalReleaseBuild.didWorkTasks)
            .contains(":app:mergeReleaseResources")

        // Check generated resValues and merged.dir values.xml have been updated.
        assertThat(generatedResValueReleaseXml).contentWithUnixLineSeparatorsIsExactly(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>

                    <!-- Automatically generated file. DO NOT MODIFY -->

                    <!-- Value from default config. -->
                    <string name="app_name" translatable="false">TEST APP 1.1 (2)</string>

                </resources>""".trimIndent()
        )
        assertThat(incrementalMergedFile).contentWithUnixLineSeparatorsIsExactly(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="app_name" translatable="false">TEST APP 1.1 (2)</string>
                    <string name="some_string" translatable="false">hello world</string>
                </resources>""".trimIndent()
        )
    }

    @Test
    fun testMergeResourcesUpToDateWhenNoResValueChange() {
        val executor = project.executor()
        executor.run("clean", "assembleRelease")
        val secondBuild = executor.run("assembleRelease")
        assertThat(secondBuild.upToDateTasks).contains(":app:mergeReleaseResources")
    }
}
