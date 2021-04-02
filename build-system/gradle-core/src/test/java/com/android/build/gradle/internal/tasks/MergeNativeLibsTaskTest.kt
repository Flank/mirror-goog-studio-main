/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.builder.merge.DuplicateRelativeFileException
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import junit.framework.Assert.fail
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.workers.WorkerExecutor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.inject.Inject

/**
 * Unit tests for [MergeNativeLibsTask].
 */
class MergeNativeLibsTaskTest {

    @get: Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var task: MergeNativeLibsTask
    private lateinit var projectNativeLibs: List<File>
    private lateinit var subProjectNativeLibs: List<File>
    private lateinit var externalLibNativeLibs: List<File>
    private lateinit var profilerNativeLibs: File
    private lateinit var excludes: Set<String>
    private lateinit var pickFirsts: Set<String>
    private lateinit var outputDir: File
    private lateinit var unfilteredProjectNativeLibs: List<File>

    private val abis = listOf("x86", "x86_64")

    abstract class TestMergeNativeLibsTask @Inject constructor(testWorkerExecutor: WorkerExecutor) :
        MergeNativeLibsTask() {
        override val workerExecutor = testWorkerExecutor
    }

    @Before
    fun setUp() {
        val project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        task = project.tasks.register(
            "mergeNativeLibsTask",
            TestMergeNativeLibsTask::class.java,
            FakeGradleWorkExecutor(project.objects, temporaryFolder.newFolder())
        ).get()
        task.analyticsService.set(FakeNoOpAnalyticsService())

        outputDir = temporaryFolder.newFolder("out")

        val projectNativeLib1 =
            temporaryFolder.newFolder("projectNativeLib1").also { dir ->
                abis.forEach { abi ->
                    dir.resolve("$abi/projectNativeLib1.so").also {
                        it.parentFile.mkdirs()
                        it.writeText("projectNativeLib1")
                    }
                    dir.resolve("$abi/notANativeLib.txt").also {
                        it.parentFile.mkdirs()
                        it.writeText("notANativeLib")
                    }
                    dir.resolve("$abi/exclude.so").also {
                        it.parentFile.mkdirs()
                        it.writeText("exclude")
                    }
                }
            }
        val projectNativeLib2 =
            temporaryFolder.newFolder("projectNativeLib2").also { dir ->
                abis.forEach { abi ->
                    dir.resolve("$abi/projectNativeLib2.so").also {
                        it.parentFile.mkdirs()
                        it.writeText("projectNativeLib2")
                    }
                }
            }
        unfilteredProjectNativeLibs = listOf(projectNativeLib1, projectNativeLib2)
        projectNativeLibs =
            mutableListOf<File>().also {
                abis.forEach { abi ->
                    it.add(projectNativeLib1.resolve("$abi/projectNativeLib1.so"))
                    it.add(projectNativeLib2.resolve("$abi/projectNativeLib2.so"))
                }
            }.toList()

        val subProjectNativeLib1 =
            temporaryFolder.newFolder("subProjectNativeLib1").also { dir ->
                abis.forEach { abi ->
                    dir.resolve("$abi/subProjectNativeLib1.so").also {
                        it.parentFile.mkdirs()
                        it.writeText("subProjectNativeLib1")
                    }
                    dir.resolve("$abi/exclude.so").also {
                        it.parentFile.mkdirs()
                        it.writeText("exclude")
                    }
                    dir.resolve("$abi/pickFirst.so").also {
                        it.parentFile.mkdirs()
                        it.writeText("subProjectPickFirst")
                    }
                }
            }
        val subProjectNativeLib2 =
            temporaryFolder.newFolder("subProjectNativeLib2").also { dir ->
                abis.forEach { abi ->
                    dir.resolve("$abi/subProjectNativeLib2.so").also {
                        it.parentFile.mkdirs()
                        it.writeText("subProjectNativeLib2")
                    }
                }
            }
        subProjectNativeLibs = listOf(subProjectNativeLib1, subProjectNativeLib2)

        val externalLibNativeLib1 =
            temporaryFolder.newFolder("externalLibNativeLib1").also { dir ->
                abis.forEach { abi ->
                    dir.resolve("$abi/externalLibNativeLib1.so").also {
                        it.parentFile.mkdirs()
                        it.writeText("externalLibNativeLib1")
                    }
                    dir.resolve("$abi/exclude.so").also {
                        it.parentFile.mkdirs()
                        it.writeText("exclude")
                    }
                    dir.resolve("$abi/pickFirst.so").also {
                        it.parentFile.mkdirs()
                        it.writeText("externalLibPickFirst")
                    }
                }
            }
        val externalLibNativeLib2 =
            temporaryFolder.newFolder("externalLibNativeLib2").also { dir ->
                abis.forEach { abi ->
                    dir.resolve("$abi/externalLibNativeLib2.so").also {
                        it.parentFile.mkdirs()
                        it.writeText("externalLibNativeLib2")
                    }
                }
            }
        externalLibNativeLibs = listOf(externalLibNativeLib1, externalLibNativeLib2)

        profilerNativeLibs =
            temporaryFolder.newFolder("profilerNativeLib").also { dir ->
                abis.forEach { abi ->
                    dir.resolve("$abi/profilerNativeLib1.so").also {
                        it.parentFile.mkdirs()
                        it.writeText("profilerNativeLib1")
                    }
                    dir.resolve("$abi/profilerNativeLib2.so").also {
                        it.parentFile.mkdirs()
                        it.writeText("profilerNativeLib2")
                    }
                    dir.resolve("$abi/exclude.so").also {
                        it.parentFile.mkdirs()
                        it.writeText("exclude")
                    }
                    dir.resolve("$abi/pickFirst.so").also {
                        it.parentFile.mkdirs()
                        it.writeText("profilerPickFirst")
                    }
                }
            }

        excludes = setOf("**/exclude.so")
        pickFirsts = setOf("**/pickFirst.so")

        task.projectNativeLibs.from(projectNativeLibs)
        task.subProjectNativeLibs.from(subProjectNativeLibs)
        task.externalLibNativeLibs.from(externalLibNativeLibs)
        task.profilerNativeLibs.set(profilerNativeLibs)
        task.excludes.set(excludes)
        task.pickFirsts.set(pickFirsts)
        task.outputDir.set(outputDir)
        task.unfilteredProjectNativeLibs.from(unfilteredProjectNativeLibs)
    }

