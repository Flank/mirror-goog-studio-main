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

package com.android.build.gradle.internal.res.namespaced

import com.android.ide.common.symbols.Symbol
import com.android.ide.common.symbols.SymbolJavaType
import com.android.ide.common.symbols.SymbolTable
import com.android.resources.ResourceType
import com.android.testutils.TestResources
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.URLClassLoader
import javax.tools.JavaFileObject
import javax.tools.ToolProvider
import kotlin.test.assertFailsWith

class NamespaceRewriterTest {

    @get:Rule
    var temporaryFolder = TemporaryFolder()

    lateinit var testClass: File
    lateinit var moduleRClass: File
    lateinit var moduleRStringClass: File
    lateinit var dependencyRClass: File
    lateinit var javacOutput: File

    @Before
    fun setUp() {
        javacOutput = temporaryFolder.newFolder("out")

        val javac = ToolProvider.getSystemJavaCompiler()
        val manager = javac.getStandardFileManager(null, null, null)

        val sources = ImmutableList.of(
                getFile("R.java"),
                getFile("Test.java"),
                getFile("dependency/R.java")
        )

        val source = manager.getJavaFileObjectsFromFiles(sources) as Iterable<JavaFileObject>
        javac.getTask(
                null,
                manager, null,
                ImmutableList.of("-d", javacOutput.absolutePath), null,
                source
        )
            .call()

        testClass = FileUtils.join(javacOutput, "com", "example", "mymodule", "Test.class")
        assertThat(testClass).exists()
        moduleRClass = FileUtils.join(javacOutput, "com", "example", "mymodule", "R.class")
        assertThat(moduleRClass).exists()
        moduleRStringClass = FileUtils.join(moduleRClass.parentFile, "R\$string.class")
        assertThat(moduleRStringClass).exists()
        dependencyRClass = FileUtils.join(javacOutput, "com", "example", "dependency", "R.class")
        assertThat(dependencyRClass).exists()
    }

    private fun getFile(name: String): File {
        return TestResources.getFile(NamespaceRewriterTest::class.java, name)
    }

    @Test
    fun noChangesWhenLeaf() {
        // Test class will contain only resources from its' own module.
        val moduleTable = SymbolTable.builder()
            .tablePackage("com.example.mymodule")
            .add(symbol("string", "s1"))
            .add(symbol("string", "s2"))
            .add(symbol("string", "s3"))
            .build()

        // Just override the existing file as we compile them per test.
        NamespaceRewriter(ImmutableList.of(moduleTable))
            .rewriteClass(testClass.toPath(), testClass.toPath())

        val urls = arrayOf(javacOutput.toURI().toURL())
        URLClassLoader(urls, null).use { classLoader ->
            val testC = classLoader.loadClass("com.example.mymodule.Test")
            val method = testC.getMethod("test")
            val result = method.invoke(null) as Int
            // Values from mymodule.R
            assertThat(result).isEqualTo(2 * 3 * 5)
        }
    }

    @Test
    fun rewritePackages() {
        // Test class contains references to its own resources as well as resources from its
        // dependencies. Only resources not defined in this module need to be rewritten.
        // Test class will contain only resources from its' own module.
        val moduleTable = SymbolTable.builder()
            .tablePackage("com.example.mymodule")
            .add(symbol("string", "s1"))
            .build()
        val dependencyTable = SymbolTable.builder()
            .tablePackage("com.example.dependency")
            .add(symbol("string", "s2"))
            .add(symbol("string", "s3"))
            .build()

        // Just override the existing file as we compile them per test.
        NamespaceRewriter(ImmutableList.of(moduleTable, dependencyTable))
            .rewriteClass(testClass.toPath(), testClass.toPath())

        val urls = arrayOf(javacOutput.toURI().toURL())
        URLClassLoader(urls, null).use { classLoader ->
            val testC = classLoader.loadClass("com.example.mymodule.Test")
            val method = testC.getMethod("test")
            val result = method.invoke(null) as Int
            // First value from mymodule.R, second and third from dependency.R
            assertThat(result).isEqualTo(2 * 11 * 13)
        }
    }

    @Test
    fun exceptionOnMissingResources() {
        val e = assertFailsWith<IllegalStateException> {
            NamespaceRewriter(ImmutableList.of()).rewriteClass(
                    testClass.toPath(),
                    testClass.toPath()
            )
        }
        assertThat(e.message).contains("Unknown symbol of type string and name s1.")
    }

    private fun symbol(type: String, name: String): Symbol {
        val resType = ResourceType.getEnum(type)!!
        var javaType = SymbolJavaType.INT
        var value = "0"
        if (resType == ResourceType.DECLARE_STYLEABLE) {
            javaType = SymbolJavaType.INT_LIST
            value = "{}"
        }
        return Symbol.createSymbol(resType, name, javaType, value)
    }
}