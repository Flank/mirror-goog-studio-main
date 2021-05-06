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

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MutablePrefixTreeTests {
    @Test
    fun testTrie() {
        val trie = createTestPrefixTree()

        val calledItems = mutableListOf<String>()
        val firstOrNull = trie.firstOrNull("a") {
            calledItems.add(it)
            assertTrue(it.startsWith("a"), "expected $it to start with 'a'")
            false
        }
        assertEquals(4, calledItems.size, "Expected to get called with 4 items prefixed with 'a'")
        assertEquals(firstOrNull, null)
        assertEquals(
            calledItems,
            listOf("a", "ab", "abc", "abd")
        )
    }

    @Test
    fun testIterator() {
        val trie = createTestPrefixTree()
        assertThat(trie.prefixIterator("ab").asSequence().toSet())
            .containsExactly("a", "ab", "abc", "abd")
    }
}

internal fun createTestPrefixTree(): MutablePrefixTree<String> {
    val trie = MutablePrefixTree<String>()
    val items = listOf(
        "a",
        "ab",
        "abc",
        "abd",
        "bcd",
    )
    for (item in items) trie.put(item, item)
    return trie
}
