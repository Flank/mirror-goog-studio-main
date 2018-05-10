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

import com.android.ide.common.symbols.SymbolTable
import com.android.testutils.TestResources
import com.android.testutils.truth.FileSubject
import com.android.testutils.truth.PathSubject.assertThat
import com.android.tools.build.apkzlib.zip.ZFile
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.URLClassLoader
import kotlin.test.assertFailsWith

class NamespaceRewriterTest {

    @get:Rule
    var temporaryFolder = TemporaryFolder()

    private lateinit var testClass: File
    private lateinit var test2Class: File
    private lateinit var moduleRClass: File
    private lateinit var moduleRStringClass: File
    private lateinit var dependencyRClass: File
    private lateinit var dependencyRStringClass: File
    private lateinit var javacOutput: File

    private fun setBytecodeUp() {
        javacOutput = temporaryFolder.newFolder("out")

        compileSources(
                ImmutableList.of(
                        getFile("R.java"),
                        getFile("Test.java"),
                        getFile("Test2.java"),
                        getFile("dependency/R.java")
                ),
                javacOutput
        )

        testClass = FileUtils.join(javacOutput, "com", "example", "mymodule", "Test.class")
        assertThat(testClass).exists()
        test2Class = FileUtils.join(javacOutput, "com", "example", "mymodule", "Test2.class")
        assertThat(test2Class).exists()
        moduleRClass = FileUtils.join(javacOutput, "com", "example", "mymodule", "R.class")
        assertThat(moduleRClass).exists()
        moduleRStringClass = FileUtils.join(moduleRClass.parentFile, "R\$string.class")
        assertThat(moduleRStringClass).exists()
        dependencyRClass = FileUtils.join(javacOutput, "com", "example", "dependency", "R.class")
        assertThat(dependencyRClass).exists()
        dependencyRStringClass = FileUtils.join(
                javacOutput,
                "com",
                "example",
                "dependency",
                "R\$string.class"
        )
        assertThat(dependencyRStringClass).exists()
    }

    private fun getFile(name: String): File {
        return TestResources.getFile(NamespaceRewriterTest::class.java, name)
    }

    @Test
    fun noChangesWhenLeaf() {
        setBytecodeUp()
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
        setBytecodeUp()
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
        val secondDependencyTable = SymbolTable.builder()
                .tablePackage("com.example.libA")
                .add(symbol("string", "s2"))
                .build()
        val thirdDependencyTable = SymbolTable.builder()
                .tablePackage("com.example.libB")
                .add(symbol("string", "s1"))
                .add(symbol("string", "s2"))
                .build()

        val logger = MockLogger()
        // Just override the existing file as we compile them per test.
        NamespaceRewriter(
                ImmutableList.of(
                        moduleTable,
                        dependencyTable,
                        secondDependencyTable,
                        thirdDependencyTable
                ),
                logger
        )
                .rewriteClass(testClass.toPath(), testClass.toPath())

        assertThat(logger.warnings).hasSize(2)
        assertThat(logger.warnings[0]).contains(
                "In package com.example.mymodule multiple options found in its dependencies for " +
                        "resource string s1. Using com.example.mymodule, other available: " +
                        "com.example.libB"
        )
        assertThat(logger.warnings[1]).contains(
                "In package com.example.mymodule multiple options found in its dependencies for " +
                        "resource string s2. Using com.example.dependency, other available: " +
                        "com.example.libA, com.example.libB"
        )

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
        setBytecodeUp()
        val e = assertFailsWith<IllegalStateException> {
            val symbols = SymbolTable.builder().tablePackage("my.example.lib").build()
            NamespaceRewriter(ImmutableList.of(symbols)).rewriteClass(
                    testClass.toPath(),
                    testClass.toPath()
            )
        }
        assertThat(e.message).contains(
                "In package my.example.lib found unknown symbol of type string and name s1."
        )
    }

