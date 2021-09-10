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
import com.android.tools.lint.checks.AnnotationDetector.Companion.ATTR_FROM_INCLUSIVE
import com.android.tools.lint.checks.AnnotationDetector.Companion.ATTR_TO
import com.android.tools.lint.checks.AnnotationDetector.Companion.ATTR_TO_INCLUSIVE
import com.android.tools.lint.detector.api.UastLintUtils.Companion.getAnnotationBooleanValue
import com.android.tools.lint.detector.api.UastLintUtils.Companion.getAnnotationDoubleValue
import com.google.common.annotations.VisibleForTesting
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import kotlin.Double.Companion.NEGATIVE_INFINITY
import kotlin.Double.Companion.POSITIVE_INFINITY

class FloatRangeConstraint private constructor(
    val from: Double,
    val to: Double,
    val fromInclusive: Boolean,
    val toInclusive: Boolean
) : RangeConstraint() {
    fun isValid(value: Double): Boolean {
        return (fromInclusive && value >= from || !fromInclusive && value > from) &&
            (toInclusive && value <= to || !toInclusive && value < to)
    }

    fun describe(argument: Double): String {
        return describe(null, argument)
    }

    @JvmOverloads
    fun describe(argument: UExpression? = null, actualValue: Double? = null): String {
        val sb = StringBuilder(20)
        val valueString = if (argument is ULiteralExpression) {
            // Use source text instead to avoid rounding errors involved in conversion, e.g
            //    Error: Value must be > 2.5 (was 2.490000009536743) [Range]
            //    printAtLeastExclusive(2.49f); // ERROR
            //                          ~~~~~
            var str = argument.asSourceString()
            if (str.endsWith("f") || str.endsWith("F")) {
                str = str.substring(0, str.length - 1)
            }
            str
        } else actualValue?.toString()

        // If we have an actual value, don't describe the full range, only describe
        // the parts that are outside the range
        if (actualValue != null && !isValid(actualValue)) {
            val value: Double = actualValue
            if (from != NEGATIVE_INFINITY) {
                if (to != POSITIVE_INFINITY) {
                    if (fromInclusive && value < from || !fromInclusive && value <= from) {
                        sb.append("Value must be ")
                        if (fromInclusive) {
                            sb.append('\u2265') // >= sign
                        } else {
                            sb.append('>')
                        }
                        sb.append(' ')
                        sb.append(from.toString())
                    } else {
                        assert(toInclusive && value > to || !toInclusive && value >= to)
                        sb.append("Value must be ")
                        if (toInclusive) {
                            sb.append('\u2264') // <= sign
                        } else {
                            sb.append('<')
                        }
                        sb.append(' ')
                        sb.append(to.toString())
                    }
                } else {
                    sb.append("Value must be ")
                    if (fromInclusive) {
                        sb.append('\u2265') // >= sign
                    } else {
                        sb.append('>')
                    }
                    sb.append(' ')
                    sb.append(from.toString())
                }
            } else if (to != POSITIVE_INFINITY) {
                sb.append("Value must be ")
                if (toInclusive) {
                    sb.append('\u2264') // <= sign
                } else {
                    sb.append('<')
                }
                sb.append(' ')
                sb.append(to.toString())
            }
            sb.append(" (was ").append(valueString).append(")")
            return sb.toString()
        }
        if (from != NEGATIVE_INFINITY) {
            if (to != POSITIVE_INFINITY) {
                sb.append("Value must be ")
                if (fromInclusive) {
                    sb.append('\u2265') // >= sign
                } else {
                    sb.append('>')
                }
                sb.append(' ')
                sb.append(from.toString())
                sb.append(" and ")
                if (toInclusive) {
                    sb.append('\u2264') // <= sign
                } else {
                    sb.append('<')
                }
                sb.append(' ')
                sb.append(to.toString())
            } else {
                sb.append("Value must be ")
                if (fromInclusive) {
                    sb.append('\u2265') // >= sign
                } else {
                    sb.append('>')
                }
                sb.append(' ')
                sb.append(from.toString())
            }
        } else if (to != POSITIVE_INFINITY) {
            sb.append("Value must be ")
            if (toInclusive) {
                sb.append('\u2264') // <= sign
            } else {
                sb.append('<')
            }
            sb.append(' ')
            sb.append(to.toString())
        }
        if (valueString != null) {
            sb.append(" (is ").append(valueString).append(')')
        }
        return sb.toString()
    }

    override fun contains(other: RangeConstraint): Boolean? {
        if (other is FloatRangeConstraint) {
            return !(other.from < from || other.to > to) &&
                !(!fromInclusive && other.fromInclusive && other.from == from) &&
                !(!toInclusive && other.toInclusive && other.to == to)
        } else if (other is IntRangeConstraint) {
            return !(other.from < from || other.to > to) &&
                !(!fromInclusive && other.from.toDouble() == from) &&
                !(!toInclusive && other.to.toDouble() == to)
        }
        return null
    }

    override fun toString(): String {
        return describe(null, null)
    }

    companion object {
        @JvmStatic
        fun create(annotation: UAnnotation): FloatRangeConstraint {
            assert(FLOAT_RANGE_ANNOTATION.isEquals(annotation.qualifiedName))
            val from = getAnnotationDoubleValue(annotation, ATTR_FROM, NEGATIVE_INFINITY)
            val to = getAnnotationDoubleValue(annotation, ATTR_TO, POSITIVE_INFINITY)
            val fromInclusive = getAnnotationBooleanValue(annotation, ATTR_FROM_INCLUSIVE, true)
            val toInclusive = getAnnotationBooleanValue(annotation, ATTR_TO_INCLUSIVE, true)
            return FloatRangeConstraint(from, to, fromInclusive, toInclusive)
        }

        @JvmStatic
        @VisibleForTesting
        fun range(from: Double, to: Double): FloatRangeConstraint {
            return FloatRangeConstraint(from, to, true, true)
        }

        @JvmStatic
        @VisibleForTesting
        fun atLeast(from: Double): FloatRangeConstraint {
            return FloatRangeConstraint(from, POSITIVE_INFINITY, true, true)
        }

        @JvmStatic
        @VisibleForTesting
        fun atMost(to: Double): FloatRangeConstraint {
            return FloatRangeConstraint(NEGATIVE_INFINITY, to, true, true)
        }

        @JvmStatic
        @VisibleForTesting
        fun greaterThan(from: Double): FloatRangeConstraint {
            return FloatRangeConstraint(from, POSITIVE_INFINITY, false, true)
        }

        @JvmStatic
        @VisibleForTesting
        fun lessThan(to: Double): FloatRangeConstraint {
            return FloatRangeConstraint(NEGATIVE_INFINITY, to, true, false)
        }
    }
}
