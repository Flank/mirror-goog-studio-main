/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.utils.PathUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Tests generation of unit test configuration after relocating build dir
 */
class BuildDirRelocationTest {
    @get:Rule
    var project: GradleTestProject = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.library"))
        .addGradleProperties("${BooleanOption.USE_ANDROID_X.propertyName}=true")
        .addGradleProperties("org.gradle.caching=true")
        .addGradleProperties("${BooleanOption.USE_RELATIVE_PATH_IN_TEST_CONFIG.propertyName}=true")
        .create()

    // Regression test for b/146922959
    @Test
    fun checkUnitTestConfigAfterRelocation() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
            android.testOptions.unitTests.includeAndroidResources = true
            dependencies {
                testImplementation 'junit:junit:4.12'
                testImplementation 'androidx.test:core:1.2.0'
            }
            """.trimIndent()
        )

        //Generate the test config and runs the tests.
        project.execute("testDebugUnitTest")

        // Change the build dir to foo/bar
        TestFileUtils.appendToFile(
            project.buildFile,
            "buildDir = \"foo/bar\""
        )

        PathUtils.deleteRecursivelyIfExists(project.buildDir.toPath())

        /*
         * Re-generate the test config artifact
         * ./foo/bar/intermediates/unit_test_config_directory/debugUnitTest/out/com/android/tools/test_config.properties
         */
        project.execute("generateDebugUnitTestConfig")

        val testConfigFile = project.file("foo/bar/intermediates/unit_test_config_directory/debugUnitTest/out/com/android/tools/test_config.properties")
            .readText()

        /*
         * Verify that test config was re-generated after changing build dir location
         * and not fetched from cache
         */
        assertThat(testConfigFile).doesNotContain("build/")
    }

}
