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

package com.android.build.gradle.internal.services

import com.android.ide.common.symbols.Symbol
import com.android.ide.common.symbols.SymbolTable
import com.android.resources.ResourceType
import com.google.common.cache.CacheBuilderSpec
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Unit tests for ClasspathBuildService */
class SymbolTableBuildServiceTest {

    @get:Rule
    val temporaryDirectory = TemporaryFolder()

    class TestCaching() : SymbolTableBuildService(STRONG_KEYED_CACHE) {
        override fun getParameters() = throw UnsupportedOperationException()
    }

    /** Smoke test for the classpath build service, check things work as expected. */
    @Test
    fun smokeTest() {
        val classpathBuildService = TestCaching()

        val file1 = fileWithContent("com.example.lib1\nstring foo")
        val content1 = classpathBuildService.loadClasspath(listOf(file1)).single()
        assertThat(content1).isEqualTo(
            SymbolTable.builder()
                .tablePackage("com.example.lib1")
                .add(Symbol.normalSymbol(ResourceType.STRING, "foo"))
                .build()
        )
    }

    @Test
    fun checkSymbolIoPersistence() {
        val classpathBuildService = TestCaching()

        val file1 = fileWithContent("com.example.lib1\nstring foo")
        val content1 = classpathBuildService.loadClasspath(listOf(file1)).single()

        // Check that loading a new file reuses the loader.
        val file2 = fileWithContent("com.example.lib2\nstring foo")
        val content2 = classpathBuildService.loadClasspath(listOf(file2)).single()

        assertWithMessage("SymbolIO should be reused")
            .that(content2.onlySymbol()).isSameInstanceAs(content1.onlySymbol())
    }

    @Test
    fun checkSymbolInternerReloading() {
        val classpathBuildService = TestCaching()
        val file1 = fileWithContent("com.example.lib1\nstring foo")
        val content1 = classpathBuildService.loadClasspath(listOf(file1)).single()

        classpathBuildService.dropSymbolInterner()

        // Check that loading a new file reuses the loader.
        val file2 = fileWithContent("com.example.lib2\nstring foo")
        val content2 = classpathBuildService.loadClasspath(listOf(file2)).single()

        assertWithMessage("Symbol interner is reinitialized correctly")
            .that(content2.onlySymbol()).isSameInstanceAs(content1.onlySymbol())
    }

    @Test
    fun checkSymbolTablePersistence() {
        val classpathBuildService = TestCaching()

        val file1 = fileWithContent("com.example.lib1\nstring foo")
        val content1 = classpathBuildService.loadClasspath(listOf(file1)).single()

        val content1Again = classpathBuildService.loadClasspath(listOf(file1)).single()

        assertWithMessage("Symbol table was persisted")
            .that(content1Again)
            .isSameInstanceAs(content1)
    }

    @Test
    fun checkSymbolTableReloading() {
        val classpathBuildService = TestCaching()

        val file1 = fileWithContent("com.example.lib1\nstring foo")
        val content1 = classpathBuildService.loadClasspath(listOf(file1)).single()

        classpathBuildService.dropSymbolTables()

        val content1Again = classpathBuildService.loadClasspath(listOf(file1)).single()

        assertThat(content1Again).isEqualTo(content1)
        assertWithMessage("Symbol table is reloaded")
            .that(content1Again)
            .isNotSameInstanceAs(content1)
        // No assertion that the symbol is or is not reloaded here, as the intern table may or may
        // not be dropped as it is held in a soft reference.
    }

    @Test
    fun checkAllReloading() {
        val classpathBuildService = TestCaching()

        val file1 = fileWithContent("com.example.lib1\nstring foo")
        val content1 = classpathBuildService.loadClasspath(listOf(file1)).single()

        classpathBuildService.dropSymbolTables()
        classpathBuildService.dropSymbolInterner()

        val content2 = classpathBuildService.loadClasspath(listOf(file1)).single()

        assertWithMessage("Symbol table is reloaded").that(content2).isNotSameInstanceAs(content1)
        assertWithMessage("Symbol intern table is dropped")
            .that(content2.onlySymbol())
            .isNotSameInstanceAs(content1.onlySymbol())
    }

    private fun SymbolTable.onlySymbol(): Symbol = symbols.values().single()
    private fun fileWithContent(content: String): File =
        temporaryDirectory.newFile().also { it.writeText(content) }

    companion object {
        val STRONG_KEYED_CACHE: CacheBuilderSpec = CacheBuilderSpec.parse("")
    }
}
