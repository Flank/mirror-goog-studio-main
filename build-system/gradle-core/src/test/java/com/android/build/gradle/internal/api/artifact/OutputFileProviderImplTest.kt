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

import com.android.build.api.artifact.BuildArtifactType.JAVAC_CLASSES
import com.android.build.api.artifact.BuildArtifactType.JAVA_COMPILE_CLASSPATH
import com.android.build.gradle.internal.fixtures.FakeDeprecationReporter
import com.android.build.gradle.internal.fixtures.FakeEvalIssueReporter
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.variant2.DslScopeImpl
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.ide.common.util.multimapOf
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.test.assertFailsWith

/**
 * Test for [OutputFileProviderImpl]
 */
class OutputFileProviderImplTest {

    private val project = ProjectBuilder.builder().build()
    private val task = project.task("task")

    private val dslScope = DslScopeImpl(
            FakeEvalIssueReporter(throwOnError = true),
            FakeDeprecationReporter(),
            FakeObjectFactory())

    companion object {
        @BeforeClass @JvmStatic
        fun setUp() {
            BuildableArtifactImpl.disableResolution()
        }
    }

    @Test
    fun replaceOutput() {
        val holder = newTaskOutputHolder()
        val output =
                OutputFileProviderImpl(
                        holder,
                        listOf(JAVAC_CLASSES),
                        listOf(),
                        multimapOf(JAVAC_CLASSES to "foo"),
                        listOf(),
                        "task",
                        dslScope)
        holder.createFirstArtifactFiles(JAVAC_CLASSES, task, "bar")
        assertThat(output.file).hasName("foo")
        assertThat(holder.getArtifactFiles(JAVAC_CLASSES).single()).hasName("foo")
    }

    @Test
    fun appendOutput() {
        val holder = newTaskOutputHolder()
        val output =
                OutputFileProviderImpl(
                        holder,
                        listOf(),
                        listOf(JAVAC_CLASSES),
                        multimapOf(JAVAC_CLASSES to "foo"),
                        listOf(),
                        "task",
                        dslScope)
        holder.createFirstArtifactFiles(JAVAC_CLASSES, task, "bar")
        BuildableArtifactImpl.enableResolution()
        assertThat(output.file).hasName("foo")
        assertThat(holder.getArtifactFiles(JAVAC_CLASSES).map(File::getName)).containsExactly("foo", "bar")
    }

    @Test
    fun multipleFiles() {
        val holder = newTaskOutputHolder()
        val output =
                OutputFileProviderImpl(
                        holder,
                        listOf(JAVAC_CLASSES),
                        listOf(JAVA_COMPILE_CLASSPATH),
                        multimapOf(
                                JAVAC_CLASSES to "foo",
                                JAVA_COMPILE_CLASSPATH to "foo",
                                JAVA_COMPILE_CLASSPATH to "bar"),
                        listOf(),
                        "task",
                        dslScope)
        holder.createFirstArtifactFiles(JAVAC_CLASSES, task, "javac")
        holder.createFirstArtifactFiles(JAVA_COMPILE_CLASSPATH, task, "classpath")
        BuildableArtifactImpl.enableResolution()

        assertThat(output.getFile("foo")).hasName("foo")
        assertThat(output.getFile("bar")).hasName("bar")
        assertFailsWith<RuntimeException> { output.getFile("baz") }

        assertThat(holder.getArtifactFiles(JAVAC_CLASSES).map(File::getName)).containsExactly("foo")
        assertThat(holder.getArtifactFiles(JAVA_COMPILE_CLASSPATH).map(File::getName))
                .containsExactly("foo", "bar", "classpath")
    }

    private fun newTaskOutputHolder() =
            BuildArtifactsHolder(
                    project,
                    "debug",
                    project.file("root"),
                    "debug",
                    listOf(JAVAC_CLASSES, JAVA_COMPILE_CLASSPATH),
                    dslScope)
}
