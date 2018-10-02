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
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.api.artifact.BuildableArtifactImpl
import com.android.build.gradle.internal.fixtures.FakeDeprecationReporter
import com.android.build.gradle.internal.fixtures.FakeEvalIssueReporter
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.scope.BuildElements
import com.android.build.gradle.internal.scope.BuildOutput
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.variant2.DslScopeImpl
import com.android.ide.common.build.ApkInfo
import com.google.common.collect.ImmutableList
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import com.google.common.truth.Truth.assertThat

class CopyOutputsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var fileSet: Set<String>

    private lateinit var outputDir: File
    private lateinit var testDir: File

    private lateinit var artifact: BuildableArtifact

    private fun getOrCreateFile(name: String): File {
        val file = File(testDir.path + File.separator + name)
        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.createNewFile()
        }
        return file
    }

    @Before
    fun setUp() {
        testDir = temporaryFolder.newFolder()
        outputDir = temporaryFolder.newFolder()
        val project = ProjectBuilder.builder().withProjectDir(testDir).build()

        val dslScope = DslScopeImpl(
            FakeEvalIssueReporter(throwOnError = true),
            FakeDeprecationReporter(),
            FakeObjectFactory()
        )

        val apkInfo =
            ApkInfo.of(VariantOutput.OutputType.MAIN, ImmutableList.of<FilterData>(), 12345)

        val elements = BuildElements(
            mutableListOf(
                BuildOutput(
                    InternalArtifactType.FULL_APK,
                    apkInfo,
                    getOrCreateFile("apk1")
                ),
                BuildOutput(
                    InternalArtifactType.FULL_APK,
                    apkInfo,
                    getOrCreateFile("apk2")
                ),
                BuildOutput(
                    InternalArtifactType.ABI_PACKAGED_SPLIT,
                    apkInfo,
                    getOrCreateFile("split1")
                ),
                BuildOutput(
                    InternalArtifactType.ABI_PACKAGED_SPLIT,
                    apkInfo,
                    getOrCreateFile("split2")
                ),
                BuildOutput(
                    InternalArtifactType.DENSITY_OR_LANGUAGE_PACKAGED_SPLIT,
                    apkInfo,
                    getOrCreateFile("resource1")
                ),
                BuildOutput(
                    InternalArtifactType.DENSITY_OR_LANGUAGE_PACKAGED_SPLIT,
                    apkInfo,
                    getOrCreateFile("resource2")
                )
            )
        )

        elements.save(testDir)

        artifact = BuildableArtifactImpl(project.files(getOrCreateFile("output.json")), dslScope)

        fileSet = setOf(
            "apk1",
            "apk2",
            "split1",
            "split2",
            "resource1",
            "resource2",
            "output.json"
        )
    }

    @Test
    fun copyOutputs() {
        val copiedFilesList = ArrayList<BuildOutput>()
        var task = CopyOutputs.CopyOutputsRunnable(
            CopyOutputs.CopyOutputsParams(
                InternalArtifactType.FULL_APK,
                artifact.get(),
                outputDir,
                copiedFilesList
            )
        )
        task.run()

        task = CopyOutputs.CopyOutputsRunnable(
            CopyOutputs.CopyOutputsParams(
                InternalArtifactType.ABI_PACKAGED_SPLIT,
                artifact.get(),
                outputDir,
                copiedFilesList
            )
        )
        task.run()

        task = CopyOutputs.CopyOutputsRunnable(
            CopyOutputs.CopyOutputsParams(
                InternalArtifactType.DENSITY_OR_LANGUAGE_PACKAGED_SPLIT,
                artifact.get(),
                outputDir,
                copiedFilesList
            )
        )
        task.run()

        BuildElements(copiedFilesList).save(outputDir)

        assertThat(outputDir.listFiles()).hasLength(7)

        assertThat(HashSet<String>().apply {
            outputDir.listFiles().forEach { add(it.name) }
        }).containsExactlyElementsIn(fileSet)
    }
}