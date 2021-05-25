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

package com.android.tools.lint.checks

import com.android.tools.lint.checks.ApiConstraint.Companion.above
import com.android.tools.lint.checks.ApiConstraint.Companion.atLeast
import com.android.tools.lint.checks.ApiConstraint.Companion.atMost
import com.android.tools.lint.checks.ApiConstraint.Companion.below
import com.android.tools.lint.checks.ApiConstraint.Companion.deserialize
import com.android.tools.lint.checks.ApiConstraint.Companion.range
import com.android.tools.lint.checks.ApiConstraint.Companion.same
import com.android.tools.lint.checks.ApiConstraint.Companion.serialize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiConstraintTest {
    @Test
    fun testToString() {
        assertEquals("API level ≥ 15", atLeast(15).toString())
        assertEquals("API level ≥ 16", above(15).toString())
        assertEquals("API level < 15", below(15).toString())
        assertEquals("API level ≥ 15 and API level < 23", range(15, 23).toString())
    }

    @Test
    fun testAtLeast() {
        assertFalse(atLeast(10).matches(8))
        assertFalse(atLeast(10).matches(9))
        assertTrue(atLeast(10).matches(10))
        assertTrue(atLeast(10).matches(11))
    }

    @Test
    fun testAbove() {
        assertFalse(above(10).matches(9))
        assertFalse(above(10).matches(10))
        assertTrue(above(10).matches(11))
        assertTrue(above(10).matches(12))
    }

    @Test
    fun testBelow() {
        assertTrue(below(10).matches(8))
        assertTrue(below(10).matches(9))
        assertFalse(below(10).matches(10))
        assertFalse(below(10).matches(11))
    }

    @Test
    fun testAtMost() {
        assertTrue(atMost(10).matches(8))
        assertTrue(atMost(10).matches(9))
        assertTrue(atMost(10).matches(10))
        assertFalse(atMost(10).matches(11))
    }

    @Test
    fun testRange() {
        assertFalse(range(10, 15).matches(8))
        assertFalse(range(10, 15).matches(9))
        assertTrue(range(10, 15).matches(10))
        assertTrue(range(10, 15).matches(11))
        assertTrue(range(10, 15).matches(14))
        assertFalse(range(10, 15).matches(15))
    }

    @Test
    fun testSame() {
        assertFalse(same(11).matches(9))
        assertFalse(same(11).matches(10))
        assertTrue(same(11).matches(11))
        assertFalse(same(11).matches(12))
        assertFalse(same(11).matches(13))
    }

    @Test
    fun testAlwaysAtLeast() {
        assertTrue(atLeast(21).alwaysAtLeast(21))
        assertTrue(atLeast(21).alwaysAtLeast(23))
        assertFalse(atLeast(21).alwaysAtLeast(20))
        assertFalse(atLeast(21).alwaysAtLeast(20))
        assertFalse(range(15, 21).alwaysAtLeast(23))
        assertFalse(range(15, 21).alwaysAtLeast(19))
        assertFalse(below(11).alwaysAtLeast(9))
    }

    @Test
    fun testNeverAtMost() {
        assertFalse(below(21).neverAtMost(20))
        assertTrue(below(21).neverAtMost(21))
        assertTrue(below(21).neverAtMost(22))
        assertFalse(atLeast(21).neverAtMost(23))
        assertFalse(atLeast(25).neverAtMost(23))
    }

    @Test
    fun testMerge() {
        assertEquals("API level ≥ 26", (atLeast(24) + atLeast(26)).toString())
        assertEquals("API level ≥ 24", (atLeast(24) + atLeast(22)).toString())
        assertEquals("API level < 22", (below(24) + below(22)).toString())
        assertEquals("Nothing", (above(24) + below(22)).toString())
    }

    @Test
    fun testInvert() {
        assertEquals("API level ≥ 24", (!below(24)).toString())
        assertEquals("API level ≥ 24", atLeast(24).toString())
        assertEquals("API level < 24", (!atLeast(24)).toString())
        assertEquals("API level ≥ 24", (!(!atLeast(24))).toString())
    }

    @Test
    fun testSerialization() {
        assertEquals("1d001a", serialize(atLeast(26) + atMost(28)))
        assertEquals("API level ≥ 26 and API level < 29", deserialize("1d001a").toString())
        assertEquals("7fff001a", serialize(atLeast(26)))
        assertEquals("API level ≥ 26", deserialize("7fff001a").toString())
    }
}
