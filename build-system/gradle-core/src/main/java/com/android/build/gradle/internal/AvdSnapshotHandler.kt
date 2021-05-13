/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal

import com.android.utils.GrabProcessOutput
import com.android.utils.ILogger
import java.io.File
import java.lang.RuntimeException
import java.nio.file.Files.readAllLines
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider

private const val EMULATOR_EXECUTABLE = "emulator"
private const val SNAPSHOT_CHECK_TIMEOUT_SEC = 30L
private const val WAIT_AFTER_BOOT_MS = 5000L
private const val DEVICE_BOOT_TIMEOUT_SEC = 80L
private const val MINIMUM_MAJOR_VERSION = 30
private const val MINIMUM_MINOR_VERSION = 6
private const val MINIMUM_MICRO_VERSION = 4

class AvdSnapshotHandler(
    private val processFactory: (List<String>) -> ProcessBuilder = { ProcessBuilder(it) }) {
    /**
     * Checks whether the emulator directory contains a valid emulator executable, and returns it.
     *
     * @param emulatorDirectoryProvider provider for the directory containing the emulator and related
     * files.
     *
     * @return the emulator executable from the given directory.
     */
    fun getEmulatorExecutable(emulatorDirectoryProvider: Provider<Directory>): File {
        val emulatorDir =
            emulatorDirectoryProvider.orNull?.asFile ?: error("Emulator dir does not exist")
        ensureEmulatorVersionRequirement(emulatorDir)
        return emulatorDir.resolve(EMULATOR_EXECUTABLE)
    }

    /**
     * Checks whether the given snapshot on a device is loadable with the emulator.
     *
     * Uses a command of the form:
     * ./emulator @[avdName] -no-window -no-boot-anim -check-snapshot-loadable [snapshotName]
     * Which does not open an instance of the emulator and instead returns "Loadable" or
     * "Not loadable" depending on if the snapshot is compatible with current emulator.
     * Note: -no-window and -no-boot-anim affect which snapshots may be loadable, but
     * otherwise have no effect.
     *
     * @param avdName The name of the device to check if the snapshot is loadable on.
     * @param snapshotName The name of the snapshot to check if loadable.
     *
     * @return true if and only if the snapshot is loadable with the current version of
     * the emulator.
     */
    fun checkSnapshotLoadable(
        avdName: String,
        emulatorExecutable: File,
        avdLocation: File,
        logger: ILogger,
        snapshotName: String = "default-boot"
    ): Boolean {
        logger.info("Checking $snapshotName on device $avdName is loadable.")
        val processBuilder = processFactory(
            listOf(
                emulatorExecutable.absolutePath,
                "@$avdName",
                "-no-window",
                "-no-boot-anim",
                "-check-snapshot-loadable",
                snapshotName
            )
        )
        processBuilder.environment()["ANDROID_AVD_HOME"] = avdLocation.absolutePath
        val process = processBuilder.start()

        var success = false
        try {
            GrabProcessOutput.grabProcessOutput(
                process,
                GrabProcessOutput.Wait.WAIT_FOR_PROCESS,
                object : GrabProcessOutput.IProcessOutput {
                    override fun out(line: String?) {
                        line ?: return
                        logger.verbose(line)
                        // If it fails, the line will contain "Not loadable"
                        // so checking for the capitalized text should be fine.
                        if (line.contains("Loadable")) {
                            success = true
                        }
                    }

                    override fun err(line: String?) {}
                }
            )
            if (!process.waitFor(SNAPSHOT_CHECK_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                process.destroy()
            }
        } catch (e: Exception) {
            process.destroy()
            throw RuntimeException(e)
        }
        return success
    }

    /**
     * Generates a snapshot for the given device.
     *
     * Temporarily opens the emulator to load the device in a state where a
     * snapshot can be saved. Then the emulator is closed to force a snapshot
     * write.
     *
     * @param avdName name of the device for a snapshot to be created.
     */
    fun generateSnapshot(
        avdName: String,
        emulatorExecutable: File,
        avdLocation: File,
        logger: ILogger
    ) {
        logger.verbose("Creating snapshot for $avdName")

        val processBuilder = processFactory(
            listOf(
                emulatorExecutable.absolutePath,
                "@${avdName}",
                "-no-window",
                "-no-boot-anim"
            )
        )
        processBuilder.environment()["ANDROID_AVD_HOME"] = avdLocation.absolutePath
        val process = processBuilder.start()

        try {
            GrabProcessOutput.grabProcessOutput(
                process,
                GrabProcessOutput.Wait.ASYNC,
                object : GrabProcessOutput.IProcessOutput {
                    override fun out(line: String?) {
                        line ?: return
                        logger.verbose(line)
                        if (line.contains("boot completed")) {
                            Thread.sleep(WAIT_AFTER_BOOT_MS)
                            process.destroyForcibly()
                        }
                    }

                    override fun err(line: String?) {}
                }
            )
            if (!process.waitFor(DEVICE_BOOT_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                logger.verbose("Snapshot creation timed out. Closing emulator.")
                process.destroyForcibly()
                process.waitFor()
                error("Failed to generate snapshot for device: $avdName")
            } else {
                logger.verbose("Successfully created snapshot for: $avdName")
            }
        } catch (e: Exception) {
            process.destroyForcibly()
            process.waitFor()
            throw e
        }
    }

    /**
     * Ensures that all required features of the emulator are present.
     *
     * Checks the emulator version to ensure the emulator executable supports the
     * "--check-snapshot-loadable" flag. Errors on failure.
     */
    private fun ensureEmulatorVersionRequirement(emulatorDir: File) {
        val packageFile = emulatorDir.resolve("package.xml")
        val versionPattern =
            Pattern.compile("<major>(\\d+)</major><minor>(\\d+)</minor><micro>(\\d+)</micro>")
        for (line in readAllLines(packageFile.toPath())) {
            val matcher = versionPattern.matcher(line)
            if (matcher.find()) {
                val majorVersion = matcher.group(1).toInt()
                val minorVersion = matcher.group(2).toInt()
                val microVersion = matcher.group(3).toInt()
                when {
                    majorVersion > MINIMUM_MAJOR_VERSION -> return
                    majorVersion == MINIMUM_MAJOR_VERSION &&
                            minorVersion > MINIMUM_MINOR_VERSION -> return
                    majorVersion == MINIMUM_MAJOR_VERSION &&
                            minorVersion == MINIMUM_MINOR_VERSION &&
                            microVersion >= MINIMUM_MICRO_VERSION -> return
                    else ->
                        error(
                            "Emulator needs to be updated in order to use managed devices. Minimum " +
                                    "version required: $MINIMUM_MAJOR_VERSION.$MINIMUM_MINOR_VERSION" +
                                    ".$MINIMUM_MICRO_VERSION. Version found: $majorVersion.$minorVersion" +
                                    ".$microVersion."
                        )
                }
            }
        }
        error(
            "Could not determine version of Emulator in ${emulatorDir.absolutePath}. Update " +
                    "emulator in order to use Managed Devices."
        )
    }
}
