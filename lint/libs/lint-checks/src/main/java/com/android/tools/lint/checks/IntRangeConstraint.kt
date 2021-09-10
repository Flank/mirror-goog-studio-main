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
package com.android.tools.lint.checks

import com.android.tools.lint.checks.AnnotationDetector.Companion.ATTR_FROM
import com.android.tools.lint.checks.AnnotationDetector.Companion.ATTR_TO
import com.android.tools.lint.detector.api.UastLintUtils.Companion.getAnnotationLongValue
import com.google.common.annotations.VisibleForTesting
import org.jetbrains.uast.UAnnotation
import kotlin.Long.Companion.MAX_VALUE
import kotlin.Long.Companion.MIN_VALUE

class IntRangeConstraint private constructor(
    val from: Long,
    val to: Long
) : RangeConstraint() {
    fun isValid(value: Long): Boolean {
        return value in from..to
    }

    fun describe(): String {
        return describe(null)
    }

    fun describe(actualValue: Long?): String {
        val sb = StringBuilder(20)

        // If we have an actual value, don't describe the full range, only describe
        // the parts that are outside the range
        if (actualValue != null && !isValid(actualValue)) {
            val value: Long = actualValue
            if (value < from) {
                sb.append("Value must be \u2265 ")
                sb.append(from.toString())
            } else {
                assert(value > to)
                sb.append("Value must be \u2264 ")
                sb.append(to.toString())
            }
            sb.append(" (was ").append(value).append(')')
            return sb.toString()
        }
        if (to == MAX_VALUE) {
            sb.append("Value must be \u2265 ")
            sb.append(from.toString())
        } else if (from == MIN_VALUE) {
            sb.append("Value must be \u2264 ")
            sb.append(to.toString())
        } else {
            sb.append("Value must be \u2265 ")
            sb.append(from.toString())
            sb.append(" and \u2264 ")
            sb.append(to.toString())
        }
        if (actualValue != null) {
            sb.append(" (is ").append(actualValue).append(')')
        }
        return sb.toString()
    }

    override fun toString(): String {
        return describe(null)
    }

    override fun contains(other: RangeConstraint): Boolean? {
        if (other is IntRangeConstraint) {
            return other.from >= from && other.to <= to
        } else if (other is FloatRangeConstraint) {
            if (!other.fromInclusive && other.from == from.toDouble() ||
                !other.toInclusive && other.to == to.toDouble()
            ) {
                return false
            }

            // Both represent infinity
            if (other.to > to && !(java.lang.Double.isInfinite(other.to) && to == MAX_VALUE)) {
                return false
            }
            return !(other.from < from && !(java.lang.Double.isInfinite(other.from) && from == MIN_VALUE))
        }
        return null
    }

    companion object {
        @JvmStatic
        fun create(annotation: UAnnotation): IntRangeConstraint {
            assert(INT_RANGE_ANNOTATION.isEquals(annotation.qualifiedName))
            val from = getAnnotationLongValue(annotation, ATTR_FROM, MIN_VALUE)
            val to = getAnnotationLongValue(annotation, ATTR_TO, MAX_VALUE)
            return IntRangeConstraint(from, to)
        }

        @JvmStatic
        @VisibleForTesting
        fun atLeast(value: Long): IntRangeConstraint {
            return IntRangeConstraint(value, MAX_VALUE)
        }

        @JvmStatic
        @VisibleForTesting
        fun atMost(value: Long): IntRangeConstraint {
            return IntRangeConstraint(MIN_VALUE, value)
        }

        @JvmStatic
        @VisibleForTesting
        fun range(from: Long, to: Long): IntRangeConstraint {
            return IntRangeConstraint(from, to)
        }
    }
}
