/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.configure

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.io.File

class IdempotentFileWriterTest {
    private val folder = File("./folder")
    private val file = File("./folder/my-file")

    private fun withTestLogging(action : () -> Unit) : List<String> {
        val messages = mutableListOf<String>()
        object : ThreadLoggingEnvironment() {
            override fun error(message: String) {
                messages += "error: $message"
            }

            override fun warn(message: String) {
                messages += "warn: $message"
            }

            override fun info(message: String) {
                messages += "info: $message"
            }
        }.use {
            action()
            return messages
        }
    }

    @Before
    fun before() {
        folder.deleteRecursively()
        folder.mkdirs()
    }

    @Test
    fun testWritingWorks() {
        val writer = IdempotentFileWriter()
        writer.addFile(file.path, "my-content")
        val messages = withTestLogging {
            assertThat(writer.write()).containsExactly(file.path)
        }
        assertThat(messages).containsExactly("info: Writing ./folder/my-file")
        assertThat(file.readText()).isEqualTo("my-content")
    }

    @Test
    fun testWritingSkipsSameContent() {
        val writer = IdempotentFileWriter()
        file.writeText("my-content")
        writer.addFile(file.path, "my-content")
        val messages = withTestLogging {
            assertThat(writer.write()).isEmpty()
        }
        assertThat(messages).containsExactly(
            "info: Not writing ./folder/my-file because there was no change")
        assertThat(file.readText()).isEqualTo("my-content")
    }

    @Test
    fun testWritingReplacesOldContent() {
        val writer = IdempotentFileWriter()
        file.writeText("my-content-old")
        writer.addFile(file.path, "my-content")
        assertThat(writer.write()).containsExactly(file.path)
        assertThat(file.readText()).isEqualTo("my-content")
    }

    @Test
    fun testSecondWriteWins() {
        val writer = IdempotentFileWriter()
        writer.addFile(file.path, "my-content-1")
        writer.addFile(file.path, "my-content-2")
        assertThat(writer.write()).containsExactly(file.path)
        assertThat(file.readText()).isEqualTo("my-content-2")
    }
}