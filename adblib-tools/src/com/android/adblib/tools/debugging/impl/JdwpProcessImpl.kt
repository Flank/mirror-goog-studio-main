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

import com.android.adblib.AdbSession
import com.android.adblib.DeviceSelector
import com.android.adblib.thisLogger
import com.android.adblib.tools.debugging.AtomicStateFlow
import com.android.adblib.tools.debugging.JdwpProcess
import com.android.adblib.tools.debugging.JdwpProcessProperties
import com.android.adblib.tools.debugging.JdwpProcessPropertiesCollector
import com.android.adblib.tools.debugging.JdwpSession
import com.android.adblib.tools.debugging.SharedJdwpSession
import com.android.adblib.tools.debugging.rethrowCancellation
import com.android.adblib.tools.debugging.utils.ReferenceCountedResource
import com.android.adblib.utils.closeOnException
import com.android.adblib.utils.createChildScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.EOFException
import java.time.Duration

/**
 * Maximum amount of time to keep a JDWP connection open while waiting for "handshake"/"process info" packets.
 *
 * This cannot be too long since a process will only eventually only show up when all intro packets have been
 * received and analyzed.
 */
private val PROCESS_PROPERTIES_READ_TIMEOUT = Duration.ofSeconds(2)

/**
 * Amount of time to wait before retrying a JDWP session to retrieve process properties
 */
private val PROCESS_PROPERTIES_RETRY_DURATION = Duration.ofSeconds(2)

/**
 * Implementation of [JdwpProcess]
 */
internal class JdwpProcessImpl(
  session: AdbSession,
  override val device: DeviceSelector,
  deviceScope: CoroutineScope,
  override val pid: Int
) : JdwpProcess, AutoCloseable {

    private val logger = thisLogger(session)

    private val stateFlow = AtomicStateFlow(MutableStateFlow(JdwpProcessProperties(pid)))

    override val scope = deviceScope.createChildScope()

    override val propertiesFlow = stateFlow.asStateFlow()

    /**
     * Provides concurrent and on-demand access to the `jdwp` session of the device.
     *
     * We use a [ReferenceCountedResource] to ensure only one session is created at a time,
     * while at the same time allowing multiple consumers to access the jdwp session concurrently.
     *
     * We currently have 2 consumers:
     * * A [JdwpProcessPropertiesCollector] that opens a jdwp session for a few seconds to collect
     *   the process properties (package name, process name, etc.)
     * * A [JdwpSessionProxy] that opens a jdwp session "on demand" when a Java debugger wants
     *   to connect to the process on the device.
     *
     * Typically, both consumers don't overlap, but if a debugger tries to attach to the process
     * just after its creation, before we are done collecting properties, the [JdwpSessionProxy]
     * ends up trying to open a jdwp session before [JdwpProcessPropertiesCollector] is done
     * collecting process properties. When this happens, we open a single JDWP connection that
     * is used for collecting process properties and for a debugging session. The connection
     * lasts until the debugging session ends.
     */
    private val jdwpSessionRef = ReferenceCountedResource(session, session.host.ioDispatcher) {
        JdwpSession.openJdwpSession(session, device, pid).closeOnException { jdwpSession ->
            SharedJdwpSession.create(session, pid, jdwpSession)
        }
    }

    private val collector = JdwpProcessPropertiesCollector(session, pid, jdwpSessionRef)

    private val jdwpSessionProxy = JdwpSessionProxy(session, pid, jdwpSessionRef)

    fun startMonitoring() {
        scope.launch {
            jdwpSessionProxy.execute(stateFlow)
        }
        scope.launch {
            executePropertyCollector()
        }
    }

    private suspend fun executePropertyCollector() {
        while (true) {
            // Opens a JDWP session to the process, and collect as many properties as we
            // can for a short period of time, then close the session and try again
            // later if needed.
            try {
                withTimeout(PROCESS_PROPERTIES_READ_TIMEOUT.toMillis()) {
                    collector.collect(stateFlow)
                }
                // If we have what we want, reset the exception to `null` (this could
                // be a retry of something that failed earlier)
                stateFlow.update { it.copy(exception = null) }
            } catch (e: TimeoutCancellationException) {
                // On API 28+, we get a timeout if there is an active JDWP connection to the
                // process **or** if the collector did not collect all the data it wanted
                // (which is common in case there is no WAIT DDMS packet).
                // We set the exception only if the collector did not receive the DDMS HELO
                // packet, so that we don't retry indefinitely.
                if (stateFlow.value.heloResponseReceived()) {
                    // If we have what we want, reset the exception to `null` (this could
                    // be a retry of something that failed earlier)
                    stateFlow.update { it.copy(exception = null) }
                } else {
                    stateFlow.update { it.copy(exception = e) }
                }
            } catch (e: EOFException) {
                // On API 27 and earlier, we may immediately get an "EOFException" if there
                // already was an active JDWP connection to the process
                stateFlow.update { it.copy(exception = e) }
            } catch (t: Throwable) {
                t.rethrowCancellation()
                stateFlow.update { it.copy(exception = t) }
            }

            // Delay and retry if we did not collect all properties we want
            val properties = stateFlow.value
            if (properties.exception == null) {
                logger.debug { "Successfully retrieved JDWP process properties: $properties" }
                break
            }

            logger.info(properties.exception) { "Error retrieving (at least some of) JDWP process properties ($properties), retrying later" }
            delay(PROCESS_PROPERTIES_RETRY_DURATION.toMillis())
        }
    }

    override fun close() {
        val msg = "Closing coroutine scope of JDWP process $pid"
        logger.debug { msg }
        jdwpSessionRef.close()
        scope.cancel(msg)
    }

    private fun JdwpProcessProperties.heloResponseReceived(): Boolean {
        return this.processName != null &&
                this.vmIdentifier != null
    }
}
