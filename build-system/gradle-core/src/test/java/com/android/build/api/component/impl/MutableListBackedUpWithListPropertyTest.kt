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

import com.android.build.gradle.internal.fixtures.FakeListProperty
import com.google.common.truth.Truth
import org.junit.Test

internal class MutableListBackedUpWithListPropertyTest {

    val target = MutableListBackedUpWithListProperty(FakeListProperty(ArrayList<String>()), "Test")

    @Test
    fun get() {
        target.add("Foo")
        target.add("Bar")
        Truth.assertThat(target).containsExactly("Foo", "Bar")
    }

    @Test
    fun getSize() {
        target.add("Foo")
        target.add("Bar")
        Truth.assertThat(target).hasSize(2)
    }

    @Test
    fun clear() {
        target.add("Foo")
        target.clear()
        target.add("Bar")
        Truth.assertThat(target).containsExactly("Bar")
    }

    @Test
    fun listIterator() {
        target.add("Foo")
        target.add("Bar")
        target.add("FooBar")
        val iterator = target.listIterator()
        Truth.assertThat(iterator.next()).isEqualTo("Foo")
        Truth.assertThat(iterator.next()).isEqualTo("Bar")
        Truth.assertThat(iterator.next()).isEqualTo("FooBar")
        Truth.assertThat(iterator.hasNext()).isFalse()
    }

    @Test
    fun testToString() {
        target.add("Foo")
        target.add("Bar")
        Truth.assertThat(target.toString()).isEqualTo("[Foo, Bar]")
    }
}
