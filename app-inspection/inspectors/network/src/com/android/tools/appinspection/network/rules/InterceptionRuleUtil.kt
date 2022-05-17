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

import studio.network.inspection.NetworkInspectorProtocol.MatchingText
import studio.network.inspection.NetworkInspectorProtocol.MatchingText.Type.PLAIN
import studio.network.inspection.NetworkInspectorProtocol.MatchingText.Type.REGEX
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

const val MATCHING_TEXT_TYPE_UNEXPECTED = "MatchingText type not expected"
const val FIELD_CONTENT_TYPE = "content-type"
const val FIELD_CONTENT_ENCODING = "content-encoding"
const val FIELD_RESPONSE_STATUS_CODE = "response-status-code"

/**
 * Return true if this pattern matches the [text]. An undefined [MatchingText] matches all texts.
 */
fun MatchingText.matches(text: String?): Boolean = when (type) {
    PLAIN -> this.text == text
    REGEX -> text?.let { Regex(this.text).matches(text) } == true
    else -> true
}

/**
 * Return true if a [pattern] matches the [text]. An empty pattern matches all texts.
 */
fun wildCardMatches(pattern: String, text: String?): Boolean {
    return (pattern.isBlank()) || text?.let { wildCardToRegex(pattern).matches(text) } == true
}

/**
 * Return the [Regex] for a [MatchingText].
 */
fun MatchingText.toRegex(): Regex = when (type) {
    PLAIN -> Regex.fromLiteral(text)
    REGEX -> Regex(text)
    else -> throw RuntimeException(MATCHING_TEXT_TYPE_UNEXPECTED)
}

fun isContentCompressed(response: NetworkResponse): Boolean {
    val contentHeaderValues = response.responseHeaders[FIELD_CONTENT_ENCODING] ?: return false
    return contentHeaderValues.any { it.toLowerCase().contains("gzip") }
}

fun ByteArray.gzip(): ByteArray {
    val outputStream = ByteArrayOutputStream()
    GZIPOutputStream(outputStream).use { it.write(this) }
    return outputStream.toByteArray()
}

private fun wildCardToRegex(wildCardText: String): Regex {
    val patternBuilder = StringBuilder()
    val segment = StringBuilder()
    // Add previous escaped text and then the wild card.
    val consumeWildCard = { str: String ->
        if (segment.isNotEmpty()) {
            patternBuilder.append(Regex.escape(segment.toString()))
            segment.clear()
        }
        patternBuilder.append(str)
    }
    for (c in wildCardText) {
        when (c) {
            '?' -> consumeWildCard(".")
            '*' -> consumeWildCard(".*")
            else -> segment.append(c)
        }
    }
    // Add the last segment of escaped text.
    consumeWildCard("")
    return Regex(patternBuilder.toString())
}
