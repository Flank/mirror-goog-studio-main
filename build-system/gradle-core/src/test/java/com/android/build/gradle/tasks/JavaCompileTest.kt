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

package com.android.build.gradle.tasks

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Unit test for JavaCompile (created in [JavaCompileCreationAction]). */
class JavaCompileTest {

    @Test
    fun `configureCompileArgumentsForLombok - with -proc-none, without -processor`() {
        val compilerArgs = mutableListOf("-cp", "sample.jar", "-proc:none")
        configureCompilerArgumentsForLombok(compilerArgs)
        assertEquals(
            listOf(
                "-cp",
                "sample.jar",
                "-processor",
                "lombok.launch.AnnotationProcessorHider\$AnnotationProcessor"
            ),
            compilerArgs
        )
    }

    @Test
    fun `configureCompileArgumentsForLombok - with -proc-none, with -processor`() {
        val compilerArgs =
            mutableListOf("-cp", "sample.jar", "-proc:none", "-processor", "SampleProcessor")
        configureCompilerArgumentsForLombok(compilerArgs)
        assertEquals(
            listOf(
                "-cp",
                "sample.jar",
                "-processor",
                "lombok.launch.AnnotationProcessorHider\$AnnotationProcessor"
            ),
            compilerArgs
        )
    }

    @Test
    fun `configureCompileArgumentsForLombok - without -proc-none, with -processor`() {
        val compilerArgs = mutableListOf("-cp", "sample.jar", "-processor", "SampleProcessor")
        val exception = assertFailsWith<IllegalStateException> {
            configureCompilerArgumentsForLombok(compilerArgs)
        }
        assertEquals(
            "compilerArgs [-cp, sample.jar, -processor, SampleProcessor]" +
                    " does not contain -proc:none",
            exception.message
        )
    }

    @Test
    fun `configureCompileArgumentsForLombok - without -proc-none, without -processor`() {
        val compilerArgs = mutableListOf("-cp", "sample.jar")
        val exception = assertFailsWith<IllegalStateException> {
            configureCompilerArgumentsForLombok(compilerArgs)
        }
        assertEquals(
            "compilerArgs [-cp, sample.jar] does not contain -proc:none",
            exception.message
        )
    }
}