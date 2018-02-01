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

package com.android.build.gradle.internal.api.artifact

import com.android.build.api.artifact.BuildArtifactTransformBuilder
import com.android.build.api.artifact.BuildArtifactTransformBuilder.ConfigurationAction
import com.android.build.api.artifact.BuildArtifactTransformBuilder.OperationType.APPEND
import com.android.build.api.artifact.BuildArtifactTransformBuilder.OperationType.REPLACE
import com.android.build.api.artifact.BuildArtifactType.JAVAC_CLASSES
import com.android.build.api.artifact.BuildArtifactType.JAVA_COMPILE_CLASSPATH
import com.android.build.api.artifact.InputArtifactProvider
import com.android.build.api.artifact.OutputFileProvider
import com.android.build.gradle.internal.fixtures.FakeDeprecationReporter
import com.android.build.gradle.internal.fixtures.FakeEvalIssueReporter
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.variant2.DslScopeImpl
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertFailsWith

/**
 * Test for [BuildArtifactTransformBuilder].
 */
class BuildArtifactTransformBuilderImplTest {

    open class TestTask : DefaultTask()

    private val project = ProjectBuilder().build()!!
    private val dslScope = DslScopeImpl(
            FakeEvalIssueReporter(throwOnError = true),
            FakeDeprecationReporter(),
            FakeObjectFactory())
    lateinit private var builder : BuildArtifactTransformBuilder<TestTask>
    lateinit private var taskHolder : BuildArtifactsHolder

    @Before
    fun setUp() {
        BuildableArtifactImpl.disableResolution()
        taskHolder =
                BuildArtifactsHolder(
                        project,
                        "debug",
                        project.file("root"),
                        "debug",
                        listOf(JAVAC_CLASSES, JAVA_COMPILE_CLASSPATH),
                        dslScope)
        builder = BuildArtifactTransformBuilderImpl(
                project,
                taskHolder,
                "test",
                TestTask::class.java,
                dslScope)
    }


    @Test
    fun default() {
        var configuredTask : Task? = null
        val task = builder.create { input ,output ->
                    configuredTask = this
                    assertThat(this).isInstanceOf(TestTask::class.java)
                    assertThat(name).isEqualTo("testDebug")
                    assertFailsWith<RuntimeException> { input.artifact }
                    assertFailsWith<RuntimeException> { input.getArtifact(JAVAC_CLASSES) }
                    assertFailsWith<RuntimeException> { output.file }
                    assertFailsWith<RuntimeException> { output.getFile("output.txt") }
                }
        assertThat(task).isSameAs(configuredTask)
    }

    @Test
    fun defaultWithConfigAction() {
        var configuredTask : Task? = null
        val task = builder.create(
                object : ConfigurationAction<TestTask> {
                    override fun accept(
                            task: TestTask,
                            input: InputArtifactProvider,
                            output: OutputFileProvider) {
                        configuredTask = task
                        assertThat(task).isInstanceOf(TestTask::class.java)
                        assertThat(task.name).isEqualTo("testDebug")
                        assertFailsWith<RuntimeException> { input.artifact }
                        assertFailsWith<RuntimeException> { input.getArtifact(JAVAC_CLASSES) }
                        assertFailsWith<RuntimeException> { output.file }
                        assertFailsWith<RuntimeException> { output.getFile("output.txt") }
                    }
                })
        assertThat(task).isSameAs(configuredTask)
    }

    @Test
    fun singleInput() {
        var input : InputArtifactProvider? = null
        val task = builder
                .input(JAVAC_CLASSES)
                .create { i ,o ->
                    input = i
                    assertThat(this).isInstanceOf(TestTask::class.java)
                    assertThat(i.getArtifact(JAVAC_CLASSES)).isSameAs(i.artifact)
                    assertFailsWith<RuntimeException> { o.file }
                }
        taskHolder.createFirstArtifactFiles(JAVAC_CLASSES, task, "javac")
        BuildableArtifactImpl.enableResolution()
        assertThat(input!!.artifact.map(File::getName)).containsExactly("javac")
    }

    @Test
    fun multiInput() {
        var input : InputArtifactProvider? = null
        BuildableArtifactImpl.enableResolution()
        val task = builder
            .input(JAVAC_CLASSES)
            .input(JAVA_COMPILE_CLASSPATH)
            .create { i, o ->
                input = i
                assertThat(this).isInstanceOf(TestTask::class.java)
                assertFailsWith<RuntimeException> { i.artifact }
                assertFailsWith<RuntimeException> { o.file }
            }
        taskHolder.createFirstArtifactFiles(JAVAC_CLASSES, task ,"javac")
        taskHolder.createFirstArtifactFiles(JAVA_COMPILE_CLASSPATH, task,"classpath")
        BuildableArtifactImpl.enableResolution()

        assertThat(input!!.getArtifact(JAVAC_CLASSES).map(File::getName))
                .containsExactly("javac")
        assertThat(input!!.getArtifact(JAVA_COMPILE_CLASSPATH).map(File::getName))
                .containsExactly("classpath")
    }

    @Test
    fun output() {
        var output : OutputFileProvider? = null
        BuildableArtifactImpl.enableResolution()
        val task = builder
                .output(JAVAC_CLASSES, REPLACE)
                .output(JAVA_COMPILE_CLASSPATH, APPEND)
                .outputFile("foo", JAVAC_CLASSES)
                .outputFile("bar", JAVA_COMPILE_CLASSPATH)
                .outputFile("baz")
                .create { i ,o ->
                    output = o
                    assertThat(this).isInstanceOf(TestTask::class.java)
                    assertFailsWith<RuntimeException> { i.artifact }
                    assertFailsWith<RuntimeException> { i.getArtifact(JAVAC_CLASSES) }
                    assertFailsWith<RuntimeException> { o.file }
                }
        taskHolder.createFirstArtifactFiles(JAVAC_CLASSES, task, "javac")
        taskHolder.createFirstArtifactFiles(JAVA_COMPILE_CLASSPATH, task, "classpath")
        BuildableArtifactImpl.enableResolution()

        assertThat(output!!.getFile("foo")).hasName("foo")
        assertThat(output!!.getFile("bar")).hasName("bar")
        assertThat(output!!.getFile("baz")).hasName("baz")
        assertThat(taskHolder.getArtifactFiles(JAVAC_CLASSES).files.map(File::getName))
                .containsExactly("foo")
        assertThat(taskHolder.getArtifactFiles(JAVA_COMPILE_CLASSPATH).files.map(File::getName))
                .containsExactly("bar", "classpath")
    }

    @Test
    fun checkSeal() {
        (builder as BuildArtifactTransformBuilderImpl).seal()
        assertFailsWith<RuntimeException> { builder.input(JAVAC_CLASSES) }
        assertFailsWith<RuntimeException> { builder.output(JAVAC_CLASSES, REPLACE) }
        assertFailsWith<RuntimeException> { builder.create { _ , _ -> } }
    }
}