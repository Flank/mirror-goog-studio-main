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
import com.google.common.truth.ThrowableSubject
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.util.Random
import javax.xml.parsers.DocumentBuilderFactory

class ResourceDirectoryParserTest {

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private val random: Random = Random()

    private val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

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
        makeRandom(directory, "drawable-hdpi/foo.png")
        makeRandom(directory, "drawable-hdpi/density_specific.png")
        makeRandom(directory, "drawable-en/language_specific.png")
        makeRandom(directory, "drawable-hdpi-en/language_and_density.png")
        makeRandom(directory, "raw/foo.png")
        makeRandom(directory, "raw-hdpi/foo.png")
        makeRandom(directory, "raw-en/bar.png")
        makeRandom(directory, "raw-hdpi-en/mixed.png")

        val platformTable = SymbolTable.builder().tablePackage("android").build()

        val parsed =
                parseResourceSourceSetDirectory(
                        directory, IdProvider.sequential(), platformTable)

        val expected =
                SymbolTable.builder()
                        .add(SymbolTestUtils.createSymbol("drawable", "bar", "int", 0x7f_08_0001))
                        .add(SymbolTestUtils.createSymbol("drawable", "foo", "int", 0x7f_08_0002))
                        .add(SymbolTestUtils.createSymbol("raw", "foo", "int", 0x7f_13_0001))
                        .add(SymbolTestUtils.createSymbol("drawable", "density_specific", "int", 0x7f_08_0003))
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

        val values_two = """
<resources>
    <color name="b">#000000</color>
    <color name="c">#000000</color>
</resources>""".trimIndent()

        make(values.toByteArray(), directory, "values/col.xml")
        make(values_two.toByteArray(), directory, "values/col_two.xml")

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
    fun ignoresNonDefaultLanguageConfigs() {
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
                .build()

        assertEquals(expected, parsed)
    }

    @Test
    fun parseAarZipEntrySmokeTest() {
        val values = """
            <resources>
                <color name="my_color">#000000</color>
            </resources>""".trimIndent()
        val layout = """
            <androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="match_parent">

                <TextView
                    android:id="@+id/my_id"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/hello_first_fragment"
                    app:layout_constraintBottom_toTopOf="@id/button_first"
                    app:layout_constraintEnd_toEndOf="parent"
                    app:layout_constraintStart_toStartOf="parent"
                    app:layout_constraintTop_toTopOf="parent" />
            </androidx.constraintlayout.widget.ConstraintLayout>

        """.trimIndent()

        val parsed = SymbolTable.builder().apply {
            // Read OK
            assertParses("res/values/col.xml", values)
            assertParses("res/drawable/my_drawable.png")
            assertParses("res/layout/my_layout.xml", layout)
        }.build()

        val expected =
            SymbolTable.builder()
                .add(SymbolTestUtils.createSymbol("color", "my_color", "int", 0))
                .add(SymbolTestUtils.createSymbol("drawable", "my_drawable", "int", 0))
                .add(SymbolTestUtils.createSymbol("id", "my_id", "int", 0))
                .add(SymbolTestUtils.createSymbol("layout", "my_layout", "int", 0))
                .build()

        assertThat(parsed).isEqualTo(expected)
    }

    @Test
    fun parseAarZipIgnoresNonResources() {
        val parsed = SymbolTable.builder().apply {
            assertParses("classes.jar")
            assertParsesWithError("res/bad").hasMessageThat().isEqualTo("Error parsing 'res/bad': Invalid resource path.")
            assertParsesWithError("res/bad/a.xml").hasMessageThat().isEqualTo("Error parsing 'res/bad/a.xml': Invalid resource directory name 'bad'")
            assertParsesWithError("res/drawable/%.png").hasMessageThat().isEqualTo("Error parsing 'res/drawable/%.png': The resource name must start with a letter")
        }.build()

        assertThat(parsed).isEqualTo(SymbolTable.builder().build())
    }

    @Test
    fun parseAarZipIgnoresCorruptValues() {
        val parsed = SymbolTable.builder().apply {
            assertParsesWithError("res/values/corrupt.xml", "<").hasMessageThat().isEqualTo("Error parsing 'res/values/corrupt.xml'")
            assertParsesWithError("res/values/corrupt.xml", "<other></other>").hasMessageThat().isEqualTo("Error parsing 'res/values/corrupt.xml'")
        }.build()

        assertThat(parsed).isEqualTo(SymbolTable.builder().build())
    }

    @Test
    fun parseAarZipIsLenientCorruptLayout() {
        val parsed = SymbolTable.builder().apply {
            // Inner XML not read, but outer resource defined.
            assertParsesWithError("res/layout/bad.xml", "<").hasMessageThat().isEqualTo("Error parsing 'res/layout/bad.xml'")
            assertParsesWithError("res/layout/bad2.xml", "" ).hasMessageThat().isEqualTo("Error parsing 'res/layout/bad2.xml'")
        }.build()

        val expected =
            SymbolTable.builder()
                .add(SymbolTestUtils.createSymbol("layout", "bad", "int", 0))
                .add(SymbolTestUtils.createSymbol("layout", "bad2", "int", 0))
                .build()

        assertThat(parsed).isEqualTo(expected)
    }

