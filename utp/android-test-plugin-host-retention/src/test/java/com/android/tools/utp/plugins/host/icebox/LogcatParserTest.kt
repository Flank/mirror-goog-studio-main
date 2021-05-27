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

package com.android.tools.utp.plugins.host.icebox

import com.android.testutils.MockitoKt
import com.google.common.truth.Truth
import com.google.testing.platform.api.device.CommandHandle
import com.google.testing.platform.api.device.CommandResult
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.runtime.android.controller.ext.deviceShell
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.quality.Strictness

/**
 * Unit tests for [LogcatParser]
 */
@RunWith(JUnit4::class)
class LogcatParserTest {

    @get:Rule
    var mockitoJunit = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @Mock
    private lateinit var mockCommandHandle: CommandHandle

    @Mock
    private lateinit var mockDeviceController: DeviceController

    @Test
    fun parseLogcat() {
        val logcatParser = LogcatParser()
        val startTime = "05-19 22:19:09"
        val waitingForDebugger =
            "05-19 22:19:10.979  1234 5678 I AndroidJUnitRunner: Waiting for debugger to connect..."

        Mockito.`when`(
            mockDeviceController.deviceShell(listOf("date", "+%m-%d\\ %H:%M:%S"))
        ).thenReturn(CommandResult(0, listOf(startTime)))
        Mockito.`when`(
            mockDeviceController.executeAsync(
                MockitoKt.eq(
                    listOf(
                        "shell",
                        "logcat",
                        "-v",
                        "threadtime",
                        "-b",
                        "main",
                        "-T",
                        "\'$startTime.000\'"
                    )
                ),
                MockitoKt.any()
            )
        ).then {
            it.getArgument<(String) -> Unit>(1).invoke(waitingForDebugger)
            mockCommandHandle
        }
        logcatParser.start(mockDeviceController) { date, time, pid_, tid_, verbose, tag, message ->
            Truth.assertThat(date).isEqualTo("05-19")
            Truth.assertThat(time).isEqualTo("22:19:10.979")
            Truth.assertThat(pid_).isEqualTo("1234")
            Truth.assertThat(tid_).isEqualTo("5678")
            Truth.assertThat(verbose).isEqualTo("I")
            Truth.assertThat(tag).isEqualTo("AndroidJUnitRunner")
            Truth.assertThat(message).isEqualTo("Waiting for debugger to connect...")
        }
    }
}
