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
import com.android.adblib.DeviceSelector
import com.android.adblib.thisLogger
import com.android.adblib.tools.debugging.AtomicStateFlow
import com.android.adblib.tools.debugging.JdwpProcessProperties
import com.android.adblib.tools.debugging.JdwpSessionHandler
import com.android.adblib.tools.debugging.JdwpSessionProxyStatus
import com.android.adblib.tools.debugging.rethrowCancellation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.EOFException

/**
 * Implementation of a JDWP proxy for the process [pid] on [device]. The proxy creates a
 * [server socket][AdbChannelFactory.createServerSocket] on `localhost` (see
 * [JdwpSessionProxyStatus.socketAddress]), then it accepts JDWP connections from external
 * Java debuggers (e.g. IntelliJ or Android Studio) on that server socket. Each time a new
 * socket connection is opened by an external debugger, the proxy opens a JDWP session to the
 * process on the device (see [JdwpSessionHandler.create]) and forwards (both ways) JDWP protocol
 * packets between the external debugger and the process on the device.
 */
internal class JdwpSessionProxy(
    private val session: AdbSession,
    private val device: DeviceSelector,
    private val pid: Int
) {

    private val logger = thisLogger(session)

    fun proxyActivityFlow(processStateFlow: AtomicStateFlow<JdwpProcessProperties>) = flow {
        proxyLoop(this, processStateFlow)
    }.flowOn(session.host.ioDispatcher)

    private suspend fun proxyLoop(
        proxyActivityFlow: FlowCollector<ProxyActivity>,
        processStateFlow: AtomicStateFlow<JdwpProcessProperties>
    ) {
        // Proxy is initially inactive
        proxyActivityFlow.emit(ProxyActivity.Inactive)

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
                            proxyActivityFlow,
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
        proxyActivityFlow: FlowCollector<ProxyActivity>,
        processStateFlow: AtomicStateFlow<JdwpProcessProperties>
    ) {
        serverSocket.accept().use { debuggerSocket ->
            logger.debug { "pid=$pid: External debugger connection accepted: $debuggerSocket" }
            proxyActivityFlow.emit(ProxyActivity.BeforeActivation)
            processStateFlow.updateProxyStatus { it.copy(isExternalDebuggerAttached = true) }
            try {
                proxyJdwpSession(debuggerSocket)
            } finally {
                logger.debug { "pid=$pid: Debugger proxy has ended proxy connection" }
                processStateFlow.updateProxyStatus { it.copy(isExternalDebuggerAttached = false) }
            }
            proxyActivityFlow.emit(ProxyActivity.Inactive)
        }
    }

    private suspend fun proxyJdwpSession(debuggerSocket: AdbChannel) {
        logger.debug { "pid=$pid: Start proxying socket between external debugger and process on device" }
        JdwpSessionHandler.create(session, device, pid).use { deviceSession ->
            JdwpSessionHandler.create(session, debuggerSocket, pid).use { debuggerSession ->
                coroutineScope {
                    // Forward packets from external debugger to jdwp process on device
                    val job1 = launch(session.host.ioDispatcher) {
                        forwardJdwpSession(
                            debuggerSession,
                            deviceSession,
                            "external debugger -> adblib -> device"
                        )
                    }

                    // Forward packets from jdwp process on device to external debugger
                    val job2 = launch(session.host.ioDispatcher) {
                        forwardJdwpSession(
                            deviceSession,
                            debuggerSession,
                            "device -> adblib -> external debugger"
                        )
                    }

                    // Note about termination of this coroutine scope:
                    // * [automatic] The common case is to wait for job1 and job2 to complete
                    //   successfully
                    // * [automatic] If job1 or job2 throws non-cancellation exception, the
                    //   exception is propagated to the scope and the scope is cancelled
                    // * [manual] If job1 or job2 throws a CancellationException, we need to
                    //   propagate cancellation to the scope so that all jobs are cancelled
                    //   together.
                    propagateCancellationException(job1, job2)
                }
            }
        }
    }

    private suspend fun forwardJdwpSession(
        fromSession: JdwpSessionHandler,
        toSession: JdwpSessionHandler,
        logMessage: String
    ) {
        while (true) {
            try {
                val packet = try {
                    fromSession.receivePacket()
                } catch (e: EOFException) {
                    logger.debug { "pid=$pid: $logMessage: EOF" }
                    toSession.close()
                    break
                }
                logger.verbose { "pid=$pid: $logMessage: forwarding $packet" }
                toSession.sendPacket(packet)
            } catch (t: Throwable) {
                t.rethrowCancellation()
                logger.debug(t) { "pid=$pid: $logMessage: exception $t" }
                throw t
            }
        }
    }

    private fun AtomicStateFlow<JdwpProcessProperties>.updateProxyStatus(
        updater: (JdwpSessionProxyStatus) -> JdwpSessionProxyStatus
    ) {
        this.update {
            it.copy(jdwpSessionProxyStatus = updater(it.jdwpSessionProxyStatus))
        }
    }

    /**
     * Propagate [CancellationException] from any [Job] in [jobs] to this [CoroutineScope],
     * ensuring all children are cancelled.
     */
    private fun CoroutineScope.propagateCancellationException(vararg jobs: Job) {
        jobs.forEach {
            it.invokeOnCompletion { cause: Throwable? ->
                if (cause is CancellationException) {
                    this.cancel(cause)
                }
            }
        }
    }
}

internal enum class ProxyActivity {
    BeforeActivation,
    Inactive
}
