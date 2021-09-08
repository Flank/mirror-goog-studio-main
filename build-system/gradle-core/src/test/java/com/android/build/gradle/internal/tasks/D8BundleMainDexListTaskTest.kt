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
import com.android.build.gradle.internal.transforms.NoOpMessageReceiver
import com.android.build.gradle.options.SyncOptions
import com.android.builder.dexing.ClassFileInputs
import com.android.builder.dexing.DexArchiveBuilder
import com.android.builder.dexing.DexParameters
import com.android.builder.dexing.ERROR_DUPLICATE_HELP_PAGE
import com.android.builder.dexing.r8.ClassFileProviderFactory
import com.android.builder.multidex.D8MainDexList
import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestUtils
import com.google.common.truth.Truth.assertThat
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertFailsWith

/**
 * Test for calculating the main dex list using D8.
 */
class D8BundleMainDexListTaskTest {

    @get:Rule
    val tmpDir: TemporaryFolder = TemporaryFolder()

    @Test
    fun testProguardRules() {
        val output = tmpDir.newFile()
        val programDexFiles = listOf(generateDexArchive(listOf("test/A")))
        val proguardRules = tmpDir.root.resolve("proguard_rules")
        proguardRules.writeText("-keep class test.A")

        executeWorkerAction(
            output = output,
            programDexFiles = programDexFiles,
            proguardRulesFiles = listOf(proguardRules)
        )
        assertThat(output.readLines()).containsExactly("test/A.class")
    }

    @Test
    fun testAllInputs() {
        val output = tmpDir.newFile()
        val programDexFiles = listOf(generateDexArchive(listOf("test/A", "test/B")))

        val proguardRules = tmpDir.root.resolve("proguard_rules")
        proguardRules.writeText("-keep class test.A")

        val userProguardRules = tmpDir.root.resolve("user_proguard_rules")
        userProguardRules.writeText("-keep class test.B")

        executeWorkerAction(
            output = output,
            programDexFiles = programDexFiles,
            proguardRulesFiles = listOf(proguardRules, userProguardRules)
        )

        assertThat(output.readLines()).containsExactly("test/A.class", "test/B.class")
    }

    @Test
    fun testUserClassesKeptAndDeDuped() {
        val output = tmpDir.newFile()

        val programDexFiles = listOf(generateDexArchive(listOf("test/A")))

        val userClasses = tmpDir.root.resolve("user_rules.txt")
        userClasses.writeText(
            listOf(
                "test/User1.class",
                "test/User2.class",
                "test/User2.class"
            ).joinToString(separator = System.lineSeparator())
        )
        executeWorkerAction(
            output = output,
            programDexFiles = programDexFiles,
            userMultidexKeepFile = userClasses
        )

        assertThat(output.readLines()).containsExactly("test/User1.class", "test/User2.class")
    }

    @Test
    fun testNoneKept() {
        val output = tmpDir.newFile()

        val programDexFiles = listOf(generateDexArchive(listOf("test/A")))

        executeWorkerAction(
            output = output,
            programDexFiles = programDexFiles
        )

        assertThat(output.readLines()).isEmpty()
    }

    @Test
    fun testThrowsIfDuplicateClasses() {
        val output = tmpDir.newFile()

        val programDexFiles = listOf(
            generateDexArchive(listOf("test/A")), generateDexArchive(listOf("test/A")))

        val exception = assertFailsWith(D8MainDexList.MainDexListException::class) {
            executeWorkerAction(
                output = output,
                programDexFiles = programDexFiles
            )
        }

        assertThat(exception.message).contains("is defined multiple times")
        assertThat(exception.message).contains(ERROR_DUPLICATE_HELP_PAGE)
    }

    private fun executeWorkerAction(
        programDexFiles: Collection<File>,
        proguardRulesFiles: Collection<File> = listOf(),
        libraryClasses: Collection<File> = listOf(),
        userMultidexKeepFile: File? = null,
        output: File
    ) {
        object : D8BundleMainDexListTask.MainDexListWorkerAction() {
            override fun getParameters() = object : Params() {
                override val proguardRules: ConfigurableFileCollection
                    get() = FakeConfigurableFileCollection(proguardRulesFiles)
                override val programDexFiles: ConfigurableFileCollection
                    get() = FakeConfigurableFileCollection(programDexFiles)
                override val libraryClasses: ConfigurableFileCollection
                    get() = FakeConfigurableFileCollection(libraryClasses)
                override val bootClasspath: ConfigurableFileCollection
                    get() = FakeConfigurableFileCollection(
                        TestUtils.resolvePlatformPath("android.jar").toFile())
                override val userMultidexKeepFile: Property<File>
                    get() = FakeGradleProperty(userMultidexKeepFile)
                override val output: RegularFileProperty
                    get() = FakeObjectFactory.factory.fileProperty().fileValue(output)
                override val errorFormat: Property<SyncOptions.ErrorFormatMode>
                    get() = FakeGradleProperty(SyncOptions.ErrorFormatMode.HUMAN_READABLE)
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

    private fun generateDexArchive(emptyClassNames: List<String>) : File {
        val dexArchivePath = tmpDir.newFolder()
        val emptyClassesDir = tmpDir.newFolder().also {
            TestInputsGenerator.dirWithEmptyClasses(it.toPath(), emptyClassNames)
        }
        generateDexArchive(emptyClassesDir, dexArchivePath)
        return dexArchivePath
    }

    private fun generateDexArchive(classesDirOrJar: File, dexArchivePath: File) {
        val builder = DexArchiveBuilder.createD8DexBuilder(
            DexParameters(
                minSdkVersion = 1,
                debuggable = true,
                dexPerClass = true,
                withDesugaring = false,
                desugarBootclasspath = ClassFileProviderFactory(emptyList()),
                desugarClasspath = ClassFileProviderFactory(emptyList()),
                coreLibDesugarConfig = null,
                coreLibDesugarOutputKeepRuleFile = null,
                messageReceiver = NoOpMessageReceiver()
            )
        )
        ClassFileInputs.fromPath(classesDirOrJar.toPath()).use { input ->
            builder.convert(
                input.entries { _, _ -> true },
                dexArchivePath.toPath()
            )
        }
    }
}
