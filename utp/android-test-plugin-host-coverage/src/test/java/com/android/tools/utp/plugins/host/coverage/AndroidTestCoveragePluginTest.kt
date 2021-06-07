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

package com.android.tools.utp.plugins.host.coverage.com.android.tools.utp.plugins.host.coverage

import com.android.testutils.MockitoKt.argThat
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.tools.utp.plugins.host.coverage.AndroidTestCoveragePlugin
import com.android.tools.utp.plugins.host.coverage.proto.AndroidTestCoverageConfigProto.AndroidTestCoverageConfig
import com.google.protobuf.Any
import com.google.testing.platform.api.config.ProtoConfig
import com.google.testing.platform.api.device.CommandResult
import com.google.testing.platform.api.device.Device
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.proto.api.core.ExtensionProto
import com.google.testing.platform.proto.api.core.TestArtifactProto.Artifact
import com.google.testing.platform.proto.api.core.TestResultProto.TestResult
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import com.google.testing.platform.runtime.android.device.AndroidDeviceProperties
import java.util.function.Supplier
import java.util.logging.Logger
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

/**
 * Unit tests for [AndroidTestCoveragePlugin]
 */
@RunWith(JUnit4::class)
class AndroidTestCoveragePluginTest {

    companion object {
        private const val TESTED_APP = "com.example.application"
        private const val TEST_STORAGE_SERVICE_OUTPUT_DIR = "/sdcard/googletest/internal_use/"
    }

    @get:Rule var mockitoJUnitRule: MockitoRule =
        MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @Mock private lateinit var mockDeviceController: DeviceController
    @Mock private lateinit var mockLogger: Logger

    private var commandResult: CommandResult = CommandResult(0, listOf())

    @Before
    fun setUpMocks() {
        `when`(mockDeviceController.execute(anyList(), nullable(Long::class.java))).then {
            commandResult
        }
    }

    private fun installTestStorageService() {
        `when`(mockDeviceController.execute(
            eq(listOf("shell", "pm", "list", "packages", "androidx.test.services")),
            nullable(Long::class.java))
        ).thenReturn(CommandResult(0, listOf("package:androidx.test.services")))

        val mockDevice = mock<Device>()
        `when`(mockDevice.properties).thenReturn(AndroidDeviceProperties(deviceApiLevel = "30"))
        `when`(mockDeviceController.getDevice()).thenReturn(mockDevice)
    }

    private fun createAndroidTestCoveragePlugin(config: AndroidTestCoverageConfig)
        : AndroidTestCoveragePlugin {
        val packedConfig = Any.pack(config)
        val protoConfig = object: ProtoConfig {
            override val configProto: Any
                get() = packedConfig
            override val configResource: ExtensionProto.ConfigResource?
                get() = null
        }
        return AndroidTestCoveragePlugin(mockLogger) {
            "UUID"
        }.apply {
            configure(protoConfig)
        }
    }

    private fun runAndroidTestCoveragePlugin(
        configFunc: AndroidTestCoverageConfig.Builder.() -> Unit) {
        val config = AndroidTestCoverageConfig.newBuilder().apply {
            configFunc(this)
        }.build()
        createAndroidTestCoveragePlugin(config).apply {
            beforeAll(mockDeviceController)
            beforeEach(null, mockDeviceController)
            afterEach(TestResult.getDefaultInstance(), mockDeviceController)
            afterAll(TestSuiteResult.getDefaultInstance(), mockDeviceController)
        }
    }

    private fun createTestArtifact(
        filePathOnDevice: String, filePathOnHost: String): Artifact {
        return Artifact.newBuilder().apply {
            sourcePathBuilder.path = filePathOnHost
            destinationPathBuilder.path = filePathOnDevice
        }.build()
    }

