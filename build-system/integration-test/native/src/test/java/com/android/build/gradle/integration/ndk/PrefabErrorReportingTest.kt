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

package com.android.build.gradle.integration.ndk

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.json.jsonStringOf
import com.android.build.gradle.internal.cxx.logging.PassThroughDeduplicatingLoggingEnvironment
import com.android.build.gradle.internal.cxx.logging.text
import com.android.build.gradle.internal.cxx.prefab.AndroidAbiMetadata
import com.android.build.gradle.internal.cxx.prefab.ModuleMetadataV1
import com.android.build.gradle.internal.cxx.prefab.PackageMetadataV1
import com.android.build.gradle.internal.ndk.Stl
import com.android.build.gradle.tasks.DEFAULT_PREFAB_VERSION
import com.android.build.gradle.tasks.reportErrors
import com.android.testutils.TestUtils.getPrebuiltOfflineMavenRepo
import com.android.testutils.TestUtils.runningFromBazel
import com.google.common.truth.Truth.assertThat
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Random
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue

/**
 * Execute Prefab for different configurations. Check for expected errors.
 * Also, there is a fuzz tester that was used to generate most of these cases.
 */
class PrefabErrorReportingTest {

    @Test
    fun `Check that baseline Prefab configuration doesn't report errors`() {
        baseline.assertNoError()
    }

    /**
     * If a message in stderr is not recognized then it should still be reported.
     * It just won't have an error code.
     */
    @Test
    fun `Ensure unrecognized error is still reported`() {
        val stderr = tempFolder.newFile("stderr.txt")
        stderr.writeText("Prefab reported an unknown failure")
        try {
            reportErrors(stderr)
        } catch (e : RuntimeException) {
            assertThat(e.message).isEqualTo("Prefab reported an unknown failure")
            return
        }
        error("Expected an error to be reported")
    }

    /**
     * The reportErrors(...) function tries (and always succeeds in known cases) to find a
     * library name that is related to an error that comes later. This test fabricates a STDERR
     * such that relevantLibrary isn't found. When this happens, there should still be a
     * reasonable error message.
     */
    @Test
    fun `Ensure reasonable error message even when relevantLibrary not found`() {
        val stderr = tempFolder.newFile("stderr.txt")
        stderr.writeText("User has minSdkVersion 10 but library was built for 11")
        PassThroughDeduplicatingLoggingEnvironment().use { logger ->
            reportErrors(stderr)
            assertThat(logger.errors)
                .containsExactly("[CXX1214] User has minSdkVersion 10 but library was built for 11")
        }
    }

    @Test
    fun `CXX1210⋮ No compatible library found for package`() {
        baseline.copy(moduleLibraryAbi = "armeabi-v7a")
            .assertHasError("[CXX1210] No compatible library found [//pkg1/lib1]")
    }

    @Test
    fun `CXX1211⋮ Library is a shared library with a statically linked STL and cannot be used with any library using the STL`() {
        baseline.copy(
            stl = "gnustl_shared",
            moduleLibraryAbiStl = "gnustl_static",
        )
        .assertHasError(
            "[CXX1211] Library is a shared library with a statically linked STL and cannot be used with any library using the STL [//pkg1/lib1]"
        )
    }

    @Test
    fun `CXX1212⋮ User is using a static STL but library requires a shared STL`() {
        baseline.copy(
            stl = "c++_static",
            moduleLibraryAbiStl = "c++_shared",
        )
        .assertHasError(
            "[CXX1212] User is using a static STL but library requires a shared STL [//pkg1/lib1]"
        )
    }

    @Test
    fun `CXX1213⋮ User requested libstdc++ but library requires libc++`() {
        baseline.copy(
            stl = "gnustl_static",
            moduleLibraryAbiStl = "c++_static",
        )
        .assertHasError(
            "[CXX1213] User requested libstdc++ but library requires libc++ [//pkg1/lib1]"
        )
    }

