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

import com.android.build.gradle.internal.fixtures.FakeConfigurableFileCollection
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.packaging.JarCreatorType
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.testutils.TestInputsGenerator
import com.android.testutils.truth.PathSubject.assertThat
import com.android.testutils.truth.ZipFileSubject
import com.android.utils.FileUtils
import org.gradle.api.provider.Property
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for [MergeClassesTask].
 */
class MergeClassesTaskTest {

    @get: Rule
    val temporaryFolder = TemporaryFolder()

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

        object : MergeClassesTask.MergeClassesWorkAction() {
            override fun getParameters(): Parameters {
                return object : Parameters() {
                    override val inputFiles = FakeConfigurableFileCollection(inputFiles)
                    override val outputFile = FakeObjectFactory.factory.fileProperty().fileValue(outputFile)
                    override val jarCreatorType = FakeGradleProperty(JarCreatorType.JAR_FLINGER)
                    override val projectPath = FakeGradleProperty("projectName")
                    override val taskOwner = FakeGradleProperty("taskOwner")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService>
                        get() = FakeGradleProperty(FakeNoOpAnalyticsService())
                }
            }
        }.execute()

        // outputFile should only contain classes, not java resources or .kotlin_module files
        ZipFileSubject.assertThat(outputFile) {
            it.contains("Foo.class")
            it.contains("Bar.class")
            it.contains("Baz.class")
            it.doesNotContain("foo.txt")
            it.doesNotContain("bar.txt")
            it.doesNotContain("baz.txt")
            it.doesNotContain("META-INF/duplicate.kotlin_module")
        }
    }
}
