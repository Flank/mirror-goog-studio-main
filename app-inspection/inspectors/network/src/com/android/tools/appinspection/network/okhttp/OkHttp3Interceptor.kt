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
package com.android.tools.appinspection.network.okhttp

import com.android.tools.appinspection.common.logError
import com.android.tools.appinspection.network.HttpTrackerFactory
import com.android.tools.appinspection.network.rules.InterceptionRuleService
import com.android.tools.appinspection.network.rules.NetworkConnection
import com.android.tools.appinspection.network.rules.NetworkResponse
import com.android.tools.appinspection.network.trackers.HttpConnectionTracker
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Okio
import java.io.IOException

class OkHttp3Interceptor(
    private val trackerFactory: HttpTrackerFactory,
    private val interceptionRuleService: InterceptionRuleService
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var tracker: HttpConnectionTracker? = null
        try {
            tracker = trackRequest(request)
        } catch (ex: Exception) {
            logError("Could not track an OkHttp3 request", ex)
        } catch (error: NoSuchMethodError) {
            logError(
                "Could not track an OkHttp3 request due to a missing method, which could"
                        + " happen if your project uses proguard to remove unused code",
                error
            )
        }
        var response: Response
        response = try {
            chain.proceed(request)
        } catch (ex: IOException) {
            tracker?.error(ex.toString())
            throw ex
        }
        try {
            if (tracker != null) {
                response = trackResponse(tracker, request, response)
            }
        } catch (ex: Exception) {
            logError("Could not track an OkHttp3 response", ex)
        } catch (error: NoSuchMethodError) {
            logError(
                "Could not track an OkHttp3 response due to a missing method, which could"
                        + " happen if your project uses proguard to remove unused code",
                error
            )
        }
        return response
    }

    private fun trackRequest(request: Request): HttpConnectionTracker {
        val callstack = getOkHttpCallStack(request.javaClass.getPackage().name)
        val tracker = trackerFactory.trackConnection(request.url().toString(), callstack)
        tracker.trackRequest(request.method(), request.headers().toMultimap())
        request.body()?.let { body ->
            val outputStream = tracker.trackRequestBody(createNullOutputStream())
            val bufferedSink = Okio.buffer(Okio.sink(outputStream))
            body.writeTo(bufferedSink)
            bufferedSink.close()
        }
        return tracker
    }

    private fun trackResponse(
        tracker: HttpConnectionTracker,
        request: Request,
        response: Response
    ): Response {
        val fields = mutableMapOf<String?, List<String>>()
        fields.putAll(response.headers().toMultimap())
        fields["response-status-code"] = listOf(response.code().toString())
        val body = response.body() ?: throw Exception("No response body found")

        val interceptedResponse = interceptionRuleService.interceptResponse(
            NetworkConnection(request.url().toString(), request.method()),
            NetworkResponse(fields, body.source().inputStream())
        )

        tracker.trackResponseHeaders(interceptedResponse.responseHeaders)
        val source = Okio.buffer(
            Okio.source(tracker.trackResponseBody(interceptedResponse.body))
        )
        val responseBody = ResponseBody.create(
            body.contentType(), body.contentLength(), source
        )
        return response.newBuilder().body(responseBody).build()
    }
}
