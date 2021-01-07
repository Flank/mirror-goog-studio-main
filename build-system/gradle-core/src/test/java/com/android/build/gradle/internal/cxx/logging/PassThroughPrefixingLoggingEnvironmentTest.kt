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

package com.android.build.gradle.internal.cxx.logging

import com.google.common.truth.Truth.*

import org.junit.Test
import java.io.File

class PassThroughPrefixingLoggingEnvironmentTest {

    @Test
    fun `attach filename to error`() {
        PassThroughPrefixingLoggingEnvironment(File("my-file")).apply {
            errorln("an error")
            assertThat(errors.single().toString()).isEqualTo("C/C++: my-file : an error")
        }
    }

    @Test
    fun `attach tag to error`() {
        PassThroughPrefixingLoggingEnvironment(tag = "my-tag").apply {
            errorln("an error")
            assertThat(errors.single().toString()).isEqualTo("C/C++: my-tag : an error")
        }
    }

    @Test
    fun `attach filename and tag to error`() {
        PassThroughPrefixingLoggingEnvironment(File("my-file"), "my-tag").apply {
            errorln("an error")
            assertThat(errors.single().toString()).isEqualTo("C/C++: my-file my-tag : an error")
        }
    }

    @Test
    fun `nested has correct precedence`() {
        PassThroughPrefixingLoggingEnvironment(File("my-file-outer"), "my-tag").apply {
            PassThroughPrefixingLoggingEnvironment(File("my-file-inner")).use {
                errorln("an error")
            }
            assertThat(errors.single().toString()).isEqualTo("C/C++: my-file-inner my-tag : an error")
        }
    }
}
