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

package com.android.build.gradle.internal.cxx.json

import com.android.build.gradle.internal.cxx.StructuredLog
import com.android.utils.cxx.CxxDiagnosticCode
import com.android.utils.cxx.CxxDiagnosticCode.BUILD_FILE_DID_NOT_EXIST
import com.android.utils.cxx.CxxDiagnosticCode.BUILD_TARGET_COMMAND_COMPONENTS_COMMAND_DID_NOT_EXIST
import com.android.utils.cxx.CxxDiagnosticCode.BUILD_TARGET_COMMAND_COMPONENTS_DID_NOT_EXIST
import com.android.utils.cxx.CxxDiagnosticCode.COULD_NOT_CANONICALIZE_PATH
import com.android.utils.cxx.CxxDiagnosticCode.LIBRARY_ABI_NAME_DID_NOT_EXIST
import com.android.utils.cxx.CxxDiagnosticCode.LIBRARY_ABI_NAME_IS_INVALID
import com.android.utils.cxx.CxxDiagnosticCode.LIBRARY_ARTIFACT_NAME_DID_NOT_EXIST
import com.android.utils.cxx.CxxDiagnosticCode.LIBRARY_HAD_MULTIPLE_ABIS
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LintKtTest {
    private val fileBeingLinted = File("android_gradle_build_mini.json")
    private val filePathThatIsNotCanonicalizableOnAllPlatforms = File("\u0000")

    @Test
    fun `no error case`() {
        NativeBuildConfigValueMini().apply {
            buildFiles = listOf(tempFolder.newFile("CMakeLists.txt"))
        }.lint(fileBeingLinted)
        log.assertNoErrors()
    }

    @Test
    fun `non-existent buildFile`() {
        NativeBuildConfigValueMini().apply {
            buildFiles = listOf(File("CMakeLists.txt"))
        }.lint(fileBeingLinted)
        log.assertError(
            BUILD_FILE_DID_NOT_EXIST,
            "expected buildFiles file 'CMakeLists.txt' to exist"
        )
    }

    @Test
    fun `non-existent buildTargetsCommandComponents command`() {
        NativeBuildConfigValueMini().apply {
            buildTargetsCommandComponents = listOf("path/to/cmake.exe")
        }.lint(fileBeingLinted)
        log.assertError(
            BUILD_TARGET_COMMAND_COMPONENTS_DID_NOT_EXIST,
            "expected buildTargetsCommandComponents command 'path/to/cmake.exe' to exist"
        )
    }

    @Test
    fun `empty library buildCommandComponents`() {
        NativeBuildConfigValueMini().apply {
            libraries = mutableMapOf()
            libraries["lib1"] = NativeLibraryValueMini()
        }.lint(fileBeingLinted)
        log.assertError(
            BUILD_TARGET_COMMAND_COMPONENTS_DID_NOT_EXIST,
            "expected buildTargetsCommandComponents or lib1.buildCommandComponents to exist"
        )
    }

    @Test
    fun `non-existent library buildCommandComponents command`() {
        NativeBuildConfigValueMini().apply {
            libraries = mutableMapOf()
            libraries["lib1"] = NativeLibraryValueMini().apply {
                buildCommandComponents = listOf("path/to/cmake.exe")
            }
        }.lint(fileBeingLinted)
        log.assertError(
            BUILD_TARGET_COMMAND_COMPONENTS_COMMAND_DID_NOT_EXIST,
            "expected lib1.buildCommandComponents command 'path/to/cmake.exe' to exist"
        )
    }

    @Test
    fun `non-existent library artifact name`() {
        NativeBuildConfigValueMini().apply {
            libraries = mutableMapOf()
            libraries["lib1"] = NativeLibraryValueMini()
        }.lint(fileBeingLinted)
        log.assertError(
            LIBRARY_ARTIFACT_NAME_DID_NOT_EXIST,
            "expected lib1.artifactName to exist"
        )
    }

    @Test
    fun `non-canonicalizable runtime library name`() {
        NativeBuildConfigValueMini().apply {
            libraries = mutableMapOf()
            libraries["lib1"] = NativeLibraryValueMini().apply {
                runtimeFiles = listOf(filePathThatIsNotCanonicalizableOnAllPlatforms)
            }
        }.lint(fileBeingLinted)
        log.assertError(
            COULD_NOT_CANONICALIZE_PATH,
            "Could not canonicalize '\u0000' in lib1.runtimeFiles due to IOException"
        )
    }

    @Test
    fun `missing ABI`() {
        NativeBuildConfigValueMini().apply {
            libraries = mutableMapOf()
            libraries["lib1"] = NativeLibraryValueMini().apply {
            }
        }.lint(fileBeingLinted)
        log.assertError(
            LIBRARY_ABI_NAME_DID_NOT_EXIST,
            "expected lib1.abi to exist"
        )
    }

    @Test
    fun `misspelled ABI name`() {
        val buildCommand = tempFolder.newFile("cmake")
        buildCommand.writeText("")
        NativeBuildConfigValueMini().apply {
            libraries = mutableMapOf()
            libraries["lib1"] = NativeLibraryValueMini().apply {
                buildCommandComponents = listOf(buildCommand.path)
                artifactName = "liblib1"
                abi = "x86_65"
            }
        }.lint(fileBeingLinted)
        log.assertError(
            LIBRARY_ABI_NAME_IS_INVALID,
            "lib1.abi 'x86_65' is invalid. Valid values are 'armeabi-v7a, arm64-v8a, x86, x86_64'"
        )
    }

    @Test
    fun `mismatched ABI names`() {
        val buildCommand = tempFolder.newFile("cmake")
        buildCommand.writeText("")
        NativeBuildConfigValueMini().apply {
            libraries = mutableMapOf()
            libraries["lib1"] = NativeLibraryValueMini().apply {
                buildCommandComponents = listOf(buildCommand.path)
                artifactName = "liblib1"
                abi = "x86_64"
            }
            libraries["lib2"] = NativeLibraryValueMini().apply {
                buildCommandComponents = listOf(buildCommand.path)
                artifactName = "liblib2"
                abi = "x86"
            }
        }.lint(fileBeingLinted)
        log.assertError(
            LIBRARY_HAD_MULTIPLE_ABIS,
            "unexpected mismatched library ABIs: x86_64, x86"
        )
    }

    @Test
    fun `same ABI twice is not an error`() {
        val buildCommand = tempFolder.newFile("cmake")
        buildCommand.writeText("")
        NativeBuildConfigValueMini().apply {
            libraries = mutableMapOf()
            libraries["lib1"] = NativeLibraryValueMini().apply {
                buildCommandComponents = listOf(buildCommand.path)
                artifactName = "liblib1"
                abi = "armeabi-v7a"
            }
            libraries["lib2"] = NativeLibraryValueMini().apply {
                buildCommandComponents = listOf(buildCommand.path)
                artifactName = "liblib2"
                abi = "armeabi-v7a"
            }
        }.lint(fileBeingLinted)
        log.assertNoErrors()
    }

    @Rule @JvmField val tempFolder = TemporaryFolder()
    private val log by lazy { StructuredLog(tempFolder) }
    @Before fun before() { log }
}
