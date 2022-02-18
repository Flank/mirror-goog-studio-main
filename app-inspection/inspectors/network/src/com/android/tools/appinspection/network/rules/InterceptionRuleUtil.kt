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

const val FIELD_RESPONSE_STATUS_CODE = "response-status-code"

fun MatchingText.matches(text: String): Boolean {
    return this.text.isBlank() || when (type) {
        MatchingText.Type.PLAIN -> this.text == text
        MatchingText.Type.REGEX -> Regex(this.text).matches(text)
        else -> false
    }
}

fun wildCardMatches(pattern: String, text: String): Boolean {
    return (pattern.isBlank()) || wildCardToRegex(pattern).matches(text)
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
