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
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.options.SyncOptions
import com.android.builder.dexing.ERROR_DUPLICATE_HELP_PAGE
import com.android.builder.multidex.D8MainDexList
import com.android.testutils.TestClassesGenerator
import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestUtils
import com.google.common.truth.Truth.assertThat
import org.gradle.api.provider.Property
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertFailsWith

/**
 * Test for calculating the main dex list using D8.
 */
class D8MainDexListTaskTest {

    @Rule
    @JvmField
    val tmpDir: TemporaryFolder = TemporaryFolder()

    @Test
    fun testProguardRules() {
        val output = tmpDir.newFile()

        val inputJar = tmpDir.root.resolve("input.jar")
        TestInputsGenerator.jarWithEmptyClasses(inputJar.toPath(), listOf("test/A"))

        val proguardRules = tmpDir.root.resolve("proguard_rules")
        proguardRules.writeText("-keep class test.A")

        object: D8MainDexListTask.MainDexListWorkerAction() {
            override fun getParameters(): Params {
                return object: Params() {
                    override val proguardRules = FakeConfigurableFileCollection(proguardRules)
                    override val programClasses = FakeConfigurableFileCollection(inputJar)
                    override val libraryClasses = FakeConfigurableFileCollection()
                    override val bootClasspath = getBootClasspath()
                    override val userMultidexKeepFile = FakeGradleProperty(null as File?)
                    override val output = FakeGradleProperty(output)
                    override val errorFormat = FakeGradleProperty(SyncOptions.ErrorFormatMode.HUMAN_READABLE)
                    override val projectName = FakeGradleProperty("project")
                    override val taskOwner = FakeGradleProperty("task")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService> = FakeGradleProperty(
                        FakeNoOpAnalyticsService()
                    )
                }
            }
        }.execute()

        assertThat(output.readLines()).containsExactly("test/A.class")
    }

    @Test
    fun testAllInputs() {
        val output = tmpDir.newFile()

        val inputJar = tmpDir.root.resolve("input.jar")
        TestInputsGenerator.jarWithEmptyClasses(inputJar.toPath(), listOf("test/A", "test/B"))

        val proguardRules = tmpDir.root.resolve("proguard_rules")
        proguardRules.writeText("-keep class test.A")

        val userProguardRules = tmpDir.root.resolve("user_proguard_rules")
        userProguardRules.writeText("-keep class test.B")

        object: D8MainDexListTask.MainDexListWorkerAction() {
            override fun getParameters(): Params {
                return object: Params() {
                    override val proguardRules = FakeConfigurableFileCollection(proguardRules, userProguardRules)
                    override val programClasses = FakeConfigurableFileCollection(inputJar)
                    override val libraryClasses = FakeConfigurableFileCollection()
                    override val bootClasspath = getBootClasspath()
                    override val userMultidexKeepFile = FakeGradleProperty(null as File?)
                    override val output = FakeGradleProperty(output)
                    override val errorFormat = FakeGradleProperty(SyncOptions.ErrorFormatMode.HUMAN_READABLE)
                    override val projectName = FakeGradleProperty("project")
                    override val taskOwner = FakeGradleProperty("task")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService> = FakeGradleProperty(FakeNoOpAnalyticsService())
                }
            }
        }.execute()

        assertThat(output.readLines()).containsExactly("test/A.class", "test/B.class")
    }

    @Test
    fun testUserClassesKeptAndDeDuped() {
        val output = tmpDir.newFile()

        val inputJar = tmpDir.root.resolve("input.jar")
        TestInputsGenerator.jarWithEmptyClasses(inputJar.toPath(), listOf("test/A"))

        val userClasses = tmpDir.root.resolve("user_rules.txt")
        userClasses.writeText(
            listOf(
                "test/User1.class",
                "test/User2.class",
                "test/User2.class"
            ).joinToString(separator = System.lineSeparator())
        )

        object: D8MainDexListTask.MainDexListWorkerAction() {
            override fun getParameters(): Params {
                return object: Params() {
                    override val proguardRules = FakeConfigurableFileCollection()
                    override val programClasses = FakeConfigurableFileCollection(inputJar)
                    override val libraryClasses = FakeConfigurableFileCollection()
                    override val bootClasspath = getBootClasspath()
                    override val userMultidexKeepFile = FakeGradleProperty(userClasses)
                    override val output = FakeGradleProperty(output)
                    override val errorFormat = FakeGradleProperty(SyncOptions.ErrorFormatMode.HUMAN_READABLE)
                    override val projectName = FakeGradleProperty("project")
                    override val taskOwner = FakeGradleProperty("task")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService> = FakeGradleProperty(FakeNoOpAnalyticsService())
                }
            }
        }.execute()

        assertThat(output.readLines()).containsExactly("test/User1.class", "test/User2.class")
    }

