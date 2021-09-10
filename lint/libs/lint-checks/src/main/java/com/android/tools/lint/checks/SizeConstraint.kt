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

import com.android.SdkConstants
import com.android.tools.lint.detector.api.UastLintUtils.Companion.getAnnotationLongValue
import com.google.common.annotations.VisibleForTesting
import com.intellij.psi.CommonClassNames
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UExpression

internal class SizeConstraint private constructor(
    val exact: Long,
    val min: Long,
    val max: Long,
    val multiple: Long
) : RangeConstraint() {
    override fun toString(): String {
        return describe(null, null, null)
    }

    fun isValid(actual: Long): Boolean {
        if (exact != -1L) {
            if (exact != actual) {
                return false
            }
        } else if (actual < min || actual > max || actual % multiple != 0L) {
            return false
        }
        return true
    }

    fun describe(argument: Long): String {
        return describe(null, null, argument)
    }

    @JvmOverloads
    fun describe(
        argument: UExpression? = null,
        unit: String? = null,
        actualValue: Long? = null
    ): String {
        val actualUnit = unit ?: if (argument?.getExpressionType() != null &&
            argument.getExpressionType()?.canonicalText == CommonClassNames.JAVA_LANG_STRING
        ) {
            "Length"
        } else {
            "Size"
        }

        if (actualValue != null && !isValid(actualValue)) {
            val actual: Long = actualValue
            if (exact != -1L) {
                if (exact != actual) {
                    return "Expected $actualUnit $exact (was $actual)"
                }
            } else if (actual < min || actual > max) {
                val sb = StringBuilder(20)
                if (actual < min) {
                    sb.append("Expected ").append(actualUnit).append(" \u2265 ")
                    sb.append(min.toString())
                } else {
                    assert(actual > max)
                    sb.append("Expected ").append(actualUnit).append(" \u2264 ")
                    sb.append(max.toString())
                }
                sb.append(" (was ").append(actual).append(')')
                return sb.toString()
            } else if (actual % multiple != 0L) {
                return "Expected $actualUnit to be a multiple of $multiple (was $actual " +
                    "and should be either ${actual / multiple * multiple} or ${(actual / multiple + 1) * multiple})"
            }
        }
        val sb = StringBuilder(20)
        sb.append(actualUnit)
        sb.append(" must be")
        if (exact != -1L) {
            sb.append(" exactly ")
            sb.append(exact.toString())
            return sb.toString()
        }
        var continued = true
        if (min != Long.MIN_VALUE && max != Long.MAX_VALUE) {
            sb.append(" at least ")
            sb.append(min.toString())
            sb.append(" and at most ")
            sb.append(max.toString())
        } else if (min != Long.MIN_VALUE) {
            sb.append(" at least ")
            sb.append(min.toString())
        } else if (max != Long.MAX_VALUE) {
            sb.append(" at most ")
            sb.append(max.toString())
        } else {
            continued = false
        }
        if (multiple != 1L) {
            if (continued) {
                sb.append(" and")
            }
            sb.append(" a multiple of ")
            sb.append(multiple.toString())
        }
        if (actualValue != null) {
            sb.append(" (was ").append(actualValue).append(')')
        }
        return sb.toString()
    }

    override fun contains(other: RangeConstraint): Boolean? {
        if (other is SizeConstraint) {
            if (exact != -1L && other.exact != -1L) {
                return exact == other.exact
            }
            if (multiple != 1L) {
                if (other.exact != -1L) {
                    if (other.exact % multiple != 0L) {
                        return false
                    }
                } else if (other.multiple % multiple != 0L) {
                    return false
                }
            }
            return if (other.exact != -1L) {
                other.exact in min..max
            } else
                other.min >= min && other.max <= max
        }
        return null
    }

    companion object {
        @JvmStatic
        fun create(annotation: UAnnotation): SizeConstraint {
            assert(SIZE_ANNOTATION.isEquals(annotation.qualifiedName))
            val exact = getAnnotationLongValue(annotation, SdkConstants.ATTR_VALUE, -1)
            val min = getAnnotationLongValue(annotation, AnnotationDetector.ATTR_MIN, Long.MIN_VALUE)
            val max = getAnnotationLongValue(annotation, AnnotationDetector.ATTR_MAX, Long.MAX_VALUE)
            val multiple = getAnnotationLongValue(annotation, AnnotationDetector.ATTR_MULTIPLE, 1)
            return SizeConstraint(exact, min, max, multiple)
        }

        @JvmStatic
        @VisibleForTesting
        fun exactly(value: Long): SizeConstraint {
            return SizeConstraint(value, Long.MIN_VALUE, Long.MAX_VALUE, 1)
        }

        @JvmStatic
        @VisibleForTesting
        fun atLeast(value: Long): SizeConstraint {
            return SizeConstraint(-1, value, Long.MAX_VALUE, 1)
        }

        @JvmStatic
        @VisibleForTesting
        fun atMost(value: Long): SizeConstraint {
            return SizeConstraint(-1, Long.MIN_VALUE, value, 1)
        }

        @JvmStatic
        @VisibleForTesting
        fun range(from: Long, to: Long): SizeConstraint {
            return SizeConstraint(-1, from, to, 1)
        }

        @JvmStatic
        @VisibleForTesting
        fun multiple(multiple: Int): SizeConstraint {
            return SizeConstraint(-1, Long.MIN_VALUE, Long.MAX_VALUE, multiple.toLong())
        }

        @JvmStatic
        @VisibleForTesting
        fun rangeWithMultiple(from: Long, to: Long, multiple: Int): SizeConstraint {
            return SizeConstraint(-1, from, to, multiple.toLong())
        }

        @VisibleForTesting
        fun minWithMultiple(from: Long, multiple: Int): SizeConstraint {
            return SizeConstraint(-1, from, Long.MAX_VALUE, multiple.toLong())
        }
    }
}
