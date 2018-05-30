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

    @Test
    fun rewriteValuesFile() {
        val original = temporaryFolder.newFile("values.xml")
        FileUtils.writeToFile(original, """<?xml version="1.0" encoding="UTF-8"?>
<resources>
    <string name="app_name">@string/string</string>
    <string name="string">string</string>
    <string name="activity_name">foo</string>
    <string name="activity_ref">@string/activity_name</string>

    <integer name="version_code">@integer/remote_value</integer>

    <style name="MyStyle" parent="@style/Parent">
        <item name="android:textSize">20sp</item>
        <item name="android:textColor">#008</item>
    </style>

    <style name="MyStyle2" parent="Parent">
        <item name="android:textSize">20sp</item>
        <item name="android:textColor">#008</item>
        <item name="showText">true</item>
    </style>

    <declare-styleable name="PieChart">
        <attr name="showText" format="boolean" />
        <attr name="labelPosition" format="enum">
            <enum name="left" value="0"/>
            <enum name="right" value="1"/>
        </attr>
    </declare-styleable>

</resources>""")
        val namespaced = File(temporaryFolder.newFolder("namespaced", "values") , "values.xml")
        // TODO: add mock platform attributes, styles and styleables
        val moduleTable = SymbolTable.builder()
                .tablePackage("com.example.module")
                .add(symbol("integer", "version_code"))
                .add(symbol("string", "app_name"))
                .add(symbol("string", "string")) // just make sure we don't rewrite the types
                .add(symbol("string", "activity_name")) // overrides library string
                .add(symbol("string", "activity_ref")) // to make sure we will reference the app one
                .add(symbol("attr", "showText"))
                .build()
        val dependencyTable = SymbolTable.builder()
                .tablePackage("com.example.dependency")
                .add(symbol("string", "reference"))
                .add(symbol("integer", "remote_value"))
                .add(symbol("string", "activity_name"))
                .add(symbol("style", "Parent"))
                .build()

        NamespaceRewriter(ImmutableList.of(moduleTable, dependencyTable))
                .rewriteValuesFile(original, namespaced)

        assertThat(FileUtils.loadFileWithUnixLineSeparators(namespaced)).contains(
                """<?xml version="1.0" encoding="utf-8"?>
<resources>

    <string name="app_name">@com.example.module:string/string</string>
    <string name="string">string</string>
    <string name="activity_name">foo</string>
    <string name="activity_ref">@com.example.module:string/activity_name</string>

    <integer name="version_code">@com.example.dependency:integer/remote_value</integer>

    <style name="MyStyle" parent="@com.example.dependency:style/Parent">
        <item name="android:textSize">20sp</item>
        <item name="android:textColor">#008</item>
    </style>

    <style name="MyStyle2" parent="@com.example.dependency:style/Parent">
        <item name="android:textSize">20sp</item>
        <item name="android:textColor">#008</item>
        <item name="com.example.module:showText">true</item>
    </style>

    <declare-styleable name="PieChart">
        <attr name="showText" format="boolean" />
        <attr name="labelPosition" format="enum">
            <enum name="left" value="0" />
            <enum name="right" value="1" />
        </attr>
    </declare-styleable>

</resources>""")
    }

    @Test
    fun rewriteLayoutFile() {
        val original = temporaryFolder.newFile("layout.xml")
        FileUtils.writeToFile(original, """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    xmlns:custom="http://schemas.android.com/apk/res/com.example.customviews"

    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:layout_behavior="@string/appbar_scrolling_view_behavior"
    tools:context=".MainActivity"
    tools:showIn="@layout/activity_main">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/text"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintLeft_toLeftOf="parent"
        app:layout_constraintRight_toRightOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <com.example.module.PieChart
        custom:showText="true"
        custom:labelPosition="left" />

</LinearLayout>""")
        val namespaced = File(temporaryFolder.newFolder("namespaced", "layout") , "layout.xml")
        val moduleTable = SymbolTable.builder()
                .tablePackage("com.example.module")
                .add(symbol("layout", "activity_main"))
                .add(symbol("string", "text"))
                .add(symbol("attr", "labelPosition"))
                .add(symbol("attr", "showText"))
                .build()
        val dependencyTable = SymbolTable.builder()
                .tablePackage("com.example.dependency")
                .add(symbol("string", "appbar_scrolling_view_behavior"))
                .build()
        val coordinatorlayoutTable = SymbolTable.builder()
                .tablePackage("androidx.coordinatorlayout")
                .add(symbol("attr", "layout_behavior"))
                .build()
        val constraintTable = SymbolTable.builder()
                .tablePackage("android.support.constraint")
                .add(symbol("attr", "layout_constraintBottom_toBottomOf"))
                .add(symbol("attr", "layout_constraintLeft_toLeftOf"))
                .add(symbol("attr", "layout_constraintRight_toRightOf"))
                .add(symbol("attr", "layout_constraintTop_toTopOf"))
                .build()

        NamespaceRewriter(
                ImmutableList.of(
                        moduleTable, dependencyTable, coordinatorlayoutTable, constraintTable))
                .rewriteXmlFile(original, namespaced)

        assertThat(FileUtils.loadFileWithUnixLineSeparators(namespaced)).contains(
                """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:custom="http://schemas.android.com/apk/res/com.example.customviews"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    androidx_coordinatorlayout:layout_behavior="@com.example.dependency:string/appbar_scrolling_view_behavior"
    xmlns:android_support_constraint="http://schemas.android.com/apk/res/android.support.constraint"
    xmlns:androidx_coordinatorlayout="http://schemas.android.com/apk/res/androidx.coordinatorlayout"
    xmlns:com_example_dependency="http://schemas.android.com/apk/res/com.example.dependency"
    xmlns:com_example_module="http://schemas.android.com/apk/res/com.example.module"
    tools:context=".MainActivity"
    tools:showIn="@com.example.module:layout/activity_main" >

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android_support_constraint:layout_constraintBottom_toBottomOf="parent"
        android_support_constraint:layout_constraintLeft_toLeftOf="parent"
        android_support_constraint:layout_constraintRight_toRightOf="parent"
        android_support_constraint:layout_constraintTop_toTopOf="parent"
        android:text="@com.example.module:string/text" />

    <com.example.module.PieChart
        custom:labelPosition="left"
        custom:showText="true" />

</LinearLayout>""")
    }
}