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

import com.android.ddmlib.IDevice
import com.android.ddmlib.MultiLineReceiver
import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.google.common.truth.Truth.assertThat
import com.google.testing.platform.api.device.CommandHandle
import com.google.testing.platform.proto.api.core.PathProto
import com.google.testing.platform.proto.api.core.TestArtifactProto
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations.initMocks
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [DdmlibAndroidDeviceController].
 */
class DdmlibAndroidDeviceControllerTest {

    companion object {
        private const val EXPECTED_DESTINATION_PATH = "expected/destination"
        private const val EXPECTED_SOURCE_PATH = "expected/source"
        private const val EXIT_CODE_REPORT_TAG = "utp_shell_exit_code"
        private const val EXIT_CODE_REPORT = "; echo ${EXIT_CODE_REPORT_TAG}=$?"
    }

    @Mock
    private lateinit var mockDevice: IDevice

    private lateinit var artifactNonAndroidApk: TestArtifactProto.Artifact
    private lateinit var artifactNoSourcePath: TestArtifactProto.Artifact
    private lateinit var artifactNoDestinationPath: TestArtifactProto.Artifact
    private lateinit var properArtifact: TestArtifactProto.Artifact

    private lateinit var controller: DdmlibAndroidDeviceController

    @Before
    fun setUp() {
        initMocks(this)

        `when`(mockDevice.serialNumber).thenReturn("serial-1234")
        `when`(mockDevice.version).thenReturn(AndroidVersion(22))

        controller = DdmlibAndroidDeviceController()
        controller.setDevice(DdmlibAndroidDevice(mockDevice))
    }

    @Before
    fun initializeArtifacts() {
        val destinationPathProto = PathProto.Path.newBuilder().apply {
            path = EXPECTED_DESTINATION_PATH
        }.build()

        val sourcePathProto = PathProto.Path.newBuilder().apply {
            path = EXPECTED_SOURCE_PATH
        }.build()

        artifactNonAndroidApk = TestArtifactProto.Artifact.newBuilder().apply {
            sourcePath = sourcePathProto
            destinationPath = destinationPathProto
            type = TestArtifactProto.ArtifactType.ARTIFACT_TYPE_UNSPECIFIED
        }.build()

        artifactNoSourcePath = TestArtifactProto.Artifact.newBuilder().apply {
            destinationPath = destinationPathProto
            type = TestArtifactProto.ArtifactType.ANDROID_APK
        }.build()

        artifactNoDestinationPath = TestArtifactProto.Artifact.newBuilder().apply {
            sourcePath = sourcePathProto
            type = TestArtifactProto.ArtifactType.ANDROID_APK
        }.build()

        properArtifact = TestArtifactProto.Artifact.newBuilder().apply {
            sourcePath = sourcePathProto
            destinationPath = destinationPathProto
            type = TestArtifactProto.ArtifactType.ANDROID_APK
        }.build()
    }

    @Test
    fun installRequiresAndroidApk() {
        assertThrows(
                "#install should not allow artifacts that are not Android APKs.",
                IllegalArgumentException::class.java
        ) {
            controller.install(artifactNonAndroidApk)
        }
    }

    @Test
    fun installRequiresArtifactWithSourcePath() {
        assertThrows(
                "#install artifacts should be required to have a source path.",
                IllegalArgumentException::class.java
        ) {
            controller.install(artifactNoSourcePath)
        }
    }

    @Test
    fun installPassesCorrectArgsToCommand() {
        controller.install(properArtifact)
        verify(mockDevice).installPackage(EXPECTED_SOURCE_PATH, true, "-t")
    }

    @Test
    fun installGrantsPermissionOnApiGreater23() {
        `when`(mockDevice.version).thenReturn(AndroidVersion(23))

        controller.install(properArtifact)
        verify(mockDevice).installPackage(EXPECTED_SOURCE_PATH, true, "-t", "-g")
    }

    @Test
    fun executeShellCommand() {
        val ret = controller.execute(listOf("shell", "am", "instrument"))
        assertThat(ret.statusCode).isEqualTo(0)
        verify(mockDevice).executeShellCommand(
            eq("am instrument ${EXIT_CODE_REPORT}"), any(), eq(0L), eq(0L), any())
    }

