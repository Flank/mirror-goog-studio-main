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

package com.android.build.gradle.integration.annotationprocessor

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.getOutputDir
import com.android.build.gradle.tasks.ANNOTATION_PROCESSOR_LIST_FILE_NAME
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

/** Integration test to ensure that plugin binary compatibility of AGP APIs is preserved. */
class KspJavaPreCompileTest {

    @get:Rule
    val project = GradleTestProject.builder().fromTestProject("kotlinAppWithKsp")
        .create()

    @Test
    fun kspJavaPreCompileTest() {
        val result = project.executor().run(":app:javaPreCompileDebug")

        val annotationProcessorList =
            InternalArtifactType.ANNOTATION_PROCESSOR_LIST.getOutputDir(project.getSubproject("app").buildDir)
                .resolve("debug/$ANNOTATION_PROCESSOR_LIST_FILE_NAME").readText()
        Truth.assertThat(annotationProcessorList).isEqualTo(
            "{\"mock-processor.jar (project :mock-processor)\":\"KSP_PROCESSOR\"}")
    }

}
