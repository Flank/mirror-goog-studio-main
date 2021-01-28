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

package com.android.tools.utp.plugins.deviceprovider.gradle

import com.google.testing.platform.core.device.DeviceProviderException
import com.google.testing.platform.lib.process.Handle
import com.google.testing.platform.lib.process.inject.SubprocessComponent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Component of the [GradleManagedDeviceLauncher] to handle the emulator instance
 */
class EmulatorHandleImpl(private val subprocessComponent: SubprocessComponent) : EmulatorHandle {
    private companion object {
        /** Max length of time to wait for the emulator to finish booting */
        private const val EMULATOR_BOOT_TIMEOUT_SECONDS = 40L
        private const val EMULATOR_NO_WINDOW = "-no-window"
        private const val EMULATOR_NO_BOOT_ANIM = "-no-boot-anim"
        private const val EMULATOR_READ_ONLY = "-read-only"
    }

    private lateinit var emulatorPath: String

    private lateinit var processHandle: Handle

    override fun configure(emulatorPath: String) {
        this.emulatorPath = emulatorPath
    }

    override fun isAlive() =
            if (this::processHandle.isInitialized) {
                processHandle.isAlive()
            } else {
                false
            }

    override fun launchInstance(
            avdName: String,
            avdFolder: String,
            avdId: String,
            enableDisplay: Boolean
    ) {
        val args = mutableListOf<String>(emulatorPath)
        args.add("@$avdName")
        if (!enableDisplay) {
            args.add(EMULATOR_NO_WINDOW)
        }
        args.add(EMULATOR_READ_ONLY)
        args.add(EMULATOR_NO_BOOT_ANIM)
        args.add("-id")
        args.add(avdId)

        val bootCountDown = CountDownLatch(1)

        val emulatorEnvironment = System.getenv().toMutableMap()
        emulatorEnvironment["ANDROID_AVD_HOME"] = avdFolder
        processHandle = subprocessComponent.subprocess().executeAsync(
                args = args,
                environment = emulatorEnvironment,
                stdoutProcessor = { line ->
                    if (bootCountDown.getCount() != 0L) {
                        if (line.contains("boot completed")) {
                            bootCountDown.countDown()
                        }
                    }
                }
        )

        bootCountDown.await(EMULATOR_BOOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        // In case the processHandle errored out.
        if (!processHandle.isAlive()) {
            throw DeviceProviderException(
                    "Booting the emulator failed. Command was: ${args.joinToString(" ")}"
            )
        }
    }

    override fun closeInstance() {
        if (this::processHandle.isInitialized) {
            processHandle.destroy()
        }
    }
}
