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

package com.android.build.gradle.internal.cxx.ninja

import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.ninja.Record.Dependencies
import com.android.build.gradle.internal.cxx.ninja.Record.Path
import com.android.build.gradle.internal.cxx.ninja.Record.Version
import com.android.build.gradle.internal.cxx.string.StringTable
import com.android.testutils.TestResources
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NinjaBuildDepsCodecTest {
    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private fun testFile(testFileName: String): File {
        val resourceFolder = "/com/android/build/gradle/external/ninja/"
        return TestResources.getFile(
            NinjaBuildDepsCodecTest::class.java, resourceFolder + testFileName
        )
    }

    @Test
    fun `repro missing zero path`() {
        val file = testFile("repro_missing_zero.ninja_deps")
        sanityCheckNinjaDependencies(file)
        val deps = NinjaDepsInfo.readFile(file)
        println(deps)
    }

    @Test
    fun `check dolphin x86_64`() {
        val file = testFile("dolphin-x86_64.ninja_deps")
        val kbytes = file.length() / 1024 // 915K
        sanityCheckNinjaDependencies(file)
        val start = System.currentTimeMillis()
        val iterations = 1000
        repeat(iterations) {
            NinjaDepsInfo.readFile(file)
        }
        val mean = (System.currentTimeMillis() - start) / iterations
        println("Average dolphin read time is $mean ms for ${kbytes}K bytes. Baseline on MacBook pro was 2 ms.")
        // Check for a mean two orders of magnitude greater than MacBook baseline as a crude check
        // for regressions. That shouldn't be flaky in pre-submit, right? If it is, then disable
        // just this check below.
        if (mean > 200) {
            error("Very large regression in dolphin .ninja_deps read")
        }
    }

    @Test
    fun `repro missing dependencies`() {
        val file = testFile("repro_missing_deps.ninja_deps")
        println(file)
        val path = "../../../../src/main/cxx/executable/main.cpp"
        sanityCheckNinjaDependencies(file)
        val deps = NinjaDepsInfo.readFile(file)
        deps.pathTable.getId(path)
    }

    @Test
    fun `create empty`() {
        val depsFile = temporaryFolder.newFile("test.ninja_deps")
        depsFile.delete()
        createEmptyNinjaDepsFile(depsFile, schemaVersion = 3)
        assertThat(depsFile.isFile).isTrue()
        sanityCheckNinjaDependencies(depsFile)
    }

    @Test
    fun `create empty then open`() {
        val depsFile = temporaryFolder.newFile("test.ninja_deps")
        depsFile.delete()
        createEmptyNinjaDepsFile(depsFile, schemaVersion = 3)
        NinjaDepsEncoder.open(depsFile).use { }
        assertThat(depsFile.isFile).isTrue()
        sanityCheckNinjaDependencies(depsFile)
    }

    @Test
    fun `write one target schema 3`() {
        val depsFile = temporaryFolder.newFile("test.ninja_deps")
        depsFile.delete()
        val target = temporaryFolder.newFile("target.c.o")
        val dependency = temporaryFolder.newFile("target.c")
        createEmptyNinjaDepsFile(depsFile, schemaVersion = 3)
        NinjaDepsEncoder.open(depsFile).use { encoder ->
            encoder.writeTarget(target.path, 5L, listOf(dependency.path))
        }
        sanityCheckNinjaDependencies(depsFile)
        val deps = NinjaDepsInfo.readFile(depsFile)
        val recoveredTarget = deps.pathTable.decode(0)
        assertThat(recoveredTarget).isEqualTo(target.path)
        assertThat(deps.pathTable.decode(1)).isEqualTo(dependency.path)
        val recoveredDependencies = deps.getDependencies(target.path)!!
        assertThat(recoveredDependencies).isEqualTo(listOf(dependency.path))
    }

    @Test
    fun `write one target schema 4`() {
        val depsFile = temporaryFolder.newFile("test.ninja_deps")
        depsFile.delete()
        val target = temporaryFolder.newFile("target.c.o")
        val dependency = temporaryFolder.newFile("target.c")
        createEmptyNinjaDepsFile(depsFile, schemaVersion = 3)
        NinjaDepsEncoder.open(depsFile).use { encoder ->
            encoder.writeTarget(target.path, 5L, listOf(dependency.path))
        }
        sanityCheckNinjaDependencies(depsFile)
        val deps = NinjaDepsInfo.readFile(depsFile)
        val recoveredTarget = deps.pathTable.decode(0)
        assertThat(recoveredTarget).isEqualTo(target.path)
        assertThat(deps.pathTable.decode(1)).isEqualTo(dependency.path)
        val recoveredDependencies = deps.getDependencies(target.path)!!
        assertThat(recoveredDependencies).isEqualTo(listOf(dependency.path))
    }

    @Test
    fun `round trip dolphin`() {
        val file = testFile("dolphin-x86_64.ninja_deps")
        sanityCheckRoundTrip(file)
    }

    @Test
    fun `round trip example`() {
        val file = testFile("repro_missing_deps.ninja_deps")
        sanityCheckRoundTrip(file)
    }

    @Test
    fun `round trip example 2`() {
        val file = testFile("repro_missing_zero.ninja_deps")
        sanityCheckRoundTrip(file)
    }

    private fun streamingDuplicate(input:File, output:File) {
        output.delete()
        val strings = StringTable()
        lateinit var encoder: NinjaDepsEncoder
        streamNinjaDepsFile(input) { record ->
            when (record) {
                is Dependencies -> {
                    val target = strings.decode(record.targetPath)
                    val dependencyIds = IntArray(record.dependencies.remaining())
                    record.dependencies.get(dependencyIds)
                    val dependencies = dependencyIds.toList().map { strings.decode(it) }
                    encoder.writeTarget(
                        target,
                        record.timestamp,
                        dependencies
                    )
                }

                is Path -> strings.getIdCreateIfAbsent(record.path.toString())
                is Version -> {
                    createEmptyNinjaDepsFile(output, record.version)
                    encoder = NinjaDepsEncoder.open(output)
                }
                else -> error("")
            }
        }
        encoder.close()
    }

    private fun sanityCheckRoundTrip(ninjaDepsFile: File) {
        val writeTo = temporaryFolder.newFile("write-to.ninja_deps")
        streamingDuplicate(ninjaDepsFile, writeTo)
        val originalDeps = NinjaDepsInfo.readFile(ninjaDepsFile)

        streamNinjaDepsFile(writeTo) { }

        val copiedDeps = NinjaDepsInfo.readFile(writeTo)
        assertThat(copiedDeps.pathTable.size).isEqualTo(originalDeps.pathTable.size)
        for(i in copiedDeps.pathTable.indices) {
            val original = originalDeps.pathTable.decode(i)
            val copied = copiedDeps.pathTable.decode(i)
            assertThat(copied).isEqualTo(original)
        }
    }

    /**
     * Check validity of a ninja_deps file
     */
    private fun sanityCheckNinjaDependencies(file : File) {
        infoln("ninja_deps: Checking '$file'")
        val stringTable = StringTable()
        streamNinjaDepsFile(file) { record ->
            when(record) {
                is Version -> {
                    assert(record.version == 3 || record.version == 4)
                }
                is Dependencies -> {
                    if (!stringTable.containsId(record.targetPath)) {
                        error("Did not previously see '${record.targetPath}'")
                    }
                    val dependencies = IntArray(record.dependencies.remaining())
                    record.dependencies.get(dependencies)
                    for(path in dependencies) {
                        if (!stringTable.containsId(path)) {
                            error("Did not previously see '$path'")
                        }
                    }
                }
                is Path -> {
                    val path = record.path.toString()
                    if (stringTable.containsString(path)) {
                        error("Already saw '$path'")
                    }
                    val id = stringTable.getIdCreateIfAbsent(path)
                    if(id != record.checkSum.inv()) {
                        error("Checksum was wrong. Expected $id but got ${record.checkSum.inv()} (${record.checkSum})")
                    }
                }
                else -> error("unexpected")
            }
        }
    }
}
