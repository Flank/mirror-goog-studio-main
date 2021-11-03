/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.lint.checks;

import static com.android.tools.lint.checks.IntRangeConstraint.atLeast;
import static com.android.tools.lint.checks.IntRangeConstraint.atMost;
import static com.android.tools.lint.checks.IntRangeConstraint.range;
import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class IntRangeConstraintTest {

    @Test
    public void testDescribe() {
        assertThat(range(1, 5).describe()).isEqualTo("Value must be ≥ 1 and ≤ 5");
        assertThat(atLeast(5).describe()).isEqualTo("Value must be ≥ 5");
        assertThat(atMost(5).describe()).isEqualTo("Value must be ≤ 5");

        assertThat(range(1, 5).describe(3L)).isEqualTo("Value must be ≥ 1 and ≤ 5 (is 3)");
        assertThat(range(1, 5).describe(0L)).isEqualTo("Value must be ≥ 1 (was 0)");
        assertThat(range(1, 5).describe(6L)).isEqualTo("Value must be ≤ 5 (was 6)");
    }

    @Test
    public void testRangeContainsRange() {
        assertThat(range(1, 5).contains(range(2, 4))).isTrue();
        assertThat(range(1, 5).contains(range(2, 5))).isTrue();
        assertThat(range(1, 5).contains(range(2, 6))).isFalse();
        assertThat(range(1, 5).contains(range(1, 5))).isTrue();
        assertThat(range(1, 5).contains(range(0, 5))).isFalse();
    }

    @Test
    public void testMinContainsMin() {
        assertThat(atLeast(2).contains(atLeast(3))).isTrue();
        assertThat(atLeast(2).contains(atLeast(2))).isTrue();
        assertThat(atLeast(2).contains(atLeast(1))).isFalse();
    }

    @Test
    public void testMaxContainsMax() {
        assertThat(atMost(4).contains(atMost(3))).isTrue();
        assertThat(atMost(4).contains(atMost(4))).isTrue();
        assertThat(atMost(4).contains(atMost(5))).isFalse();
    }

    @Test
    public void testInvalid() {
        // Ranges don't contain open intervals
        assertThat(atMost(4).contains(atLeast(1))).isFalse();
        assertThat(atLeast(4).contains(atMost(4))).isFalse();
        assertThat(range(1, 4).contains(atLeast(1))).isFalse();
        assertThat(range(1, 4).contains(atMost(4))).isFalse();
    }

    @Test
    public void testCompareIntWithFloat() {
        assertThat(range(1, 5).contains(FloatRangeConstraint.range(2, 4))).isTrue();
        assertThat(range(1, 5).contains(FloatRangeConstraint.range(2, 5))).isTrue();
        assertThat(range(1, 5).contains(FloatRangeConstraint.range(2, 6))).isFalse();
        assertThat(range(1, 5).contains(FloatRangeConstraint.range(1, 5))).isTrue();
        assertThat(range(1, 5).contains(FloatRangeConstraint.range(0, 5))).isFalse();
    }

    @Test
    public void testCompareFloatWithInt() {
        assertThat(FloatRangeConstraint.range(1, 5).contains(range(2, 4))).isTrue();
        assertThat(FloatRangeConstraint.range(1, 5).contains(range(2, 5))).isTrue();
        assertThat(FloatRangeConstraint.range(1, 5).contains(range(2, 6))).isFalse();
        assertThat(FloatRangeConstraint.range(1, 5).contains(range(1, 5))).isTrue();
        assertThat(FloatRangeConstraint.range(1, 5).contains(range(0, 5))).isFalse();
    }

    @Test
    public void testDescribeDelta() {
        assertThat(range(1, 5).describeDelta(range(2, 6), "`var`", "Argument"))
                .isEqualTo("Argument must be ≥ 1 and ≤ 5 but `var` can be 6");
        assertThat(range(1, 5).describeDelta(range(2, 10), "", ""))
                .isEqualTo("Value must be ≥ 1 and ≤ 5 but can be 10");
        assertThat(range(1, 5).describeDelta(range(0, 5), "", ""))
                .isEqualTo("Value must be ≥ 1 and ≤ 5 but can be 0");
        assertThat(range(1, 5).describeDelta(range(5, 10), "", ""))
                .isEqualTo("Value must be ≥ 1 and ≤ 5 but can be 10");
        assertThat(range(1, 5).describeDelta(range(-4, 1), "", ""))
                .isEqualTo("Value must be ≥ 1 and ≤ 5 but can be -4");
        assertThat(range(1, 5).describeDelta(range(-4, 7), "", ""))
                .isEqualTo("Value must be ≥ 1 and ≤ 5 but can be -4");
        assertThat(range(1, 5).describeDelta(atLeast(1), "", ""))
                .isEqualTo("Value must be ≥ 1 and ≤ 5 but can be > 5");
        assertThat(range(1, 5).describeDelta(atLeast(-5), "", ""))
                .isEqualTo("Value must be ≥ 1 and ≤ 5 but can be -5");
        assertThat(range(1, 5).describeDelta(atMost(5), "", ""))
                .isEqualTo("Value must be ≥ 1 and ≤ 5 but can be < 1");
        assertThat(range(1, 5).describeDelta(atMost(1), "", ""))
                .isEqualTo("Value must be ≥ 1 and ≤ 5 but can be < 1");
        assertThat(range(1, 5).describeDelta(atLeast(5), "", ""))
                .isEqualTo("Value must be ≥ 1 and ≤ 5 but can be > 5");
        assertThat(range(1, 5).describeDelta(FloatRangeConstraint.range(2, 6), "", ""))
                .isEqualTo("Value must be ≥ 1 and ≤ 5 but can be 6");
    }

    @Test
    public void testConversionRounding() {
        assertThat(new IntRangeConstraint(FloatRangeConstraint.range(1, true, 5, true)).describe())
                .isEqualTo("Value must be ≥ 1 and ≤ 5");
        assertThat(new IntRangeConstraint(FloatRangeConstraint.range(1, false, 5, true)).describe())
                .isEqualTo("Value must be ≥ 2 and ≤ 5");
        assertThat(new IntRangeConstraint(FloatRangeConstraint.range(1, true, 5, false)).describe())
                .isEqualTo("Value must be ≥ 1 and ≤ 4");
        assertThat(
                        new IntRangeConstraint(FloatRangeConstraint.range(1, false, 5, false))
                                .describe())
                .isEqualTo("Value must be ≥ 2 and ≤ 4");
    }

    @Test
    public void testIntersection() {
        assertThat(atLeast(2).and(atLeast(3)).toString()).isEqualTo("Value must be ≥ 3");
        assertThat(range(1, 5).and(atLeast(3)).toString()).isEqualTo("Value must be ≥ 3 and ≤ 5");
        assertThat(range(1, 5).and(range(4, 10)).toString()).isEqualTo("Value must be ≥ 4 and ≤ 5");
        assertThat(atLeast(2).and(atMost(2)).toString()).isEqualTo("Value must be 2");
        assertThat(atLeast(2).and(atMost(-1)).toString())
                .isEqualTo("Value must be ≥ 2 and ≤ -1 (not possible)");
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void testRemove() {
        assertThat(atLeast(-1).remove(range(-1, -1)).toString()).isEqualTo("Value must be ≥ 0");
        assertThat(atLeast(2).remove(range(0, 3)).toString()).isEqualTo("Value must be ≥ 4");
        assertThat(atMost(10).remove(range(10, 10)).toString()).isEqualTo("Value must be ≤ 9");
        assertThat(atMost(10).remove(range(9, 12)).toString()).isEqualTo("Value must be ≤ 8");
        assertThat(atLeast(-1).remove(range(-2, -2))).isNull();
        assertThat(atLeast(-1).remove(FloatRangeConstraint.range(-1, -1)).toString())
                .isEqualTo("Value must be > -1.0");
    }

    @Test
    public void testInfinite() {
        assertThat(atLeast(-1).getInfinite()).isTrue();
        assertThat(atMost(1).getInfinite()).isTrue();
        assertThat(range(1, 5).getInfinite()).isFalse();
    }
}
