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
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.DefaultAsserter.fail

class NoReadingBeforeExecutionTest {

    @Mock lateinit var property: Property<String>
    @Mock lateinit var provider: Provider<String>

    private val executionMode= AtomicBoolean(false)
    lateinit var gradleProperty: Property<String>

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        gradleProperty = GradleProperty.noReadingBeforeExecution(
            id = "someId",
            property = property,
            initialValue = "initial",
            executionMode = executionMode)
        Mockito.`when`(property.get()).thenReturn("initial")
    }

    @Test
    fun testNoReadingBeforeExecution() {
        try {
            gradleProperty.get()
            fail("exception not raised.")
        } catch(e: RuntimeException) {
            assertThat(e.message).contains("someId")
        }

        try {
            gradleProperty.getOrElse("foo")
            fail("exception not raised.")
        } catch(e: RuntimeException) {
            assertThat(e.message).contains("someId")
        }

        try {
            gradleProperty.orNull
            fail("exception not raised.")
        } catch(e: RuntimeException) {
            assertThat(e.message).contains("someId")
        }

        // fake execution phase
        executionMode.set(true)

        assertThat(gradleProperty.get()).isEqualTo("initial")
    }
}