    @Test
    fun testNoneKept() {
        val output = tmpDir.newFile()

        val inputJar = tmpDir.root.resolve("input.jar")
        TestInputsGenerator.jarWithEmptyClasses(inputJar.toPath(), listOf("test/A"))

        object: D8MainDexListTask.MainDexListWorkerAction() {
            override fun getParameters(): Params {
                return object: Params() {
                    override val proguardRules = FakeConfigurableFileCollection()
                    override val programClasses = FakeConfigurableFileCollection(inputJar)
                    override val libraryClasses = FakeConfigurableFileCollection()
                    override val bootClasspath = getBootClasspath()
                    override val userMultidexKeepFile = FakeGradleProperty(null as File?)
                    override val output = FakeGradleProperty(output)
                    override val errorFormat = FakeGradleProperty(SyncOptions.ErrorFormatMode.HUMAN_READABLE)
                    override val projectName = FakeGradleProperty("project")
                    override val taskOwner = FakeGradleProperty("task")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService> = FakeGradleProperty(FakeNoOpAnalyticsService())
                }
            }
        }.execute()

        assertThat(output.readLines()).isEmpty()
    }

    @Test
    fun testThrowsIfDuplicateClasses() {
        val output = tmpDir.newFile()

        val inputJar1 = tmpDir.root.resolve("input1.jar")
        TestInputsGenerator.jarWithEmptyClasses(inputJar1.toPath(), listOf("test/A"))

        val inputJar2 = tmpDir.root.resolve("input2.jar")
        TestInputsGenerator.jarWithEmptyClasses(inputJar2.toPath(), listOf("test/A"))

        val exception = assertFailsWith(D8MainDexList.MainDexListException::class) {
            object: D8MainDexListTask.MainDexListWorkerAction() {
                override fun getParameters(): Params {
                    return object: Params() {
                        override val proguardRules = FakeConfigurableFileCollection()
                        override val programClasses = FakeConfigurableFileCollection(inputJar1, inputJar2)
                        override val libraryClasses = FakeConfigurableFileCollection()
                        override val bootClasspath = getBootClasspath()
                        override val userMultidexKeepFile = FakeGradleProperty(null as File?)
                        override val output = FakeGradleProperty(output)
                        override val errorFormat = FakeGradleProperty(SyncOptions.ErrorFormatMode.HUMAN_READABLE)
                        override val projectName = FakeGradleProperty("project")
                        override val taskOwner = FakeGradleProperty("task")
                        override val workerKey = FakeGradleProperty("workerKey")
                        override val analyticsService: Property<AnalyticsService> = FakeGradleProperty(FakeNoOpAnalyticsService())
                    }
                }
            }.execute()
        }

        assertThat(exception.message).contains("is defined multiple times")
        assertThat(exception.message).contains(ERROR_DUPLICATE_HELP_PAGE)
    }

    @Test
    fun testMultiReleaseClassesInDir() {
        val output = tmpDir.newFile()

        val inputDir = tmpDir.newFolder("input")
        TestInputsGenerator.dirWithEmptyClasses(inputDir.toPath(), listOf("test/A"))
        inputDir.resolve("META-INF/versions/9/module-info.class").also {
            it.parentFile.mkdirs()
            it.createNewFile()
        }

        object: D8MainDexListTask.MainDexListWorkerAction() {
            override fun getParameters(): Params {
                return object: Params() {
                    override val proguardRules = FakeConfigurableFileCollection()
                    override val programClasses = FakeConfigurableFileCollection(inputDir)
                    override val libraryClasses = FakeConfigurableFileCollection()
                    override val bootClasspath = getBootClasspath()
                    override val userMultidexKeepFile = FakeGradleProperty(null as File?)
                    override val output = FakeGradleProperty(output)
                    override val errorFormat = FakeGradleProperty(SyncOptions.ErrorFormatMode.HUMAN_READABLE)
                    override val projectName = FakeGradleProperty("project")
                    override val taskOwner = FakeGradleProperty("task")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService> = FakeGradleProperty(FakeNoOpAnalyticsService())
                }
            }
        }.execute()

        assertThat(output.readLines()).isEmpty()
    }

    @Test
    fun testMultiReleaseClassesInJar() {
        val output = tmpDir.newFile()

        val inputJar = tmpDir.root.resolve("input.jar").also { file ->
            ZipOutputStream(file.outputStream()).use {
                it.putNextEntry(ZipEntry("test/A.class"))
                it.write(TestClassesGenerator.emptyClass("test", "A"))
                it.closeEntry()
                it.putNextEntry(ZipEntry("META-INF/versions/9/module-info.class"))
                it.closeEntry()
            }
        }

        object: D8MainDexListTask.MainDexListWorkerAction() {
            override fun getParameters(): Params {
                return object: Params() {
                    override val proguardRules = FakeConfigurableFileCollection()
                    override val programClasses = FakeConfigurableFileCollection(inputJar)
                    override val libraryClasses = FakeConfigurableFileCollection()
                    override val bootClasspath = getBootClasspath()
                    override val userMultidexKeepFile = FakeGradleProperty(null as File?)
                    override val output = FakeGradleProperty(output)
                    override val errorFormat = FakeGradleProperty(SyncOptions.ErrorFormatMode.HUMAN_READABLE)
                    override val projectName = FakeGradleProperty("project")
                    override val taskOwner = FakeGradleProperty("task")
                    override val workerKey = FakeGradleProperty("workerKey")
                    override val analyticsService: Property<AnalyticsService> = FakeGradleProperty(FakeNoOpAnalyticsService())
                }
            }
        }.execute()

        assertThat(output.readLines()).isEmpty()
    }

    private fun getBootClasspath(): FakeConfigurableFileCollection {
        return FakeConfigurableFileCollection(TestUtils.resolvePlatformPath("android.jar").toFile())
    }
}
