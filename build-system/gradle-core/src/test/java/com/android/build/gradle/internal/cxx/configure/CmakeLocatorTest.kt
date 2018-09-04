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

package com.android.build.gradle.internal.cxx.configure

import com.android.repository.Revision
import com.android.repository.api.LocalPackage
import com.android.repository.testframework.FakePackage
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import java.io.IOException

class CmakeLocatorTest {
    private val newline = System.lineSeparator()
    private val slash = File.separator

    private fun fakeLocalPackageOf(path: String, revision: String): FakePackage.FakeLocalPackage {
        // path is like p;1.1
        val result = FakePackage.FakeLocalPackage(path)
        result.setRevision(Revision.parseRevision(revision))
        return result
    }

    data class FindCmakeEncounter(
        val errors: MutableList<String> = mutableListOf(),
        val warnings: MutableList<String> = mutableListOf(),
        val info: MutableList<String> = mutableListOf(),
        var environmentPathsRetrieved: Boolean = false,
        var sdkPackagesRetrieved: Boolean = false,
        var downloadRemote: Boolean = false,
        var result: String? = null,
        var downloadAttempts: Int = 0
    )

    private fun findCmakePath(
        cmakeVersionFromDsl: String?,
        environmentPaths: () -> List<File> = { listOf() },
        cmakePathFromLocalProperties: File? = null,
        cmakeVersion: (File) -> Revision? = { _ -> null },
        repositoryPackages: () -> List<LocalPackage> = { listOf() },
        downloader: () -> Unit = {}
    ): FindCmakeEncounter {
        val encounter = FindCmakeEncounter()
        val fileResult = findCmakePathLogic(
            cmakeVersionFromDsl = cmakeVersionFromDsl,
            cmakePathFromLocalProperties = cmakePathFromLocalProperties,
            error = { message -> encounter.errors += message },
            warn = { message -> encounter.warnings += message },
            info = { message -> encounter.info += message },
            environmentPaths = {
                encounter.environmentPathsRetrieved = true
                environmentPaths()
            },
            cmakeVersion = cmakeVersion,
            repositoryPackages = {
                encounter.sdkPackagesRetrieved = true
                repositoryPackages()
            },
            downloader = {
                encounter.downloadAttempts = encounter.downloadAttempts + 1
                downloader()
            }
        )
        if (fileResult != null) {
            encounter.result = fileResult.toString().replace("\\", "/")
        }
        if (encounter.result != null) {
            // Should be the cmake install folder without the "bin"
            assertThat(encounter.result!!.endsWith("bin")).isFalse()
        }
        return encounter
    }

    private fun expectException(message: String, action: () -> Unit) {
        try {
            action()
            throw RuntimeException("expected exception")
        } catch (e: Throwable) {
            if (message != e.message) {
                println("Expected: $message")
                println("Actual: ${e.message}")
            }
            assertThat(e).hasMessageThat().isEqualTo(message)
        }
    }

    private fun expectExceptionAndTriggerAndroidStudioDownload(message: String, action: () -> Unit) {
        try {
            action()
            throw RuntimeException("expected exception")
        } catch (e: Throwable) {
            if (message != e.message) {
                println("Expected: $message")
                println("Actual: ${e.message}")
                assertThat(e).hasMessageThat().isEqualTo(message)
            }
            if (!e.message!!.contains("was not found in PATH or by cmake.dir propert")) {
                println("Expected: Message containing 'as not found in PATH or by cmake.dir propert'")
                assertThat(e.message).contains("was not found in PATH or by cmake.dir propert")
            }

        }
    }

