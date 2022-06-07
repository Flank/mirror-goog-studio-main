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

import androidx.inspection.Inspector
import com.android.tools.appinspection.network.http.FakeHttpUrlConnection
import com.android.tools.idea.protobuf.ByteString
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import studio.network.inspection.NetworkInspectorProtocol
import studio.network.inspection.NetworkInspectorProtocol.InterceptCommand
import studio.network.inspection.NetworkInspectorProtocol.InterceptRuleAdded
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executor

private const val URL_PARAMS = "activity=http"
private val FAKE_URL = URL("https://www.google.com?$URL_PARAMS")
private const val EXPECTED_RESPONSE = "HTTP/1.0 200 OK"

class HttpUrlTest {

    @get:Rule
    val inspectorRule = NetworkInspectorRule()

    @Test
    fun httpGet() {
        with(FakeHttpUrlConnection(FAKE_URL, "Test".toByteArray(), "GET").triggerHttpExitHook()) {
            val inputStream = inputStream
            inputStream.use { it.readBytes() }
        }
        assertThat(inspectorRule.connection.httpData).hasSize(6)
        val httpRequestStarted =
            inspectorRule.connection.findHttpEvent(
                NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.HTTP_REQUEST_STARTED
            )!!
        assertThat(httpRequestStarted.httpRequestStarted.url).contains(URL_PARAMS)
        assertThat(httpRequestStarted.httpRequestStarted.method).isEqualTo("GET")

        val httpResponseStarted =
            inspectorRule.connection.findHttpEvent(
                NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.HTTP_RESPONSE_STARTED
            )
        assertThat(httpResponseStarted!!.httpResponseStarted.fields).contains(EXPECTED_RESPONSE)

        assertThat(
            inspectorRule.connection.findHttpEvent(
                NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.RESPONSE_PAYLOAD
            )!!.responsePayload.payload.toStringUtf8()
        ).isEqualTo("Test")

        assertThat(inspectorRule.connection.httpData.last().httpClosed.completed).isTrue()
    }

    @Test
    fun httpIntercept() {
        // Step1: add a new body rule.
        val ruleAddedBuilder = InterceptRuleAdded.newBuilder().apply {
            ruleId = 1
            ruleBuilder.apply {
                criteriaBuilder.apply {
                    protocol = FAKE_URL.protocol
                    host = FAKE_URL.host
                    port = ""
                    path = FAKE_URL.path
                    query = FAKE_URL.query
                    method = ""
                }
                addTransformation(
                    NetworkInspectorProtocol.Transformation.newBuilder().apply {
                        bodyReplacedBuilder.apply {
                            body =
                                ByteString.copyFrom("InterceptedBody1".toByteArray())
                        }
                    })
            }
        }

        receiveInterceptCommand(InterceptCommand.newBuilder().apply {
            interceptRuleAdded = ruleAddedBuilder.build()
        }.build())

        with(FakeHttpUrlConnection(FAKE_URL, "Test".toByteArray(), "GET").triggerHttpExitHook()) {
            val inputStream = inputStream
            inputStream.use { it.readBytes() }
        }
        assertThat(
            inspectorRule.connection.findHttpEvent(
                NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.RESPONSE_PAYLOAD
            )!!.responsePayload.payload.toStringUtf8()
        ).isEqualTo("InterceptedBody1")

        assertThat(
            inspectorRule.connection.findHttpEvent(
                NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.HTTP_RESPONSE_INTERCEPTED
            )!!.httpResponseIntercepted.bodyReplaced
        ).isTrue()
        assertThat(inspectorRule.connection.httpData.last().httpClosed.completed).isTrue()

        // Step2: add another body rule with different content.
        receiveInterceptCommand(InterceptCommand.newBuilder().apply {
            interceptRuleAdded = ruleAddedBuilder.apply {
                ruleId = 2
                ruleBuilder.transformationBuilderList[0].bodyReplacedBuilder.body =
                    ByteString.copyFrom("InterceptedBody2".toByteArray())
            }.build()
        }.build())
        with(FakeHttpUrlConnection(FAKE_URL, "Test".toByteArray(), "GET").triggerHttpExitHook()) {
            val inputStream = inputStream
            inputStream.use { it.readBytes() }
        }
        assertThat(
            inspectorRule.connection.findLastHttpEvent(
                NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.RESPONSE_PAYLOAD
            )!!.responsePayload.payload.toStringUtf8()
        ).isEqualTo("InterceptedBody2")

        // Step3: reorder two body rules.
        receiveInterceptCommand(InterceptCommand.newBuilder().apply {
            reorderInterceptRulesBuilder.apply {
                addAllRuleId(listOf(2, 1))
            }.build()
        }.build())
        with(FakeHttpUrlConnection(FAKE_URL, "Test".toByteArray(), "GET").triggerHttpExitHook()) {
            val inputStream = inputStream
            inputStream.use { it.readBytes() }
        }
        assertThat(
            inspectorRule.connection.findLastHttpEvent(
                NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.RESPONSE_PAYLOAD
            )!!.responsePayload.payload.toStringUtf8()
        ).isEqualTo("InterceptedBody1")

        // Step4: remove the last body rule.
        receiveInterceptCommand(InterceptCommand.newBuilder().apply {
            interceptRuleRemovedBuilder.apply {
                ruleId = 1
            }.build()
        }.build())
        with(FakeHttpUrlConnection(FAKE_URL, "Test".toByteArray(), "GET").triggerHttpExitHook()) {
            val inputStream = inputStream
            inputStream.use { it.readBytes() }
        }
        assertThat(
            inspectorRule.connection.findLastHttpEvent(
                NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.RESPONSE_PAYLOAD
            )!!.responsePayload.payload.toStringUtf8()
        ).isEqualTo("InterceptedBody2")
    }

