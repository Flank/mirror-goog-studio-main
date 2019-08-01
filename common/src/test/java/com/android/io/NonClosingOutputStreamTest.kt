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

package com.android.io

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.assertFailsWith

class NonClosingOutputStreamTest {

    @Test
    fun smokeTest() {
        val byteArrayOutputStream = ByteArrayOutputStream()
        // Use a zip output stream as an example of an output stream that is reused
        ZipOutputStream(byteArrayOutputStream).use { zipOutputStream ->
            zipOutputStream.putNextEntry(ZipEntry("test"))
            // Test writing with the non closing wrapper.
            writeAndClose(zipOutputStream.nonClosing())
            zipOutputStream.putNextEntry(ZipEntry("test2"))
            // Write another entry, but this time allow the output stream to be closed.
            writeAndClose(zipOutputStream)
            // Expect the next attempt to fail
            assertFailsWith(IOException::class) {
                zipOutputStream.putNextEntry(ZipEntry("test3"))
            }
        }
        // Expect that the zip entries were written correctly.
        val expectedContent = byteArrayOf(0xD, 0xE, 0xA, 0xD, 0xB, 0xE, 0xE, 0xF)
        var count = 0
        ZipInputStream(byteArrayOutputStream.toByteArray().inputStream()).use { zipInputStream ->
            while (true) {
                val entry = zipInputStream.nextEntry ?: break
                assertThat(zipInputStream.nonClosing().readBytes()).isEqualTo(expectedContent)
                count += 1
            }
        }
        assertThat(count).isEqualTo(2)
    }

    private fun writeAndClose(os: OutputStream) {
        os.use {
            // Check all three output stream methods.
            it.write(0xD)
            it.write(byteArrayOf(0xE, 0xA, 0xD))
            it.write(byteArrayOf(0x1, 0xB, 0xE, 0xE, 0xF, 0x2), 1, 4)
        }
    }
}