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

@file:JvmName("LintTestUtils")

package com.android.tools.lint.checks.infrastructure

import org.junit.Assert.assertFalse
import org.junit.Assert.fail

// Misc utilities to help writing lint tests

/**
 * Ensures that the comparator for the given comparable [list], the
 * comparison is transitive.
 *
 * (Note: This is inefficient (n^3) so is only meant for tests to do
 * some basic validation of comparison operators. As an example, the
 * ApiClassTest takes 100 randomly chosen elements from the large list
 * and checks those, with a different seed each time.)
 *
 * TODO: See
 *     https://r8-review.googlesource.com/c/r8/+/60142/1/src/main/java/com/android/tools/r8/utils/ListUtils.java#225
 *     for an O(n^2) implementation
 */
fun <T : Comparable<T>> checkTransitiveComparator(list: List<T>) {
    // TODO: Consider caching the comparisons of all the pairs in the list
    for (i in list.indices) {
        for (j in list.indices) {
            for (k in list.indices) {
                val x = list[i]
                val y = list[j]
                val z = list[k]
                val a = Integer.signum(x.compareTo(y))
                val b = Integer.signum(y.compareTo(z))
                val c = Integer.signum(x.compareTo(z))

                if (a != -Integer.signum(y.compareTo(x))) {
                    fail("x.compareTo(y) != -y.compareTo(x) for x=$x and y=$y")
                }
                if (b != -Integer.signum(z.compareTo(y))) {
                    fail("x.compareTo(y) != -y.compareTo(z) for x=$y and y=$z")
                }
                if (a != 0 && b != 0) {
                    if (a == b && c != a) {
                        if (!(x > y && y > z && x > z)) {
                            fail("Not true that when x > y and y > z, then x > z for x = $x, y = $y, z = $z\n")
                        } else {
                            assertFalse(x < y && y < x && x < z)
                            fail("Not true that when x < y and y < z, then x < z for x = $x, y = $y, z = $z\n")
                        }
                    }
                } else if (a == b) {
                    if (c != 0) {
                        fail("\nEquality not transitive: Not true that x == y and y == z, then x = y for x = $x, y = $y, z = $z\n")
                    }
                } else if (a != 0) {
                    if (c != a) {
                        if (!(x < y && y == z && x < z)) {
                            fail("Not true that when x < y and y == z, then x < z for x = $x, y = $y, z = $z\n")
                        } else {
                            assertFalse(x > y && y == z && x > z)
                            fail("Not true that when x > y and y == z, then x > z for x = $x, y = $y, z = $z\n")
                        }
                    }
                } else if (b != 0) {
                    if (c != b) {
                        fail("\nEither\n  x == y && y < z => x < z\nor\n  x == y && y > z => x > z\nis not true for x = $x, y = $y, z = $z")
                        if (!(x == y && y < z && x < z)) {
                            fail("Not true that when x == y and y < z, then x < z for x = $x, y = $y, z = $z\n")
                        } else {
                            assertFalse(x == y && y > z && x > z)
                            fail("Not true that when x == y and y > z, then x > z for x = $x, y = $y, z = $z\n")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Checks transitivity for a given [comparator] over the given [list].
 */
fun <T> checkTransitiveComparator(list: List<T>, comparator: Comparator<T>) {
    class Wrapper(val item: T) : Comparable<Wrapper> {
        override fun toString(): String = item.toString()
        override fun compareTo(other: Wrapper): Int {
            return comparator.compare(this.item, other.item)
        }
    }
    checkTransitiveComparator(list.map { Wrapper(it) })
}
