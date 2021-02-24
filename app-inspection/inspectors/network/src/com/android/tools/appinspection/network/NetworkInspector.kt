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

package com.android.tools.appinspection.network

import android.app.Application
import android.net.TrafficStats
import androidx.inspection.ArtTooling
import androidx.inspection.Connection
import androidx.inspection.Inspector
import androidx.inspection.InspectorEnvironment
import com.android.tools.appinspection.network.httpurl.wrapURLConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import studio.network.inspection.NetworkInspectorProtocol
import java.net.URL
import java.net.URLConnection
import java.util.concurrent.CancellationException

private const val POLL_INTERVAL_MS = 500L
private const val MULTIPLIER_FACTOR = 1000 / POLL_INTERVAL_MS

class NetworkInspector(
    connection: Connection,
    private val environment: InspectorEnvironment
) : Inspector(connection) {

    private val supervisorJob = SupervisorJob()
    private val scope =
        CoroutineScope(supervisorJob + environment.executors().primary().asCoroutineDispatcher())

    private val trackerService = HttpTrackerFactory(connection)
    private var isStarted = false

    override fun onReceiveCommand(data: ByteArray, callback: CommandCallback) {
        // Network inspector only supports the start inspector command.
        // Studio should only ever send one start command, but it's harmless to
        // reply with a response. We just need to make sure that we don't collect
        // information twice.
        val command = NetworkInspectorProtocol.Command.parseFrom(data)
        assert(command.hasStartInspectionCommand())
        callback.reply(
            NetworkInspectorProtocol.Response.newBuilder()
                .setStartInspectionResponse(
                    NetworkInspectorProtocol.StartInspectionResponse.newBuilder()
                        .setTimestamp(System.nanoTime())
                )
                .build()
                .toByteArray()
        )

        if (!isStarted) {
            startSpeedCollection()
            registerHooks()
            isStarted = true
        }
    }

    private fun startSpeedCollection() {
        val application = environment.artTooling().findInstances(Application::class.java).single()
        val uid = application.applicationInfo.uid

        scope.launch {
            var prevRxBytes = TrafficStats.getUidRxBytes(uid)
            var prevTxBytes = TrafficStats.getUidTxBytes(uid)
            while (true) {
                delay(POLL_INTERVAL_MS)
                val rxBytes = TrafficStats.getUidRxBytes(uid)
                val txBytes = TrafficStats.getUidTxBytes(uid)
                connection.sendEvent(
                    NetworkInspectorProtocol.Event.newBuilder()
                        .setSpeedEvent(
                            NetworkInspectorProtocol.SpeedEvent.newBuilder()
                                .setRxSpeed((rxBytes - prevRxBytes) * MULTIPLIER_FACTOR)
                                .setTxSpeed((txBytes - prevTxBytes) * MULTIPLIER_FACTOR)
                        )
                        .setTimestamp(System.nanoTime())
                        .build()
                        .toByteArray()
                )
                prevRxBytes = rxBytes
                prevTxBytes = txBytes
            }
        }
    }

    private fun registerHooks() {
        environment.artTooling().registerExitHook(
            URL::class.java,
            "openConnection()Ljava/net/URLConnection;",
            ArtTooling.ExitHook<URLConnection> { urlConnection ->
                wrapURLConnection(urlConnection, trackerService)
            }
        )
    }

    override fun onDispose() {
        if (!supervisorJob.isCancelled) {
            supervisorJob.cancel(CancellationException("Network Inspector has been disposed."))
        }
    }
}