    /**
     * User Request: "3.12"
     * Candidates from path: "3.12.0-rc1"
     * Result: "3.12.0-rc1" from path is selected because the prefix "3.12" matches.
     */
    @Test
    fun partialVersionNumberMatchWithRc() {
        val encounter = findCmakePath(
            cmakeVersionFromDsl = "3.12",
            environmentPaths = {
                listOf(
                    File("/a/b/c/cmake/3.12.0-rc1/bin"),
                    File("/d/e/f")
                )
            },
            cmakeVersion = { folder ->
                if (folder.toString().replace("\\", "/") == "/a/b/c/cmake/3.12.0-rc1/bin") {
                    Revision.parseRevision("3.12.0-rc1")
                } else {
                    null
                }
            })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/a/b/c/cmake/3.12.0-rc1"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    /**
     * User request: "3.12"
     * Candidates from path: "3.12.0-rc1" and "3.12.0"
     * Result: "3.12.0-rc1" from path is selected because it is first on the path.
     */
    @Test
    fun partialVersionNumberMatchWithRcAndProduction() {
        val encounter = findCmakePath(
            cmakeVersionFromDsl = "3.12",
            environmentPaths = {
                listOf(
                    File("/a/b/c/cmake/3.12.0-rc1/bin"),
                    File("/a/b/c/cmake/3.12.0/bin"),
                    File("/d/e/f")
                )
            },
            cmakeVersion = { folder ->
                when (folder.toString().replace("\\", "/")) {
                    "/a/b/c/cmake/3.12.0-rc1/bin" -> Revision.parseRevision("3.12.0-rc1")
                    "/a/b/c/cmake/3.12.0/bin" -> Revision.parseRevision("3.12.0")
                    else -> null
                }
            })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/a/b/c/cmake/3.12.0-rc1"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    /**
     * User request: "3.12.0"
     * Candidates from path: "3.12.0-a" and "3.12.0-b"
     * Result: "3.12.0-a" from path is selected because it is the first on path.
     */
    @Test
    fun exactVersionNumberMatchWithRcAndProduction() {
        val encounter = findCmakePath(
            cmakeVersionFromDsl = "3.12.0",
            environmentPaths = {
                listOf(
                    File("/a/b/c/cmake/3.12.0-a/bin"),
                    File("/a/b/c/cmake/3.12.0-b/bin"),
                    File("/d/e/f")
                )
            },
            cmakeVersion = { folder ->
                when (folder.toString().replace("\\", "/")) {
                    "/a/b/c/cmake/3.12.0-a/bin" -> Revision.parseRevision("3.12.0")
                    "/a/b/c/cmake/3.12.0-b/bin" -> Revision.parseRevision("3.12.0")
                    else -> null
                }
            })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/a/b/c/cmake/3.12.0-a"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    /**
     * User request: "3.12"
     * Candidates from path: "3.12.0-rc1" and "3.12.0-rc2"
     * Result: "3.12.0-rc1" is selected because it is first on the path.
     */
    @Test
    fun exactVersionMatchesTwoLocations() {
        val encounter = findCmakePath(
            cmakeVersionFromDsl = "3.12",
            environmentPaths = {
                listOf(
                    File("/a/b/c/cmake/3.12.0-rc1/bin"),
                    File("/a/b/c/cmake/3.12.0-rc2/bin"),
                    File("/d/e/f")
                )
            },
            cmakeVersion = { folder ->
                when (folder.toString().replace("\\", "/")) {
                    "/a/b/c/cmake/3.12.0-rc1/bin" -> Revision.parseRevision("3.12.0-rc1")
                    "/a/b/c/cmake/3.12.0-rc2/bin" -> Revision.parseRevision("3.12.0-rc2")
                    else -> null
                }
            })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/a/b/c/cmake/3.12.0-rc1"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    /**
     * User request: "3.12.0-rc1"
     * Candidates from path: "3.12.0-rc1" and "3.12.0"
     * Result: "3.12.0-rc1" is selected because it is the only match.
     */
    @Test
    fun partialVersionNumberMatchWithTwoRcs() {
        val encounter = findCmakePath(
            cmakeVersionFromDsl = "3.12.0-rc1",
            environmentPaths = {
                listOf(
                    File("/a/b/c/cmake/3.12.0-rc1/bin"),
                    File("/a/b/c/cmake/3.12.0/bin"),
                    File("/d/e/f")
                )
            },
            cmakeVersion = { folder ->
                when (folder.toString().replace("\\", "/")) {
                    "/a/b/c/cmake/3.12.0-rc1/bin" -> Revision.parseRevision("3.12.0-rc1")
                    "/a/b/c/cmake/3.12.0/bin" -> Revision.parseRevision("3.12.0")
                    else -> null
                }
            })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/a/b/c/cmake/3.12.0-rc1"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    /**
     * User request: "3.6.0-rc2"
     * Candidates from Local Repository: "3.6.0"
     * Result: "3.6.0" from local repository is selected.
     */
    @Test
    fun sdkCmakeExistsLocally() {
        val localCmake = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = "3.6.0",
            repositoryPackages = { listOf(localCmake) })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/sdk/cmake/3.6.4111459"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    /**
     * User request: default
     * Candidates from Local Repository: "3.6.4111459"
     * Result: "3.6.4111459" from local repository is selected.
     */
    @Test
    fun sdkCmakeDefaultedExistsLocally() {
        val localCmake = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = null,
            repositoryPackages = { listOf(localCmake) })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/sdk/cmake/3.6.4111459"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    /**
     * User request: "3.10.2"
     * Candidates from Local Repository: "3.6.4111459"
     * Result: No matches.
     */
    @Test
    fun requestedCmakeNotFoundFallbackToSdk() {
        val localCmake = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        expectExceptionAndTriggerAndroidStudioDownload(
            "CMake '3.10.2' was not found in PATH or by cmake.dir property.$newline" +
                    "- CMake '3.6.0' found in SDK did not satisfy requested version '3.10.2' because MINOR value 6 wasn't exactly 10."
        )
        {
            findCmakePath(
                cmakeVersionFromDsl = "3.10.2",
                repositoryPackages = { listOf(localCmake) })
        }
    }

    /**
     * User request: "3.6.1234567"
     * Candidates from Local Repository: "3.6.4111459"
     * Result: No matches.
     */
    @Test
    fun requestedSdkLikeCmakeNotFoundFallbackToSdk() {
        val localCmake = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        expectExceptionAndTriggerAndroidStudioDownload(
            "CMake '3.6.1234567' was not found in PATH " +
                    "or by cmake.dir property.$newline" +
                    "- CMake '3.6.0' found in SDK did not satisfy requested version " +
                    "'3.6.1234567' because MICRO value 0 wasn't exactly 1234567."
        )
        {
            findCmakePath(
                cmakeVersionFromDsl = "3.6.1234567", // <-- intentionally wrong
                repositoryPackages = { listOf(localCmake) })
        }
    }

    /**
     * User request: default
     * Candidates from Local Repository: None
     * Candidates from Path: "3.8.0"
     * Downloader: Null downloader that does not download anything.
     * Result: Default version not found. Download attempt from SDK failed.
     */
    @Test
    fun noVersionInDslAndNoLocalSdkVersion() {
        expectExceptionAndTriggerAndroidStudioDownload(
            "CMake '3.6.0' was not found in PATH or by cmake.dir property."
        ) {
            findCmakePath(
                cmakeVersionFromDsl = null,
                environmentPaths = {
                    listOf(
                        File("/a/b/c/cmake/3.8.0/bin"),
                        File("/d/e/f")
                    )
                })
        }
    }

    /**
     * User request: default
     * Candidates from Local Repository: None
     * Downloader: Successfully downloads "3.6.0-rc2" from SDK.
     * Result: Default version not found. Download attempt from SDK succeeded. Version "3.6.4111459"
     * from local repository is selected.
     */
    @Test
    fun noVersionInDslAndNoLocalSdkVersion2() {
        val repositoryPackages = emptyList<LocalPackage>().toMutableList()
        val encounter = findCmakePath(
            cmakeVersionFromDsl = null,
            repositoryPackages = { repositoryPackages },
            downloader = {
                repositoryPackages.add(
                    fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
                )
            }
        )
        assertThat(encounter.downloadAttempts).isEqualTo(1)
    }

    /**
     * User request: "3.bob"
     * Candidates from Local Repository: "3.6.4111459"
     * Result: Invalid revision error is issued. Default version "3.6.4111459" is selected.
     */
    @Test
    fun incorrectlyFormattedRevisionInBuildGradle() {
        val localCmake = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = "3.bob",
            repositoryPackages = { listOf(localCmake) })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/sdk/cmake/3.6.4111459"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors.single()).isEqualTo(
            "CMake version '3.bob' is not formatted correctly."
        )
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    /**
     * User request: default
     * Candidates from Local Repository: "3.10.2"
     * Downloader: Null downloader that does not download anything.
     * Result: Default version not found in local repository. Download attempt from SDK failed.
     *
     * In this scenario, even though there is a CMake version in the SDK it is still an
     * error because when there is no version specified in build.gradle then the exact default
     * version is required.
     */
    @Test
    fun noDslButUpVersionInSdk() {
        expectExceptionAndTriggerAndroidStudioDownload(
            "CMake '3.6.0' was not found in PATH or by cmake.dir property.$newline" +
                    "- CMake '3.10.2' found in SDK did not satisfy requested version " +
                    "'3.6.0' because MINOR value 10 wasn't exactly 6."
        ) {
            val localCmake = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.2")
            findCmakePath(
                cmakeVersionFromDsl = null,
                repositoryPackages = { listOf(localCmake) })
        }
    }

    /**
     * User request: default
     * Candidates from Local Repository: "3.6.4111459", "3.10.4111459"
     * Result: "3.6.4111459" from local repository is selected.
     *
     * In this scenario, even though there is a better CMake version in the local Repository, the
     * Cmake with a lower version is selected, because when there is no version specified in
     * build.gradle then the exact default version is required.
     */
    @Test
    fun noDslButMultipleUpVersionInSdk1() {
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.2")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = null,
            repositoryPackages = { listOf(threeSix, threeTen) })

        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/sdk/cmake/3.6.4111459"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    /**
     * User request: default
     * Candidates from Local Repository: "3.6.4111459", "3.10.2"
     * Result: "3.6.4111459" from local repository is selected.
     *
     * In this scenario, even though there is a better CMake version in the local Repository, the
     * Cmake with a lower version is selected, because when there is no version specified in
     * build.gradle then the exact default version is required.
     */
    @Test
    fun noDslButMultipleUpVersionInSdk2() {
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.2")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = null,
            repositoryPackages = { listOf(threeTen, threeSix) })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/sdk/cmake/3.6.4111459"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    /**
     * User request: "3.6.0"
     * Candidates from Local Repository:  "3.10.2"
     * Downloader: Null downloader that does not download anything.
     * Result: Version not found. Download attempt from SDK failed.
     */
    @Test
    fun dslButUpVersionInSdk() {
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.2")
        expectExceptionAndTriggerAndroidStudioDownload(
            "CMake '3.6.0' was not found in PATH or by cmake.dir property.$newline" +
                    "- CMake '3.10.2' found in SDK did not satisfy requested version '3.6.0'" +
                    " because MINOR value 10 wasn't exactly 6."
        ) {
            findCmakePath(
                cmakeVersionFromDsl = "3.6.0",
                repositoryPackages = { listOf(threeTen) })
        }
    }

    /**
     * User request: "3.6.4111459"
     * Candidates from Local Repository:  "3.6.0", "3.10.2"
     * Result: An error is reported even though "3.6.4111459" is the SDK version of 3.6.0-rc2
     */
    @Test
    fun dslWithMultipleVersionInSdk() {
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.2")
        expectExceptionAndTriggerAndroidStudioDownload(
            "CMake '3.6.4111459' was not found in PATH or by cmake.dir property.$newline" +
                    "- CMake '3.10.2' found in SDK did not satisfy requested version '3.6.4111459' because MINOR value 10 wasn't exactly 6.$newline" +
                    "- CMake '3.6.0' found in SDK did not satisfy requested version '3.6.4111459' because MICRO value 0 wasn't exactly 4111459."
        ) {
            findCmakePath(
                cmakeVersionFromDsl = "3.6.4111459",
                environmentPaths = {
                    listOf(
                        File("/a/b/c/cmake/bin"),
                        File("/d/e/f")
                    )
                },
                repositoryPackages = { listOf(threeTen, threeSix) })
        }
    }

    /**
     * User request: "3.10.2"
     * Candidates from Local Repository:  "3.6.4111459", "3.10.2"
     * Result: "3.10.2" from local repository is selected.
     */
    @Test
    fun dslWithMultipleVersionInSdk2() {
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.2")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = "3.10",
            environmentPaths = {
                listOf(
                    File("/a/b/c/cmake/bin"),
                    File("/d/e/f")
                )
            },
            repositoryPackages = { listOf(threeTen, threeSix) })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/sdk/cmake/3.10.4111459"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    /**
     * User request: "3.6.0"
     * Candidates from Local Repository:  "3.6.4111459", "3.10.2"
     * Result: "3.6.4111459" from local repository is selected.
     *
     * In this scenario, the user has asked for 3.6.0. This is the CMake-reported version
     * of the forked CMake 3.6.4111459. As a helper, and for backward compatibility, translate
     * this version for the user.
     */
    @Test
    fun dslWithForkCmakeVersion3dot6() {
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.2")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = "3.6.0",
            repositoryPackages = { listOf(threeTen, threeSix) })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!).isEqualTo(
            "/sdk/cmake/3.6.4111459"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    /**
     * In this scenario the user specified 3.2+ which means 3.2 or higher. However, 3.2 is known
     * in advance to be too low so we give an error up front.
     */
    @Test
    fun dslLowVersionPlus() {
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.2")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = "3.2+",
            repositoryPackages = { listOf(threeTen, threeSix) })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!).isEqualTo(
            "/sdk/cmake/3.10.4111459"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors.single()).isEqualTo(
            "CMake version '3.2' is too low. Use 3.7.0 or higher."
        )
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    /**
     * In this scenario, user specified 3.7+ and CMake 3.10 is available in the SDK so use it.
     */
    @Test
    fun dslThreeSevenVersionPlus() {
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.2")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = "3.7+",
            repositoryPackages = { listOf(threeTen, threeSix) })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!).isEqualTo(
            "/sdk/cmake/3.10.4111459"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    /**
     * In this scenario the user specified exactly the fork version but with a +. In this case
     * we still use 3.10.2 because it is higher.
     */
    @Test
    fun forkPlusRequestedAndThreeTenIsAvailableInSdkAscendingRevision() {
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.2")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = "3.6.0+",
            repositoryPackages = { listOf(threeTen, threeSix) })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!).isEqualTo(
            "/sdk/cmake/3.10.4111459"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    /**
     * In this scenario the user specified exactly the fork version but with a +. In this case
     * we still use 3.10.2 because it is higher.
     */
    @Test
    fun forkPlusRequestedAndThreeTenIsAvailableInSdkDescendingRevision() {
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.2")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = "3.6.0+",
            repositoryPackages = { listOf(threeSix, threeTen) })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!).isEqualTo(
            "/sdk/cmake/3.10.4111459"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    /**
     * In this scenario the user specified exactly the fork version but with a +. There is no
     * 3.10 downloaded so we use 3.6
     */
    @Test
    fun forkPlusRequestedAndThreeTenIsNotAvailableInSdk() {
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = "3.6.0-rc2+",
            repositoryPackages = { listOf(threeSix) })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!).isEqualTo(
            "/sdk/cmake/3.6.4111459"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    /**
     * User request: 3.10.3-rc1+
     * Candidates from Local Properties: None.
     * Candidates from Local Repository: 3.10.2
     * Candidates from Path: None.
     * Result: Parts matching is skipped because there are four parts in '3.10.3-rc1' and three
     *   parts in '3.10.2' (from SDK). In this case an error message indicating that '3.10.3-rc1'
     *   is too high is issued.
     */
    @Test
    fun forkPlusRequestedAndThreeTenIsNotAvailableInSdkx() {
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.2")
        expectExceptionAndTriggerAndroidStudioDownload(
            "CMake '3.10.3-rc1' or higher was not found in PATH or by cmake.dir property.$newline" +
                    "- CMake '3.10.2' found at /sdk/cmake/3.10.4111459 could not satisfy requested version '3.10.3-rc1+' because it was lower."
        ) {
            findCmakePath(
                cmakeVersionFromDsl = "3.10.3-rc1+",
                repositoryPackages = { listOf(threeTen) })
        }
    }

    /**
     * User requested a + version that's not found. Expect resulting error message to contain
     * 'or higher'.
     */
    @Test
    fun dslRequestsSdkVersionPlusThatHasNotBeenDownloadedYet() {
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        expectExceptionAndTriggerAndroidStudioDownload(
            "CMake '3.7' or higher was not found in PATH or by cmake.dir property.$newline" +
                    "- CMake '3.6.0' found at /sdk/cmake/3.6.4111459 could not satisfy requested version '3.7+' because it was lower."
        ) {
            findCmakePath(
                cmakeVersionFromDsl = "3.7+",
                repositoryPackages = { listOf(threeSix) })
        }
    }

    /**
     * User request: "2.2" or "3.2"
     * Result: Versions below 3.7 are not allowed.
     */
    @Test
    fun dslVersionNumberTooLow() {
        fun testCase(cmakeVersion: String) {
            val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
            val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.2")
            val encounter = findCmakePath(
                cmakeVersionFromDsl = cmakeVersion,
                environmentPaths = {
                    listOf(
                        File("/a/b/c/cmake/bin"),
                        File("/d/e/f")
                    )
                },
                repositoryPackages = { listOf(threeTen, threeSix) })
            assertThat(encounter.warnings).hasSize(0)
            assertThat(encounter.errors.single()).isEqualTo(
                "CMake version '$cmakeVersion' is too low. Use 3.7.0 or higher."
            )
            assertThat(encounter.result).isNotNull()
            assertThat(encounter.result!!.toString()).isEqualTo(
                "/sdk/cmake/3.6.4111459"
            )
            assertThat(encounter.downloadAttempts).isEqualTo(0)
        }

        testCase("3.2")
        testCase("2.2")
    }

    /**
     * User request: "3.12"
     * Candidates from Local Repository:  "3.6.4111459", "3.10.2"
     * Candidates from Path: "3.12"
     * Result: "3.12" from path is selected because it matches better than the ones in the local repository.
     */
    @Test
    fun dslVersionFindOnPath() {
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.2")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = "3.12",
            environmentPaths = {
                listOf(
                    File("/a/b/c/cmake/bin"),
                    File("/d/e/f")
                )
            },
            repositoryPackages = { listOf(threeTen, threeSix) },
            cmakeVersion = { folder ->
                if (folder.toString().replace("\\", "/") == "/a/b/c/cmake/bin") {
                    Revision.parseRevision("3.12")
                } else {
                    null
                }
            })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/a/b/c/cmake"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    /**
     * User request: default
     * Candidates from Local Repository: None
     * Candidates from Path: "3.12.0", "3.13.0"
     * Downloader: Null downloader that does not download anything.
     * Result: Default version not found. Download attempt from SDK failed.
     *
     * In this scenario, user has two CMakes on his $PATH and has not requested a specific
     * version in build.gradle. This requires downloading the default version.
     */
    @Test
    fun findOnPathWithNoDslVersion() {
        expectExceptionAndTriggerAndroidStudioDownload(
            "CMake '3.6.0' was not found in PATH or by cmake.dir property.$newline" +
                    "- CMake '3.12.0' found in PATH did not satisfy requested version " +
                    "'3.6.0' because MINOR value 12 wasn't exactly 6."
        ) {
            findCmakePath(
                cmakeVersionFromDsl = null,
                environmentPaths = {
                    listOf(
                        File("/a/b/c/cmake/bin"),
                        File("/d/e/f/cmake/bin")
                    )
                },
                cmakeVersion = { folder ->
                    val folderPath = folder.toString().replace("\\", "/")
                    when (folderPath) {
                        "/a/b/c/cmake/bin" ->
                            Revision.parseRevision("3.12.0")
                        "/d/e/f/cmake/bin" ->
                            Revision.parseRevision("3.13.0")
                        else -> null
                    }
                })
        }
    }

    /**
     * User request: "3.12"
     * Candidates from Path: "reading version throws IOException", "3.12.0"
     * Result: The first candidate on path is skipped. Selects "3.12.0" from path.
     */
    @Test
    fun dslFindOnPathWhereOnePathCmakeInvokeThrowsAnException() {
        val encounter = findCmakePath(
            cmakeVersionFromDsl = "3.12",
            environmentPaths = {
                listOf(
                    File("/a/b/c/cmake/bin"),
                    File("/d/e/f/cmake/bin")
                )
            },
            cmakeVersion = { folder ->
                if (folder.toString().replace("\\", "/") == "/d/e/f/cmake/bin") {
                    Revision.parseRevision("3.12.0")
                } else {
                    throw IOException("Problem executing CMake.exe")
                }
            })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/d/e/f/cmake"
        )
        assertThat(encounter.warnings).containsExactly(
            "Could not execute cmake at " +
                    "'${slash}a${slash}b${slash}c${slash}cmake${slash}bin' to get version. Skipping."
        )
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    /**
     * User request: "3.12"
     * Candidates from Local Properties: "3.12"
     * Candidates from Local Repository: "3.6.4111459", "3.10.2"
     * Candidates from Path: None.
     * Result: "3.12" from local properties is selected.
     *
     * In this scenario, user has specified a path to cmake in cmake.dir of his properties
     * file. It has a version that matches the version in build.gradle
     */
    @Test
    fun findCmakeByCmakeDir() {
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.2")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = "3.12",
            cmakePathFromLocalProperties = File("/a/b/c/cmake"),
            environmentPaths = { listOf(File("/d/e/f")) },
            repositoryPackages = { listOf(threeTen, threeSix) },
            cmakeVersion = { folder ->
                if (folder.toString().replace("\\", "/") == "/a/b/c/cmake/bin") {
                    Revision.parseRevision("3.12")
                } else {
                    null
                }
            })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/a/b/c/cmake"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    /**
     * User request: "3.13"
     * Candidates from Local Properties: "3.12"
     * Candidates from Local Repository: "3.6.0-rc2", "3.10.2"
     * Candidates from Path: None.
     * Result: No matches found.
     *
     * In this scenario, user specified path in cmake.dir as well as a version in build.gradle
     * that does not agree.
     */
    @Test
    fun findWrongVersionByCmakeDir() {
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.2")
        expectException(
            "CMake '3.12' found via cmake.dir='${slash}a${slash}b${slash}c${slash}cmake' does not match " +
                    "requested version '3.13'.$newline" +
                    "- CMake '3.12' found from cmake.dir did not satisfy requested version" +
                    " '3.13' because MINOR value 12 wasn't exactly 13.$newline" +
                    "- CMake '3.10.2' found in SDK did not satisfy requested version " +
                    "'3.13' because MINOR value 10 wasn't exactly 13.$newline" +
                    "- CMake '3.6.0' found in SDK did not satisfy requested version '3.13' because MINOR value 6 wasn't exactly 13."
        ) {
            findCmakePath(
                cmakeVersionFromDsl = "3.13",
                cmakePathFromLocalProperties = File("/a/b/c/cmake"),
                environmentPaths = { listOf(File("/d/e/f")) },
                repositoryPackages = { listOf(threeTen, threeSix) },
                cmakeVersion = { folder ->
                    if (folder.toString().replace("\\", "/") == "/a/b/c/cmake/bin") {
                        Revision.parseRevision("3.12")
                    } else {
                        null
                    }
                })
        }
    }

    /**
     * User request: default
     * Candidates from Local Properties: "3.12"
     * Candidates from Local Repository: "3.6.4111459", "3.10.2"
     * Candidates from Path: None.
     * Result: The "3.12" version from cmake.dir is selected.
     *
     * In this scenario, user specified cmake.dir but no version in build.gradle.
     */
    @Test
    fun findVersionByCmakeDirWithNoVersionInBuildGradle() {
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.2")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = null,
            cmakePathFromLocalProperties = File("/a/b/c/cmake"),
            environmentPaths = { listOf(File("/d/e/f")) },
            repositoryPackages = { listOf(threeTen, threeSix) },
            cmakeVersion = { folder ->
                if (folder.toString().replace("\\", "/") == "/a/b/c/cmake/bin") {
                    Revision.parseRevision("3.12")
                } else {
                    null
                }
            })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/a/b/c/cmake"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    /**
     * User request: default
     * Candidates from Local Properties: "not a valid cmake directory"
     * Candidates from Local Repository: "3.6.0-rc2", "3.10.2"
     * Candidates from Path: None.
     * Result: "3.6.0-rc2" is selected from local repository.
     *
     * In this scenario, user specified path in cmake.dir, but the directory does not contain a
     * valid cmake version, so the cmake.dir input is ignored.
     */
    @Test
    fun cmakeDirHasWrongFolder() {
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.2")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = null,
            cmakePathFromLocalProperties = File("/a/b/c/cmake/bin-mistake"),
            environmentPaths = { listOf(File("/d/e/f")) },
            repositoryPackages = { listOf(threeTen, threeSix) },
            cmakeVersion = { folder ->
                if (folder.toString() == "/a/b/c/cmake/bin") {
                    Revision.parseRevision("3.12")
                } else {
                    null
                }
            })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!).isEqualTo(
            "/sdk/cmake/3.6.4111459"
        ) // This is a fallback
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).containsExactly(
            "Could not get version from " +
                    "cmake.dir path '${slash}a${slash}b${slash}c${slash}cmake${slash}bin-mistake'."
        )
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    /**
     * User request: 3.6.0
     * Candidates from Local Properties: None.
     * Candidates from Local Repository: None.
     * Candidates from Path: None.
     * Result: Throw an exception that should indicate to Android Studio to download that version
     * from the the SDK.
     */
    @Test
    fun downloadOfThreeSixZeroShouldTriggerDownloadAttempt() {
        expectExceptionAndTriggerAndroidStudioDownload(
            "CMake '3.6.0' was not found in PATH or by cmake.dir property.") {
            findCmakePath(
                cmakeVersionFromDsl = "3.6.0"
            )
        }
    }

    /**
     * User request: 3.6.0+
     * Candidates from Local Properties: None.
     * Candidates from Local Repository: None.
     * Candidates from Path: None.
     * Result: Throw an exception that should indicate to Android Studio to download that version
     * from the the SDK.
     */
    @Test
    fun downloadOfThreeSixZeroPlusShouldTriggerDownloadAttempt() {
        expectExceptionAndTriggerAndroidStudioDownload(
            "CMake '3.6.0' or higher was not found in PATH or by cmake.dir property.") {
            findCmakePath(
                cmakeVersionFromDsl = "3.6.0+",
                environmentPaths = {
                    listOf(
                        File("/a/b/c/cmake/bin"),
                        File("/d/e/f")
                    )
                },
                repositoryPackages = { listOf(/* threeSix */) })
        }
    }
}