    @Test
    fun `CXX1214⋮ User has minSdkVersion 7 but library was built for 16`() {
        baseline.copy(
            abi = "x86",
            osVersion = 7,
            moduleLibraryAbi = "x86",
            moduleLibraryAbiMinSdkVersion = 16
        )
        .assertHasError(
            "[CXX1214] User has minSdkVersion 7 but library was built for 16 [//pkg1/lib1]"
        )
    }

    @Test
    fun `CXX1215⋮ ndk-build does not support fully qualified module names`() {
        baseline.copy(
            buildSystem = "ndk-build",
            prefabPackageName = "pkg1",
            moduleLibraryName = "lib1",
            hasLibrary2 = true,
            prefabPackageName2 = "pkg2",
            moduleLibraryName2 = "lib1",
        )
        .assertHasError(
            "[CXX1215] Duplicate module name found (//pkg2/lib1 and //pkg1/lib1). ndk-build does not support fully qualified module names."
        )
    }

    @Test
    fun `Only first error is reported`() {
        // This would report two errors if not for the logic to stop at the first.
        // Reported: [CXX1211] Library is a shared library with a statically linked STL ...
        // Not Reported: [CXX1214] User has minSdkVersion 21 but library was built for 27 ...
        baseline.copy(
            stl = "gnustl_shared",
            moduleLibraryAbiStl = "gnustl_static",
            hasLibrary2 = true,
            moduleLibraryAbiStl2 = "stlport_shared",
        )
        .assertHasError(
            "[CXX1211] Library is a shared library with a statically linked STL and cannot be used with any library using the STL [//pkg1/lib1]",
        )
    }

    @Test
    fun `CXX1216⋮ package contains artifacts for an unsupported platform "windows"`() {
        baseline.copy(moduleLibraryTargetPlatform = "windows")
            .assertHasError("[CXX1216] //pkg1/lib1 contains artifacts for an unsupported platform \"windows\"")
    }

    @Test
    fun `CXX1217⋮ Only schema_version 1 is supported, pkg1 uses version 2`() {
        Assume.assumeFalse(runningFromBazel()) // Prefab 1.1.3 isn't available
        baseline.copy(prefabVersion = "1.1.3")
            .assertHasError("[CXX1217] Only schema_version 1 is supported. pkg1 uses version 2.")

        baseline.copy(prefabSchemaVersion = 100)
            .assertHasError("[CXX1217] schema_version must be between 1 and 2. Package uses version 100.")
    }

    @Test
    fun `CXX1218⋮ Unexpected JSON token at offset 87, Encountered an unknown key 'static'`() {
        Assume.assumeFalse(runningFromBazel()) // Prefab 1.1.3 isn't available
        baseline.copy(
            prefabVersion = "1.1.3",
            prefabSchemaVersion = 1,
            moduleLibraryAbiStatic = true
        )
            .assertHasError(
                Regex(".*CXX1218.* Unexpected JSON token at offset .*: Encountered an unknown key 'static'.")
            )
    }

    @Test
    fun `CXX1219⋮ Prebuilt directory does not contain …`() {
        Assume.assumeFalse(runningFromBazel()) // Prefab 1.1.3 isn't available
        baseline.copy(
            prefabVersion = "1.1.3",
            prefabSchemaVersion = 1,
        )
            .assertHasError(
                Regex(".*CXX1219.* Prebuilt directory does not contain.*")
            )
    }

    @Test
    fun `CXX1220⋮ Miscellaneous Fatal Errors`() {
        baseline.copy(stl = "trash-stl")
            .assertHasError(Regex(".*CXX1220.* Invalid value for .*"))

        baseline.copy(moduleLibraryAbiStl = "trash-stl")
            .assertHasError("[CXX1220] Unknown STL: trash-stl")

        baseline.copy(abi = "trash-abi")
            .assertHasError(Regex(".*CXX1220.* Unknown ABI: trash-abi.*"))

        baseline.copy(moduleLibraryAbi = "trash-abi")
            .assertHasError("[CXX1220] Unknown ABI: trash-abi")

        baseline.copy(prefabPackageVersion = "trash-version")
            .assertHasError("[CXX1220] version must be compatible with CMake, if present")
    }


