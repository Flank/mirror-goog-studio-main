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

package com.android.build.gradle.internal.res

import com.android.ide.common.symbols.Symbol.Companion.normalSymbol
import com.android.ide.common.symbols.SymbolIo
import com.android.ide.common.symbols.SymbolTable
import com.android.resources.ResourceType
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.Before

import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class GenerateApiPublicTxtTaskTest {

    private lateinit var dir: Path

    @Before
    fun setupFileSystem() {
        dir = Jimfs.newFileSystem(Configuration.windows()).getPath("dir")
    }

    @Test
    fun `test copies when public txt exists`() {
        Files.createDirectories(dir)

        val internalPublicTxt = dir.resolve("internalPublicTxt.txt")
        Files.write(internalPublicTxt, listOf("string public_string"))

        val symbols = dir.resolve("rdef.txt")
        Files.write(symbols, listOf("Invalid R-def file. Should not be loaded at all"))

        val externalPublicTxt = dir.resolve("public.txt")

        GenerateApiPublicTxtTask.writeFile(
            internalPublicTxt = internalPublicTxt,
            symbols = symbols,
            externalPublicTxt = externalPublicTxt
        )

        assertThat(externalPublicTxt).hasContents("string public_string")
    }

    @Test
    fun `test generates when public txt does not exist`() {
        Files.createDirectories(dir)

        val internalPublicTxt = dir.resolve("internalPublicTxt.txt")

        val symbols = dir.resolve("rdef.txt")
        SymbolIo.writeRDef(
            SymbolTable.builder()
                .tablePackage("foo.bar")
                .add(normalSymbol(ResourceType.STRING, "foo")).build(),
            symbols
        )

        val externalPublicTxt = dir.resolve("public.txt")

        GenerateApiPublicTxtTask.writeFile(
            internalPublicTxt = internalPublicTxt,
            symbols = symbols,
            externalPublicTxt = externalPublicTxt
        )

        assertThat(externalPublicTxt).hasContents("string foo")
    }

    @Test
    fun `test no resources at all`() {
        Files.createDirectories(dir)

        val internalPublicTxt = dir.resolve("internalPublicTxt.txt")
        // Not created as will not be if there are no resources

        val symbols = dir.resolve("rdef.txt")
        SymbolIo.writeRDef(
            SymbolTable.builder().tablePackage("foo.bar").build(),
            symbols
        )

        val externalPublicTxt = dir.resolve("public.txt")

        GenerateApiPublicTxtTask.writeFile(
            internalPublicTxt = internalPublicTxt,
            symbols = symbols,
            externalPublicTxt = externalPublicTxt
        )

        assertThat(externalPublicTxt).hasContents()
    }
}