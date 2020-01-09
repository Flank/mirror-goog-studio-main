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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantBuildArtifactsHolder
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Test for [BuildArtifactReportTask].
 */
class BuildArtifactReportTaskTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()
    private lateinit var project : Project
    private lateinit var artifactsHolder: BuildArtifactsHolder

    @Before
    fun setUp() {
        project = ProjectBuilder.builder().withProjectDir(temporaryFolder.newFolder()).build()
        artifactsHolder =
                VariantBuildArtifactsHolder(
                    project,
                    "debug",
                    project.file("root")
                )

        val output0 = project.objects.fileProperty()
        val output1 = project.objects.fileProperty()
        val task0 = project.tasks.register("task0")
        val task1 = project.tasks.register("task1")

        artifactsHolder.producesFile(
            InternalArtifactType.COMPILE_LIBRARY_CLASSES_JAR,
            task0,
            { output0 },
            "task0_output"
        )


        artifactsHolder.producesFile(
            InternalArtifactType.COMPILE_LIBRARY_CLASSES_JAR,
            task1,
            { output1 },
            "task1_output"
        )
    }

    @Test
    fun report() {
        val task = project.tasks.create("report", BuildArtifactReportTask::class.java)
        task.init(artifactsHolder::createReport)
        task.report()
    }

    @Ignore
    fun reportToFile() {
        val task = project.tasks.create("report", BuildArtifactReportTask::class.java)
        val outputFile = project.file("report.txt")
        task.init(artifactsHolder::createReport, outputFile)

        task.report()

        val report = BuildArtifactsHolder.parseReport(outputFile)
        val javacArtifacts = report[InternalArtifactType.COMPILE_LIBRARY_CLASSES_JAR.name()] ?: throw NullPointerException()
        assertThat(javacArtifacts.producers).hasSize(2)
        assertThat(javacArtifacts.producers[0].files[0]).endsWith("task0_output")
        assertThat(javacArtifacts.producers[0].builtBy).isEqualTo("task0")
        assertThat(javacArtifacts.producers[1].files[0]).endsWith("task1_output")
        assertThat(javacArtifacts.producers[1].builtBy).isEqualTo("task1")
    }

}