    @Test
    fun testBasic() {
        task.taskAction()

        assertThat(outputDir).exists()
        abis.forEach { abi ->
            assertThat(outputDir.resolve("lib/$abi/projectNativeLib1.so")).exists()
            assertThat(outputDir.resolve("lib/$abi/projectNativeLib1.so"))
                .hasContents("projectNativeLib1")
            assertThat(outputDir.resolve("lib/$abi/projectNativeLib2.so")).exists()
            assertThat(outputDir.resolve("lib/$abi/projectNativeLib2.so"))
                .hasContents("projectNativeLib2")
            assertThat(outputDir.resolve("lib/$abi/subProjectNativeLib1.so")).exists()
            assertThat(outputDir.resolve("lib/$abi/subProjectNativeLib1.so"))
                .hasContents("subProjectNativeLib1")
            assertThat(outputDir.resolve("lib/$abi/subProjectNativeLib2.so")).exists()
            assertThat(outputDir.resolve("lib/$abi/subProjectNativeLib2.so"))
                .hasContents("subProjectNativeLib2")
            assertThat(outputDir.resolve("lib/$abi/externalLibNativeLib1.so")).exists()
            assertThat(outputDir.resolve("lib/$abi/externalLibNativeLib1.so"))
                .hasContents("externalLibNativeLib1")
            assertThat(outputDir.resolve("lib/$abi/externalLibNativeLib2.so")).exists()
            assertThat(outputDir.resolve("lib/$abi/externalLibNativeLib2.so"))
                .hasContents("externalLibNativeLib2")
            assertThat(outputDir.resolve("lib/$abi/profilerNativeLib1.so")).exists()
            assertThat(outputDir.resolve("lib/$abi/profilerNativeLib1.so"))
                .hasContents("profilerNativeLib1")
            assertThat(outputDir.resolve("lib/$abi/profilerNativeLib2.so")).exists()
            assertThat(outputDir.resolve("lib/$abi/profilerNativeLib2.so"))
                .hasContents("profilerNativeLib2")

            assertThat(outputDir.resolve("lib/$abi/notANativeLib.txt")).doesNotExist()
            assertThat(outputDir.resolve("lib/$abi/exclude.so")).doesNotExist()
            assertThat(outputDir.resolve("lib/$abi/pickFirst.so")).exists()
            assertThat(outputDir.resolve("lib/$abi/pickFirst.so"))
                .hasContents("subProjectPickFirst")
        }
    }