    @Test
    fun `STDERR with only blank lines is not an error`() {
        val stderr = tempFolder.newFile("stderr.txt")
        stderr.writeText(" \n  \n")
        PassThroughDeduplicatingLoggingEnvironment().use { logger ->
            reportErrors(stderr)
            assertThat(logger.errors).isEmpty()
        }
    }

    @Test
    fun `Lines after first error are logged as lifecycle`() {
        val stderr = tempFolder.newFile("stderr.txt")
        stderr.writeText("""
            User has minSdkVersion 10 but library was built for 11

            Line above is intentionally blank
            """.trimIndent())
        PassThroughDeduplicatingLoggingEnvironment().use { logger ->
            reportErrors(stderr)
            assertThat(logger.errors)
                .containsExactly("[CXX1214] User has minSdkVersion 10 but library was built for 11")
            assertThat(logger.lifecycles)
                .containsExactly("C/C++: ", "C/C++: Line above is intentionally blank")
        }
    }

    @Test
    fun fuzz() {
        if (testRootFolder.isDirectory) {
            testRootFolder.delete()
        }
        val known = setOf(
            setOf(),
            setOf(1210),
            setOf(1211),
            setOf(1212),
            setOf(1213),
            setOf(1214),
            setOf(1215),
            setOf(1217),
            setOf(1218),
            setOf(1219),
            setOf(1220),
        )
        randomPermutations
            .take(20)
            .forEach {
                if (!it.hasLibrary && !it.hasLibrary2) return@forEach
                if (it.prefabVersion == "1.1.3" && runningFromBazel()) return@forEach
                it.writePrefabPackage()
                it.runPrefab()
                PassThroughDeduplicatingLoggingEnvironment().use { logger ->
                    try {
                        reportErrors(it.stderr)
                    } catch (e: Exception) {
                        println(it)
                        throw(e)
                    }
                    if (!known.contains(logger.errorMessages.map { err-> err.diagnosticCode }.distinct().toSet())) {
                        println(it)
                        error("New error set seen")
                    }
                    if (it.stderr.readText().isNotBlank() && logger.errors.isEmpty()) {
                        if (logger.errors.isEmpty()) {
                            println(it)
                            error("Error should have been reported")
                        }
                    }
                }
            }
    }


    private val baseline by lazy { Permutation(testRootFolder) }

    private val prefabs = listOf("1.1.3", "2.0.0", DEFAULT_PREFAB_VERSION).distinct().toTypedArray()
    private val prefabSchemaVersions = arrayOf(1, 2, 100)
    private val buildSystems = arrayOf("cmake", "ndk-build")
    private val abis = (Abi.getDefaultValues().map { it.tag } + "trash-abi").toTypedArray()
    private val osVersions = (1 until 35).toList().toTypedArray()
    private val stls = (Stl.values().map { it.argumentName } + "trash-stl").toTypedArray()
    private val ndkMajorVersions = (13 until 25).toList().toTypedArray()
    private val prefabJsonPackageNames = arrayOf("pkg1", "pkg2")
    private val moduleLibraryName = arrayOf("lib1", "lib2")
    private val moduleLibraryAbiMinSdkVersions = (1 until 35).map { it }.toTypedArray()
    private val moduleLibraryAbiNdkMajorVersions = (1 until 25).map { it }.toTypedArray()
    private val moduleLibraryAbiStatics = arrayOf(true, false, null)
    private val prefabPackageVersions = arrayOf("1.0.0", "2.0.0", "trash-version", "1", "2")

    private val testRootFolder by lazy {
        tempFolder.newFolder().parentFile.parentFile.resolve("prefab-error")
    }

