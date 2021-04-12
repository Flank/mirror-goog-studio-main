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

package com.android.tools.profgen

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuleFragmentTests {
    @Test
    fun testFoo() {
        assertMatch("foo", "foo")
        assertMatch("foo", "*")
        assertNoMatch("foo", "?")
        assertMatch("x", "?")
        assertNoMatch("/", "?")
        assertNoMatch("foo", "?")
        assertNoMatch("foo/", "foo", "?")
        assertMatch("foox", "foo", "?")
        assertNoMatch("foo/bar", "*")
        assertMatch("foo/bar", "**")
        assertMatch("Lcom/foo/Bar;", "**")
        assertMatch("<init>", "*")
        assertMatch("lambda-0", "*")
        assertNoMatch("foo->bar", "*")
        assertNoMatch("foo->bar", "**")
        assertNoMatch("foo(bar)", "**")
        assertMatch("foo/anything/bar", "foo/", "*", "/bar")
        assertNoMatch("foo/a/b/bar", "foo/", "*", "/bar")
        assertMatch("foo/a/b/bar", "foo/", "**", "/bar")
        assertMatch("foo/a/b/bar", "fo", "?", "/", "**", "/bar")
        assertMatch("fox/a/b/bar", "fo", "?", "/", "**", "/bar")
        assertNoMatch("fo/a/b/bar", "fo", "?", "/", "**", "/bar")
    }

    fun assertMatch(value: String, vararg parts: String) = assertTrue(fragment(*parts).matches(value), "Expected '$value' to match '${parts.joinToString("")}'")
    fun assertNoMatch(value: String, vararg parts: String) = assertFalse(fragment(*parts).matches(value), "Expected '$value' to not match '${parts.joinToString("")}'")
    private fun fragment(vararg parts: String): RuleFragment = RuleFragmentParser(0, parts.map {
            when (it) {
                "*" -> Part.WildPart
                "?" -> Part.WildChar
                "**" -> Part.WildParts
                else -> Part.Exact(it)
            }
        }.toMutableList()
    ).build()
}