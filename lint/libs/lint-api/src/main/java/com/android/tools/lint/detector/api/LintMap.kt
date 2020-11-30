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

/**
 * A [LintMap] represents a collection of key value pairs used.
 * The map can be serialized, and this is the main use-case for
 * it: storing and retrieving additional information as part
 * of incidents such that the incidents can later be filtered
 * quickly to see if they're relevant in a downstream module's
 * lint report.
 *
 * The specific types of values that are allowed are:
 *  - [String]
 *  - int
 *  - boolean
 *  - an [Incident]
 *  - a [Location]
 *  - a nested [LintMap]
 */
class LintMap : Iterable<String> {
    /**
     * Notes related to this incident. This is intended to be used by
     * [Context.reportProvisional] and [Detector.processProvisional].
     * This is limited to a few primitive data types (because it needs
     * to be safely persisted across lint invocations.)
     */
    private var map: MutableMap<String, Any> = HashMap()

    /**
     * Records a string note related to this incident. This is intended to
     * be used to record notes via [Context.reportProvisional] for later
     * usage by [Detector.processProvisional] to decide whether the incident
     * still applies and perhaps to customize the message.
     */
    fun put(key: String, value: String): LintMap {
        map[key] = value
        return this
    }

    /** Like [put] but for integers */
    fun put(key: String, value: Int): LintMap {
        map[key] = value
        return this
    }

    /** Like [put] but for booleans */
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

    /** Returns the keys of the items in this map */
    fun keys(): Sequence<String> {
        return map.keys.asSequence()
    }

    /** Returns true if the given [key] is a key in the map */
    fun containsKey(key: String): Boolean {
        return map.containsKey(key)
    }

    /** Returns a note previously stored as a String by [put] */
    @Contract("_, !null -> !null")
    fun getString(key: String, default: String? = null): String? {
        return map[key] as? String ?: default
    }

    /** Returns a note previously stored as an integer by [put] */
    @Contract("_, !null -> !null")
    fun getInt(key: String, default: Int? = null): Int? {
        return map[key] as? Int ?: default
    }

    /** Returns an API level previously stored as an integer or string by [put] */
    @Contract("_, !null -> !null")
    fun getApi(key: String, default: Int? = null): Int? {
        val value = map[key] ?: return default
        return when (value) {
            is Int -> value
            is String -> SdkVersionInfo.getVersion(value, null)?.featureLevel ?: default
            else -> default
        }
    }

    /** Returns a note previously stored as a boolean by [put] */
    @Contract("_, !null -> !null")
    fun getBoolean(key: String, default: Boolean? = null): Boolean? {
        return map[key] as? Boolean ?: default
    }

    /** Returns a note previously stored as a boolean by [put] */
    @Contract("_, !null -> !null")
    fun getLocation(key: String): Location? {
        return map[key] as? Location
    }

    /** Returns a note previously stored as a map by [put] */
    fun getMap(key: String): LintMap? {
        @Suppress("UNCHECKED_CAST")
        return map[key] as? LintMap
    }

    /** Returns a note previously stored as a map by [put] */
    fun getIncident(key: String): Incident? {
        @Suppress("UNCHECKED_CAST")
        return map[key] as? Incident
    }

    /** Removes the given key's value from the map, if any */
    fun remove(key: String): LintMap {
        map.remove(key)
        return this
    }

    companion object {
        /**
         * Returns the internal map. This is **only** intended for
         * use by lint to be able to persist and restore the data.
         */
        fun getInternalMap(map: LintMap): MutableMap<String, Any> = map.map
    }

    /** Iterates through the keys. Makes it easy to use this map with a for each statement. */
    override fun iterator(): Iterator<String> = keys().iterator()
}
