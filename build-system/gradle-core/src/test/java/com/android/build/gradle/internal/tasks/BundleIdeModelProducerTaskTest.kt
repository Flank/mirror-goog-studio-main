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

import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.google.common.truth.Truth
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertNotNull

class BundleIdeModelProducerTaskTest {

    @get: Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var task: BundleIdeModelProducerTask
    private lateinit var outputFile: File

    @Test
    fun testModelProduction() {
        val project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        task = project.tasks.register(
                "bundleModelProducer",
                BundleIdeModelProducerTask::class.java).get()
        task.analyticsService.set(FakeNoOpAnalyticsService())
        outputFile = temporaryFolder.newFile()
        task.bundleIdeModel.set(temporaryFolder.newFile())
        task.applicationId.set("some.application")
        task.variantName = "debug"
        val bundleFile = temporaryFolder.newFile("file.aab")
        task.finalBundleFile.set(bundleFile)

        task.taskAction()

        val modelFile = task.bundleIdeModel.get().asFile
        Truth.assertThat(modelFile.exists()).isTrue()

        val builtArtifacts = BuiltArtifactsLoaderImpl.loadFromFile(modelFile)
        assertNotNull(builtArtifacts)
        Truth.assertThat(builtArtifacts.elements).hasSize(1)
        Truth.assertThat(builtArtifacts.applicationId).isEqualTo("some.application")
        Truth.assertThat(builtArtifacts.variantName).isEqualTo("debug")
        val bundleModel = builtArtifacts.elements.single()
        Truth.assertThat(bundleModel.filters).isEmpty()
        Truth.assertThat(File(bundleModel.outputFile).absolutePath).isEqualTo(
                bundleFile.absolutePath)
    }
}