    private val randomPermutations : Sequence<Permutation> = sequence {
        val random = Random(192)
        while(true) {
            yield(Permutation(
                testRootFolder = testRootFolder,
                prefabVersion = prefabs.pickOne(random),
                buildSystem = buildSystems.pickOne(random),
                abi = abis.pickOne(random),
                osVersion = osVersions.pickOne(random),
                stl = stls.pickOne(random),
                ndkMajorVersion = ndkMajorVersions.pickOne(random),
                hasLibrary = arrayOf(true, false).pickOne(random),
                prefabSchemaVersion = prefabSchemaVersions.pickOne(random),
                prefabPackageVersion = prefabPackageVersions.pickOne(random),
                prefabPackageName = prefabJsonPackageNames.pickOne(random),
                moduleLibraryName = moduleLibraryName.pickOne(random),
                moduleLibraryAbi = abis.pickOne(random),
                moduleLibraryAbiMinSdkVersion = moduleLibraryAbiMinSdkVersions.pickOne(random),
                moduleLibraryAbiNdkMajorVersion = moduleLibraryAbiNdkMajorVersions.pickOne(random),
                moduleLibraryAbiStl = stls.pickOne(random),
                moduleLibraryAbiStatic = moduleLibraryAbiStatics.pickOne(random),
                hasLibrary2 = arrayOf(true, false).pickOne(random),
                prefabSchemaVersion2 = prefabSchemaVersions.pickOne(random),
                prefabPackageVersion2 = prefabPackageVersions.pickOne(random),
                prefabPackageName2 = prefabJsonPackageNames.pickOne(random),
                moduleLibraryName2 = moduleLibraryName.pickOne(random),
                moduleLibraryAbi2 = abis.pickOne(random),
                moduleLibraryAbiMinSdkVersion2 = moduleLibraryAbiMinSdkVersions.pickOne(random),
                moduleLibraryAbiNdkMajorVersion2 = moduleLibraryAbiNdkMajorVersions.pickOne(random),
                moduleLibraryAbiStl2 = stls.pickOne(random),
                moduleLibraryAbiStatic2 = moduleLibraryAbiStatics.pickOne(random),
            ))
        }
    }

    private fun <T> Array<T>.pickOne(random : Random) : T {
        return get(random.nextInt().absoluteValue % size)
    }

