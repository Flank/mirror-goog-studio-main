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

import com.android.tools.appinspection.network.okhttp3.FakeOkHttp3Client
import com.google.common.truth.Truth.assertThat
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSink
import org.junit.Rule
import org.junit.Test
import studio.network.inspection.NetworkInspectorProtocol
import java.io.IOException
import java.net.URL

private const val URL_PARAMS = "activity=OkHttp3Test"
private val FAKE_URL = URL("https://www.google.com?$URL_PARAMS")
private const val EXPECTED_RESPONSE_CODE = "response-status-code = 200"

class OkHttp3Test {

    @get:Rule
    val inspectorRule = NetworkInspectorRule()

    @Test
    fun get() {
        val client = createFakeOkHttp3Client()

        val request = Request.Builder().url(FAKE_URL).build()
        val fakeResponse = createFakeResponse(request)

        val response = client.newCall(request, fakeResponse).execute()
        response.body().byteStream().use { it.readBytes() }

        assertThat(inspectorRule.connection.httpData).hasSize(6)
        val httpRequestStarted = inspectorRule.connection.httpData.first().httpRequestStarted
        assertThat(httpRequestStarted.url).contains(URL_PARAMS)
        assertThat(httpRequestStarted.method).isEqualTo("GET")

        val httpResponseStarted =
            inspectorRule.connection.findHttpEvent(
                NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.HTTP_RESPONSE_STARTED
            )!!
        assertThat(httpResponseStarted.httpResponseStarted.fields)
            .contains(EXPECTED_RESPONSE_CODE)

        assertThat(
            inspectorRule.connection.findHttpEvent(
                NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.RESPONSE_PAYLOAD
            )!!.responsePayload.payload.toStringUtf8()
        ).isEqualTo("Test")

        assertThat(inspectorRule.connection.httpData.last().httpClosed.completed).isTrue()
    }

    @Test
    fun post() {
        val client = createFakeOkHttp3Client()
        val requestBody = object : RequestBody() {
            override fun contentType(): MediaType {
                return MediaType.parse("text/text")
            }

            override fun writeTo(bufferedSink: BufferedSink) {
                val requestBody = "request body"
                bufferedSink.write(requestBody.toByteArray())
            }
        }
        val request = Request.Builder().url(FAKE_URL).post(requestBody).build()
        val fakeResponse = createFakeResponse(request)

        val response = client.newCall(request, fakeResponse).execute()
        response.body().byteStream().use { it.readBytes() }

        assertThat(inspectorRule.connection.httpData).hasSize(8)
        val httpRequestStarted = inspectorRule.connection.httpData.first().httpRequestStarted
        assertThat(httpRequestStarted.url).contains(URL_PARAMS)
        assertThat(httpRequestStarted.method).isEqualTo("POST")

        val httpRequestCompleted =
            inspectorRule.connection.findHttpEvent(
                NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.HTTP_REQUEST_COMPLETED
            )
        assertThat(httpRequestCompleted).isNotNull()

        val requestPayload =
            inspectorRule.connection.findHttpEvent(
                NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.REQUEST_PAYLOAD
            )
        assertThat(requestPayload!!.requestPayload.payload.toStringUtf8())
            .isEqualTo("request body")
    }

    @Test
    fun abort() {
        val client = createFakeOkHttp3Client()
        val request = Request.Builder().url(FAKE_URL).build()
        val fakeResponse = createFakeResponse(request)

        val call = client.newCall(request, fakeResponse)
        try {
            call.executeThenBlowUp()
        } catch (e: IOException) {
            // This exception comes from blowing up the interceptor chain and is expected.
        }

        val response = client.newCall(request, fakeResponse).execute()
        response.body().byteStream().use { it.readBytes() }

        val events = inspectorRule.connection.httpData.groupBy { it.connectionId }

        run {
            // events for the aborted call
            val abortedEvents = events[0]!!
            assertThat(abortedEvents).hasSize(3)
            assertThat(abortedEvents[2].hasHttpClosed()).isTrue()
            assertThat(abortedEvents[2].httpClosed.completed).isFalse()
            assertThat(abortedEvents[1].hasHttpThread()).isTrue()
        }

        run {
            // events for the follow up GET call that is successful
            val successEvents = events[1]!!
            assertThat(successEvents).hasSize(6)
            assertThat(successEvents[2].hasHttpResponseStarted()).isTrue()
            val httpResponseStarted =
                inspectorRule.connection.findHttpEvent(
                    NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.HTTP_RESPONSE_STARTED
                )
            assertThat(httpResponseStarted!!.httpResponseStarted.fields).contains(
                EXPECTED_RESPONSE_CODE
            )
            assertThat(successEvents[5].hasHttpClosed()).isTrue()
            assertThat(successEvents[5].httpClosed.completed).isTrue()
        }
    }

    private fun createFakeOkHttp3Client(): FakeOkHttp3Client {
        return FakeOkHttp3Client(
            inspectorRule.environment.fakeArtTooling.triggerExitHook(
                okhttp3.OkHttpClient::class.java,
                "networkInterceptors()Ljava/util/List;",
                emptyList()
            )
        )

    }
}

private fun createFakeResponse(request: Request): Response {
    return Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_2)
        .code(200)
        .message("")
        .body(
            ResponseBody.create(
                MediaType.parse("text/text; charset=utf-8"),
                "Test"
            )
        )
        .build()
}
