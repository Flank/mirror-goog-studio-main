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
import android.util.Log
import androidx.inspection.ArtTooling
import androidx.inspection.Connection
import androidx.inspection.Inspector
import androidx.inspection.InspectorEnvironment
import com.android.tools.appinspection.network.httpurl.wrapURLConnection
import com.android.tools.appinspection.network.okhttp.OkHttp2Interceptor
import com.android.tools.appinspection.network.okhttp.OkHttp3Interceptor
import com.android.tools.appinspection.network.rules.InterceptionRule
import com.android.tools.appinspection.network.rules.InterceptionRuleServiceImpl
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
private val INTERCEPT_COMMAND_RESPONSE =
    NetworkInspectorProtocol.Response.newBuilder().apply {
        interceptResponse =
            NetworkInspectorProtocol.InterceptResponse.getDefaultInstance()
    }
        .build()
        .toByteArray()

class NetworkInspector(
    connection: Connection,
    private val environment: InspectorEnvironment
) : Inspector(connection) {

    private val scope =
        CoroutineScope(SupervisorJob() + environment.executors().primary().asCoroutineDispatcher())

    private val trackerService = HttpTrackerFactoryImpl(connection)
    private var isStarted = false

    private var okHttp2Interceptors: List<Interceptor>? = null

    private val interceptionService = InterceptionRuleServiceImpl()

    override fun onReceiveCommand(data: ByteArray, callback: CommandCallback) {
        val command = NetworkInspectorProtocol.Command.parseFrom(data)
        when {
            command.hasStartInspectionCommand() -> {
                callback.reply(
                    NetworkInspectorProtocol.Response.newBuilder()
                        .setStartInspectionResponse(
                            NetworkInspectorProtocol.StartInspectionResponse.newBuilder()
                                .setTimestamp(System.nanoTime())
                        )
                        .build()
                        .toByteArray()
                )

                // Studio should only ever send one start command, but it's harmless to
                // reply with a response. We just need to make sure that we don't collect
                // information twice.
                if (!isStarted) {
                    startSpeedCollection()
                    registerHooks()
                    isStarted = true
                }
            }
            command.hasInterceptCommand() -> {
                val interceptCommand = command.interceptCommand
                when {
                    interceptCommand.hasInterceptRuleAdded() -> {
                        val interceptRuleAdded = interceptCommand.interceptRuleAdded
                        val rule = interceptRuleAdded.rule
                        interceptionService.addRule(
                            interceptRuleAdded.ruleId,
                            InterceptionRule(rule)
                        )
                        callback.reply(INTERCEPT_COMMAND_RESPONSE)
                    }
                    interceptCommand.hasInterceptRuleUpdated() -> {
                        val interceptRuleAdded = interceptCommand.interceptRuleUpdated
                        val rule = interceptRuleAdded.rule
                        interceptionService.addRule(
                            interceptRuleAdded.ruleId,
                            InterceptionRule(rule)
                        )
                        callback.reply(INTERCEPT_COMMAND_RESPONSE)
                    }
                    interceptCommand.hasInterceptRuleRemoved() -> {
                        interceptionService.removeRule(interceptCommand.interceptRuleRemoved.ruleId)
                        callback.reply(INTERCEPT_COMMAND_RESPONSE)
                    }
                    interceptCommand.hasReorderInterceptRules() -> {
                        interceptionService
                            .reorderRules(interceptCommand.reorderInterceptRules.ruleIdList)
                        callback.reply(INTERCEPT_COMMAND_RESPONSE)
                    }
                }
            }
        }
    }

    private fun startSpeedCollection() = scope.launch {
        // The app can have multiple Application instances. In that case, we use the first non-null
        // uid, which is most likely from the Application created by Android.
        val uid = environment.artTooling().findInstances(Application::class.java)
            .mapNotNull {
                try {
                    it.applicationInfo?.uid
                } catch (e: Exception) {
                    null
                }
            }
            .firstOrNull() ?: run {
            Log.e(
                this::class.java.name,
                "Failed to find application instance. Collection of network speed is not available."
            )
            return@launch
        }
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

    private fun registerHooks() {
        environment.artTooling().registerExitHook(
            URL::class.java,
            "openConnection()Ljava/net/URLConnection;",
            ArtTooling.ExitHook<URLConnection> { urlConnection ->
                wrapURLConnection(urlConnection, trackerService, interceptionService)
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
                        list.add(0, OkHttp2Interceptor(trackerService, interceptionService))
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
                    interceptors.add(OkHttp3Interceptor(trackerService, interceptionService))
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