    data class Permutation(
        val testRootFolder: File,
        val prefabVersion: String = "2.0.0",
        val buildSystem : String = "cmake",
        val abi : String = "arm64-v8a",
        val osVersion : Int = 20,
        val stl : String = "none",
        val ndkMajorVersion : Int = 21,
        val hasLibrary : Boolean = true,
        val prefabSchemaVersion : Int = 2,
        val prefabPackageVersion : String = "1.0.0",
        val prefabPackageName : String = "pkg1",
        val moduleLibraryName : String = "lib1",
        val moduleLibraryAbi : String = abi,
        val moduleLibraryAbiMinSdkVersion : Int = osVersion,
        val moduleLibraryAbiNdkMajorVersion : Int = ndkMajorVersion,
        val moduleLibraryAbiStl : String = stl,
        val moduleLibraryAbiStatic :  Boolean? = null,
        val moduleLibraryTargetPlatform : String = "android",
        val hasLibrary2 : Boolean = false,
        val prefabSchemaVersion2 : Int = 2,
        val prefabPackageVersion2 : String = "2.0.0",
        val prefabPackageName2 : String = "pkg2",
        val moduleLibraryName2 : String = "lib2",
        val moduleLibraryAbi2 : String = abi,
        val moduleLibraryAbiMinSdkVersion2 : Int = osVersion,
        val moduleLibraryAbiNdkMajorVersion2 : Int = ndkMajorVersion,
        val moduleLibraryAbiStl2 : String = stl,
        val moduleLibraryAbiStatic2 :  Boolean? = null) {

        val root = testRootFolder.resolve("prefab${hashCode()}")

        val workingDir = root.resolve("working-dir")
        val output = root.resolve("output-folder")
        val command = root.resolve("command.txt")
        val stdout = root.resolve("stdout.txt")
        val stderr = root.resolve("stderr.txt")
        val packages = root.resolve("packages")

        val packageDir = packages.resolve(prefabPackageName)
        val prefabJson = packageDir.resolve("prefab.json")
        val moduleDir = packageDir.resolve("modules").resolve(moduleLibraryName)
        val moduleJson = moduleDir.resolve("module.json")
        val moduleLibsDir = moduleDir.resolve("libs")
        val abiJson = moduleLibsDir.resolve("$moduleLibraryTargetPlatform.$moduleLibraryAbi").resolve("abi.json")

        val packageDir2 = packages.resolve(prefabPackageName2)
        val prefabJson2 = packageDir2.resolve("prefab.json")
        val moduleDir2 = packageDir2.resolve("modules").resolve(moduleLibraryName2)
        val moduleJson2 = moduleDir2.resolve("module.json")
        val moduleLibsDir2 = moduleDir2.resolve("libs")
        val abiJson2 = moduleLibsDir2.resolve("android.$moduleLibraryAbi2").resolve("abi.json")

        override fun toString(): String {
            return """
                    baseline.copy(
                        prefabVersion = "$prefabVersion",
                        buildSystem = "$buildSystem",
                        abi = "$abi",
                        osVersion = $osVersion,
                        stl = "$stl",
                        ndkMajorVersion = $ndkMajorVersion,
                        hasLibrary = $hasLibrary,
                        prefabSchemaVersion = $prefabSchemaVersion,
                        prefabPackageVersion = "$prefabPackageVersion",
                        prefabPackageName = "$prefabPackageName",
                        moduleLibraryName = "$moduleLibraryName",
                        moduleLibraryAbi = "$moduleLibraryAbi",
                        moduleLibraryAbiMinSdkVersion = $moduleLibraryAbiMinSdkVersion,
                        moduleLibraryAbiNdkMajorVersion = $moduleLibraryAbiNdkMajorVersion,
                        moduleLibraryAbiStl = "$moduleLibraryAbiStl",
                        moduleLibraryAbiStatic = $moduleLibraryAbiStatic,
                        hasLibrary2 = $hasLibrary2,
                        prefabSchemaVersion2 = $prefabSchemaVersion2,
                        prefabPackageVersion2 = "$prefabPackageVersion2",
                        prefabPackageName2 = "$prefabPackageName2",
                        moduleLibraryName2 = "$moduleLibraryName2",
                        moduleLibraryAbi2 = "$moduleLibraryAbi2",
                        moduleLibraryAbiMinSdkVersion2 = $moduleLibraryAbiMinSdkVersion2,
                        moduleLibraryAbiNdkMajorVersion2 = $moduleLibraryAbiNdkMajorVersion2,
                        moduleLibraryAbiStl2 = "$moduleLibraryAbiStl2",
                        moduleLibraryAbiStatic2 = $moduleLibraryAbiStatic2)
                """.trimIndent()
        }
    }

