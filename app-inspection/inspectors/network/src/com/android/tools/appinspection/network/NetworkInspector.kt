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
import com.android.tools.appinspection.network.okhttp.OkHttp2Interceptor
import com.android.tools.appinspection.network.okhttp.OkHttp3Interceptor
import com.squareup.okhttp.Interceptor
import com.squareup.okhttp.OkHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import studio.network.inspection.NetworkInspectorProtocol
import java.net.URL
import java.net.URLConnection
import java.util.List

private const val POLL_INTERVAL_MS = 500L
private const val MULTIPLIER_FACTOR = 1000 / POLL_INTERVAL_MS

class NetworkInspector(
    connection: Connection,
    private val environment: InspectorEnvironment
) : Inspector(connection) {

    private val scope =
        CoroutineScope(SupervisorJob() + environment.executors().primary().asCoroutineDispatcher())

    private val trackerService = HttpTrackerFactory(connection)
    private var isStarted = false

    private var okHttp2Interceptors: List<Interceptor>? = null

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

        try {
            /*
             * Modifies a list of okhttp2 Interceptor in place, adding our own
             * interceptor into it if not already present, and then returning the list again.
             *
             * In okhttp2 (unlike okhttp3), networkInterceptors() returns direct access to an
             * OkHttpClient list of interceptors and uses that as the API for a user to add more.
             *
             * Therefore, we have to modify the list in place, whenever it is first accessed (either
             * by the user to add their own interceptor, or by OkHttp internally to iterate through
             * all interceptors).
             */
            environment.artTooling().registerExitHook(
                OkHttpClient::class.java,
                "networkInterceptors()Ljava/util/List;",
                ArtTooling.ExitHook<List<Interceptor>> { list ->
                    if (list.none { it is OkHttp2Interceptor }) {
                        okHttp2Interceptors = list
                        list.add(0, OkHttp2Interceptor(trackerService))
                    }
                    list
                }
            )
        } catch (e: NoClassDefFoundError) {
            // Ignore. App may not depend on OkHttp.
        }

        try {
            environment.artTooling().registerExitHook(
                okhttp3.OkHttpClient::class.java,
                "networkInterceptors()Ljava/util/List;",
                ArtTooling.ExitHook<List<okhttp3.Interceptor>> { list ->
                    val interceptors = java.util.ArrayList<okhttp3.Interceptor>()
                    interceptors.add(OkHttp3Interceptor(trackerService))
                    interceptors.addAll(list)
                    interceptors as List<okhttp3.Interceptor>
                }
            )
        } catch (e: NoClassDefFoundError) {
            // Ignore. App may not depend on OkHttp.
        }

    }

    override fun onDispose() {
        okHttp2Interceptors?.removeIf { it is OkHttp2Interceptor }
        scope.cancel("Network Inspector has been disposed.")
    }
}
