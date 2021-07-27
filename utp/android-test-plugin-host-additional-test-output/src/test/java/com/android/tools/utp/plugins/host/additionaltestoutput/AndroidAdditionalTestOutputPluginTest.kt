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

package com.android.tools.utp.plugins.host.additionaltestoutput

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.tools.utp.plugins.host.additionaltestoutput.proto.AndroidAdditionalTestOutputConfigProto.AndroidAdditionalTestOutputConfig
import com.google.protobuf.Any
import com.google.testing.platform.api.config.ProtoConfig
import com.google.testing.platform.api.device.CommandResult
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.proto.api.core.ExtensionProto
import com.google.testing.platform.proto.api.core.TestArtifactProto
import com.google.testing.platform.proto.api.core.TestResultProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import java.util.logging.Level
import java.util.logging.Logger
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

/**
 * Unit tests for [AndroidAdditionalTestOutputPlugin].
 */
@RunWith(JUnit4::class)
class AndroidAdditionalTestOutputPluginTest {
    @get:Rule var mockitoJUnitRule: MockitoRule =
        MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @get:Rule var tempDirs = TemporaryFolder()

    @Mock private lateinit var mockDeviceController: DeviceController
    @Mock private lateinit var mockLogger: Logger

    private var commandResult: CommandResult = CommandResult(0, listOf())

    @Before
    fun setUpMocks() {
        `when`(mockDeviceController.execute(anyList(), nullable(Long::class.java))).then {
            commandResult
        }
    }

    private fun createTestArtifact(
        filePathOnDevice: String, filePathOnHost: String): TestArtifactProto.Artifact {
        return TestArtifactProto.Artifact.newBuilder().apply {
            sourcePathBuilder.path = filePathOnHost
            destinationPathBuilder.path = filePathOnDevice
        }.build()
    }

    private fun createPlugin(config: AndroidAdditionalTestOutputConfig)
    : AndroidAdditionalTestOutputPlugin {
        val packedConfig = Any.pack(config)
        val protoConfig = object: ProtoConfig {
            override val configProto: Any
                get() = packedConfig
            override val configResource: ExtensionProto.ConfigResource?
                get() = null
        }
        return AndroidAdditionalTestOutputPlugin(mockLogger).apply {
            configure(protoConfig)
        }
    }

    private fun runPlugin(
        configFunc: AndroidAdditionalTestOutputConfig.Builder.() -> Unit) {
        val config = AndroidAdditionalTestOutputConfig.newBuilder().apply {
            configFunc(this)
        }.build()
        createPlugin(config).apply {
            beforeAll(mockDeviceController)
            beforeEach(null, mockDeviceController)
            afterEach(TestResultProto.TestResult.getDefaultInstance(), mockDeviceController)
            afterAll(TestSuiteResultProto.TestSuiteResult.getDefaultInstance(), mockDeviceController)
        }
    }

    @Test
    fun runPluginAndSuccess() {
        val hostDir = tempDirs.newFolder().absolutePath
        val deviceDir = "/onDevice/outputDir/"

        `when`(mockDeviceController.execute(
            eq(listOf("shell", "ls \"${deviceDir}\"")),
            nullable(Long::class.java)
        )).thenReturn(CommandResult(0, listOf("output1.txt", "output2.txt")))

        runPlugin {
            additionalOutputDirectoryOnHost = hostDir
            additionalOutputDirectoryOnDevice = deviceDir
        }

        inOrder(mockDeviceController).apply {
            verify(mockDeviceController).execute(listOf("shell", "rm -rf \"${deviceDir}\""))
            verify(mockDeviceController).execute(listOf("shell", "mkdir -p \"${deviceDir}\""))
            verify(mockDeviceController).execute(listOf("shell", "ls \"${deviceDir}\""))
            verify(mockDeviceController).pull(createTestArtifact(
                "${deviceDir}/output1.txt", "${hostDir}/output1.txt"))
            verify(mockDeviceController).pull(createTestArtifact(
                "${deviceDir}/output2.txt", "${hostDir}/output2.txt"))
            verifyNoMoreInteractions()
        }

        verifyNoMoreInteractions(mockLogger)
    }

    @Test
    fun runPluginAndFailsOnFileCopy() {
        val exception = IllegalStateException("some error")
        val hostDir = tempDirs.newFolder().absolutePath
        val deviceDir = "/onDevice/outputDir/"

        `when`(mockDeviceController.execute(
            eq(listOf("shell", "ls \"${deviceDir}\"")),
            nullable(Long::class.java)
        )).thenReturn(CommandResult(0, listOf("output1.txt")))

        `when`(mockDeviceController.pull(eq(createTestArtifact(
            "${deviceDir}/output1.txt", "${hostDir}/output1.txt"))))
            .thenThrow(exception)

        runPlugin {
            additionalOutputDirectoryOnHost = hostDir
            additionalOutputDirectoryOnDevice = deviceDir
        }

        inOrder(mockDeviceController, mockLogger).apply {
            verify(mockDeviceController).execute(listOf("shell", "rm -rf \"${deviceDir}\""))
            verify(mockDeviceController).execute(listOf("shell", "mkdir -p \"${deviceDir}\""))
            verify(mockDeviceController).execute(listOf("shell", "ls \"${deviceDir}\""))
            verify(mockDeviceController).pull(createTestArtifact(
                "${deviceDir}/output1.txt", "${hostDir}/output1.txt"))
            verify(mockLogger).log(eq(Level.WARNING), eq(exception), any())
            verifyNoMoreInteractions()
        }
    }
}
