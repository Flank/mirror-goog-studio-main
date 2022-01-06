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

package com.android.build.gradle.internal.cxx

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExplainDifferencesTest {
    @Test
    fun `basic insert line`() {
        assertThat(
            explainLineDifferences(
            """
            this is line one
            this is line two
            """.trimIndent(),
            """
            this is line one
            this is line two
            this is line three
            """.trimIndent(),
        )
        ).isEqualTo(
            """
            INSERTED this is line three (at line 3)
            """.trimIndent()
        )
    }

    @Test
    fun `basic delete line`() {
        assertThat(
            explainLineDifferences(
            """
            this is line one
            this is line two
            this is line three
            """.trimIndent(),
            """
            this is line one
            this is line two
            """.trimIndent(),
        )
        ).isEqualTo(
            """
            DELETED this is line three (at line 3)
            """.trimIndent()
        )
    }

    @Test
    fun `basic insert line text insert`() {
        assertThat(
            explainLineDifferences(
            """
            this is line one
            this is line two
            this is line three
            """.trimIndent(),
            """
            this is line one
            this is line two-prime
            this is line three
            """.trimIndent(),
        )
        ).isEqualTo(
            """
            REPLACED this is line two (at line 2)
                with this is line two-prime
                                     ^
                                     |
                            [insert]-+
            """.trimIndent()
        )
    }

    @Test
    fun `basic insert line text delete`() {
        assertThat(
            explainLineDifferences(
            """
            this is line one
            this is line two-prime
            this is line three
            """.trimIndent(),
            """
            this is line one
            this is line two
            this is line three
            """.trimIndent(),
        )
        ).isEqualTo(
            """
                                [delete]-+
                                         |
                                         v
                REPLACED this is line two-prime (at line 2)
                    with this is line two
            """.trimIndent()
        )
    }

    @Test
    fun `basic insert line text line changed`() {
        assertThat(
            explainLineDifferences(
            """
            this is line one
            this is line two abc
            this is line three
            """.trimIndent(),
            """
            this is line one
            this is line two xbc
            this is line three
            """.trimIndent(),
        )
        ).isEqualTo(
            """
            REPLACED this is line two abc (at line 2)
                with this is line two xbc
                                      ^
                                      |
                             [change]-+
            """.trimIndent()
        )
    }

    @Test
    fun `insert two lines`() {
        assertThat(
            explainLineDifferences(
            """
            this is line one
            """.trimIndent(),
            """
            this is line one
            this is line two
            this is line three
            """.trimIndent(),
        )
        ).isEqualTo(
            """
            INSERTED this is line two (at line 2)
            INSERTED this is line three (at line 3)
            """.trimIndent()
        )
    }

    @Test
    fun `check distance count`() {
        assertThat(minimumEditDistance("", "")).isEqualTo(0)
        assertThat(minimumEditDistance("", "a")).isEqualTo(1)
        assertThat(minimumEditDistance("a", "")).isEqualTo(1)
        assertThat(minimumEditDistance("a", "b")).isEqualTo(1)
        assertThat(minimumEditDistance("aab", "abb")).isEqualTo(1)
        assertThat(
            minimumEditDistance(
                "nine ladies dancing",
                "ten lords a-leaping")
        ).isEqualTo(13)
        assertThat(
            explainCharDifferences(
                "nine ladies dancing",
                "ten lords a-leaping")
        ).isEqualTo("""
                REPLACED 'n' at 0 with 't'
                REPLACED 'i' at 1 with 'e'
                DELETED e at line 3
                INSERTED o at 5
                REPLACED 'a' at 6 with 'r'
                REPLACED 'i' at 8 with 's'
                and 7 more
            """.trimIndent())
    }
}
