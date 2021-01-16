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

import com.android.testutils.MockitoKt.any
import com.google.common.truth.Truth.assertThat
import com.google.testing.platform.core.device.DeviceProviderException
import com.google.testing.platform.lib.process.Handle
import com.google.testing.platform.lib.process.Subprocess
import com.google.testing.platform.lib.process.inject.SubprocessComponent
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.nullable
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.MockitoAnnotations

/**
 * Tests for [EmulatorHandleImpl]
 */
@RunWith(JUnit4::class)
class EmulatorHandleTest {
    private companion object {
        const val emulatorPath = "/path/to/emulator"
        const val avdName = "dev29_aosp_x86_Pixel_2"
        const val avdId = "someUniqueIdHere"
        const val avdFolder = "/path/to/gradle/avd"
    }

    @Mock
    private lateinit var subprocessComponent: SubprocessComponent

    @Mock
    private lateinit var subprocess: Subprocess

    private lateinit var emulatorHandle: EmulatorHandle

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        `when`(subprocessComponent.subprocess()).thenReturn(subprocess)

        emulatorHandle = EmulatorHandleImpl(subprocessComponent)
        emulatorHandle.configure(emulatorPath)
    }

    @Test
    fun launchInstance_testLaunchAndCloseDevice() {
        setEmulatorOutput()
        emulatorHandle.launchInstance(
                avdName,
                avdFolder,
                avdId,
                enableDisplay = false
        )
        assertThat(emulatorHandle.isAlive()).isTrue()
        verifyCommand(
                "/path/to/emulator @dev29_aosp_x86_Pixel_2 -no-window -read-only -no-boot-anim " +
                        "-id someUniqueIdHere"
        )

        emulatorHandle.closeInstance()
        assertThat(emulatorHandle.isAlive()).isFalse()
    }

    @Test
    fun launchInstance_testEnableDisplay() {
        setEmulatorOutput()
        emulatorHandle.launchInstance(
                avdName,
                avdFolder,
                avdId,
                enableDisplay = true
        )
        assertThat(emulatorHandle.isAlive()).isTrue()
        verifyCommand(
                "/path/to/emulator @dev29_aosp_x86_Pixel_2 -read-only -no-boot-anim -id someUniqueIdHere"
        )

        emulatorHandle.closeInstance()
        assertThat(emulatorHandle.isAlive()).isFalse()
    }

    @Test
    fun launchInstance_emulatorFailureThrows() {
        setEmulatorOutput(fails = true)
        assertThrows(DeviceProviderException::class.java) {
            emulatorHandle.launchInstance(
                    avdName,
                    avdFolder,
                    avdId,
                    false
            )
        }
    }

    private fun setEmulatorOutput(fails: Boolean = false) {
        `when`(
                subprocess.executeAsync(
                        any(),
                        any(),
                        nullable(),
                        nullable()
                )
        ).thenAnswer {
            val outProcess = it.getArgument(2) as ((String) -> Unit)?

            // Ensure countdown latch doesn't timeout
            outProcess?.invoke("boot completed")

            object : Handle {
                var isRunning: Boolean = !fails

                override fun exitCode(): Int = if (fails) 1 else 0

                override fun destroy() {
                    isRunning = false
                }

                override fun isAlive(): Boolean = isRunning

                override fun waitFor(timeout: Long?) = destroy()
            }
        }
    }

    private fun verifyCommand(expectedCommand: String) {
        val commandCaptor = argumentCaptor<List<String>>()
        val environmentCaptor = argumentCaptor<Map<String, String>>()
        verify(subprocess).executeAsync(
                commandCaptor.capture() ?: listOf(),
                environmentCaptor.capture() ?: mapOf(),
                nullable(),
                nullable()
        )
        val command: String = commandCaptor.value.joinToString(" ")
        val environment: Map<String, String> = environmentCaptor.value
        assertThat(command).isEqualTo(expectedCommand)
        assertThat(environment["ANDROID_AVD_HOME"]).isEqualTo(avdFolder)
        verifyNoMoreInteractions(subprocess)
    }
}

private inline fun <reified T : Any> nullable(): T? {
    return nullable(T::class.java)
}

private inline fun <reified T : Any> argumentCaptor(): ArgumentCaptor<T> {
    return ArgumentCaptor.forClass(T::class.java)
}
