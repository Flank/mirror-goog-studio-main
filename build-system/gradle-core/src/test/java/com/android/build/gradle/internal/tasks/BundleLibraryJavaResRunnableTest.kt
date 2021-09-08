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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.fixtures.FakeConfigurableFileCollection
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.packaging.JarCreatorType
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.testutils.truth.ZipFileSubject.assertThat
import org.gradle.api.provider.Property
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BundleLibraryJavaResRunnableTest {
    @JvmField
    @Rule
    val tmp = TemporaryFolder()

    @Test
    fun testResourcesCopied() {
        val output = tmp.newFolder().resolve("output.jar")
        val input = setOf(
            tmp.newFolder().also { dir ->
                dir.resolve("a.txt").createNewFile()
                dir.resolve("b.txt").createNewFile()
                dir.resolve("sub").also {
                    it.mkdir()
                    it.resolve("c.txt").createNewFile()
                }
            }
        )
        object : BundleLibraryJavaResRunnable() {
            override fun getParameters(): Params {
                return object : Params() {
                    override val output = FakeObjectFactory.factory.fileProperty().fileValue(output)
                    override val inputs = FakeConfigurableFileCollection(input)
                    override val jarCreatorType = FakeGradleProperty(JarCreatorType.JAR_FLINGER)
                    override val compressionLevel = FakeGradleProperty(Deflater.BEST_SPEED)
                    override val projectPath = FakeGradleProperty("project")
                    override val taskOwner = FakeGradleProperty("task")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService> = FakeGradleProperty(
                        FakeNoOpAnalyticsService()
                    )
                }
            }
        }.execute()

        assertThat(output) {
            it.contains("a.txt")
            it.contains("b.txt")
            it.contains("sub/c.txt")
        }
    }

    @Test
    fun testClassesIgnored() {
        val output = tmp.newFolder().resolve("output.jar")
        val input = setOf(
            tmp.newFolder().also { dir ->
                dir.resolve("a.txt").createNewFile()
                dir.resolve("A.class").createNewFile()
            }
        )
        object : BundleLibraryJavaResRunnable() {
            override fun getParameters(): Params {
                return object : Params() {
                    override val output = FakeObjectFactory.factory.fileProperty().fileValue(output)
                    override val inputs = FakeConfigurableFileCollection(input)
                    override val jarCreatorType = FakeGradleProperty(JarCreatorType.JAR_FLINGER)
                    override val compressionLevel = FakeGradleProperty(Deflater.BEST_SPEED)
                    override val projectPath = FakeGradleProperty("project")
                    override val taskOwner = FakeGradleProperty("task")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService> = FakeGradleProperty(FakeNoOpAnalyticsService())
                }
            }
        }.execute()

        assertThat(output) {
            it.contains("a.txt")
            it.doesNotContain("A.class")
        }
    }

    @Test
    fun testResFromJars() {
        val output = tmp.newFolder().resolve("output.jar")

        val inputJar = tmp.root.resolve("input.jar")
        ZipOutputStream(inputJar.outputStream()).use {
            it.putNextEntry(ZipEntry("A.class"))
            it.closeEntry()
            it.putNextEntry(ZipEntry("sub/B.class"))
            it.closeEntry()
            it.putNextEntry(ZipEntry("a.txt"))
            it.closeEntry()
            it.putNextEntry(ZipEntry("sub/a.txt"))
            it.closeEntry()
        }
        object : BundleLibraryJavaResRunnable() {
            override fun getParameters(): Params {
                return object : Params() {
                    override val output = FakeObjectFactory.factory.fileProperty().fileValue(output)
                    override val inputs = FakeConfigurableFileCollection(inputJar)
                    override val jarCreatorType = FakeGradleProperty(JarCreatorType.JAR_FLINGER)
                    override val compressionLevel = FakeGradleProperty(Deflater.BEST_SPEED)
                    override val projectPath = FakeGradleProperty("project")
                    override val taskOwner = FakeGradleProperty("task")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService> = FakeGradleProperty(FakeNoOpAnalyticsService())
                }
            }
        }.execute()

        assertThat(output) {
            it.contains("a.txt")
            it.contains("sub/a.txt")
            it.doesNotContain("A.class")
            it.doesNotContain("sub/B.class")
        }
    }

    @Test
    fun testResFromDirWithJar() {
        val output = tmp.newFolder().resolve("output.jar")

        val inputDirWithJar = tmp.newFolder().also {
            ZipOutputStream(it.resolve("subJar.jar").outputStream()).use {
                it.putNextEntry(ZipEntry("A.class"))
                it.closeEntry()
            }
        }

        object : BundleLibraryJavaResRunnable() {
            override fun getParameters(): Params {
                return object : Params() {
                    override val output = FakeObjectFactory.factory.fileProperty().fileValue(output)
                    override val inputs = FakeConfigurableFileCollection(inputDirWithJar)
                    override val jarCreatorType = FakeGradleProperty(JarCreatorType.JAR_FLINGER)
                    override val compressionLevel = FakeGradleProperty(Deflater.BEST_SPEED)
                    override val projectPath = FakeGradleProperty("project")
                    override val taskOwner = FakeGradleProperty("task")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService> = FakeGradleProperty(FakeNoOpAnalyticsService())
                }
            }
        }.execute()
        assertThat(output) {
            it.contains("subJar.jar")
            it.doesNotContain("A.class")
        }
    }
}
