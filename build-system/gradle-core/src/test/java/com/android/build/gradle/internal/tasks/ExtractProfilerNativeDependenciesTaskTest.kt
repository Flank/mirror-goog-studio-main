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

import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.testutils.TestInputsGenerator
import com.google.common.truth.Truth.assertThat
import org.gradle.api.provider.Property
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ExtractProfilerNativeDependenciesTaskTest {
    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    @Test
    fun testNoFiles() {
        val inputJar = temp.root.resolve("input.jar").also {
            TestInputsGenerator.writeJarWithEmptyEntries(it.toPath(), listOf("a.jar"))
        }
        val outputDir = temp.newFolder()

        executeWorkerAction(listOf(inputJar), outputDir)

        assertThat(outputDir.listFiles()!!).isEmpty()
    }

    @Test
    fun testExtraction() {
        val inputJar = temp.root.resolve("input.jar").also {
            TestInputsGenerator.writeJarWithEmptyEntries(
                    it.toPath(),
                    listOf("a.jar", "dependencies/b.jar", "dependencies/c.jar")
            )
        }
        val outputDir = temp.newFolder()
        executeWorkerAction(listOf(inputJar), outputDir)

        assertThat(outputDir.list()!!.asList()).containsExactly("b.jar", "c.jar")
    }

    @Test
    fun testExtractionFromMultiple() {
        val fstJar = temp.root.resolve("fst.jar").also {
            TestInputsGenerator.writeJarWithEmptyEntries(
                    it.toPath(), listOf("a.jar", "dependencies/fst.jar")
            )
        }
        val sndJar = temp.root.resolve("snd.jar").also {
            TestInputsGenerator.writeJarWithEmptyEntries(
                    it.toPath(), listOf("a.jar", "dependencies/snd.jar")
            )
        }

        val outputDir = temp.newFolder()
        executeWorkerAction(listOf(fstJar, sndJar), outputDir)

        assertThat(outputDir.list()!!.asList()).containsExactly("fst.jar", "snd.jar")
    }

    private fun executeWorkerAction(inputJars: List<File>, outputDir: File) {
        object : ExtractProfilerNativeDependenciesTask.ExtractProfilerNativeDepsWorkerAction() {
            override fun getParameters() = object : Parameters() {
                override val outputDir = FakeObjectFactory.factory.directoryProperty().fileValue(outputDir)
                override val inputJars = FakeObjectFactory.factory.fileCollection().from(inputJars)
                override val projectName = FakeGradleProperty("projectName")
                override val taskOwner = FakeGradleProperty("taskOwner")
                override val workerKey = FakeGradleProperty("workerKey")
                override val analyticsService: Property<AnalyticsService>
                        = FakeGradleProperty(FakeNoOpAnalyticsService())
            }
        }.execute()
    }
}