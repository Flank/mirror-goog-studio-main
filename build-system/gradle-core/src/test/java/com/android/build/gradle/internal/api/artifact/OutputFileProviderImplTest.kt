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
import com.android.build.gradle.internal.scope.InternalArtifactType
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
                        task)
        val outputFile = output.getFile("foo")
        holder.appendArtifact(JAVAC_CLASSES, listOf(outputFile), task)
        assertThat(outputFile).hasName("foo")
        BuildableArtifactImpl.enableResolution()
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
                        task)
        val fooFile = output.getFile("foo")
        holder.appendArtifact(JAVAC_CLASSES, listOf(fooFile), task)
        holder.appendArtifact(JAVAC_CLASSES, task, "bar")
        BuildableArtifactImpl.enableResolution()
        assertThat(fooFile).hasName("foo")
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
                        task)
        val fooFile = output.getFile("foo", JAVAC_CLASSES, JAVA_COMPILE_CLASSPATH)
        val barFile = output.getFile("bar", JAVA_COMPILE_CLASSPATH)
        holder.appendArtifact(JAVAC_CLASSES, listOf(fooFile), task)
        holder.appendArtifact(JAVA_COMPILE_CLASSPATH, task, "classpath")
        holder.appendArtifact(JAVA_COMPILE_CLASSPATH, listOf(barFile, fooFile), task)
        BuildableArtifactImpl.enableResolution()

        assertThat(output.getFile("foo")).hasName("foo")
        assertThat(output.getFile("bar")).hasName("bar")
        assertFailsWith<RuntimeException> { output.getFile("baz", InternalArtifactType.JAVAC) }

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
                dslScope)
}
