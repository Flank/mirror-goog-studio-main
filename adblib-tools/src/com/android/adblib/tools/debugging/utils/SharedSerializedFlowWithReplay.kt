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
package com.android.adblib.tools.debugging.utils

import com.android.adblib.AdbSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A [SharedSerializedFlow] implementation that allows adding "replay" values that should be
 * emitted to downstream flows every time the [SharedSerializedFlow.flow] is collected.
 * Unlike [MutableSharedFlow], the sequence of replay values is not automatically built,
 * but instead is built by calling [addReplayValue] explicitly.
 */
internal class SharedSerializedFlowWithReplay<T>(
    session: AdbSession,
    upstreamFlow: Flow<T>,
    /**
     * An optional operator to ensure identical replay values are discarded.
     */
    replayKeyProvider: (T) -> Any = { it as Any }
) : SharedSerializedFlow<T>(session, upstreamFlow) {

    /**
     * Values that should be replayed everytime a new collector of [SharedSerializedFlow.flow] is
     * started.
     */
    private val replayTable = ReplayTable(replayKeyProvider)

    /**
     * [Mutex] used to ensure we never replay values concurrently to multiple [FlowCollector].
     * See [onStartCollectNewActiveFlow].
     */
    private val replayMutex = Mutex()

    override suspend fun onStartCollectNewActiveFlow(flowCollector: FlowCollector<T>) {
        val replayValues = replayTable.getSnapshot()
        replayMutex.withLock {
            replayValues.forEach {
                flowCollector.emit(it)
            }
        }
    }

    fun addReplayValue(replayValue: T) {
        replayTable.add(replayValue)
    }

    /**
     * Similar behavior to [CopyOnWriteArrayList] with the additional capability to replace
     * values with identical "keys".
     */
    class ReplayTable<T>(private val replayKeyProvider: (T) -> Any) {
        /**
         * Lock for [replayPackets] and [replayPacketList]
         */
        private val lock = Any()

        private val replayPackets = linkedMapOf<Any, T>()

        private var replayPacketList: List<T>? = null

        fun getSnapshot(): List<T> {
            return synchronized(lock) {
                replayPacketList ?: run {
                    replayPackets.values.toList().also {
                        replayPacketList = it
                    }
                }
            }
        }

        fun add(replayValue: T) {
            val replayKey = replayKeyProvider(replayValue)
            synchronized(lock) {
                replayPackets[replayKey] = replayValue
                // The map changed, invalidate the list
                replayPacketList = null
            }
        }
    }
}
