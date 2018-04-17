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
import com.android.build.gradle.internal.api.artifact.BuildableArtifactImpl
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.fixtures.FakeDeprecationReporter
import com.android.build.gradle.internal.fixtures.FakeEvalIssueReporter
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.variant2.DslScopeImpl
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Test for [BuildArtifactsHolder]
 */
class BuildArtifactsHolderTest {

    lateinit private var project : Project
    lateinit var root : File
    private val dslScope = DslScopeImpl(
            FakeEvalIssueReporter(throwOnError = true),
            FakeDeprecationReporter(),
            FakeObjectFactory())
    lateinit private var holder : BuildArtifactsHolder
    lateinit private var task0 : Task
    lateinit private var task1 : Task
    lateinit private var task2 : Task

    @Before
    fun setUp() {
        BuildableArtifactImpl.enableResolution()
        project = ProjectBuilder.builder().build()
        root = project.file("root")
        holder = VariantBuildArtifactsHolder(
            project,
            "debug",
            root,
            dslScope)
        task0 = project.tasks.create("task0")
        task1 = project.tasks.create("task1")
        task2 = project.tasks.create("task2")
    }

    /** Return the expected location of a generated file given the task name and file name. */
    private fun file(taskName : String, filename : String) =
            FileUtils.join(JAVAC_CLASSES.getOutputDir(root), "debug", taskName, filename)

    @Test
    fun replaceOutput() {
        val files1 = holder.replaceArtifact(JAVAC_CLASSES,
            project.files(file("task1", "foo")).files, task1)
        assertThat(holder.getArtifactFiles(JAVAC_CLASSES)).isSameAs(files1)
        val files2 = holder.replaceArtifact(JAVAC_CLASSES,
            project.files(file("task2", "bar")).files, task2)
        assertThat(holder.getArtifactFiles(JAVAC_CLASSES)).isSameAs(files2)
        holder.appendArtifact(JAVAC_CLASSES, task0, "baz")

        assertThat(files1.single()).isEqualTo(file("task1", "foo"))
        // TaskDependency.getDependencies accepts null.
        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        assertThat(files1.buildDependencies.getDependencies(null)).containsExactly(task1)
        assertThat(files2.single()).isEqualTo(file("task2", "bar"))
        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        assertThat(files2.buildDependencies.getDependencies(null)).containsExactly(task2)

        val history = holder.getHistory(JAVAC_CLASSES)
        assertThat(history[0]).isSameAs(files1)
        assertThat(history[1]).isSameAs(files2)
    }

    @Test
    fun appendOutput() {
        holder.appendArtifact(JAVAC_CLASSES, task1, "foo")
        val files0 = holder.getFinalArtifactFiles(JAVAC_CLASSES)
        val files1 = holder.getArtifactFiles(JAVAC_CLASSES)
        assertThat(holder.getArtifactFiles(JAVAC_CLASSES)).isSameAs(files1)
        holder.appendArtifact(JAVAC_CLASSES, task2, "bar")
        val files2 = holder.getArtifactFiles(JAVAC_CLASSES)
        assertThat(holder.getArtifactFiles(JAVAC_CLASSES)).isSameAs(files2)
        holder.appendArtifact(JAVAC_CLASSES, task0, "baz")

        assertThat(files1).containsExactly(
                file("task1", "foo"))
        assertThat(files2).containsExactly(
                file("task1", "foo"),
                file("task2", "bar"))
        assertThat(files0).containsExactly(
            file("task1", "foo"),
            file("task2", "bar"),
            file("task0", "baz"))

        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        assertThat(files0.buildDependencies.getDependencies(null)).containsExactly(task0, task1, task2)
        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        assertThat(files1.buildDependencies.getDependencies(null)).containsExactly(task1)
        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        assertThat(files2.buildDependencies.getDependencies(null))
                .containsExactly(task1, task2)

        val history = holder.getHistory(JAVAC_CLASSES)
        assertThat(history[0]).isSameAs(files1)
        assertThat(history[1]).isSameAs(files2)
        assertThat(history[2]).isSameAs(holder.getArtifactFiles(JAVAC_CLASSES))
    }

