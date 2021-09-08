/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.gradle.internal.fixtures.FakeConfigurableFileCollection
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.profile.AnalyticsService
import com.google.common.truth.Truth
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FeatureDexMergeTaskTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun testBasicFunction() {
        val inputFolder = temporaryFolder.newFolder()
        val outputFolder = temporaryFolder.newFolder()
        inputFolder.resolve("dex1").writeText("foo")
        inputFolder.resolve("dex2").writeText("bar")
        executeWorkerAction(inputFolder, outputFolder)
        Truth.assertThat(outputFolder.resolve("0.dex").isFile).isTrue()
        Truth.assertThat(outputFolder.resolve("1.dex").isFile).isTrue()
        Truth.assertThat(outputFolder.list().size).isEqualTo(2)
    }

    private fun executeWorkerAction(inputDir: File, outputDir: File) {
        object : FeatureDexMergeWorkAction() {
            override fun getParameters() = object : Params() {
                override val dexDirs: ConfigurableFileCollection
                    get() = FakeConfigurableFileCollection(inputDir)
                override val outputDir: DirectoryProperty
                    get() = FakeObjectFactory.factory.directoryProperty().fileValue(outputDir)
                override val projectPath: Property<String>
                    get() = FakeGradleProperty("projectName")
                override val taskOwner: Property<String>
                    get() = FakeGradleProperty("taskOwner")
                override val workerKey: Property<String>
                    get() = FakeGradleProperty("workerKey")
                override val analyticsService: Property<AnalyticsService>
                    get() = FakeGradleProperty(FakeNoOpAnalyticsService())
            }
        }.execute()
    }
}
