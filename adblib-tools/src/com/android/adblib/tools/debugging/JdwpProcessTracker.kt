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
package com.android.adblib.tools.debugging

import com.android.adblib.AdbDeviceServices
import com.android.adblib.AdbLibSession
import com.android.adblib.DeviceSelector
import com.android.adblib.ProcessIdList
import com.android.adblib.createDeviceScope
import com.android.adblib.emptyProcessIdList
import com.android.adblib.thisLogger
import com.android.adblib.tools.debugging.JdwpTracker.ProcessMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive
import java.io.EOFException
import java.time.Duration
import java.util.SortedMap
import java.util.TreeMap

/**
 * If the [AdbDeviceServices.trackJdwp] call fails with an error while the device is
 * still connected, we want to retry. This defines the [Duration] to wait before retrying.
 */
private val TRACK_JDWP_RETRY_DELAY = Duration.ofSeconds(2)

/**
 * Tracks JDWP processes of a given [device].
 *
 * Start a [ProcessMap] tracker [Flow] by calling the [createFlow] method.
 */
class JdwpTracker(
    private val session: AdbLibSession,
    private val device: DeviceSelector
) {

    private val logger = thisLogger(session)

    private val deviceScope = session.createDeviceScope(device)

    fun createFlow(): Flow<List<JdwpProcess>> = flow {
        val processMap = ProcessMap()
        var deviceDisconnected = false
        try {
            session.deviceServices
                .trackJdwp(device)
                .retryWhen { throwable, _ ->
                    // We want to retry the `trackJdwp` request as long as the device is connected.
                    // But we also want to end the flow when the device has been disconnected.
                    if (!deviceScope.isActive) {
                        logger.info { "JDWP tracker service ending because device is disconnected" }
                        deviceDisconnected = true
                        false // Don't retry, let exception through
                    } else {
                        // Retry after emitting empty list
                        if (throwable is EOFException) {
                            logger.info { "JDWP tracker services ended with expected EOF exception, retrying" }
                        } else {
                            logger.info(throwable) { "JDWP tracker ended unexpectedly ($throwable), retrying" }
                        }
                        // When disconnected, assume we have no processes
                        emit(emptyProcessIdList())
                        true // Retry
                    }
                }.collect { processIdList ->
                    logger.debug { "Received a new list of processes: $processIdList" }
                    updateProcessMap(processMap, processIdList)
                    emit(processMap.toList())
                }
        } catch (t: Throwable) {
            t.rethrowCancellation()
            if (deviceDisconnected) {
                logger.debug(t) { "Ignoring exception $t because device has been disconnected" }
            } else {
                throw t
            }
        } finally {
            logger.debug { "Clearing process map" }
            processMap.clear()
        }
    }.flowOn(session.host.ioDispatcher)

    private fun updateProcessMap(map: ProcessMap, list: ProcessIdList) {
        val lastKnownPids = map.pids
        val effectivePids = list.toHashSet()

        val added = effectivePids - lastKnownPids
        val removed = lastKnownPids - effectivePids
        removed.forEach { pid ->
            logger.debug { "Removing process $pid from process map" }
            map.remove(pid)
        }
        added.forEach { pid ->
            logger.debug { "Adding process $pid to process map" }
            map.add(JdwpProcessImpl(session, device, deviceScope, pid))
        }
    }

    /**
     * Stores a collection of [JdwpProcessImpl], with efficient access by process ID.
     */
    private class ProcessMap {

        /**
         * This  [SortedMap] (as opposed to a regular [Map]) merely for convenience,
         * to keep PIDs sorted.
         */
        private val map: SortedMap<Int, JdwpProcessImpl> = TreeMap()

        val pids: Set<Int>
            get() = map.keys

        fun toList(): List<JdwpProcessImpl> {
            return map.values.toList()
        }

        fun add(jdwpProcess: JdwpProcessImpl) {
            map[jdwpProcess.pid] = jdwpProcess
        }

        fun remove(pid: Int) {
            map.remove(pid)?.close()
        }

        fun clear() {
            // Close all processes
            map.values.forEach {
                it.close()
            }
            map.clear()
        }
    }
}

fun Throwable.rethrowCancellation() {
    if (this is CancellationException) {
        throw this
    }
}
