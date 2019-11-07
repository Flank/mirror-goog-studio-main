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

package com.android.build.api.variant.impl

import com.google.common.truth.Truth.assertThat
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.fail

class SafeReadingBeforeExecutionTest {

    @Mock
    lateinit var property: Property<String>

    @Mock
    lateinit var provider: Provider<String>

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
    }

    @Test
    fun testSetAndGetOrderings() {
        val executionMode = AtomicBoolean(false)
        var gradleProperty = GradleProperty.safeReadingBeforeExecution(
            id = "someId",
            property = property,
            initialValue = "initial",
            executionMode = executionMode)
        `when`(property.get()).thenReturn("initial")

        gradleProperty.set(provider)

        // now do a read, it should fail.
        try {
            gradleProperty.get()
            fail("exception not raised.")
        } catch (e: RuntimeException) {
            assertThat(e.message).contains("someId")
        }

        // we should still be able to set another provider.
        gradleProperty.set(provider)

        // now fake the execution phase.
        executionMode.set(true)

        // no exception expected
        gradleProperty.get()

        // reset
        gradleProperty = GradleProperty.safeReadingBeforeExecution(
            id = "someId",
            property = property,
            initialValue = "initial",
            executionMode = executionMode)
        `when`(property.get()).thenReturn("initial")

        executionMode.set(false)

        assertThat(gradleProperty.get()).isEqualTo("initial")
        // now setting a provider should fail.
        try {
            gradleProperty.set(provider)
            fail("exception not raised.")
        } catch (e: java.lang.RuntimeException) {
            assertThat(e.message).contains("someId")
        }

        // reset the value should still be allowed.
        gradleProperty.set("Foo")
    }
}