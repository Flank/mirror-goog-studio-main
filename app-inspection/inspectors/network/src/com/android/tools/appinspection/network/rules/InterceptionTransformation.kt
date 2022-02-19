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

package com.android.tools.appinspection.network.rules

import studio.network.inspection.NetworkInspectorProtocol.Transformation.BodyReplaced
import studio.network.inspection.NetworkInspectorProtocol.Transformation.HeaderAdded
import studio.network.inspection.NetworkInspectorProtocol.Transformation.HeaderReplaced
import studio.network.inspection.NetworkInspectorProtocol.Transformation.StatusCodeReplaced
import java.io.InputStream

interface InterceptionTransformation {

    fun transform(response: NetworkResponse): NetworkResponse
}

/**
 * A transformation class that changes the status code from response headers.
 */
class StatusCodeReplacedTransformation(
    statusCodeReplaced: StatusCodeReplaced
) : InterceptionTransformation {

    private val targetCodeProto = statusCodeReplaced.targetCode
    private val replacingCode = statusCodeReplaced.newCode

    override fun transform(response: NetworkResponse): NetworkResponse {
        val statusHeader = response.responseHeaders["null"] ?: return response
        val statusLine = statusHeader.getOrNull(0) ?: return response
        if (statusLine.startsWith("HTTP/1.")) {
            val codePos = statusLine.indexOf(' ')
            if (codePos > 0) {
                var phrasePos = statusLine.indexOf(' ', codePos + 1)
                if (phrasePos < 0) {
                    phrasePos = statusLine.length
                }
                val code = statusLine.substring(codePos + 1, phrasePos)
                if (targetCodeProto.matches(code)) {
                    val prefix = statusLine.substring(0, codePos)
                    val suffix = statusLine.substring(phrasePos)
                    val newHeaders = response.responseHeaders.toMutableMap()
                    newHeaders["null"] = listOf("$prefix $replacingCode$suffix")
                    newHeaders[FIELD_RESPONSE_STATUS_CODE] = listOf(replacingCode)
                    return response.copy(responseHeaders = newHeaders)
                }
            }
        }
        return response
    }
}

/**
 * A transformation class that adds a pair of header and value to the response.
 */
class HeaderAddedTransformation(
    private val headerAdded: HeaderAdded
) : InterceptionTransformation {

    override fun transform(response: NetworkResponse): NetworkResponse {
        val headers = response.responseHeaders.toMutableMap()
        val values = headers.getOrPut(headerAdded.name) { listOf() }.toMutableList()
        values.add(headerAdded.value)
        headers[headerAdded.name] = values
        return response.copy(responseHeaders = headers)
    }
}

/**
 * A transformation class that finds the all target name-value pairs and replaces them with a
 * new pair.
 */
class HeaderReplacedTransformation(
    private val headerReplaced: HeaderReplaced
) : InterceptionTransformation {

    override fun transform(response: NetworkResponse): NetworkResponse {
        var anyMatched = false

        // Remove all matched header values.
        val headers = response.responseHeaders.mapValues { (headerKey, headerValues) ->
            if (headerReplaced.targetName.matches(headerKey)) {
                headerValues.filter {
                    val matched = headerReplaced.targetValue.matches(it)
                    if (matched) {
                        anyMatched = true
                    }
                    !matched
                }
            } else headerValues
        }.filter {
            it.value.isNotEmpty()
        }.toMutableMap()

        // Add the replaced header and value.
        if (anyMatched) {
            headers[headerReplaced.newName] =
                (headers[headerReplaced.newName] ?: listOf()) + listOf(headerReplaced.newValue)
        }
        return response.copy(responseHeaders = headers)
    }
}

/**
 * A transformation class that replaces the response body.
 */
class BodyReplacedTransformation(bodyReplaced: BodyReplaced) : InterceptionTransformation {

    private val body: InputStream = bodyReplaced.body.toByteArray().inputStream()

    override fun transform(response: NetworkResponse): NetworkResponse {
        return response.copy(body = body)
    }
}
