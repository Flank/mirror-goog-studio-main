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

package com.android.build.gradle.internal.api.dsl.options

import com.android.build.api.artifact.BuildArtifactTransformBuilder
import com.android.build.api.artifact.BuildArtifactType.JAVAC_CLASSES
import com.android.build.api.artifact.BuildArtifactType.JAVA_COMPILE_CLASSPATH
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.api.dsl.options.BuildArtifactsOptions
import com.android.build.gradle.internal.api.artifact.BuildArtifactTransformBuilderImpl
import com.android.build.gradle.internal.api.artifact.BuildableArtifactImpl
import com.android.build.gradle.internal.fixtures.FakeEvalIssueReporter
import com.android.build.gradle.internal.scope.BuildArtifactHolder
import com.android.testutils.truth.MoreTruth.assertThat
import com.google.common.truth.Truth.assertThat
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertFailsWith

/**
 * Tests for [BuildArtifactsOptions].
 */
class BuildArtifactsOptionsImplTest {
    open class TestTask : DefaultTask() {
        @InputFiles
        lateinit var inputFiles : BuildableArtifact

        @OutputFile
        lateinit var outputFile : File
    }

    private val issueReporter = FakeEvalIssueReporter(throwOnError = true)
    lateinit private var project : Project
    lateinit private var taskHolder : BuildArtifactHolder
    lateinit private var options : BuildArtifactsOptions
    lateinit private var task0 : Task

    @Before
    fun setUp() {
        project = ProjectBuilder().build()!!
        BuildableArtifactImpl.disableResolution()
        taskHolder =
                BuildArtifactHolder(
                        project,
                        "debug",
                        project.file("root"),
                        "debug",
                        listOf(JAVAC_CLASSES, JAVA_COMPILE_CLASSPATH),
                        issueReporter)
        options = BuildArtifactsOptionsImpl(project, taskHolder, issueReporter)
        task0 = project.tasks.create("task0")
    }

    @Test
    fun appendTo() {
        options.appendTo(JAVAC_CLASSES, "task1", TestTask::class.java) { input, output ->
            inputFiles = input
            outputFile = output
        }
        taskHolder.createFirstArtifactFiles(JAVAC_CLASSES, "foo", "task0")
        BuildableArtifactImpl.enableResolution()

        project.tasks.getByName("task1Debug") { t ->
            assertThat(t).isInstanceOf(TestTask::class.java)
            val task = t as TestTask
            assertThat(task.inputFiles.single()).hasName("foo")
            assertThat(task.outputFile).hasName(JAVAC_CLASSES.name)
            assertThat(task.inputFiles.buildDependencies.getDependencies(null))
                    .containsExactly(task0)
        }
        assertThat(taskHolder.getArtifactFiles(JAVAC_CLASSES).files.map(File::getName))
                .containsExactly("foo", JAVAC_CLASSES.name)
    }

    @Test
    fun appendToConfigurationAction() {
        options.appendTo(
                JAVAC_CLASSES,
                "task1",
                TestTask::class.java,
                object : BuildArtifactTransformBuilder.SimpleConfigurationAction<TestTask> {
                    override fun accept(
                            task: TestTask,
                            input: BuildableArtifact,
                            output: File) {
                        task.inputFiles = input
                        task.outputFile = output
                    }
                })
        taskHolder.createFirstArtifactFiles(JAVAC_CLASSES, "foo", "task0")
        BuildableArtifactImpl.enableResolution()

        project.tasks.getByName("task1Debug") { t ->
            assertThat(t).isInstanceOf(TestTask::class.java)
            val task = t as TestTask
            assertThat(task.inputFiles.single()).hasName("foo")
            assertThat(task.outputFile).hasName(JAVAC_CLASSES.name)
            assertThat(task.inputFiles.buildDependencies.getDependencies(null))
                    .containsExactly(task0)
        }
        assertThat(taskHolder.getArtifactFiles(JAVAC_CLASSES).files.map(File::getName))
                .containsExactly("foo", JAVAC_CLASSES.name)
    }

    @Test
    fun replace() {
        options.replace(
                JAVAC_CLASSES,
                "task1",
                TestTask::class.java,
                object : BuildArtifactTransformBuilder.SimpleConfigurationAction<TestTask> {
                    override fun accept(
                            task: TestTask,
                            input: BuildableArtifact,
                            output: File) {
                        task.inputFiles = input
                        task.outputFile = output
                    }
                })
        taskHolder.createFirstArtifactFiles(JAVAC_CLASSES, "foo", "task0")
        BuildableArtifactImpl.enableResolution()

        project.tasks.getByName("task1Debug") { t ->
            assertThat(t).isInstanceOf(TestTask::class.java)
            val task = t as TestTask
            assertThat(task.inputFiles.single()).hasName("foo")
            assertThat(task.outputFile).hasName(JAVAC_CLASSES.name)
            assertThat(task.inputFiles.buildDependencies.getDependencies(null))
                    .containsExactly(task0)
        }
        assertThat(taskHolder.getArtifactFiles(JAVAC_CLASSES).files.map(File::getName))
                .containsExactly(JAVAC_CLASSES.name)
    }

    @Test
    fun builder() {
        val builder = options.builder("task1", TestTask::class.java)

        assertThat(builder).isInstanceOf(BuildArtifactTransformBuilder::class.java)
        builder.create { input, output ->
            assertFailsWith<RuntimeException> { input.artifact }
            assertFailsWith<RuntimeException> { output.file }
        }
    }

    @Test
    fun checkSeal() {
        val builder = options.builder("task1", TestTask::class.java)
        (options as BuildArtifactsOptionsImpl).seal()
        assertThat((builder as BuildArtifactTransformBuilderImpl).isSealed()).isTrue()
    }
}