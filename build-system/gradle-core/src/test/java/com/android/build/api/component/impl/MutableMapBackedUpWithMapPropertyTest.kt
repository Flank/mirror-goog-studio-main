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

package com.android.build.api.component.impl

import com.android.build.gradle.internal.fixtures.FakeMapProperty
import com.google.common.truth.Truth
import org.junit.Test

import org.junit.Assert.*

class MutableMapBackedUpWithMapPropertyTest {

    val target = MutableMapBackedUpWithMapProperty(FakeMapProperty(HashMap<String, String>()), "Test")

    @Test
    fun getEntries() {
        target["key1"] = "value1"
        target["key2"] = "value2"
        val entries = target.entries.iterator()
        entries.next().let {
            Truth.assertThat(it.key).isEqualTo("key1")
            Truth.assertThat(it.value).isEqualTo("value1")
        }
        entries.next().let {
            Truth.assertThat(it.key).isEqualTo("key2")
            Truth.assertThat(it.value).isEqualTo("value2")
        }
        Truth.assertThat(entries.hasNext()).isFalse()
    }

    @Test
    fun getKeys() {
        target["key1"] = "value1"
        target["key2"] = "value2"
        Truth.assertThat(target.keys).containsExactly("key1", "key2")
    }

    @Test
    fun getValues() {
        target["key1"] = "value1"
        target["key2"] = "value2"
        Truth.assertThat(target.values).containsExactly("value1", "value2")
    }

    @Test
    fun clear() {
        target["key1"] = "value1"
        target.clear()
        target["key2"] = "value2"
        Truth.assertThat(target.keys).containsExactly("key2")

    }

    @Test
    fun putAll() {
        target.putAll(mapOf("key1" to "value1", "key2" to "value2", "key3" to "value3"))
        Truth.assertThat(target.size).isEqualTo(3)
    }
}
