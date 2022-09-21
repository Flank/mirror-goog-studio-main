/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.adblib.tools.debugging.impl

import com.android.adblib.AdbChannel
import com.android.adblib.AdbChannelFactory
import com.android.adblib.AdbServerSocket
import com.android.adblib.AdbSession
import com.android.adblib.thisLogger
import com.android.adblib.tools.debugging.AtomicStateFlow
import com.android.adblib.tools.debugging.JdwpProcessProperties
import com.android.adblib.tools.debugging.JdwpSession
import com.android.adblib.tools.debugging.JdwpSessionProxyStatus
import com.android.adblib.tools.debugging.SharedJdwpSession
import com.android.adblib.tools.debugging.rethrowCancellation
import com.android.adblib.tools.debugging.utils.ReferenceCountedResource
import com.android.adblib.tools.debugging.utils.retained
import com.android.adblib.utils.launchCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.io.EOFException

/**
 * Implementation of a JDWP proxy for the process [pid] on a given device. The proxy creates a
 * [server socket][AdbChannelFactory.createServerSocket] on `localhost` (see
 * [JdwpSessionProxyStatus.socketAddress]), then it accepts JDWP connections from external
 * Java debuggers (e.g. IntelliJ or Android Studio) on that server socket. Each time a new
 * socket connection is opened by an external debugger, the proxy opens a JDWP session to the
 * process on the device (see [JdwpSession.openJdwpSession]) and forwards (both ways) JDWP protocol
 * packets between the external debugger and the process on the device.
 */
internal class JdwpSessionProxy(
    private val session: AdbSession,
    private val pid: Int,
    private val jdwpSessionRef: ReferenceCountedResource<SharedJdwpSession>,
) {

    private val logger = thisLogger(session)

    suspend fun execute(processStateFlow: AtomicStateFlow<JdwpProcessProperties>) {
        try {
            // Create server socket and start accepting JDWP connections
            session.channelFactory.createServerSocket().use { serverSocket ->
                val socketAddress = serverSocket.bind()
                processStateFlow.updateProxyStatus { it.copy(socketAddress = socketAddress) }
                try {
                    // Retry proxy as long as process is active
                    while (true) {
                        logger.debug { "pid=$pid: Waiting for debugger connection on port ${socketAddress.port}" }
                        acceptOneJdwpConnection(
                            serverSocket,
                            processStateFlow
                        )
                    }
                } finally {
                    processStateFlow.updateProxyStatus { it.copy(socketAddress = null) }
                }
            }
        } catch (e: Throwable) {
            e.rethrowCancellation()
            logger.warn(
                e,
                "JDWP proxy server error, closing debugger proxy server socket for pid=$pid"
            )
        }
    }

    private suspend fun acceptOneJdwpConnection(
        serverSocket: AdbServerSocket,
        processStateFlow: AtomicStateFlow<JdwpProcessProperties>
    ) {
        serverSocket.accept().use { debuggerSocket ->
            logger.debug { "pid=$pid: External debugger connection accepted: $debuggerSocket" }
            processStateFlow.updateProxyStatus { it.copy(isExternalDebuggerAttached = true) }
            try {
                proxyJdwpSession(debuggerSocket)
            } catch(t: Throwable) {
                t.rethrowCancellation()
                logger.info(t) { "pid=$pid: Debugger proxy had an error: $t" }
            } finally {
                logger.debug { "pid=$pid: Debugger proxy has ended proxy connection" }
                processStateFlow.updateProxyStatus { it.copy(isExternalDebuggerAttached = false) }
            }
        }
    }

    private suspend fun proxyJdwpSession(debuggerSocket: AdbChannel) {
        logger.debug { "pid=$pid: Start proxying socket between external debugger and process on device" }
        jdwpSessionRef.retained().use { deviceSessionRef ->
            JdwpSession.wrapSocketChannel(session, debuggerSocket, pid).use { debuggerSession ->
                coroutineScope {
                    // Note about termination of this coroutine scope:
                    // * [automatic] The common case is to wait for job1 and job2 to complete
                    //   successfully
                    // * [automatic] If job1 or job2 throws non-cancellation exception, the
                    //   exception is propagated to the scope and the scope is cancelled
                    // * [manual] If job1 or job2 throws a CancellationException, we need to
                    //   propagate cancellation to the scope so that all jobs are cancelled
                    //   together.


                    // We need to ensure forwarding from the device starts
                    val startStateFlow = MutableStateFlow(false)

                    // Forward packets from external debugger to jdwp process on device
                    launchCancellable(session.host.ioDispatcher) {
                        forwardDebuggerJdwpSession(
                            debuggerSession,
                            deviceSessionRef.value,
                            startStateFlow.asStateFlow()
                        )
                    }

                    // Forward packets from jdwp process on device to external debugger
                    launchCancellable(session.host.ioDispatcher) {
                        forwardDeviceJdwpSession(
                            deviceSessionRef.value,
                            debuggerSession,
                            startStateFlow
                        )
                    }
                }
            }
        }
    }

    private suspend fun forwardDebuggerJdwpSession(
        debuggerSession: JdwpSession,
        deviceSession: SharedJdwpSession,
        startStateFlow: StateFlow<Boolean>
    ) {
        debuggerSession.toFlow().collect { packet ->
            // Wait until receiver has started to avoid skipping packets
            startStateFlow.first { receiverHasStarted -> receiverHasStarted }

            logger.verbose { "pid=$pid: debugger->device proxy: Forwarding packet to shared jdwp session" }
            deviceSession.sendPacket(packet)
        }
    }

    private suspend fun forwardDeviceJdwpSession(
        deviceSession: SharedJdwpSession,
        debuggerSession: JdwpSession,
        startStateFlow: MutableStateFlow<Boolean>
    ) {
        deviceSession.newPacketReceiver()
            .withName("device session forwarder")
            .onActivation {
                logger.verbose { "pid=$pid: device->debugger proxy: Device session is ready to receive packets" }
                startStateFlow.value = true
            }.collect { packet ->
                logger.verbose { "pid=$pid: device->debugger proxy: Forwarding packet to session" }
                debuggerSession.sendPacket(packet)
            }
    }

    private fun AtomicStateFlow<JdwpProcessProperties>.updateProxyStatus(
        updater: (JdwpSessionProxyStatus) -> JdwpSessionProxyStatus
    ) {
        this.update {
            it.copy(jdwpSessionProxyStatus = updater(it.jdwpSessionProxyStatus))
        }
    }

    private fun JdwpSession.toFlow() = flow {
        while (true) {
            logger.verbose { "pid=$pid: Waiting for next JDWP packet from session" }
            val packet = try {
                this@toFlow.receivePacket()
            } catch (e: EOFException) {
                // Reached EOF, flow terminates
                logger.debug { "pid=$pid: JDWP session has ended with EOF" }
                break
            }
            logger.verbose { "pid=$pid: Emitting session packet to upstream flow: $packet" }
            emit(packet)
        }
    }
}
