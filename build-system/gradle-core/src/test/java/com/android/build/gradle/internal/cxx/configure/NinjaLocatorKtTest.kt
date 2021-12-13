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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.cxx.logging.PassThroughDeduplicatingLoggingEnvironment
import com.android.utils.cxx.CxxDiagnosticCode
import com.android.utils.cxx.CxxDiagnosticCode.NINJA_IS_MISSING
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class NinjaLocatorKtTest {
    @Test
    fun `find Ninja at found CMake location`() {
        val found = findNinjaPath(
            cmakePath = File("/path/to/cmake/bin")
        ) { path -> path == "/path/to/cmake/bin" }
        assertThat(found.result).isEqualTo("/path/to/cmake/bin/ninja")
        assertThat(found.errors).isEmpty()
        assertThat(found.warnings).isEmpty()
    }

    @Test
    fun `find Ninja on PATH`() {
        val found = findNinjaPath(
            environmentPaths = listOf("/path/to/ninja/bin")
        ) { path -> path == "/path/to/ninja/bin" }
        assertThat(found.result).isEqualTo("/path/to/ninja/bin/ninja")
        assertThat(found.errors).isEmpty()
        assertThat(found.warnings).isEmpty()
    }

    @Test
    fun `find Ninja ot other SDK CMake location`() {
        val found = findNinjaPath(
            sdkCMakeFolders = listOf("/path/to/cmake/bin")
        ) { path -> path == "/path/to/cmake/bin" }
        assertThat(found.result).isEqualTo("/path/to/cmake/bin/ninja")
        assertThat(found.errors).isEmpty()
        assertThat(found.warnings).isEmpty()
    }

    @Test
    fun `found CMake location has highest precedence`() {
        val found = findNinjaPath(
            cmakePath = File("/path/to/sdk/cmake2/bin"),
            sdkCMakeFolders = listOf("/path/to/sdk/cmake1/bin", "/path/to/sdk/cmake2/bin"),
            environmentPaths = listOf("/path/to/environment1/ninja/bin", "/path/to/environment2/ninja/bin")
        )
        assertThat(found.result).isEqualTo("/path/to/sdk/cmake2/bin/ninja")
        assertThat(found.environmentPathsRetrieved).isFalse()
        assertThat(found.sdkCMakeFoldersRetrieved).isFalse()
        assertThat(found.errors).isEmpty()
        assertThat(found.warnings).isEmpty()
    }

    @Test
    fun `other SDK CMake locations have second highest precedence`() {
        val found = findNinjaPath(
            cmakePath = File("/path/to/sdk/cmake2/bin"),
            sdkCMakeFolders = listOf("/path/to/sdk/cmake1/bin", "/path/to/sdk/cmake2/bin"),
            environmentPaths = listOf("/path/to/environment1/ninja/bin", "/path/to/environment2/ninja/bin")
        ) { path -> path != "/path/to/sdk/cmake2/bin" }
        assertThat(found.result).isEqualTo("/path/to/sdk/cmake1/bin/ninja")
        assertThat(found.environmentPathsRetrieved).isFalse()
        assertThat(found.sdkCMakeFoldersRetrieved).isTrue()
        assertThat(found.errors).isEmpty()
        assertThat(found.warnings).isEmpty()
    }

    @Test
    fun `PATH has lowest precedence`() {
        val found = findNinjaPath(
            cmakePath = File("/path/to/sdk/cmake2/bin"),
            sdkCMakeFolders = listOf("/path/to/sdk/cmake1/bin", "/path/to/sdk/cmake2/bin"),
            environmentPaths = listOf("/path/to/environment1/ninja/bin", "/path/to/environment2/ninja/bin")
        ) { path -> path == "/path/to/environment2/ninja/bin" }
        assertThat(found.result).isEqualTo("/path/to/environment2/ninja/bin/ninja")
        assertThat(found.environmentPathsRetrieved).isTrue()
        assertThat(found.sdkCMakeFoldersRetrieved).isTrue()
        assertThat(found.errors).isEmpty()
        assertThat(found.warnings).isEmpty()
    }

    @Test
    fun `PATH order is honored `() {
        val found = findNinjaPath(
            cmakePath = File("/path/to/sdk/cmake2/bin"),
            sdkCMakeFolders = listOf("/path/to/sdk/cmake1/bin", "/path/to/sdk/cmake2/bin"),
            environmentPaths = listOf("/path/to/environment1/ninja/bin", "/path/to/environment2/ninja/bin")
        ) { path -> path.startsWith("/path/to/environment") }
        assertThat(found.result).isEqualTo("/path/to/environment1/ninja/bin/ninja")
        assertThat(found.environmentPathsRetrieved).isTrue()
        assertThat(found.sdkCMakeFoldersRetrieved).isTrue()
        assertThat(found.errors).isEmpty()
        assertThat(found.warnings).isEmpty()
    }

    @Test
    fun `error message when no Ninja is found `() {
        val found = findNinjaPath(
            cmakePath = File("/path/to/sdk/cmake2/bin"),
            sdkCMakeFolders = listOf("/path/to/sdk/cmake1/bin", "/path/to/sdk/cmake2/bin"),
            environmentPaths = listOf("/path/to/environment1/ninja/bin", "/path/to/environment2/ninja/bin")
        ) { _ -> false }
        assertThat(found.result).isNull()
        assertThat(found.environmentPathsRetrieved).isTrue()
        assertThat(found.sdkCMakeFoldersRetrieved).isTrue()
        assertThat(found.errors).containsExactly(NINJA_IS_MISSING)
        assertThat(found.warnings).isEmpty()
    }

    data class FindNinjaEncounter(
        val errors: MutableList<CxxDiagnosticCode> = mutableListOf(),
        val warnings: MutableList<CxxDiagnosticCode> = mutableListOf(),
        val info: MutableList<String> = mutableListOf(),
        var environmentPathsRetrieved: Boolean = false,
        var sdkCMakeFoldersRetrieved: Boolean = false,
        var result: String? = null
    )

    private fun findNinjaPath(
        cmakePath: File? = null,
        sdkCMakeFolders: List<String> = listOf(),
        environmentPaths: List<String> = listOf(),
        doesNinjaPathExist: (String) -> Boolean = { _ -> true },
    ): FindNinjaEncounter {
        val encounter = FindNinjaEncounter()
        PassThroughDeduplicatingLoggingEnvironment().use { logger ->
            val fileResult = findNinjaPathLogic(
                cmakePath = cmakePath,
                getSdkCmakeFolders = {
                    encounter.sdkCMakeFoldersRetrieved = true
                    // If there is a found CMake path then make sure it's in the list of SDK
                    // folders (since that's true in the real world)
                    if (cmakePath != null) (sdkCMakeFolders.map { File(it) } + cmakePath).sorted()
                    else sdkCMakeFolders.map { File(it) }
                },
                getEnvironmentPaths = {
                    encounter.environmentPathsRetrieved = true
                    environmentPaths.map { File(it) }
                }
            ) { folder ->
                if (doesNinjaPathExist(folder.path.toPosixSlash())) {
                    folder.resolve("ninja")
                } else {
                    null
                }
            }
            if (fileResult != null) {
                encounter.result = fileResult.toString().toPosixSlash()
            }
            encounter.errors +=
                logger.errorMessages.map { message ->
                    CxxDiagnosticCode.values().first { it.errorCode == message.diagnosticCode }
                }
            encounter.warnings +=
                logger.warningMessages.map { message ->
                    CxxDiagnosticCode.values().first { it.warningCode == message.diagnosticCode }
                }
            encounter.info += logger.infos
        }
        return encounter
    }

    private fun String.toPosixSlash() = replace("\\", "/")

}
