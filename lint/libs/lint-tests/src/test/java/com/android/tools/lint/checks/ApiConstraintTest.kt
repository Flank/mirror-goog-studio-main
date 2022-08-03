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

import com.android.tools.lint.detector.api.ApiConstraint.Companion.above
import com.android.tools.lint.detector.api.ApiConstraint.Companion.atLeast
import com.android.tools.lint.detector.api.ApiConstraint.Companion.atMost
import com.android.tools.lint.detector.api.ApiConstraint.Companion.below
import com.android.tools.lint.detector.api.ApiConstraint.Companion.deserialize
import com.android.tools.lint.detector.api.ApiConstraint.Companion.exactly
import com.android.tools.lint.detector.api.ApiConstraint.Companion.not
import com.android.tools.lint.detector.api.ApiConstraint.Companion.range
import com.android.tools.lint.detector.api.ApiConstraint.Companion.serialize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiConstraintTest {
    @Test
    fun testToString() {
        assertEquals(
            "API level = 5 or API level = 9 or API level ≥ 11 and API level < 15 or API level = 25",
            (exactly(5) or exactly(9) or range(11, 15) or exactly(25)).toString()
        )
        assertEquals("API level ≥ 15", atLeast(15).toString())
        assertEquals("API level ≥ 16", above(15).toString())
        assertEquals("API level < 15", below(15).toString())
        assertEquals("API level ≥ 15 and API level < 23", range(15, 23).toString())
        assertEquals("API level ≠ 15", not(15).toString())
        assertEquals("API level ≥ 4 and API level < 6", range(4, 6).toString())
        assertEquals("No API levels", (above(24) and below(22)).toString())
        assertEquals("All API levels", (atLeast(11) or atMost(23)).toString())
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
    fun testExactly() {
        assertFalse(exactly(11).matches(9))
        assertFalse(exactly(11).matches(10))
        assertTrue(exactly(11).matches(11))
        assertFalse(exactly(11).matches(12))
        assertFalse(exactly(11).matches(13))
    }

    @Test
    fun testNot() {
        assertTrue(not(11).matches(10))
        assertTrue(not(11).matches(12))
        assertFalse(not(11).matches(11))

        assertEquals("API level = 11", exactly((11)).toString())
        assertEquals("API level ≠ 11", exactly((11)).not().toString())
        assertEquals("API level = 11", exactly((11)).not().not().toString())
    }

    @Test
    fun testAlwaysAtLeast() {
        assertTrue(atLeast(4).alwaysAtLeast(4))
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
    fun testNotLowerThan() {
        assertTrue(atLeast(24).notLowerThan(24))
        assertTrue(atLeast(24).notLowerThan(23))
        assertTrue(atLeast(24).notLowerThan(22))
        assertTrue(atLeast(24).notLowerThan(23))
        assertTrue(atLeast(24).notLowerThan(22))
        assertFalse(atLeast(24).notLowerThan(25))
        assertFalse(atLeast(24).notLowerThan(26))

        assertFalse(atMost(24).notLowerThan(24))
        assertFalse(atMost(24).notLowerThan(25))
        assertFalse(atMost(24).notLowerThan(10))

        assertTrue(exactly(24).notLowerThan(24))
        assertTrue(exactly(24).notLowerThan(23))
        assertFalse(exactly(24).notLowerThan(25))
    }

    @Test
    fun testCurDevelopment() {
        assertEquals("API level < 10000", below(10000).toString())
    }

    @Test(expected = IllegalStateException::class)
    fun testUnsupportedApiLevel() {
        below(100)
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
        assertEquals("e000000", serialize(atLeast(26) and atMost(28)))
        assertEquals("API level ≥ 26 and API level < 29", deserialize("e000000").toString())
        assertEquals("fffffffffe000000", serialize(atLeast(26)))
        assertEquals("API level ≥ 26", deserialize("fffffffffe000000").toString())
    }

    @Test
    fun testAnd() {
        assertEquals("API level ≥ 23", (atLeast(11) and atLeast(23)).toString())
        assertEquals("API level < 12", (atMost(11) and atMost(23)).toString())
        assertEquals("API level ≥ 11 and API level < 24", (atLeast(11) and atMost(23)).toString())
        assertEquals("API level ≥ 26", (atLeast(24) and atLeast(26)).toString())
        assertEquals("API level ≥ 24", (atLeast(24) and atLeast(22)).toString())
        assertEquals("API level < 22", (below(24) and below(22)).toString())
        assertEquals("No API levels", (above(24) and below(22)).toString())
    }

    @Test
    fun testOr() {
        assertEquals("API level ≥ 11", (atLeast(11) or atLeast(23)).toString())
        assertEquals("API level < 24", (atMost(11) or atMost(23)).toString())
        assertEquals("All API levels", (atLeast(11) or atMost(23)).toString())
    }
}
