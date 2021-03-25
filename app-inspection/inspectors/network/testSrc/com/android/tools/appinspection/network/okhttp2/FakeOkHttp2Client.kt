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

package com.android.tools.appinspection.network.okhttp2

import com.squareup.okhttp.Connection
import com.squareup.okhttp.Interceptor
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import com.squareup.okhttp.Response
import java.io.IOException

class FakeOkHttp2Client : OkHttpClient() {

    private val _networkInterceptors = mutableListOf<Interceptor>()

    override fun networkInterceptors() = _networkInterceptors

    fun newCall(request: Request, fakeResponse: Response): FakeCall {
        return FakeCall(this, request, fakeResponse)
    }

    fun triggerInterceptor(
        request: Request,
        response: Response,
        blowUp: Boolean = false
    ): Response {
        return _networkInterceptors.first().intercept(object : Interceptor.Chain {
            override fun request(): Request {
                return request
            }

            override fun proceed(request: Request): Response {
                if (blowUp) {
                    throw IOException("BLOWING UP")
                }
                return response
            }

            override fun connection(): Connection {
                throw NotImplementedError()
            }
        }
        )
    }
}
