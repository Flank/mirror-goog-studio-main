/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.utils.FileUtils.join
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

object EmptyResult

class CachingEnvironmentTest {
    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `exception is logged`() {
        val folder = temporaryFolder.newFolder()

        try {
            CachingEnvironment(folder).use {
                cache<String, EmptyResult>("key") {
                    infoln("About to throw an exception")
                    throw Exception("Exception thrown by cached function")
                }
            }
        } catch (e: Exception) {
            val keyHash = "3d675a6b" // Hash of "key"
            assertThat(e.message).isEqualTo("Exception thrown by cached function")
            val exceptionFile = join(folder, "empty_result_${keyHash}_exception.txt")
            assertThat(exceptionFile.exists())
                .named("Didn't find $exceptionFile among ${folder.listFiles().map { it.name }}")
                .isTrue()
            assertThat(exceptionFile.readText()).contains("Exception thrown by cached function")
            val logFile = join(folder, "empty_result_${keyHash}.log")
            assertThat(logFile.exists()).isTrue()
            assertThat(logFile.readText()).contains("About to throw an exception")
            return
        }
        fail("Expected an exception")
    }

    @Test
    fun `dont leave exception file after success`() {
        val folder = temporaryFolder.newFolder()

        try {
            // Cause exception file to be written
            CachingEnvironment(folder).use {
                cache<String, EmptyResult>("key") {
                    infoln("About to throw an exception")
                    throw Exception("Exception thrown by cached function")
                }
            }
        } catch (e: Exception) {
        }
        // Same run but now it doesn't throw an exception
        CachingEnvironment(folder).use {
            cache("key") {
                EmptyResult
            }
        }
        val keyHash = "3d675a6b" // Hash of "key"
        val exceptionFile = join(folder, "empty_result_${keyHash}_exception.txt")
        assertThat(!exceptionFile.exists())
            .named("Unexpectedly found $exceptionFile among ${folder.listFiles().map { it.name }}")
            .isTrue()
    }
}