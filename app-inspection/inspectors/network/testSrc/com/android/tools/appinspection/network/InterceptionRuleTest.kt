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

import com.android.tools.appinspection.network.rules.BodyModifiedTransformation
import com.android.tools.appinspection.network.rules.BodyReplacedTransformation
import com.android.tools.appinspection.network.rules.HeaderAddedTransformation
import com.android.tools.appinspection.network.rules.HeaderReplacedTransformation
import com.android.tools.appinspection.network.rules.NetworkResponse
import com.android.tools.appinspection.network.rules.StatusCodeReplacedTransformation
import com.android.tools.appinspection.network.rules.matches
import com.android.tools.appinspection.network.rules.wildCardMatches
import com.android.tools.idea.protobuf.ByteString
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import studio.network.inspection.NetworkInspectorProtocol.MatchingText
import studio.network.inspection.NetworkInspectorProtocol.Transformation.BodyModified
import studio.network.inspection.NetworkInspectorProtocol.Transformation.BodyReplaced
import studio.network.inspection.NetworkInspectorProtocol.Transformation.HeaderAdded
import studio.network.inspection.NetworkInspectorProtocol.Transformation.HeaderReplaced
import studio.network.inspection.NetworkInspectorProtocol.Transformation.StatusCodeReplaced
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class InterceptionRuleTest {

    @Test
    fun matchingTextMatchesTargets() {
        assertThat(wildCardMatches("", null)).isTrue()

        val plainMatchingText = MatchingText.newBuilder().apply {
            type = MatchingText.Type.PLAIN
            text = "myText"
        }.build()
        assertThat(plainMatchingText.matches("myText")).isTrue()
        assertThat(plainMatchingText.matches("notMyText")).isFalse()

        val wildcardMatchingPattern = "one?two*three."
        assertThat(wildCardMatches(wildcardMatchingPattern, "oneotwowdwthree.")).isTrue()
        assertThat(wildCardMatches(wildcardMatchingPattern, "oneotwowdwthreeX")).isFalse()
        assertThat(wildCardMatches(wildcardMatchingPattern, "one?two*three.")).isTrue()

        val regexMatchingText = MatchingText.newBuilder().apply {
            type = MatchingText.Type.REGEX
            text = "one[A-Z]two[a-z]three."
        }.build()
        assertThat(regexMatchingText.matches("oneBtwobthreeX")).isTrue()
        assertThat(regexMatchingText.matches("oneBtwoBthreeX")).isFalse()
        assertThat(regexMatchingText.matches("one[A-Z]two[a-z]three.")).isFalse()
    }

    @Test
    fun changeStatusCode() {
        val response = NetworkResponse(
            mapOf("null" to listOf("HTTP/1.0 200 OK")),
            "Body".byteInputStream()
        )
        val proto = StatusCodeReplaced.newBuilder().apply {
            targetCodeBuilder.apply {
                text = "200"
                type = MatchingText.Type.PLAIN
            }
            newCode = "404"
        }.build()
        var transformedResponse = StatusCodeReplacedTransformation(proto).transform(response)
        assertThat(transformedResponse.responseHeaders["null"]!![0]).isEqualTo("HTTP/1.0 404 OK")
        assertThat(transformedResponse.responseHeaders["response-status-code"]!![0]).isEqualTo("404")
        val responseWithoutMessage = NetworkResponse(
            mapOf("null" to listOf("HTTP/1.0 200")),
            "Body".byteInputStream()
        )
        transformedResponse =
            StatusCodeReplacedTransformation(proto).transform(responseWithoutMessage)
        assertThat(transformedResponse.responseHeaders["null"]!![0]).isEqualTo("HTTP/1.0 404")
        assertThat(transformedResponse.responseHeaders["response-status-code"]!![0]).isEqualTo("404")
    }

    @Test
    fun addResponseHeader() {
        val response = NetworkResponse(
            mapOf("null" to listOf("HTTP/1.0 200 OK")),
            "Body".byteInputStream()
        )
        val addingNewHeaderAndValue = HeaderAdded.newBuilder().apply {
            name = "Name"
            value = "Value"
        }.build()
        var transformedResponse =
            HeaderAddedTransformation(addingNewHeaderAndValue).transform(response)
        assertThat(transformedResponse.responseHeaders["Name"]!![0]).isEqualTo("Value")

        val addingValueToExitingHeader = HeaderAdded.newBuilder().apply {
            name = "Name"
            value = "Value2"
        }.build()

        transformedResponse =
            HeaderAddedTransformation(addingValueToExitingHeader).transform(transformedResponse)
        assertThat(transformedResponse.responseHeaders["Name"])
            .containsExactly("Value", "Value2")
    }

    @Test
    fun replaceResponseHeader() {
        val response = NetworkResponse(
            mapOf("header1" to listOf("value1", "value2")),
            "Body".byteInputStream()
        )
        val headerNotMatchedProto = HeaderReplaced.newBuilder().apply {
            targetNameBuilder.apply {
                type = MatchingText.Type.PLAIN
                text = "header"
            }
            targetValueBuilder.apply {
                type = MatchingText.Type.PLAIN
                text = "value"
            }
            newName = "newName"
            newValue = "newValue"
        }.build()
        var transformedResponse =
            HeaderReplacedTransformation(headerNotMatchedProto).transform(response)
        assertThat(transformedResponse.responseHeaders["newName"]).isNull()

        val valueNotMatchedProto = headerNotMatchedProto.toBuilder().apply {
            targetNameBuilder.text = "header1"
        }.build()
        transformedResponse = HeaderReplacedTransformation(valueNotMatchedProto).transform(response)
        assertThat(transformedResponse.responseHeaders["newName"]).isNull()

        val matchedProto = valueNotMatchedProto.toBuilder().apply {
            targetValueBuilder.text = "value1"
        }.build()
        transformedResponse = HeaderReplacedTransformation(matchedProto).transform(response)
        assertThat(transformedResponse.responseHeaders["newName"]!![0]).isEqualTo("newValue")
        assertThat(transformedResponse.responseHeaders["header1"]).containsExactly("value2")

        val multipleMatchedProto = valueNotMatchedProto.toBuilder().apply {
            targetValueBuilder.apply {
                type = MatchingText.Type.REGEX
                text = ".*"
            }
        }.build()
        transformedResponse = HeaderReplacedTransformation(multipleMatchedProto).transform(response)
        assertThat(transformedResponse.responseHeaders["newName"]!![0]).isEqualTo("newValue")
        assertThat(transformedResponse.responseHeaders["header1"]).isNull()
    }

    @Test
    fun modifyResponseBody() {
        val response = NetworkResponse(
            mapOf(
                "null" to listOf("HTTP/1.0 200 OK"),
                "content-type" to listOf("text/html")
            ),
            "BodyXBodyXBodyXBoody".byteInputStream()
        )
        val bodyModifiedProto = BodyModified.newBuilder().apply {
            targetTextBuilder.apply {
                type = MatchingText.Type.PLAIN
                text = "Body"
            }
            newText = "Test"
        }.build()
        var transformedResponse = BodyModifiedTransformation(bodyModifiedProto).transform(response)
        assertThat(transformedResponse.body.reader().use { it.readText() })
            .isEqualTo("TestXTestXTestXBoody")

        val responseWithJsonContent = NetworkResponse(
            mapOf(
                "null" to listOf("HTTP/1.0 200 OK"),
                "content-type" to listOf("application/json")
            ),
            "Body".byteInputStream()
        )
        transformedResponse = BodyModifiedTransformation(bodyModifiedProto)
            .transform(responseWithJsonContent)
        assertThat(transformedResponse.body.reader().use { it.readText() })
            .isEqualTo("Test")
    }

    @Test
    fun modifyResponseBodyWithRegex() {
        val response = NetworkResponse(
            mapOf(
                "null" to listOf("HTTP/1.0 200 OK"),
                "content-type" to listOf("text/html")
            ),
            "BodyXBodyXBodyXBoody".byteInputStream()
        )
        val bodyModifiedRegexProto = BodyModified.newBuilder().apply {
            targetTextBuilder.apply {
                type = MatchingText.Type.REGEX
                text = "Bo*dy"
            }
            newText = "Test"
        }.build()
        val transformedResponse =
            BodyModifiedTransformation(bodyModifiedRegexProto).transform(response)
        assertThat(transformedResponse.body.reader().use { it.readText() })
            .isEqualTo("TestXTestXTestXTest")
    }

    @Test
    fun modifyCompressedResponseBody() {
        val byteOutput = ByteArrayOutputStream()
        GZIPOutputStream(byteOutput).use { stream ->
            stream.write("Body".toByteArray())
        }
        val response = NetworkResponse(
            mapOf(
                "null" to listOf("HTTP/1.0 200 OK"),
                "content-type" to listOf("text/html"),
                "content-encoding" to listOf("gzip")
            ),
            byteOutput.toByteArray().inputStream()
        )
        val bodyModifiedProto = BodyModified.newBuilder().apply {
            targetTextBuilder.apply {
                type = MatchingText.Type.PLAIN
                text = "Body"
            }
            newText = "Test"
        }.build()
        val transformedResponse = BodyModifiedTransformation(bodyModifiedProto).transform(response)

        assertThat(GZIPInputStream(transformedResponse.body).reader().use { it.readText() })
            .isEqualTo("Test")
    }

    @Test
    fun replaceCompressedResponseBody() {
        val byteOutput = ByteArrayOutputStream()
        GZIPOutputStream(byteOutput).use { stream ->
            stream.write("Body".toByteArray())
        }
        val response = NetworkResponse(
            mapOf(
                "null" to listOf("HTTP/1.0 200 OK"),
                "content-type" to listOf("text/html"),
                "content-encoding" to listOf("gzip")
            ),
            byteOutput.toByteArray().inputStream()
        )
        val bodyReplaced = BodyReplaced.newBuilder().apply {
            body = ByteString.copyFrom("Test".toByteArray())
        }.build()
        val transformedResponse = BodyReplacedTransformation(bodyReplaced).transform(response)

        assertThat(GZIPInputStream(transformedResponse.body).reader().use { it.readText() })
            .isEqualTo("Test")
    }

    @Test
    fun replaceTwoResponseBody() {
        val response1 = NetworkResponse(
            mapOf(
                "null" to listOf("HTTP/1.0 200 OK"),
                "content-type" to listOf("text/html")
            ),
            "Body1".toByteArray().inputStream()
        )
        val response2 = NetworkResponse(
            mapOf(
                "null" to listOf("HTTP/1.0 200 OK"),
                "content-type" to listOf("text/html")
            ),
            "Body2".toByteArray().inputStream()
        )
        val bodyReplaced = BodyReplaced.newBuilder().apply {
            body = ByteString.copyFrom("Test".toByteArray())
        }.build()
        val transformation = BodyReplacedTransformation(bodyReplaced)
        val transformedResponse1 = transformation.transform(response1)
        val transformedResponse2 = transformation.transform(response2)
        assertThat(transformedResponse1.body.reader().use { it.readText() })
            .isEqualTo("Test")
        assertThat(transformedResponse2.body.reader().use { it.readText() })
            .isEqualTo("Test")
    }
}
