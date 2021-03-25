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

package com.android.tools.appinspection.network.http

import java.net.HttpURLConnection
import java.net.URL

class FakeHttpUrlConnection(
    url: URL,
    fakeResponseBody: ByteArray = ByteArray(0),
    private val requestMethod: String = "GET",
    private val headers: Map<String, List<String>> = mapOf(
        "null" to listOf("HTTP/1.0 200 OK"),
    )
) : HttpURLConnection(url) {

    private val inputStream = FakeInputStream(fakeResponseBody)
    private val outputStream = FakeOutputStream()

    private var dooOutput = false

    override fun connect() = Unit
    override fun disconnect() = Unit

    override fun getDoOutput() = dooOutput

    override fun setDoOutput(dooOutput: Boolean) {
        this.dooOutput = dooOutput
    }

    override fun getHeaderFields() = headers

    override fun getRequestMethod() = requestMethod

    override fun usingProxy() = false

    override fun getInputStream() = inputStream

    override fun getResponseMessage() = "OKAY"

    override fun getResponseCode() = 0

    override fun getOutputStream() = outputStream
}
