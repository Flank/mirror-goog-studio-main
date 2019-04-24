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

package com.android.build.gradle.internal.tasks

import com.android.ide.common.workers.WorkerExecutorFacade
import com.android.testutils.TestInputsGenerator
import com.android.testutils.apk.Zip
import com.android.testutils.truth.FileSubject.assertThat
import com.android.testutils.truth.ZipFileSubject
import com.android.utils.FileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.Serializable

/**
 * Unit tests for [MergeClassesTask].
 */
class MergeClassesTaskTest {

    @get: Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var workers: WorkerExecutorFacade

    @Before
    fun setUp() {
        workers = object: WorkerExecutorFacade {
            override fun submit(actionClass: Class<out Runnable>, parameter: Serializable) {
                val configuration =
                    WorkerExecutorFacade.Configuration(
                        parameter, WorkerExecutorFacade.IsolationMode.NONE, listOf()
                    )
                val action =
                    actionClass.getConstructor(configuration.parameter.javaClass)
                        .newInstance(configuration.parameter)
                action.run()
            }

            override fun await() {}

            override fun close() {}
        }
    }

    @Test
    fun `test basic`() {
        // include duplicate .kotlin_module files as regression test for
        // https://issuetracker.google.com/issues/125696148
        val jarFile1 = temporaryFolder.newFile("foo.jar")
        TestInputsGenerator.writeJarWithEmptyEntries(
            jarFile1.toPath(),
            listOf("Foo.class", "foo.txt", "META-INF/duplicate.kotlin_module")
        )
        assertThat(jarFile1).exists()
        val jarFile2 = temporaryFolder.newFile("bar.jar")
        TestInputsGenerator.writeJarWithEmptyEntries(
            jarFile2.toPath(),
            listOf("Bar.class", "bar.txt", "META-INF/duplicate.kotlin_module")
        )
        assertThat(jarFile2).exists()
        val inputDir = temporaryFolder.newFolder("inputDir")
        val classFile = FileUtils.join(inputDir, "Baz.class")
        FileUtils.createFile(classFile, "baz")
        assertThat(classFile).exists()
        val javaResFile = FileUtils.join(inputDir, "baz.txt")
        FileUtils.createFile(javaResFile, "baz")
        assertThat(javaResFile).exists()
        val inputFiles = listOf(jarFile1, jarFile2, inputDir)
        val outputFile = File(temporaryFolder.newFolder("outputDir"), "out.jar")

        MergeClassesTask.MergeClassesDelegate(inputFiles, outputFile, workers).mergeClasses()

        // outputFile should only contain classes, not java resources or .kotlin_module files
        Zip(outputFile).use {
            ZipFileSubject.assertThat(it).contains("Foo.class")
            ZipFileSubject.assertThat(it).contains("Bar.class")
            ZipFileSubject.assertThat(it).contains("Baz.class")
            ZipFileSubject.assertThat(it).doesNotContain("foo.txt")
            ZipFileSubject.assertThat(it).doesNotContain("bar.txt")
            ZipFileSubject.assertThat(it).doesNotContain("baz.txt")
            ZipFileSubject.assertThat(it).doesNotContain("META-INF/duplicate.kotlin_module")
        }
    }
}
