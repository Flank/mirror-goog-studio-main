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

import com.android.tools.appinspection.network.HttpConnectionTracker
import com.android.tools.appinspection.network.HttpTracker
import com.android.tools.appinspection.network.utils.StudioLog
import com.squareup.okhttp.Interceptor
import com.squareup.okhttp.Request
import com.squareup.okhttp.Response
import com.squareup.okhttp.ResponseBody
import okio.Okio
import java.io.IOException

class OkHttp2Interceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var tracker: HttpConnectionTracker? = null
        try {
            tracker = trackRequest(request)
        } catch (ex: Exception) {
            StudioLog.e("Could not track an OkHttp2 request", ex)
        } catch (error: NoSuchMethodError) {
            StudioLog.e(
                "Could not track an OkHttp2 request due to a missing method, which could"
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
                response = trackResponse(tracker, response)
            }
        } catch (ex: Exception) {
            StudioLog.e("Could not track an OkHttp2 response", ex)
        } catch (error: NoSuchMethodError) {
            StudioLog.e(
                "Could not track an OkHttp2 response due to a missing method, which could"
                        + " happen if your project uses proguard to remove unused code",
                error
            )
        }
        return response
    }

    private fun trackRequest(request: Request): HttpConnectionTracker {
        val callstack = getCallstack(request.javaClass.getPackage().name)
        val tracker = HttpTracker.trackConnection(request.urlString(), callstack)
        tracker.trackRequest(request.method(), request.headers().toMultimap())
        if (request.body() != null) {
            val outputStream = tracker.trackRequestBody(createNullOutputStream())
            val bufferedSink = Okio.buffer(Okio.sink(outputStream))
            request.body().writeTo(bufferedSink)
            bufferedSink.close()
        }
        return tracker
    }

    private fun trackResponse(tracker: HttpConnectionTracker, response: Response): Response {
        val fields = mutableMapOf<String, List<String>>()
        fields.putAll(response.headers().toMultimap())
        fields["response-status-code"] = listOf(response.code().toString())
        tracker.trackResponse("", fields)
        val source = Okio.buffer(
            Okio.source(tracker.trackResponseBody(response.body().source().inputStream()))
        )
        val body = ResponseBody.create(
            response.body().contentType(), response.body().contentLength(), source
        )
        return response.newBuilder().body(body).build()
    }
}