    @Test
    fun obtainFinalOutput() {
        val finalVersion = holder.getFinalArtifactFiles(JAVAC_CLASSES)
        holder.replaceArtifact(JAVAC_CLASSES, project.files(file("task1", "task1File")).files, task1)
        val files1 = holder.getArtifactFiles(JAVAC_CLASSES)

        assertThat(files1.single()).isEqualTo(file("task1", "task1File"))
        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        assertThat(files1.buildDependencies.getDependencies(null)).containsExactly(task1)

        // Now add some more files to this artifact type using all appendArtifact methods
        holder.appendArtifact(JAVAC_CLASSES, task0, "task0File")
        val task0_files = holder.getArtifactFiles(JAVAC_CLASSES)
        holder.appendArtifact(JAVAC_CLASSES, project.files("single_file"))
        holder.appendArtifact(JAVAC_CLASSES, project.files("element1", "element2"))

        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        assertThat(task0_files.buildDependencies.getDependencies(null)).containsExactly(task0, task1)

        // assert that the current buildableArtifact has all the files
        assertThat(holder.getArtifactFiles(JAVAC_CLASSES).files).containsExactly(
            file("task1", "task1File"),
            file("task0", "task0File"),
            project.file("element1"),
            project.file("element2"),
            project.file("single_file"))
        // as well as the "finalVersion"
        assertThat(finalVersion.files).containsExactly(
            file("task1", "task1File"),
            file("task0", "task0File"),
            project.file("element1"),
            project.file("element2"),
            project.file("single_file"))
    }

    @Test
    fun earlyFinalOutput() {
        val finalVersion = holder.getFinalArtifactFiles(JAVAC_CLASSES);
        // no-one appends or replaces, we should be empty files if resolved.
        assertThat(finalVersion.files).isEmpty()
    }

    @Test
    fun lateFinalOutput() {
        holder.replaceArtifact(JAVAC_CLASSES, project.files(file("task1", "task1File")).files, task1)
        val files1 = holder.getArtifactFiles(JAVAC_CLASSES)

        assertThat(files1.single()).isEqualTo(file("task1", "task1File"))
        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        assertThat(files1.buildDependencies.getDependencies(null)).containsExactly(task1)

        // now get final version.
        val finalVersion = holder.getFinalArtifactFiles(JAVAC_CLASSES)
        assertThat(finalVersion.files).hasSize(1)
        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        assertThat(finalVersion.buildDependencies.getDependencies(null)).containsExactly(task1)
    }

    @Test
    fun addBuildableArtifact() {
        holder.replaceArtifact(JAVAC_CLASSES, project.files(file("task1", "task1File")).files, task1)
        val javaClasses = holder.getArtifactFiles(JAVAC_CLASSES)

        // register the buildable artifact under a different type.
        val newHolder = TestBuildArtifactsHolder(project, { root }, dslScope)
        newHolder.appendArtifact(JAVAC_CLASSES, javaClasses)
        // and verify that files and dependencies are carried over.
        val newJavaClasses = newHolder.getArtifactFiles(JAVAC_CLASSES)
        assertThat(newJavaClasses.single()).isEqualTo(file("task1", "task1File"))
        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        assertThat(newJavaClasses.buildDependencies.getDependencies(null)).containsExactly(task1)
    }

    @Test
    fun finalBuildableLocation() {
        holder.setArtifactFile(JAVAC_CLASSES, task1, "finalFile")
        val finalArtifactFiles = holder.getFinalArtifactFiles(JAVAC_CLASSES)
        assertThat(finalArtifactFiles.files).hasSize(1)
        val outputFile = finalArtifactFiles.files.elementAt(0)
        val relativeFile = outputFile.relativeTo(project.buildDir)
        assertThat(relativeFile.path).isEqualTo(
            FileUtils.join(
                InternalArtifactType.Category.INTERMEDIATES.name.toLowerCase(),
                JAVAC_CLASSES.name.toLowerCase(),
                "debug",
                "finalFile"))
    }

    @Test
    fun finalReplacedBuildableLocation() {
        val task1Output = holder.setArtifactFile(
            InternalArtifactType.BUNDLE, task1, "finalFile")
        val task2Output = holder.setArtifactFile(
            InternalArtifactType.BUNDLE, task2, "replacingFile")
        val finalArtifactFiles = holder.getFinalArtifactFiles(InternalArtifactType.BUNDLE)
        assertThat(finalArtifactFiles.files).hasSize(1)
        val outputFile = finalArtifactFiles.files.elementAt(0)
        // check that our output file 
        assertThat(task2Output.get().asFile.path).isEqualTo(outputFile.path)
        val relativeFile1 = task1Output.get().asFile.relativeTo(project.buildDir)
        assertThat(relativeFile1.path).isEqualTo(
            FileUtils.join(
                InternalArtifactType.Category.INTERMEDIATES.outputPath,
                InternalArtifactType.BUNDLE.name.toLowerCase(),
                "debug",
                "task1",
                "finalFile"))
        val relativeFile2 = task2Output.get().asFile.relativeTo(project.buildDir)
        assertThat(relativeFile2.path).isEqualTo(
            FileUtils.join(
                InternalArtifactType.Category.OUTPUTS.name.toLowerCase(),
                InternalArtifactType.BUNDLE.name.toLowerCase(),
                "debug",
                "replacingFile")
        )
    }

    private class TestBuildArtifactsHolder(
        project: Project,
        rootOutputDir: () -> File,
        dslScope: DslScope) : BuildArtifactsHolder(project, rootOutputDir, dslScope) {

        override fun getIdentifier() = "test"
    }
}