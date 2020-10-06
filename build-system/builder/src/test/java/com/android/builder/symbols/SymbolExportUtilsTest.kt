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

package com.android.builder.symbols

import com.android.ide.common.symbols.Symbol
import com.android.ide.common.symbols.Symbol.Companion.attributeSymbol
import com.android.ide.common.symbols.Symbol.Companion.normalSymbol
import com.android.ide.common.symbols.Symbol.Companion.styleableSymbol
import com.android.ide.common.symbols.SymbolIo
import com.android.ide.common.symbols.SymbolTable
import com.android.ide.common.symbols.SymbolTable.Companion.builder
import com.android.resources.ResourceType
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.collect.ImmutableList
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.StringWriter

class SymbolExportUtilsTest {

    @Test
    fun sanityCheckNamespacedRClass() {
        val fileSystem = Jimfs.newFileSystem(Configuration.unix())
        val symbolFileOut = fileSystem.getPath("/tmp/out/R.txt")

        val depSymbolTable =
            SymbolTable.builder()
                .tablePackage("com.example.lib")
                .add(Symbol.createSymbol(ResourceType.STRING, "libstring", 0))
                .build()
        val librarySymbols =
            SymbolTable.builder()
                .tablePackage("com.example.mylib")
                .add(Symbol.createSymbol(ResourceType.STRING, "mystring", 0))
                .build()

        val processedSymbols = processLibraryMainSymbolTable(
            finalPackageName = "com.example.mylib",
            librarySymbols = librarySymbols,
            depSymbolTables = listOf(depSymbolTable),
            platformSymbols = SymbolTable.builder().build(),
            nonTransitiveRClass = false,
            symbolFileOut = symbolFileOut
        )

        assertThat(symbolFileOut).exists()
        assertThat(symbolFileOut).hasContents(
            "int string libstring 0x7f140001",
            "int string mystring 0x7f140002"
        )

        assertThat(processedSymbols).hasSize(2)
        val myProcessedSymbols = processedSymbols[0]
        val libraryProcessedSymbols = processedSymbols[1]
        assertThat(myProcessedSymbols.tablePackage).isEqualTo("com.example.mylib")
        assertThat(myProcessedSymbols.symbols.columnKeySet())
            .containsExactly("libstring", "mystring")
        assertThat(libraryProcessedSymbols.tablePackage).isEqualTo("com.example.lib")
        assertThat(libraryProcessedSymbols.symbols.columnKeySet()).containsExactly("libstring")
    }

    @Test
    fun sanityCheckNonNamespacedRClass() {
        val fileSystem = Jimfs.newFileSystem(Configuration.unix())
        val symbolFileOut = fileSystem.getPath("/tmp/out/R.txt")

        val depSymbolTable =
            SymbolTable.builder()
                .tablePackage("com.example.lib")
                .add(Symbol.createSymbol(ResourceType.STRING, "libstring", 0))
                .build()
        val librarySymbols =
            SymbolTable.builder()
                .tablePackage("com.example.mylib")
                .add(Symbol.createSymbol(ResourceType.STRING, "mystring", 0))
                .build()

        val processedSymbols = processLibraryMainSymbolTable(
            finalPackageName = "com.example.mylib",
            librarySymbols = librarySymbols,
            depSymbolTables = listOf(depSymbolTable),
            platformSymbols = SymbolTable.builder().build(),
            nonTransitiveRClass = true,
            symbolFileOut = symbolFileOut
        )

        assertThat(symbolFileOut).exists()
        assertThat(symbolFileOut).hasContents("int string mystring 0x7f140002")

        assertThat(processedSymbols).hasSize(2)
        val myProcessedSymbols = processedSymbols[0]
        val libraryProcessedSymbols = processedSymbols[1]
        assertThat(myProcessedSymbols.tablePackage).isEqualTo("com.example.mylib")
        assertThat(myProcessedSymbols.symbols.columnKeySet()).containsExactly("mystring")
        assertThat(libraryProcessedSymbols.tablePackage).isEqualTo("com.example.lib")
        assertThat(libraryProcessedSymbols.symbols.columnKeySet()).containsExactly("libstring")
    }

    @Test
    fun testWriteSymbolListWithPackageName() {
        val librarySymbols = SymbolTable.builder()
            .tablePackage("com.example.mylib")
            .add(Symbol.createSymbol(ResourceType.STRING, "my_string", 0))
            .add(Symbol.createSymbol(ResourceType.STRING, "my_other_string", 0))
            .add(
                Symbol.createStyleableSymbol(
                    "my_styleable",
                    ImmutableList.of(),
                    ImmutableList.of("child1", "child2")
                )
            )
            .build()
        val os = ByteArrayOutputStream()
        os.writer().use { writer -> writeSymbolListWithPackageName(librarySymbols, writer) }
        assertThat(os.toString(Charsets.UTF_8.name())).isEqualTo(
            """
                com.example.mylib
                string my_other_string
                string my_string
                styleable my_styleable child1 child2

                """.trimIndent()
        )
    }

    /** See [SymbolIo.readFromPublicTxtFile] for the reading counterpart */
    @Test
    fun testPublicRFileWrite() {
        val table = builder()
            .add(attributeSymbol("color"))
            .add(attributeSymbol("size"))
            .add(normalSymbol(ResourceType.STRING, "publicString"))
            .add(normalSymbol(ResourceType.INTEGER, "value"))
            .add(styleableSymbol("myStyleable", children = ImmutableList.of("a")))
            .build()
        val writer = StringWriter()
        writePublicTxtFile(table, writer)
        assertThat(writer.toString())
            .isEqualTo(
                """
                    attr color
                    attr size
                    integer value
                    string publicString
                    styleable myStyleable

                """.trimIndent()
            )
        // (Styleable children are not included in public.txt the corresponding attrs are)
    }
}
