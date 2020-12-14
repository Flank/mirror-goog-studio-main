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

package com.android.build.gradle.integration.bundle

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File

class DynamicAppPackageDependenciesTest {
    @Rule
    @JvmField
    val project = GradleTestProject.builder()
        .withGradleBuildCacheDirectory(File("local-build-cache"))
        .fromTestProject("dynamicApp").create()

    /** Regression test for http://b/150438232. */
    @Test
    fun testPackagedDependenciesCaching() {
        project.executor().withArgument("--build-cache").run("assembleDebug")

        project.executor().withArgument("--build-cache").run("clean")
        project.executor().withArgument("--build-cache").run("assembleDebug")

        val feature1Dependencies = project.getSubproject("feature1").getIntermediateFile(
            InternalArtifactType.PACKAGED_DEPENDENCIES.getFolderName(),
            "debug/deps.txt"
        )
        assertThat(feature1Dependencies).contains("feature1::debug")

        val feature2Dependencies = project.getSubproject("feature2").getIntermediateFile(
            InternalArtifactType.PACKAGED_DEPENDENCIES.getFolderName(),
            "debug/deps.txt"
        )
        assertThat(feature2Dependencies).contains("feature2::debug")
    }
}