    private fun receiveInterceptCommand(interceptCommand: InterceptCommand) {
        inspectorRule.inspector.onReceiveCommand(NetworkInspectorProtocol.Command.newBuilder()
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

    @Test
    fun httpPost() {
        with(FakeHttpUrlConnection(FAKE_URL, "Test".toByteArray(), "POST").triggerHttpExitHook()) {
            doOutput = true
            outputStream.use { it.write("TestRequestBody".toByteArray()) }
            inputStream.use { it.readBytes() }
        }

        assertThat(inspectorRule.connection.httpData).hasSize(8)
        val httpRequestStarted =
            inspectorRule.connection.findHttpEvent(
                NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.HTTP_REQUEST_STARTED
            )!!
        assertThat(httpRequestStarted.httpRequestStarted.url).contains(URL_PARAMS)
        assertThat(httpRequestStarted.httpRequestStarted.method).isEqualTo("POST")

        assertThat(
            inspectorRule.connection.findHttpEvent(
                NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.HTTP_REQUEST_COMPLETED
            )
        ).isNotNull()

        assertThat(
            inspectorRule.connection.findHttpEvent(
                NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.REQUEST_PAYLOAD
            )!!.requestPayload.payload.toStringUtf8()
        ).isEqualTo("TestRequestBody")

        val httpResponseStarted =
            inspectorRule.connection.findHttpEvent(
                NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.HTTP_RESPONSE_STARTED
            )!!
        assertThat(httpResponseStarted.httpResponseStarted.fields).contains(EXPECTED_RESPONSE)

        assertThat(
            inspectorRule.connection.findHttpEvent(
                NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.RESPONSE_PAYLOAD
            )!!.responsePayload.payload.toStringUtf8()
        ).isEqualTo("Test")

        assertThat(inspectorRule.connection.httpData.last().httpClosed.completed).isTrue()
    }

    /**
     * HttpURLConnection has many functions to query response values which cause a connection to be
     * made if one wasn't already established. This test helps makes sure our tracking code handles
     * such functions before we call 'connect' or 'getInputStream'.
     */
    @Test
    fun getResponseCodeBeforeConnect() {
        with(FakeHttpUrlConnection(FAKE_URL, "Test".toByteArray(), "GET").triggerHttpExitHook()) {
            // Calling getResponseCode() implicitly calls connect()
            responseCode
            inputStream.use { it.readBytes() }
        }
        assertThat(inspectorRule.connection.httpData).hasSize(6)
        val httpRequestStarted =
            inspectorRule.connection.findHttpEvent(
                NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.HTTP_REQUEST_STARTED
            )!!
        assertThat(httpRequestStarted.httpRequestStarted.method).isEqualTo("GET")

        val httpResponseStarted =
            inspectorRule.connection.findHttpEvent(
                NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.HTTP_RESPONSE_STARTED
            )!!
        assertThat(httpResponseStarted.httpResponseStarted.fields).contains(EXPECTED_RESPONSE)

        assertThat(
            inspectorRule.connection.findHttpEvent(
                NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.RESPONSE_PAYLOAD
            )!!.responsePayload.payload.toStringUtf8()
        ).isEqualTo("Test")

        assertThat(inspectorRule.connection.httpData.last().httpClosed.completed).isTrue()
    }

    private fun FakeHttpUrlConnection.triggerHttpExitHook(): HttpURLConnection {
        return inspectorRule.environment.fakeArtTooling.triggerExitHook(
            URL::class.java,
            "openConnection()Ljava/net/URLConnection;",
            this
        )
    }
}
