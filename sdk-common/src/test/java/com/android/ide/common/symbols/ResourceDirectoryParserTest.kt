/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.android.ide.common.symbols

import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.util.Random

class ResourceDirectoryParserTest {

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private val random: Random = Random()

    /**
     * Creates a file in a directory with specific data. The file is created in the path `path`
     * resolved against `directory`. For example, if `path` is `a/b`, the file to create is named
     * `b` and it is placed inside subdirectory `a` of `directory`. The subdirectory is created if
     * needed.
     *
     * @param data the data to write in the file
     * @param directory the directory where the file is created.
     * @param path the path for the file, relative to `directory` using forward slashes as
     * separators
     */
    private fun make(data: ByteArray, directory: File, path: String) {
        val file = directory.toPath().resolve(path)
        FileUtils.mkdirs(file.parent.toFile())
        Files.write(file, data)
    }

    /**
     * Same as [make], but writing random data in the file instead of receiving specific data to
     * write.
     *
     * @param directory the directory where the file is created.
     * @param path the path for the file, relative to `directory` using forward slashes as
     * separators
     */
    private fun makeRandom(directory: File, path: String) {
        val byteCount = random.nextInt(1000)
        val bytes = ByteArray(byteCount)
        random.nextBytes(bytes)
        make(bytes, directory, path)
    }

    @Test
    fun parseEmptyResourceDirectory() {
        val directory = temporaryFolder.newFolder()

        val platformTable = SymbolTable.builder().tablePackage("android").build()

        val parsed =
                parseResourceSourceSetDirectory(
                        directory, IdProvider.sequential(), platformTable)

        val expected = SymbolTable.builder().build()

        assertEquals(expected, parsed)
    }

    @Test
    fun parseDrawablesAndRawFiles() {
        val directory = temporaryFolder.newFolder()

        makeRandom(directory, "drawable/foo.png")
        makeRandom(directory, "drawable/bar.png")
        FileUtils.mkdirs(File(directory, "drawable-en"))
        makeRandom(directory, "drawable-en-hdpi/foo.png")
        makeRandom(directory, "raw/foo.png")
        makeRandom(directory, "raw-en/foo.png")
        makeRandom(directory, "raw-en/bar.png")

        val platformTable = SymbolTable.builder().tablePackage("android").build()

        val parsed =
                parseResourceSourceSetDirectory(
                        directory, IdProvider.sequential(), platformTable)

        val expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("drawable", "bar", "int", 0x7f_08_0001))
                        .add(SymbolTestUtils.createSymbol("drawable", "foo", "int", 0x7f_08_0002))
                        .add(SymbolTestUtils.createSymbol("raw", "bar", "int", 0x7f_13_0002))
                        .add(SymbolTestUtils.createSymbol("raw", "foo", "int", 0x7f_13_0001))
                        .build()

        assertEquals(expected, parsed)
    }

    @Test
    fun parseValues() {
        val directory = temporaryFolder.newFolder()

        val values = """
<resources>
    <color name="a">#000000</color>
    <color name="b">#000000</color>
</resources>""".trimIndent()

        val values_en = """
<resources>
    <color name="b">#000000</color>
    <color name="c">#000000</color>
</resources>""".trimIndent()

        make(values.toByteArray(), directory, "values/col.xml")
        make(values_en.toByteArray(), directory, "values-en/col.xml")

        val platformTable = SymbolTable.builder().tablePackage("android").build()

        val parsed =
                parseResourceSourceSetDirectory(
                        directory, IdProvider.sequential(), platformTable)

        val expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("color", "a", "int", 0x7f_06_0001))
                        .add(SymbolTestUtils.createSymbol("color", "b", "int", 0x7f_06_0002))
                        .add(SymbolTestUtils.createSymbol("color", "c", "int", 0x7f_06_0004))
                        .build()

        assertEquals(expected, parsed)
    }

    @Test
    fun failWithException() {
        val directory = temporaryFolder.newFolder()

        val values = """
<resources>
    <color name="a">#000000</color>
    <color name="a">#000000</color>
</resources>""".trimIndent()

        make(values.toByteArray(), directory, "values/col.xml")

        try {
            val platformTable = SymbolTable.builder().tablePackage("android").build()

            parseResourceSourceSetDirectory(
                    directory, IdProvider.sequential(), platformTable)
            fail()
        } catch (e: ResourceDirectoryParseException) {
            assertThat(e.message).contains(FileUtils.join("values", "col.xml"))
        }
    }
}
