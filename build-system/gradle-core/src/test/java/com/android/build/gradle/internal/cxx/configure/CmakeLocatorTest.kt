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
        environmentPaths: () -> List<File>,
        cmakePathFromLocalProperties: File? = null,
        cmakeVersion: (File) -> Revision? = { _ -> null },
        repositoryPackages: () -> List<LocalPackage>
    ): FindCmakeEncounter {
        val encounter = FindCmakeEncounter()
        var fileResult = findCmakePathLogic(
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
            downloader = { _ -> encounter.downloadAttempts = encounter.downloadAttempts + 1 }
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

    @Test
    fun partialVersionNumberMatchWithRc() {
        /**
         * In this scenario the user requested "3.12" but only "3.12.0-rc1" is found on the path.
         * This value is accepted because the prefix "3.12" matches.
         *
         * See https://issuetracker.google.com/110693527
         */
        val localCmake36 = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val localCmake310 = fakeLocalPackageOf("cmake;3.10.4819442", "3.10.4819442")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = "3.12",
            environmentPaths = {
                listOf(
                    File("/a/b/c/cmake/3.12.0-rc1/bin"),
                    File("/d/e/f")
                )
            },
            repositoryPackages = { listOf(localCmake36, localCmake310) },
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

    @Test
    fun partialVersionNumberMatchWithRcAndProduction() {
        /**
         * In this scenario the user requested "3.12" and both "3.12.0-rc1" and "3.12.0" are found
         * on the path. The "3.12.0" version is taken because this is the release version of
         * "3.12.0-rc1".
         *
         * See https://issuetracker.google.com/110693527
         */
        val localCmake36 = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val localCmake310 = fakeLocalPackageOf("cmake;3.10.4819442", "3.10.4819442")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = "3.12",
            environmentPaths = {
                listOf(
                    File("/a/b/c/cmake/3.12.0-rc1/bin"),
                    File("/a/b/c/cmake/3.12.0/bin"),
                    File("/d/e/f")
                )
            },
            repositoryPackages = { listOf(localCmake36, localCmake310) },
            cmakeVersion = { folder ->
                when(folder.toString().replace("\\", "/")) {
                    "/a/b/c/cmake/3.12.0-rc1/bin" -> Revision.parseRevision("3.12.0-rc1")
                    "/a/b/c/cmake/3.12.0/bin" -> Revision.parseRevision("3.12.0")
                    else -> null
                }
            })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/a/b/c/cmake/3.12.0"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    @Test
    fun exactVersionNumberMatchWithRcAndProduction() {
        /**
         * In this scenario the user requested "3.12.0-rc1" and both "3.12.0-rc1" and "3.12.0"
         * are found on the path. The "3.12.0-rc1" version is taken because the user requested
         * this exactly.
         *
         * See https://issuetracker.google.com/110693527
         */
        val localCmake36 = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val localCmake310 = fakeLocalPackageOf("cmake;3.10.4819442", "3.10.4819442")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = "3.12.0-rc1",
            environmentPaths = {
                listOf(
                    File("/a/b/c/cmake/3.12.0-rc1/bin"),
                    File("/a/b/c/cmake/3.12.0/bin"),
                    File("/d/e/f")
                )
            },
            repositoryPackages = { listOf(localCmake36, localCmake310) },
            cmakeVersion = { folder ->
                when(folder.toString().replace("\\", "/")) {
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

    @Test
    fun exactVersionMatchesTwoLocations() {
        /**
         * In this scenario the user requested "3.12.0" and it is found to match two locations in
         * the path. In this case, pick first one.
         *
         * See https://issuetracker.google.com/110693527
         */
        val localCmake36 = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val localCmake310 = fakeLocalPackageOf("cmake;3.10.4819442", "3.10.4819442")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = "3.12.0",
            environmentPaths = {
                listOf(
                    File("/a/b/c/cmake/3.12.0-a/bin"),
                    File("/a/b/c/cmake/3.12.0-b/bin"),
                    File("/d/e/f")
                )
            },
            repositoryPackages = { listOf(localCmake36, localCmake310) },
            cmakeVersion = { folder ->
                when(folder.toString().replace("\\", "/")) {
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
        val infoMessage = encounter.info.joinToString(newline)
        assertThat(infoMessage).isEqualTo("Trying to locate CMake in local SDK repository.$newline" +
                "CMake '3.6.4111459' found in SDK was not the requested version '3.12.0'.$newline" +
                "CMake '3.10.4819442' found in SDK was not the requested version '3.12.0'.$newline" +
                "Trying to locate CMake in PATH.$newline" +
                "- CMake found in in PATH at '${slash}a${slash}b${slash}c${slash}cmake${slash}3.12.0-a' had version '3.12.0'$newline" +
                "- CMake found and skipped in PATH '${slash}a${slash}b${slash}c${slash}cmake${slash}3.12.0-b' which had version '3.12.0' which was lower than or equal to a version found earlier.")
    }

    @Test
    fun partialVersionNumberMatchWithTwoRcs() {
        /**
         * In this scenario the user requested "3.12" and both "3.12.0-rc1" and "3.12.0-rc2" are
         * found on the path. The "3.12.0-rc2" version is taken because this is the higher version.
         *
         * See https://issuetracker.google.com/110693527
         */
        val localCmake36 = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val localCmake310 = fakeLocalPackageOf("cmake;3.10.4819442", "3.10.4819442")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = "3.12",
            environmentPaths = {
                listOf(
                    File("/a/b/c/cmake/3.12.0-rc1/bin"),
                    File("/a/b/c/cmake/3.12.0-rc2/bin"),
                    File("/d/e/f")
                )
            },
            repositoryPackages = { listOf(localCmake36, localCmake310) },
            cmakeVersion = { folder ->
                when(folder.toString().replace("\\", "/")) {
                    "/a/b/c/cmake/3.12.0-rc1/bin" -> Revision.parseRevision("3.12.0-rc1")
                    "/a/b/c/cmake/3.12.0-rc2/bin" -> Revision.parseRevision("3.12.0-rc2")
                    else -> null
                }
            })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/a/b/c/cmake/3.12.0-rc2"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    @Test
    fun sdkCmakeExistsLocally() {
        val localCmake = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = "3.6.4111459",
            environmentPaths = {
                listOf(
                    File("/a/b/c/cmake/bin"),
                    File("/d/e/f")
                )
            },
            repositoryPackages = { listOf(localCmake) })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/sdk/cmake/3.6.4111459"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    @Test
    fun sdkCmakeDefaultedExistsLocally() {
        val localCmake = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = null,
            environmentPaths = {
                listOf(
                    File("/a/b/c/cmake/bin"),
                    File("/d/e/f")
                )
            },
            repositoryPackages = { listOf(localCmake) })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/sdk/cmake/3.6.4111459"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    @Test
    fun requestedCmakeNotFoundFallbackToSdk() {
        val localCmake = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        expectException(
            "CMake '3.10.2' was not found in PATH or by cmake.dir property.$newline" +
                    "- CMake '3.6.4111459' found in SDK was not the requested version '3.10.2'."
        )
        {
            findCmakePath(
                cmakeVersionFromDsl = "3.10.2",
                environmentPaths = {
                    listOf(
                        File("/a/b/c/cmake/bin"),
                        File("/d/e/f")
                    )
                },
                repositoryPackages = { listOf(localCmake) })
        }
    }

    @Test
    fun requestedSdkLikeCmakeNotFoundFallbackToSdk() {
        val localCmake = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        expectException(
            "CMake '3.6.4111460' was not found in PATH " +
                    "or by cmake.dir property.$newline" +
                    "- CMake '3.6.4111459' found in SDK was not the requested " +
                    "version '3.6.4111460'."
        )
        {
            findCmakePath(
                cmakeVersionFromDsl = "3.6.4111460", // <-- intentionally wrong
                environmentPaths = {
                    listOf(
                        File("/a/b/c/cmake/bin"),
                        File("/d/e/f")
                    )
                },
                repositoryPackages = { listOf(localCmake) })
        }
    }

    @Test
    fun noVersionInDslAndNoLocalSdkVersion() {
        expectException(
            "CMake '3.6.4111459' is required but has not yet been downloaded " +
                    "from the SDK."
        ) {
            findCmakePath(
                cmakeVersionFromDsl = null,
                environmentPaths = {
                    listOf(
                        File("/a/b/c/cmake/bin"),
                        File("/d/e/f")
                    )
                },
                repositoryPackages = { listOf() })
        }
    }

    @Test
    fun unparseableRevisionInBuildGradle() {
        val localCmake = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = "3.bob",
            environmentPaths = {
                listOf(
                    File("/a/b/c/cmake/bin"),
                    File("/d/e/f")
                )
            },
            repositoryPackages = { listOf(localCmake) })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/sdk/cmake/3.6.4111459"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors.single()).isEqualTo(
            "CMake version '3.bob' is not " +
                    "formatted correctly."
        )
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    @Test
    fun noDslButUpVersionInSdk() {
        /**
         * In this scenario, there is a more recent version of CMake and the user hasn't requested
         * a CMake in build.gradle. Even though there is a CMake version in the SDK it is still an
         * error because when there is no version specified in build.gradle then the exact default
         * version is required.
         */
        expectException("CMake '3.6.4111459' is required but has not yet been downloaded " +
                "from the SDK.$newline" +
                "- CMake found in SDK at '${slash}sdk${slash}cmake${slash}3.10.4111459' had version '3.10.4111459'.",
            {
                val localCmake = fakeLocalPackageOf(
                    "cmake;3.10.4111459",
                    "3.10.4111459"
                )
                findCmakePath(
                    cmakeVersionFromDsl = null,
                    environmentPaths = {
                        listOf(
                            File("/a/b/c/cmake/bin"),
                            File("/d/e/f")
                        )
                    },
                    repositoryPackages = { listOf(localCmake) })
            })
    }

    @Test
    fun noDslButMultipleUpVersionInSdk1() {
        /**
         * In this scenario, there are multiple versions of CMake in the SDK and the user hasn't
         * requested any version in build.gradle. We should pick the highest version in the SDK.
         */
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.4111459")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = null,
            environmentPaths = {
                listOf(
                    File("/a/b/c/cmake/bin"),
                    File("/d/e/f")
                )
            },
            repositoryPackages = { listOf(threeSix, threeTen) })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/sdk/cmake/3.6.4111459"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    @Test
    fun noDslButMultipleUpVersionInSdk2() {
        /**
         * In this scenario, there are multiple versions of CMake in the SDK and the user hasn't
         * requested any version in build.gradle. We should pick the highest version in the SDK.
         */
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.4111459")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = null,
            environmentPaths = {
                listOf(
                    File("/a/b/c/cmake/bin"),
                    File("/d/e/f")
                )
            },
            repositoryPackages = { listOf(threeTen, threeSix) })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/sdk/cmake/3.6.4111459"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    @Test
    fun dslButUpVersionInSdk() {
        /**
         * In this scenario there is a version specified in build.gradle but the only downloaded
         * version is higher (but still an SDK version).
         */
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.4111459")
        expectException(
            "CMake '3.6.4111459' is required but has not yet been downloaded " +
                    "from the SDK.$newline" +
                    "- CMake '3.10.4111459' found in SDK did not satisfy requested version " +
                    "'3.6.4111459' because MINOR value 10 wasn't exactly 6."
        ) {
            findCmakePath(
                cmakeVersionFromDsl = "3.6.4111459",
                environmentPaths = {
                    listOf(
                        File("/a/b/c/cmake/bin"),
                        File("/d/e/f")
                    )
                },
                repositoryPackages = { listOf(threeTen) })
        }
    }

    @Test
    fun dslWithMultipleVersionInSdk() {
        /**
         * In this scenario, there are multiple versions of CMake in the SDK and the user has
         * chosen the lower version in build.gradle
         */
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.4111459")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = "3.6.4111459",
            environmentPaths = {
                listOf(
                    File("/a/b/c/cmake/bin"),
                    File("/d/e/f")
                )
            },
            repositoryPackages = { listOf(threeTen, threeSix) })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/sdk/cmake/3.6.4111459"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    @Test
    fun dslWithForkCmakeVersion3dot6() {
        /**
         * In this scenario, the user has asked for 3.6.0-rc2. This is the CMake-reported version
         * of the forked CMake 3.6.4111459. As a helper, and for backward compatibility, translate
         * this version for the user.
         */
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.4111459")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = "3.6.0-rc2",
            environmentPaths = {
                listOf(
                    File("/a/b/c/cmake/bin"),
                    File("/d/e/f")
                )
            },
            repositoryPackages = { listOf(threeTen, threeSix) })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!).isEqualTo(
            "/sdk/cmake/3.6.4111459"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors).hasSize(0)
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    @Test
    fun dslVersionNumberTooLow1() {
        /**
         * In this scenario, the user has requested a CMake version that is too low.
         */
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.4111459")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = "3.2",
            environmentPaths = {
                listOf(
                    File("/a/b/c/cmake/bin"),
                    File("/d/e/f")
                )
            },
            repositoryPackages = { listOf(threeTen, threeSix) })
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors.single()).isEqualTo(
            "CMake version '3.2' is too low. " +
                    "Use 3.7.0 or higher."
        )
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/sdk/cmake/3.6.4111459"
        )
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    @Test
    fun dslVersionNumberTooLow2() {
        /**
         * In this scenario, the user has requested a CMake version that is too low.
         */
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.4111459")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = "2.2",
            environmentPaths = {
                listOf(
                    File("/a/b/c/cmake/bin"),
                    File("/d/e/f")
                )
            },
            repositoryPackages = { listOf(threeTen, threeSix) })
        assertThat(encounter.result).isNotNull()
        assertThat(encounter.result!!.toString()).isEqualTo(
            "/sdk/cmake/3.6.4111459"
        )
        assertThat(encounter.warnings).hasSize(0)
        assertThat(encounter.errors.single()).isEqualTo(
            "CMake version '2.2' is too low. " +
                    "Use 3.7.0 or higher."
        )
        assertThat(encounter.downloadAttempts).isEqualTo(0)
    }

    @Test
    fun dslVersionFindOnPath() {
        /**
         * In this scenario, user has two SDK CMakes installed but has requested a third version
         * that should be found on path.
         */
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.4111459")
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

    @Test
    fun findOnPathWithNoDslVersion() {
        /**
         * In this scenario, user has two CMakes on his $PATH and has not requested a specific
         * version in build.gradle. Issue an error because no version in the DSL means
         * exactly the default version should be used.
         */
        expectException("CMake '3.6.4111459' is required but has not yet been downloaded " +
                "from the SDK.$newline" +
                "- CMake found in PATH at '${slash}a${slash}b${slash}c${slash}cmake' " +
                "had version '3.12.0'.$newline" +
                "- CMake found in PATH at '${slash}d${slash}e${slash}f${slash}cmake' " +
                "had version '3.13.0'.", {
            findCmakePath(
                cmakeVersionFromDsl = null,
                environmentPaths = {
                    listOf(
                        File("/a/b/c/cmake/bin"),
                        File("/d/e/f/cmake/bin")
                    )
                },
                repositoryPackages = { listOf() },
                cmakeVersion = { folder ->
                    var folderPath = folder.toString().replace("\\", "/")
                    when (folderPath) {
                        "/a/b/c/cmake/bin" ->
                            Revision.parseRevision("3.12.0")
                        "/d/e/f/cmake/bin" ->
                            Revision.parseRevision("3.13.0")
                        else -> null
                    }
                })
        })
    }

    @Test
    fun dslFindOnPathWhereOnePathCmakeInvokeThrowsAnException() {
        /**
         * In this scenario, user has two SDK CMakes installed but has requested a third version
         * that is found on the path. There is also another path that issues an IOException when
         * we try to execute cmake.exe to get it's version.
         */
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.4111459")
        val encounter = findCmakePath(
            cmakeVersionFromDsl = "3.12",
            environmentPaths = {
                listOf(
                    File("/a/b/c/cmake/bin"),
                    File("/d/e/f/cmake/bin")
                )
            },
            repositoryPackages = { listOf(threeTen, threeSix) },
            cmakeVersion = { folder ->
                if (folder.toString().replace("\\", "/") == "/d/e/f/cmake/bin") {
                    Revision.parseRevision("3.12")
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

    @Test
    fun findCmakeByCmakeDir() {
        /**
         * In this scenario, user has specified a path to cmake in cmake.dir of his properties
         * file. It is has a version that matches the version in build.gradle
         */
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.4111459")
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

    @Test
    fun findWrongVersionByCmakeDir() {
        /**
         * In this scenario, user specified path in cmake.dir as well as a version in build.gradle
         * that does not agree.
         */
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.4111459")
        expectException("CMake '3.12' found via cmake.dir='${slash}a${slash}b${slash}c${slash}cmake' does not match " +
                "requested version '3.13'.$newline" +
                "- CMake '3.12' found from cmake.dir did not satisfy requested version" +
                " '3.13' because MINOR value 12 wasn't exactly 13.$newline" +
                "- CMake '3.10.4111459' found in SDK was not the requested version " +
                "'3.13'.$newline" +
                "- CMake '3.6.4111459' found in SDK was not the requested version '3.13'.", {
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
        })
    }

    @Test
    fun findVersionByCmakeDirWithNoVersionInBuildGradle() {
        /**
         * In this scenario, user specified cmake.dir but no version in build.gradle.
         */
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.4111459")
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

    @Test
    fun cmakeDirHasWrongFolder() {
        /**
         * In this scenario, user specified path in cmake.dir as well as a version in build.gradle
         * that does not agree.
         */
        val threeSix = fakeLocalPackageOf("cmake;3.6.4111459", "3.6.4111459")
        val threeTen = fakeLocalPackageOf("cmake;3.10.4111459", "3.10.4111459")
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
}