    @Test
    fun parseAarZipIgnoresNonDefaultLanguageConfigs() {
        val values = """
            <resources>
                <color name="a">#000000</color>
                <color name="b">#000000</color>
            </resources>""".trimIndent()

        val parsed = SymbolTable.builder().apply {
            assertParses("res/values/col.xml", values)
            assertParses("res/values-en/col.xml")
        }.build()

        val expected =
            SymbolTable.builder()
                .add(SymbolTestUtils.createSymbol("color", "a", "int", 0))
                .add(SymbolTestUtils.createSymbol("color", "b", "int", 0))
                .build()

        assertThat(parsed).isEqualTo(expected)
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

    @Test
    fun reportPathForFailedNonValuesXmlFile() {
        val directory = temporaryFolder.newFolder()

        // Format not allowed.
        val xml = """
<LinearLayout>
    <TextView android:id="@+id/android:name"/>
</LinearLayout>
        """.trimIndent()

        make(xml.toByteArray(), directory, "layout/mylayout.xml")

        try {
            val platformTable = SymbolTable.builder().tablePackage("android").build()

            parseResourceSourceSetDirectory(
                directory, IdProvider.sequential(), platformTable)
            fail()
        } catch (e: ResourceDirectoryParseException) {
            assertThat(e.message).contains(FileUtils.join("layout", "mylayout.xml"))
            assertThat(e.cause!!.message)
                .contains("Validation of a resource with name 'android:name' and type 'id' failed.")
            assertThat(e.cause!!.cause!!.message)
                .contains("Error: ':' is not a valid resource name character")
        }
    }

    @Test
    fun shouldParseNonValuesXmlFile() {
        val directory = temporaryFolder.newFolder()

        // Format not allowed.
        val xml = """
<LinearLayout>
    <TextView android:id="@+id/toolbar"/>
</LinearLayout>
        """.trimIndent()

        make(xml.toByteArray(), directory, "layout/mylayout.xml")

        val platformTable = SymbolTable.builder().tablePackage("android").build()

        val parsed =
            parseResourceSourceSetDirectory(
                directory, IdProvider.sequential(), platformTable)

        val expected =
            SymbolTable.builder()
                .add(SymbolTestUtils.createSymbol("layout", "mylayout", "int", 0x7f0e0001))
                .add(SymbolTestUtils.createSymbol("id", "toolbar", "int", 0x7f0b0001))
                .build()

        assertEquals(expected, parsed)
    }

    @Test
    fun testSkippableConfigurations() {
        // Default configurations should not be skipped (and the types shouldn't be checked at this
        // point).
        assertThat(shouldBeParsed("values")).isTrue()
        assertThat(shouldBeParsed("drawables")).isTrue()
        assertThat(shouldBeParsed("fake")).isTrue()

        // If it's not a language directory it should be kept.
        assertThat(shouldBeParsed("values-v4")).isTrue()
        assertThat(shouldBeParsed("drawables-hdpi")).isTrue()
        assertThat(shouldBeParsed("drawables-hdpi-v21")).isTrue()
        assertThat(shouldBeParsed("layout-sw720dp")).isTrue()

        // Incorrect order and other configs should be skipped.
        assertThat(shouldBeParsed("values-en")).isFalse()
        assertThat(shouldBeParsed("values-en-hdpi")).isFalse()
        assertThat(shouldBeParsed("values-en-v4")).isFalse()
        assertThat(shouldBeParsed("drawable-v4-hdpi")).isFalse()
        assertThat(shouldBeParsed("drawable-hdpi-v21-watch")).isFalse()
        assertThat(shouldBeParsed("fake-fake")).isFalse()
        assertThat(shouldBeParsed("values-hdpi-fake")).isFalse()
        assertThat(shouldBeParsed("values-fake-v4")).isFalse()
    }

    private fun SymbolTable.Builder.assertParses(name: String) {
        getParseZipEntryError(name)?.let {
            throw AssertionError(
                "expected to parse without error",
                it
            )
        }
    }

    private fun SymbolTable.Builder.assertParses(name: String, content: String) {
        getParseZipEntryError(
            name,
            content
        )?.let { throw AssertionError("expected to parse without error", it) }
    }

    private fun SymbolTable.Builder.assertParsesWithError(
        name: String
    ): ThrowableSubject {
        return assertThat(
            getParseZipEntryError(name) ?: throw AssertionError("expected to have error parsing")
        )
    }

    private fun SymbolTable.Builder.assertParsesWithError(
        name: String,
        content: String
    ): ThrowableSubject {
        return assertThat(
            getParseZipEntryError(name, content)
                ?: throw AssertionError("expected to have error parsing")
        )
    }

    private fun SymbolTable.Builder.getParseZipEntryError(name: String): Exception? =
        getParseZipEntryError(name) { throw AssertionError("Expected content not to be read") }

    private fun SymbolTable.Builder.getParseZipEntryError(name: String, value: String): Exception? {
        var read = false
        try {
            return getParseZipEntryError(name) {
                read = true
                value
            }
        } finally {
            if (!read) {
                throw AssertionError("Expected content to be read")
            }
        }
    }

    private fun SymbolTable.Builder.getParseZipEntryError(
        name: String,
        content: () -> String
    ): Exception? {
        var error: Exception? = null
        this.parseAarZipEntry(
            documentBuilder,
            { error = it },
            name,
            { content().byteInputStream() }
        )
        return error
    }


}
