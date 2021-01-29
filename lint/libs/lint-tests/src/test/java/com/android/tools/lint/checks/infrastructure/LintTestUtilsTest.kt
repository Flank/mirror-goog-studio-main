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

package com.android.tools.lint.checks.infrastructure

import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.AssertionError

class LintTestUtilsTest {
    @Test
    fun testOk() {
        val list = listOf("def", "abc", "ghijklm")
        checkTransitiveComparator(list)
    }

    @Test
    fun testNotTransitive() {
        class Test(val v: Int) : Comparable<Test> {
            override fun compareTo(other: Test): Int {
                return v.compareTo(-other.v)
            }

            override fun toString(): String {
                return v.toString()
            }
        }
        val list = listOf(Test(1), Test(2), Test(3), Test(4))
        try {
            checkTransitiveComparator(list)
        } catch (a: AssertionError) {
            assertEquals("x.compareTo(y) != -y.compareTo(x) for x=1 and y=1", a.message)
        }
    }

    @Test
    fun testNotTransitiveComparator() {
        val comparator = object : Comparator<Int> {
            override fun compare(o1: Int, o2: Int): Int {
                return o1.compareTo(-o2)
            }
        }
        val list = listOf(1, 2, 3, 4)
        try {
            checkTransitiveComparator(list, comparator)
        } catch (a: AssertionError) {
            assertEquals("x.compareTo(y) != -y.compareTo(x) for x=1 and y=1", a.message)
        }
    }
}