    @Test
    fun runWithSingleCoverageFile() {
        val coverageFile = "/data/data/${TESTED_APP}/coverage.ec"
        val tmpDir = "/data/local/tmp/UUID-coverage_data"
        val outputDir = "coverageOutputDir/deviceName/"

        runAndroidTestCoveragePlugin() {
            runAsPackageName = TESTED_APP
            singleCoverageFile = coverageFile
            outputDirectoryOnHost = outputDir
        }

        inOrder(mockDeviceController).apply {
            verify(mockDeviceController).execute(listOf(
                "shell",
                "run-as", TESTED_APP, "rm -f \"${coverageFile}\""))
            verify(mockDeviceController).execute(listOf(
                "shell",
                "mkdir -p \"${tmpDir}\"",
            ))
            verify(mockDeviceController).execute(listOf(
                "shell",
                "run-as", TESTED_APP, "cat \"${coverageFile}\" > \"${tmpDir}/coverage.ec\""
            ))
            verify(mockDeviceController).pull(createTestArtifact(
                "${tmpDir}/coverage.ec",
                "${outputDir}/coverage.ec"))
            verify(mockDeviceController).execute(listOf(
                "shell",
                "rm -rf \"${tmpDir}\"",
            ))
            verifyNoMoreInteractions()
        }

        verifyNoMoreInteractions(mockLogger)
    }

    @Test
    fun runWithSingleCoverageFileAndTestStorageService() {
        val coverageFile = "/data/data/${TESTED_APP}/coverage.ec"
        val tmpDir = "/data/local/tmp/UUID-coverage_data"
        val outputDir = "coverageOutputDir/deviceName/"

        installTestStorageService()

        runAndroidTestCoveragePlugin() {
            runAsPackageName = TESTED_APP
            singleCoverageFile = coverageFile
            outputDirectoryOnHost = outputDir
        }

        inOrder(mockDeviceController).apply {
            verify(mockDeviceController).execute(listOf(
                "shell",
                "run-as", TESTED_APP, "rm -f \"${coverageFile}\""))
            verify(mockDeviceController).execute(listOf(
                "shell",
                "appops set androidx.test.services MANAGE_EXTERNAL_STORAGE allow"
            ))
            verify(mockDeviceController).execute(listOf(
                "shell",
                "mkdir -p \"${tmpDir}\"",
            ))
            verify(mockDeviceController).execute(listOf(
                "shell",
                "cat \"${TEST_STORAGE_SERVICE_OUTPUT_DIR}/${coverageFile}\" > \"${tmpDir}/coverage.ec\""
            ))
            verify(mockDeviceController).pull(createTestArtifact(
                "${tmpDir}/coverage.ec",
                "${outputDir}/coverage.ec"))
            verify(mockDeviceController).execute(listOf(
                "shell",
                "rm -rf \"${tmpDir}\"",
            ))
            verifyNoMoreInteractions()
        }

        verifyNoMoreInteractions(mockLogger)
    }

    @Test
    fun runWithMultipleCoverageFilesInDirectory() {
        val coverageDir = "/data/data/${TESTED_APP}/"
        val tmpDir = "/data/local/tmp/UUID-coverage_data"
        val outputDir = "coverageOutputDir/deviceName/"

        `when`(mockDeviceController.execute(
            eq(listOf(
                "shell",
                "run-as", TESTED_APP, "ls \"${coverageDir}\"")),
            nullable(Long::class.java)
        )).thenReturn(CommandResult(0, listOf("coverage1.ec", "coverage2.ec", "non_cov_file")))

        runAndroidTestCoveragePlugin() {
            runAsPackageName = TESTED_APP
            multipleCoverageFilesInDirectory = coverageDir
            outputDirectoryOnHost = outputDir
        }

        inOrder(mockDeviceController).apply {
            verify(mockDeviceController).execute(listOf(
                "shell",
                "run-as", TESTED_APP, "rm -rf \"${coverageDir}\""))
            verify(mockDeviceController).execute(listOf(
                "shell",
                "run-as", TESTED_APP, "mkdir -p \"${coverageDir}\""))
            verify(mockDeviceController).execute(listOf(
                "shell",
                "mkdir -p \"${tmpDir}\"",
            ))
            verify(mockDeviceController).execute(listOf(
                "shell",
                "run-as", TESTED_APP, "ls \"${coverageDir}\""))
            for (i in 1..2) {
                verify(mockDeviceController).execute(
                    listOf(
                        "shell",
                        "run-as", TESTED_APP,
                        "cat \"${coverageDir}/coverage$i.ec\" > \"${tmpDir}/coverage$i.ec\""
                    )
                )
                verify(mockDeviceController).pull(
                    createTestArtifact(
                        "${tmpDir}/coverage$i.ec",
                        "${outputDir}/coverage$i.ec"
                    )
                )
            }
            verify(mockDeviceController).execute(listOf(
                "shell",
                "rm -rf \"${tmpDir}\"",
            ))
            verifyNoMoreInteractions()
        }

        verifyNoMoreInteractions(mockLogger)
    }