    @Test
    fun testErrorWhenDuplicateNativeLibsInDependencies() {
        abis.forEach { abi ->
            subProjectNativeLibs[0].resolve("$abi/duplicate.so")
                .writeText("subProjectDuplicate1")
            subProjectNativeLibs[1].resolve("$abi/duplicate.so")
                .writeText("subProjectDuplicate2")
            externalLibNativeLibs[0].resolve("$abi/duplicate.so")
                .writeText("externalLibDuplicate1")
            externalLibNativeLibs[1].resolve("$abi/duplicate.so")
                .writeText("externalLibDuplicate2")
            profilerNativeLibs.resolve("$abi/duplicate.so")
                .writeText("profilerDuplicate")
        }

        try {
            task.taskAction()
            fail("task action should fail because of duplicates")
        } catch (e: DuplicateRelativeFileException) {
            assertThat(e.message).contains("5 files found with path")
        }
    }

    @Test
    fun testDuplicateNativeLibInProject() {
        abis.forEach { abi ->
            val duplicateProjectNativeLib =
                unfilteredProjectNativeLibs[0].resolve("$abi/duplicate.so").also {
                    it.writeText("projectDuplicate")
                }
            task.projectNativeLibs.from(duplicateProjectNativeLib)

            subProjectNativeLibs[0].resolve("$abi/duplicate.so")
                .writeText("subProjectDuplicate1")
            subProjectNativeLibs[1].resolve("$abi/duplicate.so")
                .writeText("subProjectDuplicate2")
            externalLibNativeLibs[0].resolve("$abi/duplicate.so")
                .writeText("externalLibDuplicate1")
            externalLibNativeLibs[1].resolve("$abi/duplicate.so")
                .writeText("externalLibDuplicate2")
            profilerNativeLibs.resolve("$abi/duplicate.so")
                .writeText("profilerDuplicate")
        }

        task.taskAction()

        abis.forEach { abi ->
            assertThat(outputDir.resolve("lib/$abi/duplicate.so")).exists()
            assertThat(outputDir.resolve("lib/$abi/duplicate.so")).hasContents("projectDuplicate")
        }
    }

    // Regression test for b/183538866
    @Test
    fun testPickFirstForAllNativeLibs() {
        pickFirsts = setOf("**/*.so")
        task.pickFirsts.set(pickFirsts)

        task.taskAction()

        assertThat(outputDir).exists()
        abis.forEach { abi ->
            assertThat(outputDir.resolve("lib/$abi/projectNativeLib1.so")).exists()
            assertThat(outputDir.resolve("lib/$abi/projectNativeLib1.so"))
                .hasContents("projectNativeLib1")
            assertThat(outputDir.resolve("lib/$abi/projectNativeLib2.so")).exists()
            assertThat(outputDir.resolve("lib/$abi/projectNativeLib2.so"))
                .hasContents("projectNativeLib2")
            assertThat(outputDir.resolve("lib/$abi/subProjectNativeLib1.so")).exists()
            assertThat(outputDir.resolve("lib/$abi/subProjectNativeLib1.so"))
                .hasContents("subProjectNativeLib1")
            assertThat(outputDir.resolve("lib/$abi/subProjectNativeLib2.so")).exists()
            assertThat(outputDir.resolve("lib/$abi/subProjectNativeLib2.so"))
                .hasContents("subProjectNativeLib2")
            assertThat(outputDir.resolve("lib/$abi/externalLibNativeLib1.so")).exists()
            assertThat(outputDir.resolve("lib/$abi/externalLibNativeLib1.so"))
                .hasContents("externalLibNativeLib1")
            assertThat(outputDir.resolve("lib/$abi/externalLibNativeLib2.so")).exists()
            assertThat(outputDir.resolve("lib/$abi/externalLibNativeLib2.so"))
                .hasContents("externalLibNativeLib2")
            assertThat(outputDir.resolve("lib/$abi/profilerNativeLib1.so")).exists()
            assertThat(outputDir.resolve("lib/$abi/profilerNativeLib1.so"))
                .hasContents("profilerNativeLib1")
            assertThat(outputDir.resolve("lib/$abi/profilerNativeLib2.so")).exists()
            assertThat(outputDir.resolve("lib/$abi/profilerNativeLib2.so"))
                .hasContents("profilerNativeLib2")

            assertThat(outputDir.resolve("lib/$abi/notANativeLib.txt")).doesNotExist()

            // exclude.so exists because pickFirst trumps exclude
            assertThat(outputDir.resolve("lib/$abi/exclude.so")).exists()
            assertThat(outputDir.resolve("lib/$abi/exclude.so")).hasContents("exclude")

            assertThat(outputDir.resolve("lib/$abi/pickFirst.so")).exists()
            assertThat(outputDir.resolve("lib/$abi/pickFirst.so"))
                .hasContents("subProjectPickFirst")
        }
    }
}