    private fun Permutation.runPrefab() {
        if (!hasLibrary && !hasLibrary2) return
        workingDir.mkdirs()
        val maven = if (runningFromBazel()) {
            File("..").resolve("maven/repository").absoluteFile.canonicalFile
        } else getPrebuiltOfflineMavenRepo().toFile()
        val prefabClassPath = maven.resolve("com/google/prefab/cli/$prefabVersion/cli-$prefabVersion-all.jar")
        if (!prefabClassPath.isFile) {
            error("Missing $prefabClassPath")
        }
        val args = mutableListOf(
            File(System.getProperty("java.home")).resolve("bin/java").path,
            "--class-path",  prefabClassPath.toString(),
            "com.google.prefab.cli.AppKt",
            "--build-system", buildSystem,
            "--platform", "android",
            "--abi", abi,
            "--os-version", "$osVersion",
            "--stl", stl,
            "--ndk-version", "$ndkMajorVersion",
            "--output", output.path)
        if (hasLibrary) args.add(packageDir.path)
        if (hasLibrary2) args.add(packageDir2.path)
        val proc = ProcessBuilder(args)
            .directory(workingDir)
            .redirectOutput(stdout)
            .redirectError(stderr)
            .start()
        command.parentFile.mkdirs()
        command.writeText(args.joinToString("\n"))
        if (stderr.isFile) {
            stderr.writeText(stderr.readText().replace(root.path, "{ROOT}"))
        }
        proc.waitFor(10, TimeUnit.SECONDS)
    }

    private fun Permutation.writePrefabPackage() {
        if (hasLibrary) {
            abiJson.parentFile.mkdirs()
            prefabJson.writeText(
                jsonStringOf(
                    PackageMetadataV1(
                        name = prefabPackageName,
                        schemaVersion = prefabSchemaVersion,
                        version = prefabPackageVersion,
                        dependencies = emptyList()
                    )
                )
            )
            moduleJson.writeText(
                jsonStringOf(
                    ModuleMetadataV1(
                        exportLibraries = emptyList(),
                        libraryName = moduleLibraryName
                    )
                )
            )

            abiJson.writeText(
                jsonStringOf(
                    AndroidAbiMetadata(
                        abi = moduleLibraryAbi,
                        api = moduleLibraryAbiMinSdkVersion,
                        ndk = moduleLibraryAbiNdkMajorVersion,
                        stl = moduleLibraryAbiStl,
                        static = moduleLibraryAbiStatic
                    )
                )
            )
        }

        if (hasLibrary2) {
            abiJson2.parentFile.mkdirs()
            prefabJson2.writeText(
                jsonStringOf(
                    PackageMetadataV1(
                        name = prefabPackageName2,
                        schemaVersion = prefabSchemaVersion2,
                        version = prefabPackageVersion2,
                        dependencies = emptyList()
                    )
                )
            )
            moduleJson2.writeText(
                jsonStringOf(
                    ModuleMetadataV1(
                        exportLibraries = emptyList(),
                        libraryName = moduleLibraryName2
                    )
                )
            )
            abiJson2.writeText(
                jsonStringOf(
                    AndroidAbiMetadata(
                        abi = moduleLibraryAbi2,
                        api = moduleLibraryAbiMinSdkVersion2,
                        ndk = moduleLibraryAbiNdkMajorVersion2,
                        stl = moduleLibraryAbiStl2,
                        static = moduleLibraryAbiStatic2
                    )
                )
            )
        }
    }

    private fun Permutation.assertNoError() {
        writePrefabPackage()
        runPrefab()
        PassThroughDeduplicatingLoggingEnvironment().use { logger ->
            reportErrors(stderr)
            assertThat(logger.errorMessages.map { it.diagnosticCode})
                .isEmpty()
        }
    }

    private fun Permutation.assertHasError(vararg errors : String) {
        writePrefabPackage()
        runPrefab()
        PassThroughDeduplicatingLoggingEnvironment().use { logger ->
            reportErrors(stderr)
            assertThat(logger.errorMessages.map { it.text() })
                .containsExactlyElementsIn(errors)
        }
    }

    private fun Permutation.assertHasError(vararg errors : Regex) {
        writePrefabPackage()
        runPrefab()
        PassThroughDeduplicatingLoggingEnvironment().use { logger ->
            reportErrors(stderr)
            val seen = mutableSetOf<Regex>()
            for(message in logger.errors) {
                for(error in errors) {
                    if (error.matches(message)) {
                        seen.add(error)
                    }
                }
            }
            assertThat(errors.toSet() - seen).isEmpty()
        }
    }

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()
}