    @Test
    fun runWithMultipleCoverageFilesInDirectoryAndStorageService() {
        val coverageDir = "/data/data/${TESTED_APP}/"
        val tmpDir = "/data/local/tmp/UUID-coverage_data"
        val outputDir = "coverageOutputDir/deviceName/"

        installTestStorageService()

        `when`(mockDeviceController.execute(
            eq(listOf(
                "shell",
                "ls \"${TEST_STORAGE_SERVICE_OUTPUT_DIR}/${coverageDir}\"")),
            nullable(Long::class.java)
        )).thenReturn(CommandResult(0, listOf("coverage1.ec", "coverage2.ec", "non_cov_file")))

        runAndroidTestCoveragePlugin() {
            runAsPackageName = TESTED_APP
            multipleCoverageFilesInDirectory = coverageDir
            outputDirectoryOnHost = outputDir
        }

        inOrder(mockDeviceController).apply {
            verify(mockDeviceController).execute(listOf(
                "shell",
                "run-as", TESTED_APP, "rm -rf \"${coverageDir}\""))
            verify(mockDeviceController).execute(listOf(
                "shell",
                "run-as", TESTED_APP, "mkdir -p \"${coverageDir}\""))
            verify(mockDeviceController).execute(listOf(
                "shell",
                "appops set androidx.test.services MANAGE_EXTERNAL_STORAGE allow"
            ))
            verify(mockDeviceController).execute(listOf(
                "shell",
                "mkdir -p \"${tmpDir}\"",
            ))
            verify(mockDeviceController).execute(listOf(
                "shell",
                "ls \"${TEST_STORAGE_SERVICE_OUTPUT_DIR}/${coverageDir}\""))
            for (i in 1..2) {
                verify(mockDeviceController).execute(
                    listOf(
                        "shell",
                        "cat \"${TEST_STORAGE_SERVICE_OUTPUT_DIR}/${coverageDir}/coverage$i.ec\"" +
                                " > \"${tmpDir}/coverage$i.ec\""
                    )
                )
                verify(mockDeviceController).pull(
                    createTestArtifact(
                        "${tmpDir}/coverage$i.ec",
                        "${outputDir}/coverage$i.ec"
                    )
                )
            }
            verify(mockDeviceController).execute(listOf(
                "shell",
                "rm -rf \"${tmpDir}\"",
            ))
            verifyNoMoreInteractions()
        }

        verifyNoMoreInteractions(mockLogger)
    }

    @Test
    fun commandFailed() {
        val coverageFile = "/data/data/${TESTED_APP}/coverage.ec"
        val outputDir = "coverageOutputDir/deviceName/"

        commandResult = CommandResult(-1, listOf())

        runAndroidTestCoveragePlugin() {
            runAsPackageName = TESTED_APP
            singleCoverageFile = coverageFile
            outputDirectoryOnHost = outputDir
        }

        verify(mockLogger, atLeastOnce()).warning(argThat<Supplier<String>> {
            it.get().contains("Shell command failed (-1)")
        })
    }
}
