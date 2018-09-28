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

import com.android.build.gradle.internal.scope.InternalArtifactType.LIBRARY_MANIFEST
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_MANIFESTS
import com.android.build.gradle.internal.api.artifact.BuildableArtifactImpl
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.fixtures.FakeDeprecationReporter
import com.android.build.gradle.internal.fixtures.FakeEvalIssueReporter
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.scope.BuildArtifactsHolder.OperationType
import com.android.build.gradle.internal.variant2.DslScopeImpl
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * Test for [BuildArtifactsHolder]
 */
@RunWith(Parameterized::class)
class BuildArtifactsHolderTest(
    private val operationType: OperationType, private val artifactType: InternalArtifactType) {

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "{index}: test({0})={1})")
        fun parameters(): Iterable<Array<Any>> {
            return listOf(
                arrayOf<Any>(OperationType.INITIAL, LIBRARY_MANIFEST),
                arrayOf<Any>(OperationType.APPEND, LIBRARY_MANIFEST),
                arrayOf<Any>(OperationType.TRANSFORM, LIBRARY_MANIFEST),
                arrayOf<Any>(OperationType.INITIAL, MERGED_MANIFESTS),
                arrayOf<Any>(OperationType.APPEND, MERGED_MANIFESTS),
                arrayOf<Any>(OperationType.TRANSFORM, MERGED_MANIFESTS)
            )
        }
    }

    private lateinit var project : Project
    lateinit var root : File
    private val dslScope = DslScopeImpl(
            FakeEvalIssueReporter(throwOnError = true),
            FakeDeprecationReporter(),
            FakeObjectFactory())
    private lateinit var holder : BuildArtifactsHolder
    private lateinit var task1 : Task
    private lateinit var task2 : Task
    private lateinit var task3 : Task

    @Before
    fun setUp() {
        BuildableArtifactImpl.enableResolution()
        project = ProjectBuilder.builder().build()
        root = project.file("build")
        holder = VariantBuildArtifactsHolder(
            project,
            "debug",
            root,
            dslScope)
        task1 = project.tasks.create("task1")
        task2 = project.tasks.create("task2")
        task3 = project.tasks.create("task3")
    }

    /** Return the expected location of a generated file given the task name and file name. */
    private fun file(artifactType: InternalArtifactType, taskName : String, filename : String) =
            FileUtils.join(artifactType.getOutputDir(root), "debug", taskName, filename)

    @Test
    fun earlyFinalOutput() {
        val finalVersion = holder.getFinalArtifactFiles(artifactType)
        // no-one appends or replaces, we should be empty files if resolved.
        assertThat(finalVersion.files).isEmpty()
    }

    @Test
    fun lateFinalOutput() {
        val newHolder = TestBuildArtifactsHolder(project, { root }, dslScope)
        val task = project.tasks.create(artifactType.name + operationType.name)
        artifactType.createOutputLocation(
            newHolder,
            operationType,
            task.name,
            "finalFile"
        )

        val files = newHolder.getArtifactFiles(artifactType)
        assertThat(files.buildDependencies.getDependencies(null)).containsExactly(task)

        // now get final version.
        val finalVersion = newHolder.getFinalArtifactFiles(artifactType)
        assertThat(finalVersion.files).hasSize(1)
        assertThat(finalVersion.buildDependencies.getDependencies(null)).containsExactly(task)
    }

    @Test
    fun addBuildableArtifact() {
        holder.createBuildableArtifact(
            MERGED_MANIFESTS,
            OperationType.INITIAL,
            project.files(file(MERGED_MANIFESTS,"task1", "task1File")).files,
            task1.name)
        val javaClasses = holder.getArtifactFiles(MERGED_MANIFESTS)

        // register the buildable artifact under a different type.
        val newHolder = TestBuildArtifactsHolder(project, { root }, dslScope)
        newHolder.createBuildableArtifact(
            MERGED_MANIFESTS,
            OperationType.INITIAL,
            javaClasses)
        // and verify that files and dependencies are carried over.
        val newJavaClasses = newHolder.getArtifactFiles(MERGED_MANIFESTS)
        assertThat(newJavaClasses.single()).isEqualTo(file(MERGED_MANIFESTS,"task1", "task1File"))
        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        assertThat(newJavaClasses.buildDependencies.getDependencies(null)).containsExactly(task1)
    }

    @Test
    fun finalBuildableLocation() {
        val newHolder = TestBuildArtifactsHolder(project, { root }, dslScope)
        artifactType.createOutputLocation(
            newHolder,
            operationType,
            artifactType.name+operationType.name,
            "finalFile")

        val finalArtifactFiles = newHolder.getFinalArtifactFiles(artifactType)
        assertThat(finalArtifactFiles.files).hasSize(1)
        val outputFile = finalArtifactFiles.files.elementAt(0)
        val relativeFile = outputFile.relativeTo(project.buildDir)
        assertThat(relativeFile.path).isEqualTo(
            FileUtils.join(
                InternalArtifactType.Category.INTERMEDIATES.name.toLowerCase(),
                artifactType.name.toLowerCase(),
                "test",
                "finalFile"))
    }

    @Test
    fun transformedBuildableIntermediatesAccess() {
        val files1 = holder.createBuildableArtifact(artifactType,
            OperationType.INITIAL,
            project.files(file(artifactType,"task1", "foo")).files, task1.name)
        assertThat(holder.getArtifactFiles(artifactType)).isSameAs(files1)
        val files2 = holder.createBuildableArtifact(artifactType,
            OperationType.TRANSFORM,
            project.files(file(artifactType,"task2", "bar")).files, task2.name)
        assertThat(holder.getArtifactFiles(artifactType)).isSameAs(files2)
        artifactType.createOutputLocation(holder, OperationType.APPEND,
            task3.name,
            "baz")

        assertThat(files1.single()).isEqualTo(file(artifactType,"task1", "foo"))
        // TaskDependency.getDependencies accepts null.
        assertThat(files1.buildDependencies.getDependencies(null)).containsExactly(task1)
        assertThat(files2.single()).isEqualTo(file(artifactType,"task2", "bar"))
        assertThat(files2.buildDependencies.getDependencies(null)).containsExactly(task2)

        val history = holder.getHistory(artifactType)
        assertThat(history[0]).isSameAs(files1)
        assertThat(history[1]).isSameAs(files2)
    }

    @Test
    fun appendedLocationIntermediatesAccess() {

        artifactType.createOutputLocation(holder, operationType, task1.name, "foo")
        val afterTask1 = holder.getArtifactFiles(artifactType)
        assertThat(holder.getArtifactFiles(artifactType)).isSameAs(afterTask1)

        artifactType.createOutputLocation(holder, OperationType.APPEND, task2.name, "bar")
        val afterTask2 = holder.getArtifactFiles(artifactType)
        assertThat(holder.getArtifactFiles(artifactType)).isSameAs(afterTask2)
        artifactType.createOutputLocation(holder, OperationType.APPEND, task3.name, "baz")
        val afterTask3 = holder.getArtifactFiles(artifactType)

        assertThat(afterTask1).containsExactly(
            file(artifactType, "task1", "foo"))
        assertThat(afterTask2).containsExactly(
            file(artifactType,"task1", "foo"),
            file(artifactType,"task2", "bar"))
        assertThat(afterTask3).containsExactly(
            file(artifactType,"task1", "foo"),
            file(artifactType,"task2", "bar"),
            file(artifactType,"task3", "baz"))

        assertThat(afterTask1.buildDependencies.getDependencies(null)).containsExactly(task1)
        assertThat(afterTask2.buildDependencies.getDependencies(null))
            .containsExactly(task1, task2)
        assertThat(afterTask3.buildDependencies.getDependencies(null))
            .containsExactly(task1, task2, task3)
    }

    @Test
    fun transformedLocationIntermediatesAccess() {

        artifactType.createOutputLocation(holder, operationType, task1.name, "foo")
        val afterTask1 = holder.getArtifactFiles(artifactType)
        assertThat(holder.getArtifactFiles(artifactType)).isSameAs(afterTask1)

        artifactType.createOutputLocation(holder, OperationType.APPEND, task2.name, "bar")
        val afterTask2 = holder.getArtifactFiles(artifactType)
        assertThat(holder.getArtifactFiles(artifactType)).isSameAs(afterTask2)
        artifactType.createOutputLocation(holder, OperationType.TRANSFORM, task3.name, "baz")
        val afterTask3 = holder.getArtifactFiles(artifactType)

        assertThat(afterTask1).containsExactly(
            file(artifactType,"task1", "foo"))
        assertThat(afterTask2).containsExactly(
            file(artifactType,"task1", "foo"),
            file(artifactType,"task2", "bar"))
        assertThat(afterTask3).containsExactly(
            file(artifactType,"task3", "baz"))

        assertThat(afterTask1.buildDependencies.getDependencies(null)).containsExactly(task1)
        assertThat(afterTask2.buildDependencies.getDependencies(null))
            .containsExactly(task1, task2)
        assertThat(afterTask3.buildDependencies.getDependencies(null))
            .containsExactly(task3)
    }

    @Test
    fun appendedLocationAndFinalVersionAccess() {
        val allFiles = holder.getFinalArtifactFiles(artifactType)
        artifactType.createOutputLocation(holder, operationType, task1.name, "foo")
        artifactType.createOutputLocation(holder, OperationType.APPEND, task2.name, "bar")
        artifactType.createOutputLocation(holder, OperationType.APPEND, task3.name, "baz")

        assertThat(allFiles).containsExactly(
            file(artifactType,"task1", "foo"),
            file(artifactType,"task2", "bar"),
            file(artifactType,"task3", "baz"))
    }

    @Test
    fun replacedLocationAndFinalVersionAccess() {
        val allFiles = holder.getFinalArtifactFiles(artifactType)
        artifactType.createOutputLocation(holder, operationType, task1.name, "foo")
        artifactType.createOutputLocation(holder, OperationType.APPEND, task2.name, "bar")
        artifactType.createOutputLocation(holder, OperationType.TRANSFORM, task3.name, "baz")

        assertThat(allFiles).containsExactly(
            file(artifactType,"task3", "baz"))
    }

    @Test
    fun appendedLocationAndHistoryAccess() {
        artifactType.createOutputLocation(holder, operationType, task1.name, "foo")
        val afterTask1 = holder.getArtifactFiles(artifactType)
        assertThat(holder.getArtifactFiles(artifactType)).isSameAs(afterTask1)
        artifactType.createOutputLocation(holder, OperationType.APPEND, task2.name, "bar")
        val afterTask2 = holder.getArtifactFiles(artifactType)
        assertThat(holder.getArtifactFiles(artifactType)).isSameAs(afterTask2)
        artifactType.createOutputLocation(holder, OperationType.APPEND, task3.name, "baz")

        val history = holder.getHistory(artifactType)
        assertThat(history[0]).isSameAs(afterTask1)
        assertThat(history[1]).isSameAs(afterTask2)
        assertThat(history[2]).isSameAs(holder.getArtifactFiles(artifactType))
    }

    @Test
    fun replacedDirectoryAndHistoryAccess() {
        artifactType.createOutputLocation(holder, operationType, task1.name, "foo")
        val afterTask1 = holder.getArtifactFiles(artifactType)
        artifactType.createOutputLocation(holder, OperationType.APPEND, task2.name, "bar")
        val afterTask2 = holder.getArtifactFiles(artifactType)
        artifactType.createOutputLocation(holder, OperationType.TRANSFORM, task3.name, "baz")

        val history = holder.getHistory(artifactType)
        assertThat(history[0]).isSameAs(afterTask1)
        assertThat(history[1]).isSameAs(afterTask2)
        assertThat(history[2]).isSameAs(holder.getArtifactFiles(artifactType))
    }

    @Test
    fun initialCollision() {
        artifactType.createOutputLocation(holder, operationType, task1.name, "foo")
        holder.getArtifactFiles(artifactType)
        try {
            artifactType.createOutputLocation(holder, OperationType.INITIAL, task2.name, "bar")
        } catch (exception: Exception) {
            assertThat(exception.message).startsWith(
                "Task task2 is expecting to be the initial producer of ${artifactType.name}, " +
                        "but task1 already registered")
        }
    }

    @Test
    fun appendedDirectoryLocation() {
        Assume.assumeFalse(operationType == OperationType.INITIAL)

        val finalArtifactFiles = holder.getFinalArtifactFiles(artifactType)

        val task1Output =
            artifactType.createOutputLocation(holder, OperationType.INITIAL, task1.name, "original")
        assertThat(task1Output.get().asFile.path).isEqualTo(finalArtifactFiles.files.elementAt(0).path)

        val task2Output = artifactType.createOutputLocation(
            holder, operationType, task2.name, "changed")

        assertThat(finalArtifactFiles.files).hasSize(
            if (operationType == OperationType.APPEND) 2 else 1)

        // check that our output file
        assertThat(task2Output.get().asFile.path).isEqualTo(
            finalArtifactFiles.files.elementAt(if (operationType == OperationType.APPEND) 1 else 0).path)

        val relativeFile1 = task1Output.get().asFile.relativeTo(project.buildDir)
        assertThat(relativeFile1.path).isEqualTo(
            FileUtils.join(
                InternalArtifactType.Category.INTERMEDIATES.outputPath,
                artifactType.name.toLowerCase(),
                "debug",
                "task1",
                "original"))
        val relativeFile2 = task2Output.get().asFile.relativeTo(project.buildDir)
        assertThat(relativeFile2.path).isEqualTo(
            FileUtils.join(
                InternalArtifactType.Category.INTERMEDIATES.name.toLowerCase(),
                artifactType.name.toLowerCase(),
                "debug",
                "task2",
                "changed")
        )
    }

    private class TestBuildArtifactsHolder(
        project: Project,
        rootOutputDir: () -> File,
        dslScope: DslScope) : BuildArtifactsHolder(project, rootOutputDir, dslScope) {

        override fun getIdentifier() = "test"
    }
}