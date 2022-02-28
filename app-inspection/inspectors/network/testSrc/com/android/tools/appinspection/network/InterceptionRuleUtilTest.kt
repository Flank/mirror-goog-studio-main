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

import com.android.tools.appinspection.network.rules.matches
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import studio.network.inspection.NetworkInspectorProtocol.MatchingText

class InterceptionRuleUtilTest {

    @Test
    fun matchingTextMatchesTargets() {
        val plainMatchingText = MatchingText.newBuilder().apply {
            type = MatchingText.Type.PLAIN
            text = "myText"
        }.build()
        assertThat(plainMatchingText.matches("myText")).isTrue()
        assertThat(plainMatchingText.matches("notMyText")).isFalse()

        val wildCardMatchingText = MatchingText.newBuilder().apply {
            type = MatchingText.Type.WILD_CARD
            text = "one?two*three."
        }.build()
        assertThat(wildCardMatchingText.matches("oneotwowdwthree.")).isTrue()
        assertThat(wildCardMatchingText.matches("oneotwowdwthreeX")).isFalse()
        assertThat(wildCardMatchingText.matches("one?two*three.")).isTrue()

        val regexMatchingText = MatchingText.newBuilder().apply {
            type = MatchingText.Type.REGEX
            text = "one[A-Z]two[a-z]three."
        }.build()
        assertThat(regexMatchingText.matches("oneBtwobthreeX")).isTrue()
        assertThat(regexMatchingText.matches("oneBtwoBthreeX")).isFalse()
        assertThat(regexMatchingText.matches("one[A-Z]two[a-z]three.")).isFalse()
    }
}
