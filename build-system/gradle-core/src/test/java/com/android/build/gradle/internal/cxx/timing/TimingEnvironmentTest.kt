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

package com.android.build.gradle.internal.cxx.timing

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TimingEnvironmentTest {
    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private data class CurrentTimeInTest(var time : Long)

    private fun timeTest(action : CurrentTimeInTest.() -> Unit) : String {
        val folder = temporaryFolder.newFolder()
        lateinit var file : File
        val time = CurrentTimeInTest(15L)
        TimingEnvironment(folder, "test") { time.time }
            .use {
                action(time)
                file = it.getTimingFile()
            }
        return file.readText()
    }

    private fun replaceTimes(value : String) : String {
        return Regex("[0-9]+ms")
                .replace(value, "{XX}ms")
                .trim()
    }

    @Test
    fun simple() {
        val log = timeTest {
            time += 50
        }

        assertThat(replaceTimes(log)).isEqualTo("""
            # C/C++ build system timings
            test {XX}ms
        """.trimIndent())
    }

    @Test
    fun `one level nested`() {
        val log = timeTest {
            time("nest-level-1") {
                time += 50
            }
        }
        assertThat(replaceTimes(log)).isEqualTo("""
            # C/C++ build system timings
            test
              nest-level-1 {XX}ms
            test completed in {XX}ms
        """.trimIndent())
    }

    @Test
    fun `two level nested`() {
        val log = timeTest {
            time("nest-level-1") {
                time("nest-level-2") {
                    time += 50
                }
            }
        }
        assertThat(replaceTimes(log)).isEqualTo("""
            # C/C++ build system timings
            test
              nest-level-1
                nest-level-2 {XX}ms
              nest-level-1 completed in {XX}ms
            test completed in {XX}ms
        """.trimIndent())
    }

    @Test
    fun `untracked gap at start of scope is logged`() {
        val log = timeTest {
            time += 50 // <- This time is outside of "scope" below
            time("scope") {
                time += 50
            }
        }
        assertThat(replaceTimes(log)).isEqualTo("""
            # C/C++ build system timings
            test
              [gap of {XX}ms]
              scope {XX}ms
            test completed in {XX}ms
        """.trimIndent())
    }

    @Test
    fun `untracked gap at end of scope is logged`() {
        val log = timeTest {
            time("scope") {
                time += 50
            }
            time += 50 // <- This time is outside of "scope" above
        }
        assertThat(replaceTimes(log)).isEqualTo("""
            # C/C++ build system timings
            test
              scope {XX}ms
              [gap of {XX}ms]
            test completed in {XX}ms
        """.trimIndent())
    }

    @Test
    fun `untracked gap between two scopes is logged`() {
        val log = timeTest {
            time("scope-1") {
                time += 50
            }
            time += 50 // <- This time is between of "scope-1" and "scope-2"
            time("scope-2") {
                time += 50
            }
        }
        assertThat(replaceTimes(log)).isEqualTo("""
            # C/C++ build system timings
            test
              scope-1 {XX}ms
              [gap of {XX}ms]
              scope-2 {XX}ms
            test completed in {XX}ms
        """.trimIndent())
    }

    @Test
    fun `repro case where time inside nested scope was attributed to outer`() {
        val log = timeTest {
            time("scope-1") {
                time("scope-2") {
                    time += 50
                }
            }
        }
        assertThat(replaceTimes(log)).isEqualTo("""
            # C/C++ build system timings
            test
              scope-1
                scope-2 {XX}ms
              scope-1 completed in {XX}ms
            test completed in {XX}ms
        """.trimIndent())
    }

    @Test
    fun `time call with no enclosing TimingEnvironment`() {
        // It shouldn't crash but the time is not attributed anywhere.
        time("discarded") { }
    }
}
