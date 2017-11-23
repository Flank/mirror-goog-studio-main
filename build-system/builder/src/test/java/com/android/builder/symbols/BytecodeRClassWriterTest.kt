/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.ide.common.symbols.Symbol
import com.android.ide.common.symbols.SymbolIo
import com.android.ide.common.symbols.SymbolJavaType
import com.android.ide.common.symbols.SymbolTable
import com.android.resources.ResourceType
import com.android.testutils.apk.Zip
import com.android.utils.PathUtils
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.lang.reflect.Field
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import javax.tools.JavaFileObject
import javax.tools.ToolProvider
import kotlin.streams.toList

class BytecodeRClassWriterTest {
    @Rule
    @JvmField
    var mTemporaryFolder = TemporaryFolder()

    @Test
    fun generateRFilesTest() {
        val rJar = mTemporaryFolder.newFile("R.jar")

        val symbols = SymbolTable.builder()
                .tablePackage("com.example.foo")
                .add(Symbol.createSymbol(ResourceType.ID,
                        "foo",
                        SymbolJavaType.INT,
                        "0"))
                .add(
                        Symbol.createSymbol(
                                ResourceType.DRAWABLE,
                                "bar",
                                SymbolJavaType.INT,
                                "1"))
                .add(
                        Symbol.createSymbol(
                                ResourceType.STYLEABLE,
                                "styles_beep",
                                SymbolJavaType.INT,
                                "2"))
                .add(
                        Symbol.createSymbol(
                                ResourceType.ATTR,
                                "beep",
                                SymbolJavaType.INT,
                                "3"))
                .add(
                        Symbol.createSymbol(
                                ResourceType.STYLEABLE,
                                "styles_boop",
                                SymbolJavaType.INT,
                                "4"))
                .add(
                        Symbol.createSymbol(
                                ResourceType.ATTR,
                                "boop",
                                SymbolJavaType.INT,
                                "5"))
                .add(
                        Symbol.createSymbol(
                                ResourceType.STYLEABLE,
                                "styles",
                                SymbolJavaType.INT_LIST,
                                "{ 2, 4 }"))
                .build()

        exportToCompiledJava(symbols, rJar.toPath())

        Zip(rJar).use {
            assertThat(it.entries).hasSize(5)

            assertThat<String, Iterable<String>>(it.entries.map { f -> f.toString() }).containsExactly(
                    "/com/example/foo/R.class",
                    "/com/example/foo/R\$id.class",
                    "/com/example/foo/R\$drawable.class",
                    "/com/example/foo/R\$styleable.class",
                    "/com/example/foo/R\$attr.class")
        }
    }

    @Test
    fun generateRFilesContentTest() {
        val javacCompiledDir = mTemporaryFolder.newFolder("javac-compiled")
        val generatedRJar = mTemporaryFolder.newFile("R.jar")
        val rDotJavaDir = mTemporaryFolder.newFolder("source")

        val symbols = SymbolTable.builder()
                .tablePackage("com.example.foo")
                .add(
                        Symbol.createSymbol(
                                ResourceType.ATTR,
                                "beep",
                                SymbolJavaType.INT,
                                "1"))
                .add(
                        Symbol.createSymbol(
                                ResourceType.ATTR,
                                "boop",
                                SymbolJavaType.INT,
                                "3"))
                .add(
                        Symbol.createSymbol(
                                ResourceType.STYLEABLE,
                                "styles",
                                SymbolJavaType.INT_LIST,
                                "{ 1004, 1002 }",
                                listOf("styles_boop", "styles_beep")))
                .add(
                        Symbol.createSymbol(
                                ResourceType.STYLEABLE,
                                "other_style",
                                SymbolJavaType.INT_LIST,
                                "{ 1004, 1002 }",
                                listOf("foo", "bar.two")))
                .build()

        // The existing path: Symbol table --exportToJava--> R.java --javac--> R classes
        // Generate the R.java file.
        val rDotJava = SymbolIo.exportToJava(symbols, rDotJavaDir, true)
        val javac = ToolProvider.getSystemJavaCompiler()
        val manager = javac.getStandardFileManager(
                null, null, null)
        // Use javac to compile R.java into R.class and R$id.class
        val source = manager.getJavaFileObjectsFromFiles(ImmutableList.of(rDotJava)) as Iterable<JavaFileObject>
        javac.getTask(null,
                manager, null,
                ImmutableList.of("-d", javacCompiledDir.absolutePath), null,
                source)
                .call()

        val expectedClasses = listOf(
                "com.example.foo.R",
                "com.example.foo.R\$styleable",
                "com.example.foo.R\$attr")

        // Sanity check
        assertThat(files(javacCompiledDir.toPath())).containsExactlyElementsIn(expectedClasses)

        // And the method under test.
        exportToCompiledJava(symbols, generatedRJar.toPath())

        Zip(generatedRJar).use {
            assertThat(it.entries.map { className(it.root.relativize(it)) })
                    .containsExactlyElementsIn(expectedClasses)
        }
        URLClassLoader(arrayOf(javacCompiledDir.toURI().toURL()), null).use { expectedClassLoader ->
            URLClassLoader(arrayOf(generatedRJar.toURI().toURL()), null).use { actualClassLoader ->
                for (javaName in expectedClasses) {
                    val expectedFields = loadFields(expectedClassLoader, javaName)
                    val actualFields = loadFields(actualClassLoader, javaName)
                    assertThat(actualFields).containsExactlyElementsIn(expectedFields)
                }
            }
        }
    }

    @Test
    fun testParseArrayLiteral() {
        assertThat(parseArrayLiteral(0, "{}").asList()).isEmpty()
        assertThat(parseArrayLiteral(1, "{70}").asList())
                .containsExactly(70)
        assertThat(parseArrayLiteral(2, "{ 71, 72 }").asList())
                .containsExactly(71, 72)
        assertThat(parseArrayLiteral(5, "{ 72, 73, 74, 71, 70 }").asList())
                .containsExactly(72, 73, 74, 71, 70)
        assertThat(parseArrayLiteral(2, "{     71    ,    72   }").asList())
                .containsExactly(71, 72)

        try {
            parseArrayLiteral(3, "{1,2}")
            fail("Expected failure")
        } catch (e: IllegalStateException) {
            // Expected.
        }

        try {
            parseArrayLiteral(1, "{1,2,3}")
            fail("Expected failure")
        } catch (e: IllegalStateException) {
            // Expected.
        }

    }

    private fun loadFields(classLoader: ClassLoader, name: String) =
            classLoader.loadClass(name)
                    .fields
                    .map { field ->
                        "${field.type.typeName} ${field.name} = ${valueAsString(field)}" }
                    .toList()

    private fun valueAsString(field: Field) = when (field.type.typeName) {
        "int" -> field.get(null)
        "int[]" -> "[" + (field.get(null) as IntArray).joinToString(",") + "]"
        else -> throw IllegalStateException("Unexpected type " + field.type.typeName)
    }

    private fun files(dir: Path) =
            Files.walk(dir)
                    .filter { Files.isRegularFile(it) }
                    .map { className(dir.relativize(it)) }
                    .toList()

    private fun className(relativePath: Path): String =
            PathUtils.toSystemIndependentPath(relativePath)
                    .removeSuffix(SdkConstants.DOT_CLASS)
                    .replace('/', '.')

}
