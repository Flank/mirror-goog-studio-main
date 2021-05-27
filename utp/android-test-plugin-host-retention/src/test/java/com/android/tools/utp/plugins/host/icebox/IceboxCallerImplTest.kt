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

import com.android.emulator.control.IceboxTarget
import com.android.emulator.control.SnapshotPackage
import com.android.emulator.control.SnapshotServiceGrpc
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.tools.utp.plugins.host.icebox.proto.IceboxPluginProto
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.ByteString
import com.google.testing.platform.api.device.CommandHandle
import com.google.testing.platform.api.device.CommandResult
import com.google.testing.platform.api.device.Device
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.runtime.android.controller.ext.deviceShell
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import io.grpc.testing.GrpcCleanupRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Answers
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.argThat
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.quality.Strictness
import java.io.DataInputStream
import java.net.ServerSocket
import org.mockito.Mockito

/**
 * Unit tests for [IceboxCallerImpl]
 */
@RunWith(JUnit4::class)
class IceboxCallerImplTest {
    @get:Rule
    var mockitoJunit = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)
    @get:Rule
    var tempFolder = TemporaryFolder()
    @get:Rule
    val grpcCleanup: GrpcCleanupRule = GrpcCleanupRule()
    @Mock
    private lateinit var mockDevice: Device
    @Mock
    private lateinit var mockDeviceController: DeviceController
    @Mock
    private lateinit var mockManagedChannelBuilder: ManagedChannelBuilder<*>
    @Mock(answer = Answers.CALLS_REAL_METHODS)
    private lateinit var mockSnapshotServiceGrpcServer: SnapshotServiceGrpc.SnapshotServiceImplBase
    @Mock
    private lateinit var mockCommandHandle: CommandHandle

    private val appId = "foo.bar.myapp"
    private val serial = "emulator-5554"
    private val pid = "101"
    private val tid = "102"
    private val startTime = "05-19 22:19:09"
    private val waitingForDebugger = "05-19 22:19:10.979  $pid  $tid I AndroidJUnitRunner: Waiting for debugger to connect..."
    private val snapshotPrefix = "snapshotPrefix"
    private lateinit var managedChannel: ManagedChannel
    private lateinit var testScope: CoroutineScope
    private lateinit var driverScope: CoroutineScope
    private lateinit var iceboxCaller: IceboxCallerImpl

    @Before
    fun setup() {
        testScope = CoroutineScope(Dispatchers.Default)
        driverScope = testScope
        val serverName: String = InProcessServerBuilder.generateName()
        grpcCleanup.register(
            InProcessServerBuilder
                .forName(serverName)
                .directExecutor()
                .addService(mockSnapshotServiceGrpcServer)
                .build()
                .start()
        )
        managedChannel = grpcCleanup.register(
            InProcessChannelBuilder.forName(serverName).directExecutor().build()
        )
        `when`(
            mockManagedChannelBuilder.build()
        ).thenReturn(managedChannel)
        iceboxCaller = IceboxCallerImpl(mockManagedChannelBuilder, "", driverScope)
    }

    @After
    fun teardown() = runBlocking {
        testScope.cancel()
    }

    private fun prepareIceboxCalls() {
        `when`(
            mockDeviceController.deviceShell(listOf("date", "+%m-%d\\ %H:%M:%S"))
        ).thenReturn(CommandResult(0, listOf(startTime)))
        doAnswer {
            val respObserver: StreamObserver<IceboxTarget> = it.getArgument(1)
            respObserver.onNext(IceboxTarget.getDefaultInstance())
            respObserver.onCompleted()
        }.`when`(mockSnapshotServiceGrpcServer).trackProcess(any(), any())
        `when`(
            mockDeviceController.executeAsync(
                eq(
                    listOf(
                        "shell", "logcat", "-v", "threadtime", "-b", "main", "-T",
                        "\'$startTime.000\'"
                    )
                ), any()
            )
        ).then {
            it.getArgument<(String) -> Unit>(1).invoke(waitingForDebugger)
            mockCommandHandle
        }
        `when`(
            mockDeviceController.deviceShell(listOf("pidof", appId))
        ).thenReturn(CommandResult(0, listOf(pid)))
        `when`(mockDeviceController.getDevice()).thenReturn(mockDevice)
        `when`(mockDevice.serial).thenReturn(serial)
    }

    @Test
    fun iceboxTestPass() {
        prepareIceboxCalls()
        doAnswer {
            val respObserver: StreamObserver<SnapshotPackage> = it.getArgument(1)
            respObserver.onNext(SnapshotPackage.newBuilder().apply {
                payload = ByteString.EMPTY
            }.build())
            respObserver.onCompleted()
        }.`when`(mockSnapshotServiceGrpcServer).pullSnapshot(any(), any())

        runBlocking {
            iceboxCaller.runIcebox(mockDeviceController, appId, snapshotPrefix, -1, 0)
            val snapshotFile = tempFolder.newFile("snapshotFile")
            iceboxCaller.fetchSnapshot(
                    snapshotFile, IceboxPluginProto.Compression.NONE,
                    "${snapshotPrefix}0"
            )
            iceboxCaller.shutdownGrpc()
            verify(mockSnapshotServiceGrpcServer).trackProcess(
                    argThat { it.pid == pid.toLong() && it.maxSnapshotNumber == -1 },
                    any())
            assertThat(snapshotFile.exists()).isFalse()
        }
    }

    @Test
    fun iceboxTestFail() {
        prepareIceboxCalls()
        runBlocking {
            iceboxCaller.runIcebox(mockDeviceController, appId, snapshotPrefix, -1, 0)
            val snapshotFile = tempFolder.newFile("snapshotFile")
            iceboxCaller.fetchSnapshot(
                    snapshotFile, IceboxPluginProto.Compression.NONE,
                    "${snapshotPrefix}0"
            )
            iceboxCaller.shutdownGrpc()
            verify(mockSnapshotServiceGrpcServer).trackProcess(
                    argThat { it.pid == pid.toLong() && it.maxSnapshotNumber == -1 },
                    any())
            verify(mockSnapshotServiceGrpcServer).pullSnapshot(
                    argThat { it.format == SnapshotPackage.Format.TAR },
                    any())
            assertThat(snapshotFile.exists()).isTrue()
        }
    }

    private fun findUnusedPort(): Int {
        ServerSocket(0).use {
            it.reuseAddress = true
            return it.localPort
        }
    }

    @Test
    fun noExceptionWithoutAndroidStudio() {
        val pid = 123L
        val port = findUnusedPort()
        // Must not throw
        runBlocking {
            notifyAndroidStudio(serial, pid, port, null)
        }
    }

    @Test
    fun notifyAndroidStudioTest() {
        ServerSocket(0, 1).use { serverSocket ->
            val pid = 456L
            val port = serverSocket.localPort
            var serverOk = false
            val serverJob = testScope.launch {
                serverSocket.accept().use {
                    val inputStream = DataInputStream(it.inputStream)
                    val expectedMessage = "disconnect:$serial:$pid"
                    val expectedString = String.format("%04x", expectedMessage.length) + expectedMessage
                    val readBuffer = ByteArray(expectedString.length)
                    inputStream.readFully(readBuffer)
                    assertThat(String(readBuffer)).isEqualTo(expectedString)
                    serverOk = true
                }
            }
            runBlocking {
                yield()
                notifyAndroidStudio(serial, pid, port, null)
                serverJob.join()
                assertThat(serverOk).isTrue()
            }
        }
    }
}
