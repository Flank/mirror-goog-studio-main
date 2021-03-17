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

import com.android.tools.appinspection.network.okhttp2.FakeOkHttp2Client
import com.google.common.truth.Truth.assertThat
import com.squareup.okhttp.MediaType
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Protocol
import com.squareup.okhttp.Request
import com.squareup.okhttp.RequestBody
import com.squareup.okhttp.Response
import com.squareup.okhttp.ResponseBody
import okio.BufferedSink
import org.junit.Rule
import org.junit.Test
import studio.network.inspection.NetworkInspectorProtocol
import java.io.IOException
import java.net.URL

private const val URL_PARAMS = "activity=OkHttp2Test"
private val FAKE_URL = URL("https://www.google.com?$URL_PARAMS")
private const val EXPECTED_RESPONSE_CODE = "response-status-code = 200"

class OkHttp2Test {

    @get:Rule
    val inspectorRule = NetworkInspectorRule()

    @Test
    fun get() {
        val client = FakeOkHttp2Client().hookInterceptors()
        
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
            )
        assertThat(httpResponseStarted!!.httpResponseStarted.fields).contains(EXPECTED_RESPONSE_CODE)

        assertThat(
            inspectorRule.connection.findHttpEvent(
                NetworkInspectorProtocol.HttpConnectionEvent.UnionCase.RESPONSE_PAYLOAD
            )!!.responsePayload.payload.toStringUtf8()
        ).isEqualTo("Test")

        assertThat(inspectorRule.connection.httpData.last().httpClosed.completed).isTrue()
    }

    @Test
    fun post() {
        val client = FakeOkHttp2Client().hookInterceptors()

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
        assertThat(requestPayload!!.requestPayload.payload.toStringUtf8()).isEqualTo("request body")
    }

    @Test
    fun abort() {
        val client = FakeOkHttp2Client().hookInterceptors()

        val request = Request.Builder().url(FAKE_URL).build()
        val fakeResponse = createFakeResponse(request)

        val call = client.newCall(request, fakeResponse)
        try {
            call.executeThenBlowUp()
        } catch (e: IOException) {
            // This exception comes from our explodingInterceptor and is expected.
            //
            // Note: In later versions of OkHttp, a connection is automatically cancelled whenever a
            // request is interrupted by an IOException. However, we need to test against OkHttp2.2
            // here (as it is the oldest version we support), so we just manually close the call
            // here (even if most users won't need to do this). If we didn't close this call, the
            // next call to client.newCall below would also fail.
            //
            // We don't do this in a finally block because we're trying to stick as close to how
            // OkHttp fixes this in later versions (IOException -> cancelled call).
            call.cancel()
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
                )!!
            assertThat(httpResponseStarted.httpResponseStarted.fields).contains(
                EXPECTED_RESPONSE_CODE
            )
            assertThat(successEvents[5].hasHttpClosed()).isTrue()
            assertThat(successEvents[5].httpClosed.completed).isTrue()
        }
    }

    private fun FakeOkHttp2Client.hookInterceptors(): FakeOkHttp2Client {
        inspectorRule.environment.fakeArtTooling.triggerExitHook(
            OkHttpClient::class.java,
            "networkInterceptors()Ljava/util/List;",
            networkInterceptors()
        )
        return this
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
