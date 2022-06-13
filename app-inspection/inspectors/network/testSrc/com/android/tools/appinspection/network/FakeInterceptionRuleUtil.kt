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

package com.android.tools.appinspection.network

import androidx.inspection.Inspector
import com.android.tools.idea.protobuf.ByteString
import studio.network.inspection.NetworkInspectorProtocol
import java.net.URL
import java.util.concurrent.Executor

fun createFakeRuleAddedEvent(url: URL): NetworkInspectorProtocol.InterceptRuleAdded =
    NetworkInspectorProtocol.InterceptRuleAdded.newBuilder().apply {
        ruleId = 1
        ruleBuilder.apply {
            enabled = true
            criteriaBuilder.apply {
                protocol = when (url.protocol) {
                    "https" -> NetworkInspectorProtocol.InterceptCriteria.Protocol.PROTOCOL_HTTPS
                    "http" -> NetworkInspectorProtocol.InterceptCriteria.Protocol.PROTOCOL_HTTP
                    else -> NetworkInspectorProtocol.InterceptCriteria.Protocol.PROTOCOL_UNSPECIFIED
                }
                host = url.host
                port = ""
                path = url.path
                query = url.query
                method = NetworkInspectorProtocol.InterceptCriteria.Method.METHOD_GET
            }
            addTransformation(
                NetworkInspectorProtocol.Transformation.newBuilder().apply {
                    bodyReplacedBuilder.apply {
                        body =
                            ByteString.copyFrom("InterceptedBody1".toByteArray())
                    }
                })
        }
    }.build()

fun NetworkInspector.receiveInterceptCommand(
    interceptCommand: NetworkInspectorProtocol.InterceptCommand
) {
    onReceiveCommand(NetworkInspectorProtocol.Command.newBuilder()
        .apply {
            this.interceptCommand = interceptCommand
        }
        .build()
        .toByteArray(), object : Inspector.CommandCallback {
        override fun reply(response: ByteArray) {
        }

        override fun addCancellationListener(executor: Executor, runnable: Runnable) {
        }
    })
}
