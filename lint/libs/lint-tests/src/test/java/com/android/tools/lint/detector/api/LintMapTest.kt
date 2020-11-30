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

import com.android.tools.lint.client.api.IssueRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class LintMapTest {
    @Test
    fun testBasicOperations() {
        val map = LintMap()
        assertThat(map.getInt("key")).isNull()
        assertThat(map.getInt("key", -1)).isEqualTo(-1)
        assertThat(map.containsKey("key")).isFalse()

        map.put("key", 42)
        assertThat(map.containsKey("key")).isTrue()
        map.put("key2", 84)
        assertThat(map.getInt("key")).isEqualTo(42)
        assertThat(map.getInt(StringBuilder("key").toString())).isEqualTo(42)
        assertThat(map.getInt("key2")).isEqualTo(84)
        assertThat(map.getInt("key", -1)).isEqualTo(42)
        map.put("key", 0)
        assertThat(map.getInt("key")).isEqualTo(0)
        map.remove("key")
        assertThat(map.getInt("key")).isNull()
        assertThat(map.put("key", 42)).isSameInstanceAs(map)

        assertThat(map.getInt("key2")).isEqualTo(84)
        assertThat(map.getString("key2")).isNull()
        assertThat(map.getBoolean("key2")).isNull()
        // Supported conversion: int to API level
        assertThat(map.getApi("key2")).isEqualTo(84)
    }

    @Test
    fun testInts() {
        val map = LintMap()
        // Defaults
        assertThat(map.getInt("key")).isNull()
        assertThat(map.getInt("key", -1)).isEqualTo(-1)

        map.put("key", 42)
        map.put("key2", 84)
        assertThat(map.getInt("key")).isEqualTo(42)
        assertThat(map.getInt("key2")).isEqualTo(84)
        assertThat(map.getInt("key", -1)).isEqualTo(42)
        map.put("key", 0)
        assertThat(map.getInt("key")).isEqualTo(0)
    }

    @Test
    fun testBooleans() {
        val map = LintMap()
        assertThat(map.getBoolean("key")).isNull()
        assertThat(map.getBoolean("key", true)).isTrue()
        map.put("key", true)
        map.put("key2", false)
        assertThat(map.getBoolean("key")).isTrue()
        assertThat(map.getBoolean("key2")).isFalse()
    }

    @Test
    fun testGetStrings() {
        val map = LintMap()
        assertThat(map.getString("key")).isNull()
        assertThat(map.getString("key", "hello")).isEqualTo("hello")
        map.put("key", "world")
        assertThat(map.getString("key")).isEqualTo("world")
        map.put("key", 9)
        assertThat(map.getString("key")).isNull()
    }

    @Test
    fun testGetApiLevel() {
        val map = LintMap()
        assertThat(map.getApi("key")).isNull()
        assertThat(map.getApi("key", 30)).isEqualTo(30)
        map.put("key", 9)
        assertThat(map.getApi("key")).isEqualTo(9)
        // Conversion from code name to API level:
        map.put("key", "honeycomb")
        assertThat(map.getApi("key")).isEqualTo(11)
    }

    @Test
    fun testNestedMap() {
        val map = LintMap()
        val map2 = LintMap()
        map.put("key", 9)
        map.put("api", "honeycomb")
        map.put("map", map2)
        val parent = LintMap()
        parent.put("map1", map)
        assertThat(parent.getMap("map1")!!.getApi("key")).isEqualTo(9)
        assertThat(parent.getMap("map1")!!.getApi("api")).isEqualTo(11)
        assertThat(map.getMap("map")).isSameInstanceAs(map2)
    }

    @Test
    fun testLocation() {
        val map = LintMap()
        val location = Location.create(File("path"), "test", 0, 0)
        assertThat(map.getLocation("key")).isNull()
        map.put("key", location)
        assertThat(map.getLocation("key")).isSameInstanceAs(location)
        assertThat(map.getString("key")).isNull()
        assertThat(map.getInt("key")).isNull()
    }

    @Test
    fun testIncident() {
        val map = LintMap()
        val location = Location.create(File("path"), "test", 0, 0)
        val incident = Incident(IssueRegistry.LINT_WARNING, "test", location)
        assertThat(map.getIncident("key")).isNull()
        map.put("key", incident)
        assertThat(map.getIncident("key")).isSameInstanceAs(incident)
        assertThat(map.getLocation("key")).isNull()
        assertThat(map.getString("key")).isNull()
        assertThat(map.getInt("key")).isNull()
    }
}
