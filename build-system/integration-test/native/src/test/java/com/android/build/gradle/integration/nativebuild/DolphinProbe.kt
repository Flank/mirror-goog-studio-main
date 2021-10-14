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

package com.android.build.gradle.integration.nativebuild

import com.android.build.gradle.integration.common.fixture.model.NativeBuildBenchmarkProject
import com.android.build.gradle.internal.cxx.io.decodeSynchronizeFile
import com.android.build.gradle.internal.cxx.process.decodeExecuteProcess
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * Tests that probe dolphin build benchmark so that issues might be caught earlier
 * than the next perfgate run.
 */
@RunWith(Parameterized::class)
class DolphinProbe(
    private val setupDiff : String
) {
    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun data() = arrayOf(
            "setup-cmake-3.10.2.diff",
            "setup-cmake-3.18.1.diff"
        )
    }

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private lateinit var project: NativeBuildBenchmarkProject

    @Before
    fun before() {
        project = NativeBuildBenchmarkProject(
            relativeBuildRoot = "Source/Android",
            workingFolder = temporaryFolder.newFolder(),
            buildbenchmark = "dolphin.3627ef8a",
            setupDiff = setupDiff
        )
    }

    @Test
    fun `simulate and probe Dolphin_nativeCodeImplChange_fromAS`() = with(project) {
        // This simulates Dolphin_nativeCodeImplChange_fromAS and enables
        // structured logging for the externalNativeBuildRelease which is
        // supposed to be mostly a NOP since Release variant-folds with
        // Debug in this project.
        addArgument("-Pandroid.injected.build.abi=arm64-v8a")
        run("externalNativeBuildDebug")
        enableCxxStructuredLogging()
        run("externalNativeBuildRelease")

        // Check to make sure the expected number of expensive operations
        // happens by reading structured logs.

        // Expect one call to Ninja to perform the NOP build
        val procs = readStructuredLogs(::decodeExecuteProcess)
        assertThat(procs).hasSize(1)

        // Expect one file sync to republish the .so file from the soFolder
        // to the Release folder. It should be a symlink.
        val synchronizeFiles = readStructuredLogs(::decodeSynchronizeFile)
        val fileSyncSummary = synchronizeFiles.map {
            val target = File(it.destinationFile)
            val relative = target.relativeTo(target.parentFile.parentFile.parentFile)
            "$relative[${it.outcome}]"
        }.sorted().joinToString("\n")
        assertThat(fileSyncSummary).isEqualTo(
            "obj/arm64-v8a/libmain.so[CREATED_HARD_LINK_FROM_SOURCE_TO_DESTINATION]")
        assertThat(synchronizeFiles).hasSize(1)

        // Lastly, perform a Clean just to make sure it is possible
        run("clean")
    }
}
