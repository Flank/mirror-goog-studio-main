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

package com.android.build.gradle.internal.dsl.decorator

import com.android.build.gradle.internal.dsl.AgpDslLockedException
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.test.assertFailsWith

internal class LockableMapTest {

    private val lockableMap = LockableMap<String, String>("stringsMap")

    @Test
    fun `check behaves as a map`() {
        lockableMap["one"] = "1"
        lockableMap["two"] = "2"

        assertThat(lockableMap).containsExactly("one", "1", "two", "2")
        assertThat(lockableMap.size).isEqualTo(2)

        lockableMap["one"] = "11"
        lockableMap.remove("two")
        lockableMap["three"] = "3"
        lockableMap.remove("three", "3")
        lockableMap.remove("N/A", "doesn't exist")
        lockableMap.putIfAbsent("one", "random")

        assertThat(lockableMap).containsExactly("one", "11")
        assertThat(lockableMap.size).isEqualTo(1)

        lockableMap.clear()
        assertThat(lockableMap).isEmpty()
        assertThat(lockableMap.size).isEqualTo(0)

        lockableMap["one"] = "1"
        lockableMap["two"] = "2"

        lockableMap.entries.remove(lockableMap.entries.find { it.key == "one" })
        assertThat(lockableMap).containsExactly("two", "2")
        assertThat(lockableMap.size).isEqualTo(1)
    }

    @Test
    fun `check map modification after locking`() {
        lockableMap.putAll(listOf(Pair("one", "one"), Pair("two", "two"), Pair("three", "three")))
        lockableMap += listOf(Pair("one", "1"), Pair("two", "2"), Pair("three", "3"))
        lockableMap += Pair("four", "4")
        lockableMap -= "two"
        lockableMap -= "five"

        lockableMap.lock()
        assertThat(lockableMap).containsExactly("one", "1", "three", "3", "four", "4")

        fun checkModificationFails(action: () -> Unit) {
            val failure = assertFailsWith<AgpDslLockedException> {
                action.invoke()
            }
            assertThat(failure).hasMessageThat().contains("It is too late to modify stringsMap")
            assertThat(lockableMap).containsExactly("one", "1", "three", "3", "four", "4")
        }

        checkModificationFails { lockableMap["x"] = "y" }
        checkModificationFails { lockableMap += Pair("x", "y") }
        checkModificationFails { lockableMap.getOrPut("x") { "y" } }
        checkModificationFails { lockableMap += listOf(Pair("x", "y"), Pair("y", "x")) }
        checkModificationFails { lockableMap -= "one" }
        checkModificationFails { lockableMap.remove("one", "1") }
        checkModificationFails { lockableMap.clear() }
        checkModificationFails { lockableMap.replace("one", "2") }
        checkModificationFails { lockableMap.replaceAll { _, _ -> "new" } }
        checkModificationFails { lockableMap.putIfAbsent("two", "2") }
        checkModificationFails { lockableMap.computeIfAbsent("two") { "2" } }
        checkModificationFails { lockableMap.computeIfPresent("one") { _, _ -> "2" } }
        checkModificationFails { lockableMap.compute("one") { _, _ -> "2" } }
    }

    @Test
    fun `check map reading after locking`() {
        lockableMap += listOf(Pair("one", "1"), Pair("four", "4"), Pair("three", "3"))
        lockableMap.lock()
        assertThat(lockableMap).containsExactly("one", "1", "three", "3", "four", "4")

        fun doAndCheckContents(action: () -> Unit) {
            action.invoke()
            assertThat(lockableMap).containsExactly("one", "1", "three", "3", "four", "4")
        }

        doAndCheckContents { assertThat(lockableMap["four"]).isEqualTo("4") }
        doAndCheckContents {
            assertThat(lockableMap.getOrDefault("one", "0")).isEqualTo("1")
        }
        doAndCheckContents {
            assertThat(lockableMap.getOrDefault("zero", "0")).isEqualTo("0")
        }
        doAndCheckContents {
            assertThat(lockableMap.getOrElse("zero") { "0" }).isEqualTo("0")
        }
        doAndCheckContents {
            assertThat(lockableMap.filterKeys { it != "one" }.keys).containsExactly("three", "four")
        }
        doAndCheckContents {
            lockableMap.forEach { (k, v) ->
                val expectedValue = when (k) {
                    "one" -> "1"
                    "three" -> "3"
                    "four" -> "4"
                    else -> throw RuntimeException("Unexpected value $k")
                }
                assertThat(v).isEqualTo(expectedValue)
            }
        }
    }

    @Test
    fun `check map entrySet modification after locking`() {
        lockableMap += listOf(Pair("one", "1"), Pair("four", "4"), Pair("three", "3"))
        val entries = lockableMap.entries

        lockableMap.lock()
        assertThat(lockableMap).containsExactly("one", "1", "three", "3", "four", "4")

        fun checkModificationFails(action: () -> Unit) {
            val failure = assertFailsWith<AgpDslLockedException> {
                action.invoke()
            }
            assertThat(failure).hasMessageThat().contains("It is too late to modify stringsMap")
            assertThat(lockableMap).containsExactly("one", "1", "three", "3", "four", "4")
        }

        checkModificationFails { entries.remove(entries.first()) }
        checkModificationFails { entries.iterator().remove() }
        checkModificationFails { entries.clear() }
    }

    @Test
    fun `check map entrySet reading after locking`() {
        lockableMap += listOf(Pair("one", "1"), Pair("four", "4"), Pair("three", "3"))
        lockableMap.lock()
        assertThat(lockableMap).containsExactly("one", "1", "three", "3", "four", "4")

        fun doAndCheckContents(action: () -> Unit) {
            action.invoke()
            assertThat(lockableMap).containsExactly("one", "1", "three", "3", "four", "4")
        }

        doAndCheckContents {
            assertThat(lockableMap.entries.map { it.toPair() })
                .containsExactly(Pair("one", "1"), Pair("three", "3"), Pair("four", "4"))
        }

        doAndCheckContents {
            assertThat(lockableMap.entries.iterator().next().toPair()).isEqualTo(Pair("one", "1"))
        }
    }
}
