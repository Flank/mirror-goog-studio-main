/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.sdklib.SdkVersionInfo
import org.jetbrains.annotations.Contract
import java.util.HashMap

/**
 * A [LintMap] represents a collection of key value pairs used in lint.
 * The map can be serialized, and this is the main use-case for it:
 * storing and retrieving additional information as part of incidents
 * for incremental build purposes.
 *
 * The specific types of values that are currently allowed are:
 * - [String]
 * - int
 * - boolean
 * - [Incident]
 * - [Location]
 * - [LintMap] (which allows nesting of data via maps of maps,
 *   recursively)
 * - [Constraint]
 */
class LintMap : Iterable<String> {
    /** Internal untyped map storage. */
    private var map: MutableMap<String, Any> = HashMap()

    /**
     * Simple string get operator to be able to use Kotlin array syntax;
     * this is short for [getString] with a null default.
     */
    operator fun get(key: String): String? {
        return getString(key, null)
    }

    /**
     * Simple string set operator to be able to use Kotlin array syntax;
     * this is mapped to the [put] method for strings.
     */
    operator fun set(key: String, value: String): LintMap {
        return put(key, value)
    }

    /** Records a string into the map. */
    fun put(key: String, value: String): LintMap {
        map[key] = value
        return this
    }

    /** Like [put] but for integers. */
    fun put(key: String, value: Int): LintMap {
        map[key] = value
        return this
    }

    /** Like [put] but for booleans. */
    fun put(key: String, value: Boolean): LintMap {
        map[key] = value
        return this
    }

    /** Like [put] but for a [Location] */
    fun put(key: String, value: Location): LintMap {
        map[key] = value
        return this
    }

    /** Like [put] but for a map. */
    fun put(key: String, value: LintMap): LintMap {
        map[key] = value
        return this
    }

    /** Like [put] but for an [Incident]. */
    fun put(key: String, value: Incident): LintMap {
        map[key] = value
        return this
    }

    /** Like [put] but for a [Constraint]. */
    fun put(key: String, constraint: Constraint): LintMap {
        map[key] = constraint
        return this
    }

    /** Returns the keys of the items in this map. */
    fun keys(): Sequence<String> {
        return map.keys.asSequence()
    }

    /** Returns true if the given [key] is a key in the map. */
    fun containsKey(key: String): Boolean {
        return map.containsKey(key)
    }

    /** Returns a string previously stored by [put] */
    @Contract("_, !null -> !null")
    fun getString(key: String, default: String? = null): String? {
        return map[key] as? String ?: default
    }

    /** Returns an int previously stored by [put] */
    @Contract("_, !null -> !null")
    fun getInt(key: String, default: Int? = null): Int? {
        return map[key] as? Int ?: default
    }

    /**
     * Returns an API level previously stored as an integer or string by
     * [put]
     */
    @Contract("_, !null -> !null")
    fun getApi(key: String, default: Int? = null): Int? {
        val value = map[key] ?: return default
        return when (value) {
            is Int -> value
            is String -> SdkVersionInfo.getVersion(value, null)?.featureLevel ?: default
            else -> default
        }
    }

    /** Returns a boolean previously stored by [put] */
    @Contract("_, !null -> !null")
    fun getBoolean(key: String, default: Boolean? = null): Boolean? {
        return map[key] as? Boolean ?: default
    }

    /** Returns a location previously stored by [put] */
    @Contract("_, !null -> !null")
    fun getLocation(key: String): Location? {
        return map[key] as? Location
    }

    /** Returns a map previously stored by [put] */
    fun getMap(key: String): LintMap? {
        @Suppress("UNCHECKED_CAST")
        return map[key] as? LintMap
    }

    /** Returns an incident previously stored by [put] */
    fun getIncident(key: String): Incident? {
        @Suppress("UNCHECKED_CAST")
        return map[key] as? Incident
    }

    /** Returns a condition previously stored by [put] */
    fun getConstraint(key: String): Constraint? {
        @Suppress("UNCHECKED_CAST")
        return map[key] as? Constraint
    }

    /** Removes the given key's value from the map, if any. */
    fun remove(key: String): LintMap {
        map.remove(key)
        return this
    }

    /** Copies all the values from the given [from] into this one. */
    fun putAll(from: LintMap): LintMap {
        this.map.putAll(from.map)
        return this
    }

    /** The number of elements in the map. */
    val size: Int get() = map.size

    /** Is this map empty? */
    fun isEmpty(): Boolean = map.isEmpty()

    /** Is this map non-empty? */
    fun isNotEmpty(): Boolean = !isEmpty()

    companion object {
        /**
         * Returns the internal map. This is **only** intended for use
         * by lint to be able to persist and restore the data.
         */
        fun getInternalMap(map: LintMap): MutableMap<String, Any> = map.map
    }

    /**
     * Iterates through the keys. Makes it easy to use this map with a
     * for each statement.
     */
    override fun iterator(): Iterator<String> = keys().iterator()
}
