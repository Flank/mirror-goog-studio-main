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

package com.android.build.gradle.tasks

import com.android.build.api.variant.impl.VariantOutputConfigurationImpl
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.workers.WorkerExecutor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import java.io.IOException
import javax.inject.Inject

/**
 * Tests for {@link ProcessManifest}
 */
class ProcessLibraryManifestTest {

    @Rule @JvmField var temporaryFolder = TemporaryFolder()

    internal lateinit var task: ProcessLibraryManifest

    abstract class TestProcessLibraryManifest @Inject constructor(testWorkerExecutor: WorkerExecutor) :
            ProcessLibraryManifest() {
        override val workerExecutor = testWorkerExecutor
    }

    @Before
    @Throws(IOException::class)
    fun setUp() {

        val project: Project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        val taskProvider = project.tasks.register("fooRelease", TestProcessLibraryManifest::class.java,
                FakeGradleWorkExecutor(project.objects, temporaryFolder.newFolder()))
        task = taskProvider.get()

        task.minSdkVersion.set("1")
        task.maxSdkVersion.set(1)
        task.targetSdkVersion.set("1")
        task.packageOverride.set("")
        task.manifestPlaceholders.set(mapOf())
        task.mainSplit.set(
            VariantOutputImpl(
                versionCode = project.objects.property(Int::class.java).also { it.set(1) },
                versionName = project.objects.property(String::class.java).also { it.set("version_name")},
                enabled = project.objects.property(Boolean::class.java).also { it.set(true) },
                variantOutputConfiguration = VariantOutputConfigurationImpl(),
                baseName = "base_name",
                fullName = "fooRelease",
                outputFileName = project.objects.property(String::class.java).also { it.set("output_file_name") }
        ))
        task.analyticsService.set(FakeNoOpAnalyticsService())
    }

    @Test
    fun testInputsAreAnnotatedCorrectly() {
        assertThat(task.inputs.properties).containsKey("maxSdkVersion")
        assertThat(task.inputs.properties).containsKey("minSdkVersion")
        assertThat(task.inputs.properties).containsKey("targetSdkVersion")
        assertThat(task.inputs.properties).containsKey("manifestPlaceholders")
        assertThat(task.inputs.properties).containsKey("packageOverride")
        assertThat(task.inputs.properties).containsEntry("mainSplit.fullName", "fooRelease")
    }

    @Test
    fun testNoSourceManifest() {
        task.variantName = "release"
        task.namespace.set("random.word")
        task.tmpDir.set(temporaryFolder.newFolder("a", "b", "c"))
        task.manifestOutputFile.set(temporaryFolder.newFile())
        task.reportFile.set(temporaryFolder.newFile())
        task.mergeBlameFile.set(temporaryFolder.newFile())
        task.disableMinSdkVersionCheck.set(false)
        task.taskAction(Mockito.mock(IncrementalTaskInputs::class.java))
        assertThat(task.manifestOutputFile.get().asFile.readText(Charsets.UTF_8))
                .contains("package=\"random.word\"")
        assertThat(task.tmpDir.get().asFileTree.files).isEmpty()
    }
}
