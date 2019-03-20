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

package com.android.builder.files

import com.android.tools.build.apkzlib.zip.compress.Zip64NotSupportedException
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.rules.TemporaryFolder
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertFailsWith

class ZipCentralDirectoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun basicTest() {
        val zipCDR = createZip(listOf(
            Entry("foo.bar", "content"),
            Entry("some/dir/"),
            Entry("some/dir/foo.bar", "content2")
        ))

        Truth.assertThat(zipCDR.entries.keys).containsExactly(
            "foo.bar",
            "some/dir/foo.bar")
    }

    @Test
    fun testRewrite() {
        val zipCDR = createZip(listOf(
            Entry("foo.bar", "content"),
            Entry("some/dir/"),
            Entry("some/dir/foo.bar", "content2")
        ))
        val toFile = temporaryFolder.newFile("foo2.zip")
        zipCDR.writeTo(toFile)

        Truth.assertThat(ZipCentralDirectory(toFile).entries).containsExactlyEntriesIn(zipCDR.entries)
    }

    @Test
    fun testCrc() {
        val zipCDR1 = createZip(listOf(
            Entry("foo.bar", "content")
        ))
        val zipCDR2 = createZip(listOf(
            Entry("foo.bar", "Content")
            ))

        Truth.assertThat(zipCDR1.entries.keys).containsExactlyElementsIn(zipCDR2.entries.keys)
        Truth.assertThat(zipCDR1.entries.values.first().crc32).isNotEqualTo(zipCDR2.entries.values.first().crc32)
    }

    @Test
    @Throws(IOException::class)
    fun testZip64File() {
        val zip64 = createZip64File(66000, 0)

        assertFailsWith<Zip64NotSupportedException> {
            ZipCentralDirectory(zip64).entries }
    }

    data class Entry(
        val name: String,
        val content: String? = null
    )


    private fun createZip(entries: List<Entry>) : ZipCentralDirectory {
        return ZipCentralDirectory(temporaryFolder.newFile().also {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(it))).use { stream ->
                for (entry in entries) {
                    stream.putNextEntry(ZipEntry(entry.name))
                    entry.content?.let { e ->
                        stream.write(e.toByteArray())
                    }
                    stream.closeEntry()
                }
            }
        })
    }

    private fun createZip64File(numClasses: Int, numResources: Int): File {
        val zip64 = temporaryFolder.newFile()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zip64))).use { zipOut ->
            for (i in 0 until numClasses) {
                zipOut.putNextEntry(ZipEntry("entry-$i.class"))
                zipOut.write("entry-$i".toByteArray())
                zipOut.closeEntry()
            }
            for (i in 0 until numResources) {
                zipOut.putNextEntry(ZipEntry("entry-$i"))
                zipOut.write("entry-$i".toByteArray())
                zipOut.closeEntry()
            }
        }
        return zip64
    }


}