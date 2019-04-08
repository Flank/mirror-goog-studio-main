/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.services

import com.google.common.truth.Truth.assertThat
import org.junit.Test

val MY_TEST_KEY_1 = object : CxxServiceKey<String> {
    override val type = String::class.java
}

val MY_TEST_KEY_2 = object : CxxServiceKey<String> {
    override val type = String::class.java
}

class CxxServiceRegistryBuilderTest {
    @Test
    fun registerAndGet() {
        val registry = CxxServiceRegistryBuilder()
        registry.registerFactory(MY_TEST_KEY_1) {
            "test value 1"
        }
        registry.registerFactory(MY_TEST_KEY_2) {
            "test value 2"
        }
        val sealed = registry.build()
        assertThat(sealed[MY_TEST_KEY_1]).isEqualTo("test value 1")
        assertThat(sealed[MY_TEST_KEY_2]).isEqualTo("test value 2")
    }

    @Test(expected = IllegalArgumentException::class)
    fun getUnregister() {
        val registry = CxxServiceRegistryBuilder()
        val sealed = registry.build()
        sealed[MY_TEST_KEY_1]
    }
}
