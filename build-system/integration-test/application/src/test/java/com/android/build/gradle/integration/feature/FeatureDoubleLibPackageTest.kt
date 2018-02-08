/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.integration.feature

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FeatureDoubleLibPackageTest {
    @JvmField @Rule
    var project : GradleTestProject =
            GradleTestProject.builder()
                .fromTestProject("multiFeature")
                .withoutNdk()
                .create()

    @Before
    @Throws(Exception::class)
    fun setUp() {
        TestFileUtils.appendToFile(project.getSettingsFile(), "include 'libfeat'\n")
        TestFileUtils.appendToFile(
                project.getSubproject("featurea").getBuildFile(),
                "dependencies {\n" + "    implementation project(':libfeat')\n" + "}\n")
        TestFileUtils.appendToFile(
                project.getSubproject("featureb").getBuildFile(),
                "dependencies {\n" + "    implementation project(':libfeat')\n" + "}\n")
    }

    @Test
    fun checkLibDeps() {
        val result =
                project
                    .executor()
                    .withEnabledAapt2(true)
                    .expectFailure()
                    .run(":instant:checkDebugLibraries")

        assertThat(result.failureMessage).contains(
                "Features [:featurea, :featureb] all package the same library [:libfeat::debug].")
    }
}