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

fun MatchingText.matches(text: String): Boolean {
    return this.text.isBlank() || when(type) {
        MatchingText.Type.PLAIN -> this.text == text
        MatchingText.Type.WILD_CARD -> wildCardToRegex(this.text).matches(text)
        MatchingText.Type.REGEX -> Regex(this.text).matches(text)
        else -> false
    }
}

private fun wildCardToRegex(wildCardText: String): Regex {
    val patternBuilder = StringBuilder()
    val segment = StringBuilder()
    // Add previous escaped text and then the wild card.
    val consumeWildCard = { str: String ->
        if (!segment.isEmpty()) {
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
