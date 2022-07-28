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

import com.android.build.gradle.internal.cxx.explainLineDifferences
import com.android.build.gradle.internal.cxx.string.StringTable
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.util.JsonFormat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class ConfigureInvalidationStateTest {

    @Rule
    @JvmField
    val tmpFolder = TemporaryFolder()

    private val fixedTimestamp = System.currentTimeMillis()
    private val temporaryFolder by lazy { tmpFolder.newFolder() }
    private val expectedJson by lazy { temporaryFolder.resolve("android_gradle.json") }
    private val commandFile by lazy { temporaryFolder.resolve("command_file.txt") }
    private val buildNinjaFile by lazy { temporaryFolder.resolve("build.ninja") }
    private val cmakeListsFile by lazy { temporaryFolder.resolve("CMakeLists.txt") }
    private val discoveredCmakeFile by lazy { temporaryFolder.resolve("discovered.cmake") }
    private val fingerPrintFile by lazy { temporaryFolder.resolve("configure_fingerprint.bin") }

    private fun getState(
        forceConfigure : Boolean = false,
        configureInputFiles: List<File> = listOf()
    ) : ConfigureInvalidationState {

        val result = createConfigurationInvalidationState(
            forceConfigure = forceConfigure,
            lastConfigureFingerPrintFile = fingerPrintFile,
            configureInputFiles = configureInputFiles,
            requiredOutputFiles = listOf(expectedJson),
            optionalOutputFiles = listOf(buildNinjaFile),
            hardConfigureFiles = listOf(commandFile)
        )

        // Make sure encoding and decoding work for all tested states
        val stringTable = StringTable()
        val transcoded = result.encode(stringTable).decode(stringTable)
        assertThat(transcoded).isEqualTo(result)

        return transcoded
    }

    private fun firstConfigure() : ConfigureInvalidationState {
        cmakeListsFile.writeTextAtStartOfTest("cmakelists txt")
        discoveredCmakeFile.writeTextAtStartOfTest("discovered cmake")
        val state = getState(configureInputFiles = listOf(cmakeListsFile))
        expectedJson.writeTextAfter(discoveredCmakeFile, "some json")
        commandFile.writeTextAfter(discoveredCmakeFile, "command file")
        state.recordConfigurationFingerPrint()
        File(state.fingerPrintFile).touchAfter(expectedJson, commandFile)
        return state
    }

    private fun secondConfigure() : ConfigureInvalidationState {
        val state = getState(configureInputFiles = listOf(cmakeListsFile, discoveredCmakeFile))
        val fingerprintFile = File(state.fingerPrintFile)
        expectedJson.writeTextAfter(fingerprintFile, "some json")
        commandFile.writeTextAfter(fingerprintFile, "command file")
        state.recordConfigurationFingerPrint()
        fingerprintFile.touchAfter(expectedJson, commandFile)
        return state
    }

    @Test
    fun `test initial state`() {
        val state = getState()
        assertThat(state.shouldConfigure).isTrue()
        assertThat(state.softConfigureOkay).isFalse()
        assertThat(state.shouldConfigureReasonMessages).containsExactly(
            "- no fingerprint file, will remove stale configuration folder"
        )
    }

    @Test
    fun `should not configure when no changes`() {
        firstConfigure()
        val state = getState()
        assertThat(state.shouldConfigure).isFalse()
        assertThat(state.softConfigureOkay).isFalse()
        assertThat(state.shouldConfigureReasonMessages).containsExactly()
    }

    @Test
    fun `should hard configure when forceConfigure true`() {
        firstConfigure()
        val state = getState(forceConfigure = true)
        assertThat(state.shouldConfigure).isTrue()
        assertThat(state.softConfigureOkay).isFalse()
        assertThat(state.shouldConfigureReasonMessages).containsExactly(
            "- force flag, will remove stale configuration folder"
        )
    }

    @Test
    fun `should hard configure due to command-file change`() {
        val first = firstConfigure()
        commandFile.writeTextAfter(File(first.fingerPrintFile), "changed")
        val state = getState()
        assertThat(state.shouldConfigure).isTrue()
        assertThat(state.softConfigureOkay).isFalse()
        assertThat(state.shouldConfigureReasonMessages).containsExactly(
            "- a hard configure file changed, will remove stale configuration folder",
            "  - $commandFile (LAST_MODIFIED_CHANGED)"
        )
    }

    @Test
    fun `should configure due to fingerprint file is missing`() {
        firstConfigure()
        fingerPrintFile.delete()
        val state = getState()
        assertThat(state.shouldConfigure).isTrue()
        assertThat(state.softConfigureOkay).isFalse()
        assertThat(state.shouldConfigureReasonMessages).containsExactly(
            "- no fingerprint file, will remove stale configuration folder"
        )
    }

    @Test
    fun `should configure due to configure input files changed`() {
        val first = firstConfigure()
        cmakeListsFile.writeTextAfter(File(first.fingerPrintFile), "changed cmakelists.txt")
        val state = secondConfigure()
        assertThat(state.shouldConfigure).isTrue()
        assertThat(state.softConfigureOkay).isTrue()
        assertThat(state.shouldConfigureReasonMessages).containsExactly(
            "- a file changed",
            "  - $cmakeListsFile (LAST_MODIFIED_CHANGED)"
        )
    }

    @Test
    fun `should not configure when nothing changed`() {
        firstConfigure()
        val state = secondConfigure()
        assertThat(state.shouldConfigure).isFalse()
    }

    @Test
    fun `should configure due to discovered input files changed`() {
        val first = firstConfigure()
        discoveredCmakeFile.writeTextAfter(File(first.fingerPrintFile), "changed discovered cmake")
        val state = secondConfigure()
        state.assertSameInvalidationState("""
            {
              "fingerPrintFile": "TEST/configure_fingerprint.bin",
              "inputFiles": ["TEST/CMakeLists.txt", "TEST/discovered.cmake"],
              "requiredOutputFiles": ["TEST/android_gradle.json"],
              "optionalOutputFiles": ["TEST/build.ninja"],
              "hardConfigureFiles": ["TEST/command_file.txt"],
              "fingerPrintFileExisted": true,
              "addedSinceFingerPrintsFiles": ["TEST/discovered.cmake"],
              "unchangedFingerPrintFiles": ["TEST/CMakeLists.txt", "TEST/android_gradle.json", "TEST/build.ninja", "TEST/command_file.txt"],
              "configureType": "SOFT_CONFIGURE",
              "softConfigureReasons": [{
                "fileName": "TEST/discovered.cmake",
                "type": "LAST_MODIFIED_CHANGED"
              }]
            }
        """.trimIndent())
        assertThat(state.shouldConfigure).isTrue()
        assertThat(state.softConfigureOkay).isTrue()
        assertThat(state.shouldConfigureReasonMessages).containsExactly(
            "- a file changed",
            "  - $discoveredCmakeFile (LAST_MODIFIED_CHANGED)"
        )
    }

    @Test
    fun `should configure due to configure outputs files changed`() {
        val first = firstConfigure()
        expectedJson.writeTextAfter(File(first.fingerPrintFile), "update expected json")
        val state = getState()
        assertThat(state.shouldConfigureReasonMessages).containsExactly(
            "- a file changed",
            "  - $expectedJson (LAST_MODIFIED_CHANGED)"
        )
        assertThat(state.shouldConfigure).isTrue()
        assertThat(state.softConfigureOkay).isTrue()
    }

    @Test
    fun `should configure due to corrupted fingerprint file`() {
        val first = firstConfigure()
        fingerPrintFile.writeTextAfter(File(first.fingerPrintFile), "corrupt")
        val state = getState()
        assertThat(state.shouldConfigure).isTrue()
        assertThat(state.softConfigureOkay).isFalse()
        assertThat(state.shouldConfigureReasonMessages).containsExactly(
            "- no fingerprint file, will remove stale configuration folder"
        )
    }

    @Test
    fun `should configure on added expected output file`() {
        val first = firstConfigure()
        assertThat(buildNinjaFile.exists()).isFalse()
        buildNinjaFile.writeTextAfter(File(first.fingerPrintFile), "new file")
        val state = getState()
        assertThat(state.shouldConfigure).isTrue()
        assertThat(state.softConfigureOkay).isTrue()
        assertThat(state.shouldConfigureReasonMessages).containsExactly(
            "- a file changed",
            "  - $buildNinjaFile (CREATED)",
        )
    }

    @Test
    fun `should configure on deleted output file`() {
        firstConfigure()
        assertThat(expectedJson.isFile).isTrue()
        expectedJson.delete()
        val state = getState()
        assertThat(state.shouldConfigure).isTrue()
        assertThat(state.softConfigureOkay).isTrue()
        assertThat(state.shouldConfigureReasonMessages).containsExactly(
            "- a file changed",
            "  - $expectedJson (DELETED)",
        )
    }

    @Test
    fun `should configure due to missing input file`() {
        firstConfigure()
        cmakeListsFile.delete()
        val state = secondConfigure()
        assertThat(cmakeListsFile.isFile).isFalse()
        assertThat(state.shouldConfigure).isTrue()
        assertThat(state.softConfigureOkay).isTrue()
        assertThat(state.shouldConfigureReasonMessages).containsExactly(
            "- a file changed",
            "  - $cmakeListsFile (DELETED)",
        )
    }

    // Tricky case: Between the first and second configures, a missing input file should not cause a
    // reconfigure. If the first configure was successful then it was successful without this
    // file, so it may be an input will never exist.
    @Test
    fun `should nod configure if missing discovered input file`() {
        firstConfigure()
        discoveredCmakeFile.delete()
        val state = secondConfigure()
        assertThat(discoveredCmakeFile.isFile).isFalse()
        assertThat(state.shouldConfigure).isFalse()
    }

    @Test
    fun `should configure repeatedly when removing a required output file`() {
        val initial = firstConfigure()
        initial.assertSameInvalidationState("""
            {
              "fingerPrintFile": "TEST/configure_fingerprint.bin",
              "inputFiles": ["TEST/CMakeLists.txt"],
              "requiredOutputFiles": ["TEST/android_gradle.json"],
              "optionalOutputFiles": ["TEST/build.ninja"],
              "hardConfigureFiles": ["TEST/command_file.txt"],
              "configureType": "HARD_CONFIGURE"
            }
        """.trimIndent())

        // First configure after delete of 'android_gradle_build.json' triggers a rebuild
        expectedJson.delete()
        val first = getState()
        first.assertSameInvalidationState("""
            {
              "fingerPrintFile": "TEST/configure_fingerprint.bin",
              "requiredOutputFiles": ["TEST/android_gradle.json"],
              "optionalOutputFiles": ["TEST/build.ninja"],
              "hardConfigureFiles": ["TEST/command_file.txt"],
              "fingerPrintFileExisted": true,
              "removedSinceFingerPrintsFiles": ["TEST/CMakeLists.txt"],
              "changesToFingerPrintFiles": [{
                "fileName": "TEST/android_gradle.json",
                "type": "DELETED"
              }],
              "unchangedFingerPrintFiles": ["TEST/CMakeLists.txt", "TEST/build.ninja", "TEST/command_file.txt"],
              "configureType": "SOFT_CONFIGURE",
              "softConfigureReasons": [{
                "fileName": "TEST/android_gradle.json",
                "type": "DELETED"
              }]
            }
        """.trimIndent())
        first.recordConfigurationFingerPrint()
        assertThat(first.shouldConfigure).isTrue()
        assertThat(first.softConfigureOkay).isTrue()

        // Second configure after delete of 'android_gradle_build.json' also triggers a rebuild
        val second = getState()
        second.assertSameInvalidationState("""
            {
              "fingerPrintFile": "TEST/configure_fingerprint.bin",
              "requiredOutputFiles": ["TEST/android_gradle.json"],
              "optionalOutputFiles": ["TEST/build.ninja"],
              "hardConfigureFiles": ["TEST/command_file.txt"],
              "fingerPrintFileExisted": true,
              "unchangedFingerPrintFiles": ["TEST/android_gradle.json", "TEST/build.ninja", "TEST/command_file.txt"],
              "configureType": "SOFT_CONFIGURE",
              "softConfigureReasons": [{
                "fileName": "TEST/android_gradle.json",
                "type": "DELETED"
              }]
            }
        """.trimIndent())
        assertThat(second.shouldConfigure).named(second.describe()).isTrue()
        assertThat(second.softConfigureOkay).named(second.describe()).isTrue()
    }

    @Test
    fun `should configure once after removing an optional output file`() {
        buildNinjaFile.createNewFile()
        firstConfigure()

        // First configure after delete of 'build.ninja' triggers a rebuild
        buildNinjaFile.delete()
        val first = getState()
        first.recordConfigurationFingerPrint()
        assertThat(first.shouldConfigure).isTrue()
        assertThat(first.softConfigureOkay).isTrue()

        // Second configure after delete of 'build.ninja' does not trigger a rebuild
        val second = getState()
        second.assertSameInvalidationState("""
            {
              "fingerPrintFile": "TEST/configure_fingerprint.bin",
              "requiredOutputFiles": ["TEST/android_gradle.json"],
              "optionalOutputFiles": ["TEST/build.ninja"],
              "hardConfigureFiles": ["TEST/command_file.txt"],
              "fingerPrintFileExisted": true,
              "unchangedFingerPrintFiles": ["TEST/android_gradle.json", "TEST/build.ninja", "TEST/command_file.txt"],
              "configureType": "NO_CONFIGURE"
            }
        """.trimIndent())
        assertThat(second.shouldConfigure).named(second.describe()).isFalse()
    }

    /*
    The best file system timestamp is millisecond and lower resolution is available depending on
    operating system and Java versions. This implementation of touch makes sure that the new
    timestamp isn't the same as the old timestamp by spinning until the clock increases.
     */
    private fun spinTouch(file: File, lastTimestamp: Long) {
        // This function repeatedly creates new File objects because Java can cache
        // get/setLastModified
        File(file.absolutePath).setLastModified(System.currentTimeMillis())
        while (getHighestResolutionTimeStamp(File(file.absolutePath)) <= lastTimestamp) {
            File(file.absolutePath).setLastModified(System.currentTimeMillis())
        }
    }

    /**
     * Ensure this file has a timestamp greater than any file in [files].
     */
    private fun File.touchAfter(vararg files: File) {
        assertThat(this.isFile).isTrue()
        val highest = files.maxOf {  getHighestResolutionTimeStamp(it) }
        spinTouch(this, highest)
    }

    private fun getHighestResolutionTimeStamp(file: File): Long {
        return Files.getLastModifiedTime(file.toPath()).toMillis()
    }

    /*
    Create a file after another file.
     */
    private fun File.writeTextAfter(other : File, value : String) : File {
        val timestamp = getHighestResolutionTimeStamp(other.absoluteFile)
        writeTextAtStartOfTest(value)
        spinTouch(this, timestamp)
        return this
    }

    /*
    Write a file and timestamp it at the beginning of this test. This is to ensure that tests
    aren't passing accidentally due to differences in timestamp
     */
    private fun File.writeTextAtStartOfTest(value : String) {
        writeText(value)
        setLastModified(fixedTimestamp)
    }

    private fun ConfigureInvalidationState.assertSameInvalidationState(expected : String) {
        val sb = StringBuilder()
        JsonFormat.printer().appendTo(this, sb)
        val stateText = clean(sb)
        if (stateText == expected) return
        assertThat(true).named("$stateText\n" + explainLineDifferences(expected, stateText)).isFalse()
    }

    private fun ConfigureInvalidationState.describe() : String {

        val stateTextStringBuilder = StringBuilder()
        JsonFormat.printer().appendTo(this, stateTextStringBuilder)
        val stateText = clean(stateTextStringBuilder)

        val sb = StringBuilder()
        sb.appendLine()
        shouldConfigureReasonMessages.forEach { message ->
            sb.appendLine(clean(message))
        }
        sb.appendLine(stateText)

        return "$sb".trim('\n')
    }

    fun clean(value : CharSequence) = "$value"
        .replace("\\\\", "/")
        .replace(temporaryFolder.path.replace("\\", "/"), "TEST")

}
