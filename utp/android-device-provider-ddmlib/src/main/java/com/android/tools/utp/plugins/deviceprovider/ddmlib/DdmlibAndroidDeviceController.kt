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

package com.android.tools.utp.plugins.deviceprovider.ddmlib

import com.android.ddmlib.InstallReceiver
import com.android.ddmlib.MultiLineReceiver
import com.google.testing.platform.api.device.CommandHandle
import com.google.testing.platform.api.device.CommandResult
import com.google.testing.platform.api.device.Device
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.proto.api.core.TestArtifactProto.Artifact
import com.google.testing.platform.proto.api.core.TestArtifactProto.ArtifactType.ANDROID_APK
import com.google.testing.platform.runtime.android.device.AndroidDevice
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeoutException
import java.util.concurrent.TimeUnit

/**
 * Android specific implementation of [DeviceController] using DDMLIB.
 */
class DdmlibAndroidDeviceController : DeviceController {

    companion object {
        private const val DEFAULT_ADB_TIMEOUT_SECONDS = 120L
        private const val SHELL_EXIT_CODE_TAG = "utp_shell_exit_code="
    }

    private lateinit var controlledDevice: DdmlibAndroidDevice
    private lateinit var androidDevice: AndroidDevice

    override fun getDevice(): AndroidDevice = androidDevice

    override fun setDevice(device: Device) {
        controlledDevice = device as DdmlibAndroidDevice
        androidDevice = AndroidDevice(
                serial = controlledDevice.serial,
                type = requireNotNull(controlledDevice.type),
                port = controlledDevice.port,
                properties = controlledDevice.properties
        )
    }

    override fun install(artifact: Artifact): Int {
        require(ANDROID_APK == artifact.type) {
            "Artifact needs to be of type: $ANDROID_APK, but was ${artifact.type}"
        }
        require(artifact.hasSourcePath()) {
            "Artifact source path needs to be set!"
        }

        controlledDevice.installPackage(
                artifact.sourcePath.path,
                /*reinstall=*/true,
                *listOfNotNull(
                        "-t",
                        "-g".takeIf { controlledDevice.version.apiLevel >= 23 }
                ).toTypedArray()
        )
        return 0
    }

    override fun execute(args: List<String>, timeout: Long?): CommandResult {
        val output = mutableListOf<String>()
        val handler = executeAsync(args) { output.add(it) }
        handler.waitFor(timeout ?: DEFAULT_ADB_TIMEOUT_SECONDS)
        return CommandResult(handler.exitCode(), output)
    }

    override fun executeAsync(args: List<String>, processor: (String) -> Unit): CommandHandle {
        var isCancelled = false
        val deferred = GlobalScope.async {
            val command = args.first().toLowerCase()
            val commandArgs = args.subList(1, args.size)

            // Setting max timeout to 0 (= indefinite) because we control
            // the timeout by the receiver.isCancelled().
            var commandExitCode: Int = 0
            val receiver = when(command) {
                "shell" -> {
                    val receiver = object: MultiLineReceiver() {
                        override fun isCancelled(): Boolean = isCancelled
                        override fun processNewLines(lines: Array<out String>) {
                            lines.forEach { line ->
                                if (line.startsWith(SHELL_EXIT_CODE_TAG)) {
                                    commandExitCode = line.removePrefix(SHELL_EXIT_CODE_TAG)
                                        .trim().toIntOrNull() ?: 0
                                } else {
                                    processor(line)
                                }
                            }
                        }
                    }
                    controlledDevice.executeShellCommand(
                        (commandArgs + "; echo ${SHELL_EXIT_CODE_TAG}$?").joinToString(" "),
                        receiver,
                        /*maxTimeout=*/0,
                        /*maxTimeToOutputResponse=*/0,
                        TimeUnit.SECONDS
                    )
                    receiver
                }
                "install" -> {
                    val installArgs = commandArgs.take(commandArgs.size - 1)
                    val apkPath = commandArgs.last()
                    val receiver = object: InstallReceiver() {
                        override fun isCancelled(): Boolean = isCancelled
                        override fun processNewLines(lines: Array<out String>) {
                            super.processNewLines(lines)
                            lines.forEach(processor)
                        }
                    }
                    controlledDevice.installPackage(
                            apkPath,
                            /*reinstall=*/true,
                            receiver,
                            /*maxTimeout=*/0,
                            /*maxTimeToOutputResponse=*/0,
                            TimeUnit.SECONDS,
                            *installArgs.toTypedArray()
                    )
                    receiver
                }
                else -> {
                    throw UnsupportedOperationException("Unsupported ADB command: $command")
                }
            }
            CommandResult(
                    if (receiver.isCancelled) {
                        -1
                    } else {
                        commandExitCode
                    },
                    emptyList()
            )
        }
        return object : CommandHandle {
            override fun exitCode(): Int = deferred.getCompleted().statusCode

            override fun stop() {
                isCancelled = true
            }

            override fun isRunning(): Boolean = deferred.isActive

            @Throws(TimeoutException::class)
            override fun waitFor(timeout: Long?): Unit = runBlocking {
                if (timeout != null) {
                    withTimeout(timeout * 1000) {
                        deferred.await()
                    }
                } else {
                    deferred.await()
                }
            }
        }
    }

    override fun push(artifact: Artifact): Int {
        require(artifact.hasSourcePath() && artifact.hasDestinationPath()) {
            "Artifact source and destination paths need to be set!"
        }
        controlledDevice.pushFile(artifact.sourcePath.path, artifact.destinationPath.path)
        return 0
    }

    override fun pull(artifact: Artifact): Int {
        require(artifact.hasSourcePath() && artifact.hasDestinationPath()) {
            "Artifact source and destination paths need to be set!"
        }
        controlledDevice.pullFile(artifact.destinationPath.path, artifact.sourcePath.path)
        return 0
    }
}
