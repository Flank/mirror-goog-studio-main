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

package com.android.build.gradle.internal.cxx.attribution

import com.android.build.gradle.internal.BuildSessionImpl
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.CxxModuleModel
import com.android.build.gradle.internal.cxx.model.CxxVariantModel
import com.android.build.gradle.internal.cxx.services.CxxBuildSessionService
import com.google.common.truth.Truth
import org.gradle.api.invocation.Gradle
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files

class UtilsKtTest {

    init {
        BuildSessionImpl.getSingleton().initialize(mock(Gradle::class.java))
    }

    @get:Rule
    val testDir: TemporaryFolder = TemporaryFolder()
    private val mockAbiModel: CxxAbiModel = mock(CxxAbiModel::class.java)
    private val mockVariantModel = mock(CxxVariantModel::class.java)
    private lateinit var buildSession: CxxBuildSessionService
    private lateinit var buildId: String
    private lateinit var buildAttributionFolder: File
    private lateinit var ninjaLogFile: File

    @Before
    fun setUp() {
        buildSession = CxxBuildSessionService.getInstance()
        buildId = buildSession.buildId.toString()
        buildAttributionFolder = testDir.root.resolve(".cxx/attribution/")
        ninjaLogFile = testDir.root.resolve(".ninja_log")
        `when`(mockAbiModel.ninjaLogFile).thenReturn(testDir.root.resolve(".ninja_log"))
        `when`(mockAbiModel.variant).thenReturn(mockVariantModel)
        val mockModuleModel = mock(CxxModuleModel::class.java)
        `when`(mockVariantModel.variantName).thenReturn("debug")
        `when`(mockVariantModel.module).thenReturn(mockModuleModel)
        `when`(mockModuleModel.buildAttributionFolder).thenReturn(buildAttributionFolder)
        `when`(mockModuleModel.gradleModulePathName).thenReturn(":app")
    }

    @After
    fun tearDown() {
        buildSession.allBuiltAbis.clear()
    }

    @Test
    fun `appendTimestampAndBuildIdToNinjaLog append time stamp and build ID`() {
        ninjaLogFile.writeText("# ninja log v5\n")
        appendTimestampAndBuildIdToNinjaLog(mockAbiModel)
        val ninjaLogLines = ninjaLogFile.readLines()
        Truth.assertThat(ninjaLogLines).hasSize(2)
        Truth.assertThat(ninjaLogLines[0]).isEqualTo("# ninja log v5")
        Truth.assertThat(ninjaLogLines[1]).matches("# \\d+ $buildId")
    }

    @Test
    fun `appendTimestampAndBuildIdToNinjaLog appends magic word if file does not exist`() {
        val ninjaLogFile = testDir.root.resolve(".ninja_log")
        appendTimestampAndBuildIdToNinjaLog(mockAbiModel)
        val ninjaLogLines = ninjaLogFile.readLines()
        Truth.assertThat(ninjaLogLines).hasSize(2)
        Truth.assertThat(ninjaLogLines[0]).isEqualTo("# ninja log v5")
        Truth.assertThat(ninjaLogLines[1]).matches("# \\d+ $buildId")
    }

    @Test
    fun `collectNinjaLogs works`() {
        val appDebugX86 = testDir.root.resolve("app-debug-x86")
        buildSession.allBuiltAbis.add(mock(CxxAbiModel::class.java).apply {
            `when`(abi).thenReturn(Abi.X86)
            `when`(ninjaLogFile).thenReturn(appDebugX86)
            `when`(variant).thenReturn(mockVariantModel)
        })
        appDebugX86.writeText(
            """
                # ninja log v5
                # 1 prev-build-id
                previous metric
                # 2 $buildId
                x86 metric 1
                x86 metric 2
            """.trimIndent()
        )
        val appDebugX8664 = testDir.root.resolve("app-debug-x86_64")
        buildSession.allBuiltAbis.add(mock(CxxAbiModel::class.java).apply {
            `when`(abi).thenReturn(Abi.X86_64)
            `when`(ninjaLogFile).thenReturn(appDebugX8664)
            `when`(variant).thenReturn(mockVariantModel)
        })
        appDebugX8664.writeText(
            """
                # ninja log v5
                # 1 prev-build-id
                previous metric
                # 2 $buildId
                x86_64 metric 1
                x86_64 metric 2
            """.trimIndent()
        )
        collectNinjaLogs(buildSession)

        val (zipFile) = buildAttributionFolder.listFiles()
        Truth.assertThat(zipFile.name).matches("ninja_build_log_\\d+\\.zip")
        val zipFileSystem = FileSystems.newFileSystem(
            URI.create("jar:${zipFile.toURI()}"),
            mapOf("create" to "true")
        )
        Truth.assertThat(Files.readAllLines(zipFileSystem.getPath("/app/debug/x86")))
            .containsExactly(
                "# 2 $buildId",
                "x86 metric 1",
                "x86 metric 2"
            ).inOrder()
        Truth.assertThat(Files.readAllLines(zipFileSystem.getPath("/app/debug/x86_64")))
            .containsExactly(
                "# 2 $buildId",
                "x86_64 metric 1",
                "x86_64 metric 2"
            ).inOrder()
    }
}
