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
import com.android.adblib.tools.debugging.rethrowCancellation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
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

    override val processPropertiesFlow = stateFlow.asStateFlow()

    private val collector = JdwpProcessPropertiesCollector(session, device, pid)

    fun startMonitoring() {
        scope.launch {
            while (true) {
                // Opens a JDWP session to the process, and collect as many properties as we
                // can for a short period of time, then close the session and try again
                // later if needed.
                try {
                    withTimeout(PROCESS_PROPERTIES_READ_TIMEOUT.toMillis()) {
                        collector.collect(stateFlow)
                    }
                } catch (e: TimeoutCancellationException) {
                    // On API 28+, we get a timeout if there is an active JDWP connection to the
                    // process **or** if the collector did not collect all the data it wanted
                    // (which is common in case there is no WAIT DDMS packet).
                    // We set the exception only if the collector did not receive the DDMS HELO
                    // packet, so that we don't retry indefinitely.
                    if (!stateFlow.value.heloResponseReceived()) {
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
    }

    override fun close() {
        val msg = "Closing coroutine scope of JDWP process $pid"
        logger.debug { msg }
        scope.cancel(msg)
    }

    private fun JdwpProcessProperties.heloResponseReceived(): Boolean {
        return this.processName != null &&
                this.vmIdentifier != null
    }
}

private fun CoroutineScope.createChildScope(job: Job = SupervisorJob(coroutineContext.job)): CoroutineScope {
    return CoroutineScope(this.coroutineContext + job)
}
