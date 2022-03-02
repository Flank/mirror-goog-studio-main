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

package com.android.tools.lint.detector.api

import kotlin.math.max
import kotlin.math.min

/**
 * Expresses an API constraint, such as "API level must be at least 21"
 */
@Suppress("EXPERIMENTAL_FEATURE_WARNING")
inline class ApiConstraint(val bits: Int) {
    /*
     * The API constraint stores the interval [from, to). For example,
     * "if (SDK_INT >= 16) X else Y" would have the range constraint [16,∞) for
     * X and [1,16) for Y. Here ∞ is using the special marker value 0xFFF.
     * The int basically is represented by from in the least significant byte
     * and to in the next byte: from | to << TO_SHIFTS.
     */

    private fun fromInclusive(): Int = bits and FROM_MASK
    private fun toExclusive(): Int = (bits shr TO_SHIFTS) and FROM_MASK

    /** Is the given [apiLevel] valid for this constraint? */
    fun matches(apiLevel: Int): Boolean {
        return apiLevel >= fromInclusive() && apiLevel < toExclusive()
    }

    /**
     * Will this API level or anything higher always match this
     * constraint?
     */
    fun alwaysAtLeast(apiLevel: Int): Boolean {
        return apiLevel >= fromInclusive() && toExclusive() == INFINITY
    }

    /**
     * Will this API level or anything higher never match this
     * constraint?
     */
    fun neverAtMost(apiLevel: Int): Boolean {
        return apiLevel >= toExclusive()
    }

    /**
     * Combines two API constraints. The result may be empty. (This
     * basically is an intersection operator.)
     */
    operator fun plus(other: ApiConstraint): ApiConstraint {
        val from = max(fromInclusive(), other.fromInclusive())
        val to = min(toExclusive(), other.toExclusive())
        return if (from >= to) {
            createConstraint(from, from)
        } else {
            createConstraint(from, to)
        }
    }

    /** Inverts the given constraint, e.g. X < 20 becomes X >= 20. */
    operator fun not(): ApiConstraint {
        val from = fromInclusive()
        val to = toExclusive()
        return createConstraint(
            if (to == INFINITY) 1 else to,
            if (from == 1) INFINITY else from
        )
    }

    /**
     * Returns a new constraint which takes the union of the two
     * constraints.
     */
    infix fun or(other: ApiConstraint?): ApiConstraint {
        other ?: return this
        val from = min(fromInclusive(), other.fromInclusive())
        val to = max(toExclusive(), other.toExclusive())
        return range(from, to)
    }

    /**
     * Returns a new constraint which takes the intersection of the two
     * constraints.
     */
    infix fun and(other: ApiConstraint?): ApiConstraint {
        other ?: return this
        val from = max(fromInclusive(), other.fromInclusive())
        val to = max(min(toExclusive(), other.toExclusive()), from)
        return range(from, to)
    }

    /**
     * Adjusts the API level range by adding the given adjustments
     * (which can be negative) to the from and to levels.
     */
    fun adjust(fromAdjustment: Int, toAdjustment: Int): ApiConstraint {
        val from = fromInclusive()
        val to = toExclusive()

        val newFrom = max(1, from + fromAdjustment)
        val newTo = if (to == INFINITY) INFINITY else max(newFrom, to + toAdjustment)
        return createConstraint(newFrom, newTo)
    }

    override fun toString(): String {
        val from = fromInclusive()
        val to = toExclusive()
        return when {
            from == to -> "Nothing"
            to == INFINITY -> "API level ≥ $from"
            from == 1 -> "API level < $to"
            else -> "API level ≥ $from and API level < $to"
        }
    }

    companion object {
        // Large enough to hold the 10000 value used by Build.VERSION_CODES.CUR_DEVELOPMENT
        private const val INFINITY = 0x7FFF
        private const val TO_SHIFTS = 16
        private const val FROM_MASK = 0xFFFF

        private fun createConstraint(
            fromInclusive: Int? = null,
            toExclusive: Int? = null
        ): ApiConstraint {
            val from = fromInclusive ?: 1
            val to = toExclusive ?: INFINITY
            return ApiConstraint(from or (to shl TO_SHIFTS))
        }

        /**
         * Create constraint where the API level is at least [apiLevel]
         */
        fun atLeast(apiLevel: Int): ApiConstraint {
            assert(apiLevel < INFINITY)
            return createConstraint(fromInclusive = apiLevel)
        }

        /**
         * Create constraint where the API level is less than [apiLevel]
         */
        fun below(apiLevel: Int): ApiConstraint {
            assert(apiLevel < INFINITY)
            return createConstraint(toExclusive = apiLevel)
        }

        /**
         * Create constraint where the API level is higher than
         * [apiLevel]
         */
        fun above(apiLevel: Int): ApiConstraint {
            assert(apiLevel < INFINITY)
            return createConstraint(fromInclusive = apiLevel + 1)
        }

        /**
         * Create constraint where the API level is lower than or equal
         * to [apiLevel]
         */
        fun atMost(apiLevel: Int): ApiConstraint {
            assert(apiLevel < INFINITY)
            return createConstraint(toExclusive = apiLevel + 1)
        }

        /**
         * Create constraint where the API level is in the given range.
         */
        fun range(fromInclusive: Int, toExclusive: Int): ApiConstraint {
            return createConstraint(fromInclusive, toExclusive)
        }

        /**
         * Creates an API constraint for API level equals a specific
         * level.
         */
        fun same(apiLevel: Int): ApiConstraint {
            return createConstraint(apiLevel, apiLevel + 1)
        }

        /**
         * Serializes the given constraint into a String, which can
         * later be retrieved by calling [deserialize].
         */
        fun serialize(constraint: ApiConstraint): String {
            return Integer.toHexString(constraint.bits)
        }

        /**
         * Deserializes a given string (previously computed by
         * [serialize] into the corresponding constraint.
         */
        fun deserialize(s: String): ApiConstraint {
            return ApiConstraint(s.toInt(16))
        }
    }
}
