/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

import com.android.build.gradle.internal.fixtures.FakeDeprecationReporter
import com.android.resources.Density
import com.google.common.collect.Iterables
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DensitySplitOptionsTest {

    @Test
    fun testDisabled() {
        val options = DensitySplitOptions(FakeDeprecationReporter())

        val values = options.applicableFilters

        assertEquals(0, values.size.toLong())
    }

    @Test
    fun testUniversal() {
        val options = DensitySplitOptions(FakeDeprecationReporter())
        options.isEnable = true

        val values = options.applicableFilters
        // at this time we have 6 densities, maybe more later.
        assertTrue(values.size >= 6)
    }

    @Test
    fun testNonDefaultInclude() {
        val options = DensitySplitOptions(FakeDeprecationReporter())
        options.isEnable = true

        options.include(Density.TV.resourceValue)

        val values = options.applicableFilters

        // test TV is showing up.
        assertTrue(values.contains(Density.TV.resourceValue))
        // test another default value also shows up
        assertTrue(values.contains(Density.HIGH.resourceValue))
    }

    @Test
    fun testUnallowedInclude() {
        val options = DensitySplitOptions(FakeDeprecationReporter())
        options.isEnable = true

        options.include(Density.ANYDPI.resourceValue)

        val values = options.applicableFilters

        // test ANYDPI isn't there.
        assertFalse(values.contains(Density.ANYDPI.resourceValue))

        // test another default value shows up
        assertTrue(values.contains(Density.XHIGH.resourceValue))
    }

    @Test
    fun testExclude() {
        val options = DensitySplitOptions(FakeDeprecationReporter())
        options.isEnable = true

        options.exclude(Density.XXHIGH.resourceValue)

        val values = options.applicableFilters

        assertFalse(values.contains(Density.XXHIGH.resourceValue))
    }

    @Test
    fun testAutoDeprecation() {

        val deprecationReporter = FakeDeprecationReporter()
        val options = DensitySplitOptions(deprecationReporter)
        options.isAuto = true

        val deprecationWarnings = deprecationReporter.deprecationWarnings
        assertThat(deprecationWarnings).hasSize(1)
        assertThat(Iterables.getOnlyElement(deprecationWarnings))
                .contains("DensitySplitOptions.auto")
    }
}