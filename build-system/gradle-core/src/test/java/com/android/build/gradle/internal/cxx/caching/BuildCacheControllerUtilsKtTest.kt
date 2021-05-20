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

package com.android.build.gradle.internal.cxx.caching

import com.android.build.gradle.internal.cxx.StructuredLog
import com.android.utils.cxx.CxxDiagnosticCode.BUILD_CACHE_DISABLED_ACCESS
import com.google.common.truth.Truth.assertThat
import org.gradle.caching.BuildCacheKey
import org.gradle.caching.internal.controller.BuildCacheController
import org.gradle.caching.internal.controller.BuildCacheLoadCommand
import org.gradle.caching.internal.controller.BuildCacheStoreCommand
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.stubbing.Answer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Optional

class BuildCacheControllerUtilsKtTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    lateinit var structuredLog : StructuredLog

    @Before
    fun before() {
        structuredLog = StructuredLog(tempFolder)
    }

    @Test
    fun `check load handshake when key found`() {
        val key = mock(BuildCacheKey::class.java, throwUnmocked)
        doReturn("found").`when`(key).hashCode
        val controller = FakeBuildCacheController()
        val result = controller.load(key) { "load result" }
        assertThat(result).isEqualTo("load result")
        structuredLog.assertNoErrors()
    }

    @Test
    fun `check load handshake when key not found`() {
        val key = mock(BuildCacheKey::class.java, throwUnmocked)
        doReturn("not found").`when`(key).hashCode
        val controller = FakeBuildCacheController()
        val result = controller.load(key) { "load result" }
        assertThat(result).isNull()
        structuredLog.assertNoErrors()
    }

    @Test
    fun `check load handshake when build cache disabled`() {
        val key = mock(BuildCacheKey::class.java, throwUnmocked)
        doReturn("found").`when`(key).hashCode
        val controller = FakeBuildCacheController(enabled = false)
        val result = controller.load(key) { "load result" }
        assertThat(result).isNull()
        structuredLog.assertError(BUILD_CACHE_DISABLED_ACCESS)
    }

    @Test
    fun `check store`() {
        val key = mock(BuildCacheKey::class.java, throwUnmocked)
        doReturn("found").`when`(key).hashCode
        val controller = FakeBuildCacheController()
        var storeCalled = 0
        controller.store(key) {
            ++storeCalled
        }
        assertThat(storeCalled).isEqualTo(1)
        structuredLog.assertNoErrors()
    }

    @Test
    fun `check store when build cache disabled`() {
        val key = mock(BuildCacheKey::class.java, throwUnmocked)
        doReturn("found").`when`(key).hashCode
        val controller = FakeBuildCacheController(enabled = false)
        var storeCalled = 0
        controller.store(key) {
            ++storeCalled
        }
        assertThat(storeCalled).isEqualTo(0)
        structuredLog.assertError(BUILD_CACHE_DISABLED_ACCESS)
    }

    private val throwUnmocked = Answer<Any> { invocation ->
        throw RuntimeException(invocation.method.toGenericString() + " is not stubbed")
    }

    private class FakeBuildCacheController(
        private val enabled : Boolean = true
    ): BuildCacheController {
        private val buffer = ByteArray(1000)
        override fun close() { }
        override fun isEnabled() = enabled
        override fun isEmitDebugLogging() = false
        override fun <T : Any?> load(command: BuildCacheLoadCommand<T>): Optional<T> {
            val key = command.key
            val result = if (key.hashCode == "found") {
                command.load(ByteArrayInputStream(buffer)).metadata
            } else null
            return Optional.ofNullable(result)
        }

        override fun store(command: BuildCacheStoreCommand) {
            command.key // Simulate access to key
            command.store(ByteArrayOutputStream())
        }
    }
}
