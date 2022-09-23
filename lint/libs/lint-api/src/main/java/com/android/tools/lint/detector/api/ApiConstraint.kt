/*
 * Copyright (C) 2022 The Android Open Source Project
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

/**
 * Expresses an API constraint, such as "API level must be at least 21"
 */
@JvmInline
value class ApiConstraint(
    /**
     * The bits here represent API levels; bit 0 is API level 1, bit 1
     * is API level 2 etc, all the way up. The very last bit represents
     * infinity.
     */
    private val bits: ULong
) {
    fun fromInclusive(): Int {
        val bit = bits.lowestBitSet()
        if (bit == -1) {
            return INFINITY // nonsensical; you called fromInclusive() on Nothing
        }
        return fromInternalApiLevel(bit)
    }

    fun toExclusive(): Int {
        val bit = bits.highestBitSet()
        if (bit == -1) {
            return INFINITY // nonsensical; you called toExclusive() on Nothing
        }
        return fromInternalApiLevel(bit + 1) // it's exclusive; bit is the inclusive position
    }

    /** Is the given [apiLevel] valid for this constraint? */
    fun matches(apiLevel: Int): Boolean {
        return (bits and getApiLevelMask(apiLevel)) != 0UL
    }

    /**
     * Will this API level or anything higher always match this
     * constraint?
     *
     * For example, if we know from minSdkVersion that SDK_INT >= 32,
     * and we see a check if SDK_INT is >= 21, that check will always
     * be true. That's what this method is for; this [ApiConstraint]
     * is the SDK_INT check, and the passed in [apiLevel] represents
     * the minimum value of SDK_INT (the known constraint from
     * minSdkVersion).
     */
    fun alwaysAtLeast(apiLevel: Int): Boolean {
        val minSdk = atLeast(apiLevel)
        return minSdk.bits and bits == minSdk.bits
    }

    /**
     * Returns true if this API constraint includes any versions that
     * are higher than the given [apiLevel].
     */
    fun everHigher(apiLevel: Int): Boolean {
        val minSdk = atLeast(apiLevel + 1)
        return (minSdk.bits and bits) != 0UL
    }

    /**
     * Will this API level or anything higher never match this
     * constraint?
     *
     * For example, if we know from minSdkVersion that SDK_INT
     * >= 32, and we see a check if SDK_INT is <= 21, that check
     * will never be true. That's what this method is for; this
     * [ApiConstraint] is the minSdkVersion requirement, and the passed
     * in [apiLevel] represents the maximum value of the at-most check.
     */
    fun neverAtMost(apiLevel: Int): Boolean {
        val minSdk = atLeast(apiLevel)
        return minSdk.bits and bits == 0UL
    }

    /**
     * True if this constraint is *not* lower than the given API level.
     * This means that an API reference to [apiLevel] is safe if the API
     * level is known to be this constraint.
     */
    fun notLowerThan(apiLevel: Int): Boolean {
        val bit = toInternalApiLevel(apiLevel)
        val intersection = bits and getBitMask(bit)
        return intersection == 0UL || intersection == getApiLevelMask(apiLevel)
    }

    /** Inverts the given constraint, e.g. X < 20 becomes X >= 20. */
    operator fun not(): ApiConstraint {
        return ApiConstraint(bits.inv())
    }

    /**
     * Returns a new constraint which takes the union of the two
     * constraints.
     */
    infix fun or(other: ApiConstraint): ApiConstraint {
        return ApiConstraint(bits or other.bits)
    }

    /**
     * Returns a new constraint which takes the intersection of the two
     * constraints.
     */
    infix fun and(other: ApiConstraint): ApiConstraint {
        return ApiConstraint(bits and other.bits)
    }

    private fun includesApiLevel(level: Int): Boolean {
        return bits and (1UL shl level) != 0UL
    }

    override fun toString(): String {
        if (bits == NO_LEVELS.bits) {
            return "No API levels"
        } else if (bits == ALL_LEVELS.bits) {
            return "All API levels"
        }

        // Simple != x ? See if the negation of the number (== x) has a single bit
        val negated = bits.inv()
        var lowest = negated and (-negated.toLong()).toULong()
        if (lowest == negated) {
            var from = 1
            while (true) {
                if (lowest == 1UL || lowest == 0UL) {
                    break
                }
                lowest = lowest shr 1
                from++
            }
            return "API level ≠ $from"
        }

        val spans = mutableListOf<String>()
        var next = 0
        val max = 64
        while (true) {
            // Find next span
            while (next < max && !includesApiLevel(next)) {
                next++
            }
            if (next == max) {
                break
            }

            val start = fromInternalApiLevel(next++)
            // Find next span
            while (next < max && includesApiLevel(next)) {
                next++
            }

            val end = fromInternalApiLevel(next)
            val startString = start.toString()
            val endString = end.toString()
            if (end == start + 1) {
                spans.add("API level = $startString")
            } else if (start == 1) {
                spans.add("API level < $endString")
            } else if (end == max || next == max) {
                spans.add("API level ≥ $startString")
                break
            } else {
                spans.add("API level ≥ $startString and API level < $endString")
            }
        }

        return spans.joinToString(" or ")
    }

    companion object {
        // The level in the 0th bit. Later, when we get closer to the [MAX] level, we can bump this,
        // since small API levels will not be useful (in fact nearly all apps have minSdkVersion 15+ already,
        // so the first 15 levels aren't very interesting; the main impact here is on all the older unit tests
        // which were written for API detector to target then-new APIs.
        private const val FIRST_LEVEL = 1

        // Represents the end point in an open interval corresponding to say "API > 26", e.g. [26, ∞)
        private const val INFINITY = 65 // the final bit (64) is usd to mark infinity; this is an exclusive index

        /** Marker for the special API level CUR_DEVELOPMENT. */
        private const val CUR_DEVELOPMENT_MARKER = 62

        /** API Value corresponding to [CUR_DEVELOPMENT_MARKER]. */
        private const val CUR_DEVELOPMENT = 10000

        /** Largest API level we allow being set */
        private const val MAX_LEVEL = 61

        val ALL_LEVELS = ApiConstraint(0xffffffffffffffffUL)
        val NO_LEVELS = ApiConstraint(0UL)

        // for debugging
        private fun ULong.binary(): String {
            val set = this.toString(2)
            return "0".repeat(64 - set.length) + set
        }

        private fun getApiLevelMask(apiLevel: Int): ULong {
            return 1UL shl toInternalApiLevel(apiLevel)
        }

        private fun toInternalApiLevel(level: Int?, default: Int): Int {
            return toInternalApiLevel(level ?: return default - FIRST_LEVEL)
        }
        private fun toInternalApiLevel(level: Int): Int {
            return if (level <= MAX_LEVEL) {
                level - FIRST_LEVEL
            } else if (level == CUR_DEVELOPMENT || level == CUR_DEVELOPMENT + 1) { // +1: 10001 is exclusive offset for exactly(10000)
                CUR_DEVELOPMENT_MARKER + (level - CUR_DEVELOPMENT) - FIRST_LEVEL
            } else {
                error("Unsupported API level $level")
            }
        }

        private fun fromInternalApiLevel(level: Int): Int {
            val userLevel = level + FIRST_LEVEL
            return if (userLevel == CUR_DEVELOPMENT_MARKER || userLevel == CUR_DEVELOPMENT_MARKER + 1) {
                CUR_DEVELOPMENT + (userLevel - CUR_DEVELOPMENT_MARKER)
            } else {
                userLevel
            }
        }

        /**
         * Gets a bit mask with all the bits up to (but not including)
         * [bit] set.
         *
         * This is used to quickly create constraint vectors. For
         * example, to create the constraint "less than N" we just
         * look up `longArray[N]`, and to take the constraint
         * "greater than or equals to N" we just reverse the bits of
         * `longArray[N]`. To set all the bits from A to B we take
         * `longArray[B] & ~longArray[A]` (modulo small adjustments
         * depending on whether the bounds are inclusive or exclusive.)
         */
        private fun getBitMask(bit: Int): ULong {
            return if (bit >= 63) ULong.MAX_VALUE else (1UL shl (bit + 1)) - 1UL
        }

        /**
         * Sets all the bits from [fromInclusive] until [toExclusive]
         */
        private fun getBitMaskRange(fromInclusive: Int, toExclusive: Int): ULong {
            val inv = if (fromInclusive == 0) 0UL.inv() else getBitMask(fromInclusive - 1).inv()
            return getBitMask(toExclusive - 1) and inv
        }

        /** Lowest bit set. Undefined if called on an empty set. */
        private fun ULong.lowestBitSet(): Int {
            return this.countTrailingZeroBits()
        }

        /** Lowest bit set. Undefined if called on an empty set. */
        private fun ULong.highestBitSet(): Int {
            return 63 - this.countLeadingZeroBits()
        }

        private fun createConstraint(
            fromInclusive: Int? = null,
            toExclusive: Int? = null,
            negate: Boolean = false
        ): ApiConstraint {
            val from = toInternalApiLevel(fromInclusive, 1)
            val to = toInternalApiLevel(toExclusive, INFINITY)
            val bits = getBitMaskRange(from, to).let {
                if (negate) it.inv() else it
            }

            return ApiConstraint(bits)
        }

        /**
         * Create constraint where the API level is at least [apiLevel].
         */
        fun atLeast(apiLevel: Int): ApiConstraint {
            return createConstraint(fromInclusive = apiLevel)
        }

        /**
         * Create constraint where the API level is less than
         * [apiLevel].
         */
        fun below(apiLevel: Int): ApiConstraint {
            return createConstraint(toExclusive = apiLevel)
        }

        /**
         * Create constraint where the API level is higher than
         * [apiLevel].
         */
        fun above(apiLevel: Int): ApiConstraint {
            return createConstraint(fromInclusive = apiLevel + 1)
        }

        /**
         * Create constraint where the API level is lower than or equal
         * to [apiLevel].
         */
        fun atMost(apiLevel: Int): ApiConstraint {
            return createConstraint(toExclusive = apiLevel + 1)
        }

        /**
         * Create constraint where the API level is in the given range.
         */
        fun range(fromInclusive: Int, toExclusive: Int): ApiConstraint {
            return createConstraint(fromInclusive, toExclusive)
        }

        /**
         * Creates an API constraint where the API level equals a
         * specific level.
         */
        fun exactly(apiLevel: Int): ApiConstraint {
            return createConstraint(apiLevel, apiLevel + 1)
        }

        /**
         * Creates an API constraint where the API level is **not** a
         * specific value (e.g. API != apiLevel).
         */
        fun not(apiLevel: Int): ApiConstraint {
            return createConstraint(apiLevel, apiLevel + 1, negate = true)
        }

        /**
         * Serializes the given constraint into a String, which can
         * later be retrieved by calling [deserialize].
         */
        fun serialize(constraint: ApiConstraint): String {
            return constraint.bits.toString(16)
        }

        /**
         * Deserializes a given string (previously computed by
         * [serialize] into the corresponding constraint.
         */
        fun deserialize(s: String): ApiConstraint {
            return ApiConstraint(s.toULong(16))
        }
    }
}
