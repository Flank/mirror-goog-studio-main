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

package com.android.build.gradle.internal.profile

import com.google.common.truth.Truth.assertThat
import org.jetbrains.kotlin.gradle.utils.`is`
import org.junit.Test
import kotlin.test.assertFailsWith

internal class ProfilingModeTest {

    @Test
    fun supportsTypicalProfilingModeTypes() {
        val profileable = ProfilingMode.getProfilingModeType("profileable")
        assertThat(profileable).`is`(ProfilingMode.PROFILEABLE::class.java)

        val debuggable = ProfilingMode.getProfilingModeType("debuggable")
        assertThat(debuggable).`is`(ProfilingMode.DEBUGGABLE::class.java)

        val nullable = ProfilingMode.getProfilingModeType(null)
        assertThat(nullable).`is`(ProfilingMode.UNDEFINED::class.java)
    }

    @Test
    fun nonSupportedProfilingTypeAccessed() {
        val exception = assertFailsWith<Exception> {
            ProfilingMode.getProfilingModeType("foo")
        }
        assertThat(exception.message).isEqualTo(
            "Unknown ProfilingMode value 'foo'. " +
                    "Possible values are 'undefined', 'debuggable', 'profileable'."
        )
    }

}
