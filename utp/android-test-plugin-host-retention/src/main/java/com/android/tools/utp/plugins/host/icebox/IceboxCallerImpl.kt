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
import io.grpc.CallCredentials
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.Status
import java.io.File
import java.io.OutputStream
import java.net.ConnectException
import java.net.Socket
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import java.net.NoRouteToHostException

/*
 * IceboxCallerImpl implements the IceboxCaller interface.
 */
class IceboxCallerImpl public constructor(
        private val managedChannelBuilder: ManagedChannelBuilder<*>,
        private val grpcToken: String,
        private val coroutineScope: CoroutineScope
) : IceboxCaller {
    public class IceboxException(message: String) : Exception(message)
    private companion object {
        @JvmStatic
        val logger = getLogger()
    }

    private lateinit var snapshotService: SnapshotServiceBlockingStub
    private lateinit var managedChannel: ManagedChannel
    private val logcatParser = LogcatParser()

    init {
        setupGrpc()
    }

    private class TokenCallCredentials(private val token: String) : CallCredentials() {
        override fun applyRequestMetadata(requestInfo: RequestInfo, executor: Executor, applier: MetadataApplier) {
            executor.execute {
                try {
                    val headers = Metadata()
                    headers.put(AUTHORIZATION_METADATA_KEY, "Bearer $token")
                    applier.apply(headers)
                }
                catch (e: Throwable) {
                    logger.severe(e.toString())
                    applier.fail(Status.UNAUTHENTICATED.withCause(e))
                }
            }
        }

        override fun thisUsesUnstableApi() {
        }
    }

    private fun setupGrpc() {
        try {
            this.managedChannel = managedChannelBuilder.build()
            if (grpcToken == "") {
                snapshotService = SnapshotServiceGrpc.newBlockingStub(managedChannel)
            } else {
                val credentials = TokenCallCredentials(grpcToken)
                snapshotService = SnapshotServiceGrpc.newBlockingStub(managedChannel)
                    .withCallCredentials(credentials)
            }
        } catch (e: Throwable) {
            logger.severe("icebox grpc failed: $e. Please try to update the emulator to the latest"
                + " version, or append the flag \"-grpc 8554\" when booting the emulator.")
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
        logcatParser.start(deviceController) { date, time, pid, uid, verbose, tag, message ->
            if (message.contains("Waiting for debugger to connect")) {
                val result = deviceController.deviceShell(listOf("pidof", testedApplicationID))
                val target_pid = result.output.firstOrNull()?.trim()
                if (target_pid == pid) {
                    val pid_long = pid.toLong()
                    notifyAndroidStudio(
                        deviceController.getDevice().serial,
                        pid_long,
                        androidStudioDdmlibPort,
                        logger
                    )

                    snapshotService.trackProcess(
                        IceboxTarget.newBuilder()
                            .setPid(pid_long)
                            .setSnapshotId(snapshotNamePrefix)
                            .setMaxSnapshotNumber(maxSnapshotNumber)
                            .build()
                    ).let {
                        if (it.failed) logger.warning("Icebox failed: $it.err")
                    }
                }
            }
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
        logcatParser.stop()
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
            logcatParser.stop()
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
fun notifyAndroidStudio(deviceSerial: String, pid: Long, port: Int, logger: Logger?) {
    try {
        Socket("localhost", port).use {
            val message = "disconnect:$deviceSerial:$pid"
            it.outputStream.write(
                    (String.format("%04x", message.length) + message).toByteArray()
            )
        }
    } catch (exception: ConnectException) {
        logger?.info("Android Studio not found: $exception")
    } catch (exception: NoRouteToHostException) {
        logger?.info("Android Studio not found: $exception")
    }
}

private val AUTHORIZATION_METADATA_KEY =
    Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
