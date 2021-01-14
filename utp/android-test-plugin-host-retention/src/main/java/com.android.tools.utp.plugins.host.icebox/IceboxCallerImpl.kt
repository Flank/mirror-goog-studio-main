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
import com.android.emulator.control.SnapshotServiceGrpc.SnapshotServiceBlockingStub
import com.android.tools.utp.plugins.host.icebox.proto.IceboxPluginProto.Compression
import com.google.common.annotations.VisibleForTesting
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.lib.logging.jvm.getLogger
import com.google.testing.platform.runtime.android.controller.ext.deviceShell
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import java.io.File
import java.io.OutputStream
import java.net.ConnectException
import java.net.Socket
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/*
 * IceboxCallerImpl implements the IceboxCaller interface.
 */
class IceboxCallerImpl public constructor(
        private val managedChannelBuilder: ManagedChannelBuilder<*>,
        private val coroutineScope: CoroutineScope
) : IceboxCaller {
    public class IceboxException(message: String) : Exception(message)
    private companion object {
        @JvmStatic
        val logger = getLogger()
    }

    private var iceboxCoroutine: Job? = null
    private lateinit var snapshotService: SnapshotServiceBlockingStub
    private lateinit var managedChannel: ManagedChannel

    init {
        setupGrpc()
    }

    @VisibleForTesting
    suspend fun queryPid(
            deviceController: DeviceController,
            testedApplicationID: String
    ): Long {
        // Query PID
        // It retries multiple times if the query fails. The number of retries and
        // intervals are determined heuristically.
        var retries = 0
        var retryTime = Duration.ofSeconds(1)
        val maxRetries = 5
        repeat(maxRetries) {
            val result = deviceController.deviceShell(listOf("pidof", testedApplicationID))
            // parse pid from output
            val pid = result.output.firstOrNull()?.trim()?.toLong() ?: 0
            if (pid > 0) return pid
            retries++
            logger.fine("IceboxCaller retrying $retries")
            delay(retryTime.toMillis())
            // Exponential wait time
            retryTime = retryTime.multipliedBy(2)
        }
        return -1
    }

    private fun setupGrpc() {
        try {
            this.managedChannel = managedChannelBuilder.build()
            snapshotService = SnapshotServiceGrpc.newBlockingStub(managedChannel)
        } catch (e: Throwable) {
            logger.warning("icebox grpc failed: $e")
            shutdownGrpc()
        }
    }

    override fun runIcebox(
            deviceController: DeviceController,
            testedApplicationID: String,
            snapshotNamePrefix: String,
            maxSnapshotNumber: Int,
            androidStudioDdmlibPort: Int
    ) {
        iceboxCoroutine = coroutineScope.launch {
            try {
                runIceboxImpl(
                        deviceController, testedApplicationID, snapshotNamePrefix, maxSnapshotNumber,
                        androidStudioDdmlibPort
                )
            } catch (e: CancellationException) {
                // No-op
            } catch (e: Throwable) {
                logger.warning("icebox failed: $e")
            }
        }
    }

    suspend fun runIceboxImpl(
            deviceController: DeviceController,
            testedApplicationID: String,
            snapshotNamePrefix: String,
            maxSnapshotNumber: Int,
            androidStudioDdmlibPort: Int
    ) {
        // The test should be marked with --debug=true which will force it to wait for
        // debugger on start. It is OK (actually, preferred) to query the pid after
        // test started.
        val pid = queryPid(deviceController, testedApplicationID)
        if (pid < 0) {
            throw IceboxException("Failed to get pid for package $testedApplicationID:$pid")
        }
        logger.fine(
                "IceboxCaller get pid $testedApplicationID:$pid"
        )

        notifyAndroidStudio(deviceController.getDevice().serial, pid, androidStudioDdmlibPort, logger)

        snapshotService.trackProcess(
                IceboxTarget.newBuilder()
                        .setPid(pid)
                        .setSnapshotId(snapshotNamePrefix)
                        .setMaxSnapshotNumber(maxSnapshotNumber)
                        .build()
        ).let {
            if (it.failed) logger.warning("Icebox failed: $it.err")
        }
    }

    /**
     * Fetch a test failure snapshot.
     *
     * @param snapshotFile: the snapshot file to be exported into output artifacts
     * @param snapshotCompression: snapshot compression settings
     * @param emulatorSnapshotId: snapshot name known by the emulator
     */
    override fun fetchSnapshot(
            snapshotFile: File,
            snapshotCompression: Compression,
            emulatorSnapshotId: String
    ) = runBlocking {
        iceboxCoroutine?.join()
        iceboxCoroutine = null
        try {
            val snapshotFormat = when (snapshotCompression) {
                Compression.NONE -> SnapshotPackage.Format.TAR
                Compression.TARGZ -> SnapshotPackage.Format.TARGZ
                Compression.UNRECOGNIZED -> SnapshotPackage.Format.TAR
            }
            logger.info(
                    "Pulling snapshot ${snapshotFile.name} from device. This may take a while."
            )
            // Try to pull the snapshot stream
            // TODO(b/168748559): rewrite it with TestResultListener to unblock other plugins
            lateinit var responses: Iterator<SnapshotPackage>
            measureTimeMillis {
                responses = snapshotService.pullSnapshot(
                        SnapshotPackage.newBuilder()
                                .setSnapshotId(emulatorSnapshotId)
                                .setFormat(snapshotFormat)
                                .setPath(snapshotFile.absolutePath)
                                .build()
                )
            }.also {
                logger.info(
                        "Pulling snapshot finished, total time ${it / 1000} seconds."
                )
            }

            var success = true

            // Initialize output stream lazily.
            // The latest emulator directly writes the snapshot to the file without posting it into the
            // gRPC payload. But we still need to read payload if it is an older emulator.

            var outputStream: OutputStream? = null
            for (response in responses) {
                if (!response.success) {
                    success = false
                    break
                }
                if (response.payload.size() > 0) {
                    if (outputStream == null) {
                        outputStream = snapshotFile.outputStream()
                    }
                    outputStream.write(response.payload.toByteArray())
                }
            }
            outputStream?.close()
            if (!success && snapshotFile.exists()) {
                snapshotFile.delete()
            }
            // Send icebox commands to the emulator, through gRPC
            // It is OK to fail deleting
            snapshotService.deleteSnapshot(
                    SnapshotPackage.newBuilder()
                            .setSnapshotId(emulatorSnapshotId)
                            .build()
            ).also {
                if (!it.success) logger.warning("Icebox failed deleting snapshot!")
            }
            Unit
        } catch (e: Throwable) {
            logger.warning("icebox failed: $e")
        }
    }

    override fun shutdownGrpc() {
        try {
            managedChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
        } finally {
            managedChannel.shutdownNow()
        }
    }
}

/**
 * Tells Android Studio to disconnect from the target process.
 *
 * @param deviceSerial: the serial number of the running device. Example: "emulator-5554".
 * @param pid: the process ID to be disconnected.
 * @param port: the port number of Android Studio DDM module.
 */
@VisibleForTesting
suspend fun notifyAndroidStudio(deviceSerial: String, pid: Long, port: Int, logger: Logger?) {
    try {
        Socket("localhost", port).use {
            val message = "disconnect:$deviceSerial:$pid"
            it.outputStream.write(
                    (String.format("%04x", message.length) + message).toByteArray()
            )
        }
    } catch (exception: ConnectException) {
        logger?.info("Android Studio not found: $exception")
    }
}