    @Test
    fun rewriteJar() {
        setBytecodeUp()
        val aarsDir = temporaryFolder.newFolder("aars")
        val inputJar = File(aarsDir, "classes.jar")
        val outputJar = File(aarsDir, "namespaced-classes.jar")

        ZFile(inputJar).use {
            it.add("com/example/mymodule/Test.class", testClass.inputStream())
            it.add("com/example/mymodule/Test2.class", test2Class.inputStream())
        }

        val moduleTable = SymbolTable.builder()
                .tablePackage("com.example.mymodule")
                .add(symbol("string", "s1"))
                .build()
        val dependencyTable = SymbolTable.builder()
                .tablePackage("com.example.dependency")
                .add(symbol("string", "s2"))
                .add(symbol("string", "s3"))
                .build()

        NamespaceRewriter(ImmutableList.of(moduleTable, dependencyTable)).rewriteJar(
                inputJar,
                outputJar
        )
        assertThat(outputJar).exists()
        ZFile(outputJar).use {
            it.add("com/example/mymodule/R.class", moduleRClass.inputStream())
            it.add("com/example/mymodule/R\$string.class", moduleRStringClass.inputStream())
            it.add("com/example/dependency/R.class", dependencyRClass.inputStream())
            it.add("com/example/dependency/R\$string.class", dependencyRStringClass.inputStream())
        }

        URLClassLoader(arrayOf(outputJar.toURI().toURL()), null).use { classLoader ->
            var testC = classLoader.loadClass("com.example.mymodule.Test")
            var method = testC.getMethod("test")
            var result = method.invoke(null) as Int
            assertThat(result).isEqualTo(2 * 11 * 13)
            testC = classLoader.loadClass("com.example.mymodule.Test2")
            method = testC.getMethod("test2")
            result = method.invoke(null) as Int
            assertThat(result).isEqualTo(2 * 11 * 13 + 2 + 11 + 13)
        }
    }

    @Test
    fun rewriteManifest() {
        val originalManifest = temporaryFolder.newFile("AndroidManifest.xml")
        FileUtils.writeToFile(originalManifest, """<?xml version="1.0" encoding="UTF-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.module"
    android:versionCode="@integer/version_code"
    android:versionName="@string/version_name">

    <application android:label="@string/app_name"
        android:icon="@drawable/ic_launcher"
        android:theme="@style/Theme.Simple"
        android:allowBackup="true">

        <activity android:name="@string/activity_name"
            android:label="@string/app_name">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>""")
        val outputManifest = temporaryFolder.newFile("com.foo.bar.example_AndroidManifest.xml")
        val moduleTable = SymbolTable.builder()
                .tablePackage("com.example.module")
                .add(symbol("integer", "version_code"))
                .add(symbol("string", "version_name"))
                .add(symbol("string", "activity_name")) // overrides library string
                .build()
        val dependencyTable = SymbolTable.builder()
                .tablePackage("com.example.dependency")
                .add(symbol("string", "app_name"))
                .add(symbol("drawable", "ic_launcher"))
                .add(symbol("style", "Theme_Simple")) // Canonicalized here, but not in the manifest
                .add(symbol("string", "activity_name")) // overridden by the one in the module
                .build()

        NamespaceRewriter(ImmutableList.of(moduleTable, dependencyTable))
                .rewriteManifest(originalManifest, outputManifest)

        assertThat(FileUtils.loadFileWithUnixLineSeparators(outputManifest)).contains(
            """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.module"
    android:versionCode="@com.example.module:integer/version_code"
    android:versionName="@com.example.module:string/version_name" >

    <application
        android:allowBackup="true"
        android:icon="@com.example.dependency:drawable/ic_launcher"
        android:label="@com.example.dependency:string/app_name"
        android:theme="@com.example.dependency:style/Theme.Simple" >
        <activity
            android:name="@com.example.module:string/activity_name"
            android:label="@com.example.dependency:string/app_name" >
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />

                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>""")
    }
}