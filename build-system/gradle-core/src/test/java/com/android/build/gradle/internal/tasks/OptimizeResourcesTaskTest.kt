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

import com.android.testutils.TestResources
import com.android.testutils.TestUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/** Test cases for [OptimizeResourcesTask]. */
@RunWith(Parameterized::class)
class OptimizeResourcesTaskTest(private val aaptOptimizeArg: String) {

    private val testApkName = "santa-tracker-release.apk"

    private val aaptOptimizeCommand = "optimize"

    private val testAapt2 = TestUtils.getAapt2().toFile().absoluteFile

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun aaptOptimizeArg() = AAPT2OptimizeFlags.values().map { it.flag }
    }

    @Test
    fun testInvokeAaptOptimize_producesExpectedOutput() {
        val sourceApk = TestResources.getFile(OptimizeResourcesTask::class.java, testApkName)
        sourceApk.setExecutable(true)

        val testFolder = temporaryFolder.newFolder()
        val optimizedApk = File(testFolder, "optimized.apk")

        val flags = arrayOf(
                aaptOptimizeCommand,
                sourceApk.path,
                aaptOptimizeArg,
                "-o",
                optimizedApk.path
        )

        invokeAapt(testAapt2, *flags)

        val previousApkSize = sourceApk.length()
        val optimizedApkSize = optimizedApk.length()
        assertThat(previousApkSize).isNotEqualTo(0)
        assertThat(optimizedApkSize).isAtMost(previousApkSize)
        assertThat(optimizedApkSize).isNotEqualTo(0)
    }

    @Test
    fun testDoFullTaskAction_ResourceShrinkingEnabled() {
        val params = getTestOptimizeResourcesParams()

        doFullTaskAction(params)

        val previousApkSize = params.inputApkFile.length()
        val optimizedApkSize = params.outputApkFile.length()
        assertThat(optimizedApkSize).isAtMost(previousApkSize)
        assertThat(optimizedApkSize).isNotEqualTo(0)
    }

    @Test
    fun testVerifyAaptFlagEnabled() {
        val params = getTestOptimizeResourcesParams()

        val result = verifyAaptFlagEnabled(params)
        assertThat(result).isTrue()
    }

    private fun getTestOptimizeResourcesParams() : OptimizeResourcesTask.OptimizeResourcesParams {
        val sourceApk = TestResources.getFile(OptimizeResourcesTask::class.java, testApkName)

        val testFolder = temporaryFolder.newFolder()
        val optimizedApk = File(testFolder, "optimized.apk")

        return OptimizeResourcesTask.OptimizeResourcesParams(
                aapt2Executable = testAapt2,
                inputApkFile = sourceApk,
                enableResourcePathShortening = aaptOptimizeArg == "--shorten-resource-paths",
                enableSparseResourceEncoding = aaptOptimizeArg == "--enable-sparse-encoding",
                enableResourceObfuscation = aaptOptimizeArg == "--collapse-resource-names",
                outputApkFile = optimizedApk
        )
    }
}