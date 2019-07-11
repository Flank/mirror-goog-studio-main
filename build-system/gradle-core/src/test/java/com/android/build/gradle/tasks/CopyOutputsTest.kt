/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.build.FilterData
import com.android.build.VariantOutput
import com.android.build.gradle.internal.scope.ApkData
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.BuildElements
import com.android.build.gradle.internal.scope.BuildOutput
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.Workers
import com.google.common.collect.ImmutableList
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.junit.After
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

class CopyOutputsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var outputDir: File
    private lateinit var testDir: File

    private lateinit var project: Project

    @Before
    fun setUp() {
        Workers.useDirectWorkerExecutor = true
        testDir = temporaryFolder.newFolder()
        outputDir = temporaryFolder.newFolder()
        project = ProjectBuilder.builder().withProjectDir(testDir).build()
    }

    @After
    fun tearDown() {
        Workers.useDirectWorkerExecutor = false
    }

    @Test
    fun copyOutputs() {
        val taskProvider = project.tasks.register("copyOutputs", CopyOutputs::class.java)
        val task = taskProvider.get()

        val apkInfo =
            ApkData.of(
                VariantOutput.OutputType.MAIN,
                ImmutableList.of<FilterData>(),
                12345
            )
        val apkDir = temporaryFolder.newFolder()
        BuildElements(
            listOf(
                BuildOutput(
                    InternalArtifactType.FULL_APK,
                    apkInfo,
                    getOrCreateFile(apkDir, "apk1")
                ),
                BuildOutput(
                    InternalArtifactType.FULL_APK,
                    apkInfo,
                    getOrCreateFile(apkDir, "apk2")
                )
            )
        ).save(apkDir)
        task.fullApks.set(apkDir)

        val splitDir = temporaryFolder.newFolder()
        BuildElements(
            listOf(
                BuildOutput(
                    InternalArtifactType.ABI_PACKAGED_SPLIT,
                    apkInfo,
                    getOrCreateFile(splitDir, "split1")
                ),
                BuildOutput(
                    InternalArtifactType.ABI_PACKAGED_SPLIT,
                    apkInfo,
                    getOrCreateFile(splitDir, "split2")
                )
            )
        ).save(splitDir)
        task.abiSplits.set(splitDir)

        val resDir = temporaryFolder.newFolder()
        BuildElements(
            listOf(
                BuildOutput(
                    InternalArtifactType.DENSITY_OR_LANGUAGE_PACKAGED_SPLIT,
                    apkInfo,
                    getOrCreateFile(resDir, "resource1")
                ),
                BuildOutput(
                    InternalArtifactType.DENSITY_OR_LANGUAGE_PACKAGED_SPLIT,
                    apkInfo,
                    getOrCreateFile(resDir, "resource2")
                )
            )
        ).save(resDir)
        task.resourcesSplits.set(resDir)

        task.destinationDir.set(outputDir)
        task.taskAction()

        assertThat(outputDir.listFiles()).hasLength(7)
        assertThat(outputDir.listFiles().map { it.name }.toSet()).containsExactlyElementsIn(
            setOf(
                "apk1",
                "apk2",
                "split1",
                "split2",
                "resource1",
                "resource2",
                "output.json"
            )
        )
    }

    private fun getOrCreateFile(parent: File, name: String): File {
        val file = File(parent.path + File.separator + name)
        if (!file.exists()) {
            file.createNewFile()
        }
        return file
    }
}