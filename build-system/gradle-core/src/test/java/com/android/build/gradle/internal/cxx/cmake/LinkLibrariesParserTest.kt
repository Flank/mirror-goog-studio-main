/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.cmake

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LinkLibrariesParserTest {
    @Test
    fun `can parse trivial list`() {
        assertEquals(listOf("foo"), parseLinkLibraries(
            "foo"
        ))
        assertEquals(listOf("foo"), parseLinkLibraries(
            "   foo    "
        ))
        assertEquals(listOf("foo", "bar", "baz"), parseLinkLibraries(
            "foo bar    baz"
        ))
    }

    @Test
    fun `can parse empty list`() {
        assertEquals(emptyList(), parseLinkLibraries(
            ""
        ))
        assertEquals(emptyList(), parseLinkLibraries(
            "    "
        ))
    }

    @Test
    fun `can parse list with quoted strings`() {
        assertEquals(listOf("foo"), parseLinkLibraries(
            "\"foo\""
        ))
        assertEquals(listOf("  foo  "), parseLinkLibraries(
            "\"  foo  \""
        ))
        assertEquals(listOf("foo bar"), parseLinkLibraries(
            "\"foo bar\""
        ))
        assertEquals(listOf("foo   bar"), parseLinkLibraries(
            "\"foo   bar\""
        ))
        assertEquals(listOf("foo", "bar", "baz"), parseLinkLibraries(
            "foo \"bar\" baz"
        ))
    }

    @Test
    fun `can parse windows paths`() {
        assertEquals(listOf("foo\\bar"), parseLinkLibraries(
            "foo\\bar"
        ))
        assertEquals(listOf("foo\\", "bar"), parseLinkLibraries(
            "foo\\ bar"
        ))
        assertEquals(listOf("foo\\\\bar"), parseLinkLibraries(
            "foo\\\\bar"
        ))
        assertEquals(listOf("C:\\foo\\bar", "C:\\foo bar\\"), parseLinkLibraries(
            "C:\\foo\\bar \"C:\\foo bar\\\""
        ))
    }

    @Test
    fun `unexpected start of quoted string causes an error`() {
        assertFailsWith(IllegalArgumentException::class) {
            parseLinkLibraries("foo\"bar\"")
        }
    }

    @Test
    fun `unterminated quoted string causes an error`() {
        assertFailsWith(IllegalArgumentException::class) {
            parseLinkLibraries("\"foo")
        }
    }
}