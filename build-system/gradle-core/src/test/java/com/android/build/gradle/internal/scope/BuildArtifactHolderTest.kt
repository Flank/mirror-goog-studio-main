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

package com.android.build.gradle.internal.scope

import com.android.build.api.artifact.BuildArtifactType.JAVAC_CLASSES
import com.android.build.api.artifact.BuildArtifactType.JAVA_COMPILE_CLASSPATH
import com.android.build.gradle.internal.api.artifact.BuildableArtifactImpl
import com.android.build.gradle.internal.fixtures.FakeEvalIssueReporter
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertFailsWith

/**
 * Test for [BuildArtifactHolder]
 */
class BuildArtifactHolderTest {

    private val variantDir = "debug"
    lateinit private var project : Project
    lateinit var root : File
    private val issueReporter = FakeEvalIssueReporter(throwOnError = true)
    lateinit private var holder : BuildArtifactHolder
    lateinit private var task0 : Task
    lateinit private var task1 : Task
    lateinit private var task2 : Task

    @Before
    fun setUp() {
        BuildableArtifactImpl.enableResolution()
        project = ProjectBuilder.builder().build()
        root = project.file("root")
        holder = BuildArtifactHolder(
                project,
                "debug",
                root,
                variantDir,
                listOf(JAVAC_CLASSES),
                issueReporter)
        task0 = project.tasks.create("task0")
        task1 = project.tasks.create("task1")
        task2 = project.tasks.create("task2")
    }

    /** Return the expected location of a generated file given the task name and file name. */
    private fun file(taskName : String, filename : String) =
            FileUtils.join(root, taskName, variantDir, filename)

    @Test
    fun initialize() {
        assertThat(holder.hasArtifact(JAVAC_CLASSES)).isTrue()
        assertFailsWith<RuntimeException> { holder.getArtifactFiles(JAVAC_CLASSES).isEmpty() }

        assertThat(holder.hasArtifact(JAVA_COMPILE_CLASSPATH)).isFalse()
        assertFailsWith<MissingBuildableArtifactException> {
            holder.getArtifactFiles(JAVA_COMPILE_CLASSPATH)
        }
    }

    @Test
    fun replaceOutput() {
        val files1 = holder.replaceArtifact(JAVAC_CLASSES, listOf("foo"), "task1")
        assertThat(holder.getArtifactFiles(JAVAC_CLASSES)).isSameAs(files1)
        val files2 = holder.replaceArtifact(JAVAC_CLASSES, listOf("bar"), "task2")
        assertThat(holder.getArtifactFiles(JAVAC_CLASSES)).isSameAs(files2)
        holder.createFirstArtifactFiles(JAVAC_CLASSES, "baz", "task0")

        assertThat(files1.single()).isEqualTo(file("task1", "foo"))
        assertThat(files1.buildDependencies.getDependencies(null)).containsExactly(task1)
        assertThat(files2.single()).isEqualTo(file("task2", "bar"))
        assertThat(files2.buildDependencies.getDependencies(null)).containsExactly(task2)
    }

    @Test
    fun appendOutput() {
        val files0 = holder.getArtifactFiles(JAVAC_CLASSES)
        val files1 = holder.appendArtifact(JAVAC_CLASSES, listOf("foo"), "task1")
        assertThat(holder.getArtifactFiles(JAVAC_CLASSES)).isSameAs(files1)
        val files2 = holder.appendArtifact(JAVAC_CLASSES, listOf("bar"), "task2")
        assertThat(holder.getArtifactFiles(JAVAC_CLASSES)).isSameAs(files2)
        holder.createFirstArtifactFiles(JAVAC_CLASSES, listOf("baz"), "task0")

        assertThat(files0).containsExactly(file(JAVAC_CLASSES.name, "baz"))
        assertThat(files1).containsExactly(file("task1", "foo"), file(JAVAC_CLASSES.name, "baz"))
        assertThat(files2).containsExactly(
                file("task1", "foo"), file("task2", "bar"), file(JAVAC_CLASSES.name, "baz"))

        assertThat(files0.buildDependencies.getDependencies(null)).containsExactly(task0)
        assertThat(files1.buildDependencies.getDependencies(null)).containsExactly(task0, task1)
        assertThat(files2.buildDependencies.getDependencies(null))
                .containsExactly(task0, task1, task2)
    }

    @Test
    fun createFirstOutput() {
        val files0 = holder.getArtifactFiles(JAVAC_CLASSES)
        holder.replaceArtifact(JAVAC_CLASSES, listOf("foo"), "task1")
        val files1 = holder.getArtifactFiles(JAVAC_CLASSES)

        assertThat(files1.single()).isEqualTo(file("task1", "foo"))
        assertThat(files1.buildDependencies.getDependencies(null)).containsExactly(task1)

        // Check the original output is modified.
        val newFiles = holder.createFirstArtifactFiles(JAVAC_CLASSES, listOf("foo"), "task0")
        assertThat(newFiles).isSameAs(files0)
        assertThat(files0.single()).isEqualTo(file(JAVAC_CLASSES.name, "foo"))
        assertThat(files0.buildDependencies.getDependencies(null)).containsExactly(task0)

        // Should not be able to create first output more than once
        assertFailsWith<RuntimeException> {
            holder.createFirstArtifactFiles(JAVAC_CLASSES, listOf("foo"), "task0") }
    }
}