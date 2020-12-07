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

package com.android.build.gradle.internal.generators

import com.android.testutils.apk.Zip
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path

internal class BuildConfigByteCodeGeneratorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `BuildConfig bytecode is correctly generated, given an sample build configuration`() {
        val packageFolder = temporaryFolder.newFolder("my.app.pkg")

        val buildConfigData = getSampleByteCodeData(packageFolder.toPath())
        val generator = BuildConfigByteCodeGenerator(buildConfigData)

        generator.generate()

        val buildConfigBytecodeFile = generator.generatedFilePath

        val urls = arrayOf<URL>(buildConfigBytecodeFile.toURI().toURL())
        URLClassLoader.newInstance(urls).use { urlClassLoader ->
            val loadedClass = urlClassLoader.loadClass("my.app.pkg.BuildConfig")

            val expectedFields = listOf(
                ExpectedField("java.lang.String", "APPLICATION_ID", "my.app.pkg"),
                ExpectedField("java.lang.String", "BUILD_TYPE", "debug"),
                ExpectedField("int", "VERSION_CODE", 1),
                ExpectedField("long", "TIME_STAMP", 123456789L),
                ExpectedField("java.lang.String", "VERSION_NAME", "1.0"),
                ExpectedField("boolean", "DEBUG", false)
            )
            expectedFields.forEachIndexed { index, expected ->
                val field = loadedClass.fields[index]
                assertThat(field.type.typeName).isEqualTo(expected.typeName)
                assertThat(field.name).isEqualTo(expected.name)
                assertThat(field.get(null)).isEqualTo(expected.value)
            }
        }
    }

    private class ExpectedField(val typeName: String, val name: String, val value: Any)

    @Test
    fun `Check JAR contains expected classes`() {
        val packageFolder = temporaryFolder.newFolder("my.app.pkg")

        val buildConfigData = getSampleByteCodeData(packageFolder.toPath())
        val generator = BuildConfigByteCodeGenerator(buildConfigData)
        generator.generate()
        val bytecodeJar = generator.generatedFilePath

        Zip(bytecodeJar).use {
            assertThat(it.entries).hasSize(1)

            assertThat(it.entries.map { f -> f.toString() })
                    .containsExactly("/my/app/pkg/BuildConfig.class")
        }
    }

    private fun getSampleByteCodeData(packageFolder: Path): BuildConfigData =
            BuildConfigData.Builder()
                    .setOutputPath(packageFolder)
                    .setNamespace(packageFolder.toFile().name)
                    .setBuildConfigName("BuildConfig")
                    .addStringField("APPLICATION_ID", "my.app.pkg")
                    .addStringField("BUILD_TYPE", "debug")
                    .addIntField("VERSION_CODE", 1)
                    .addLongField("TIME_STAMP", 123456789L)
                    .addStringField("VERSION_NAME", "1.0")
                    .addBooleanField("DEBUG", false)
                    .build()
}