    @Test
    fun executeShellCommandAndCommandFailedRemotely() {
        `when`(mockDevice.executeShellCommand(
            eq("am instrument ${EXIT_CODE_REPORT}"), any(), eq(0L), eq(0L), any())
        ).then {
            val outputMessage = "${EXIT_CODE_REPORT_TAG}=-2\n".toByteArray()
            it.getArgument<MultiLineReceiver>(1).addOutput(outputMessage, 0, outputMessage.size)
        }

        val ret = controller.execute(listOf("shell", "am", "instrument"))

        assertThat(ret.statusCode).isEqualTo(-2)
        verify(mockDevice).executeShellCommand(
            eq("am instrument ${EXIT_CODE_REPORT}"), any(), eq(0L), eq(0L), any())
    }

    @Test
    fun executeInstallCommand() {
        val ret = controller.execute(listOf("install", "apk.apk"))
        assertThat(ret.statusCode).isEqualTo(0)
        verify(mockDevice).installPackage(eq("apk.apk"), eq(true), any(), eq(0L), eq(0L), any())
    }

    @Test(expected = UnsupportedOperationException::class)
    fun executeUnsupportedCommand() {
        controller.execute(listOf("unknownCommand"))
    }

    @Test
    fun executeAsyncCancelled() {
        val handlerInitialized = CountDownLatch(1)
        lateinit var handler: CommandHandle
        `when`(mockDevice.executeShellCommand(
            eq("am instrument ${EXIT_CODE_REPORT}"), any(), eq(0L), eq(0L), any())
        ).then {
            handlerInitialized.await(1, TimeUnit.MINUTES)
            handler.stop()
        }
        handler = controller.executeAsync(listOf("shell", "am", "instrument")) {}
        handlerInitialized.countDown()
        handler.waitFor()
        assertThat(handler.exitCode()).isEqualTo(-1)
        verify(mockDevice).executeShellCommand(
            eq("am instrument ${EXIT_CODE_REPORT}"), any(), eq(0L), eq(0L), any())
    }

    @Test
    fun executeAsyncOutputShouldBeProcessed() {
        `when`(mockDevice.executeShellCommand(
                eq("am instrument ${EXIT_CODE_REPORT}"), any(), eq(0L), eq(0L), any())).then {
            val outputMessage = "This is test output message.\n".toByteArray()
            it.getArgument<MultiLineReceiver>(1).addOutput(outputMessage, 0, outputMessage.size)
        }
        lateinit var processedOutputMessage: String
        val handler = controller.executeAsync(listOf("shell", "am", "instrument")) {
            processedOutputMessage = it
        }
        handler.waitFor()
        assertThat(handler.exitCode()).isEqualTo(0)
        assertThat(processedOutputMessage).isEqualTo("This is test output message.")
        verify(mockDevice).executeShellCommand(
            eq("am instrument ${EXIT_CODE_REPORT}"), any(), eq(0L), eq(0L), any())
    }

    @Test
    fun pushRequiresSourcePath() {
        assertThrows(
                "#push should require artifacts to have source paths.",
                IllegalArgumentException::class.java
        ) {
            controller.push(artifactNoSourcePath)
        }
    }

    @Test
    fun pushRequiresDestinationPath() {
        assertThrows(
                "#push should require artifacts to have destination paths",
                IllegalArgumentException::class.java
        ) {
            controller.push(artifactNoDestinationPath)
        }
    }

    @Test
    fun pushPassesCorrectArgsToCommand() {
        controller.push(properArtifact)
        verify(mockDevice).pushFile(
                EXPECTED_SOURCE_PATH,
                EXPECTED_DESTINATION_PATH
        )
    }

    @Test
    fun pullRequiresSourcePath() {
        assertThrows(
                "#pull should require artifacts to have source paths.",
                IllegalArgumentException::class.java
        ) {
            controller.pull(artifactNoSourcePath)
        }
    }

    @Test
    fun pullRequiresDestinationPath() {
        assertThrows(
                "#pull should require artifacts to have destination paths",
                IllegalArgumentException::class.java
        ) {
            controller.pull(artifactNoDestinationPath)
        }
    }

    @Test
    fun pullPassesCorrectArgsToCommand() {
        controller.pull(properArtifact)
        verify(mockDevice).pullFile(
                EXPECTED_DESTINATION_PATH,
                EXPECTED_SOURCE_PATH
        )
    }
}
