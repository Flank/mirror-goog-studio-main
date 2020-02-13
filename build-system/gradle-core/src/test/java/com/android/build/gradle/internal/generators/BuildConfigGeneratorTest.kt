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

package com.android.build.gradle.internal.generators;

import com.google.common.base.Charsets
import com.google.common.io.Files
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BuildConfigGeneratorTest {
    @get:Rule
    var mTemporaryFolder = TemporaryFolder()

    @Test
    fun testFalse() {
        val tempDir = mTemporaryFolder.newFolder()
        val buildConfigData =
            BuildConfigData.Builder()
                .setOutputPath(tempDir.toPath())
                .setBuildConfigPackageName("my.app.pkg")
                .addBooleanField("DEBUG", false)
                .build()
        val generator =
            BuildConfigGenerator(buildConfigData)
        generator.generate()
        val file = generator.generatedFilePath
        Assert.assertTrue(file.exists())
        val actual =
            Files.toString(file, Charsets.UTF_8)
        Assert.assertEquals(
            """           /**
            * Automatically generated file. DO NOT MODIFY
            */
           package my.app.pkg;

           public final class BuildConfig {
             public static final boolean DEBUG = false;
           }
           """.trimIndent(),
            actual.trim()
        )
    }

    @Test
    fun testTrue() {
        val tempDir = mTemporaryFolder.newFolder()
        val buildConfigData =
            BuildConfigData.Builder()
                .setOutputPath(tempDir.toPath())
                .setBuildConfigPackageName("my.app.pkg")
                .addBooleanField("DEBUG", true)
                .build()
        val generator =
            BuildConfigGenerator(buildConfigData)
        generator.generate()
        val file = generator.generatedFilePath
        Assert.assertTrue(file.exists())
        val actual =
            Files.toString(file, Charsets.UTF_8)
        Assert.assertEquals(
            """           /**
            * Automatically generated file. DO NOT MODIFY
            */
           package my.app.pkg;

           public final class BuildConfig {
             public static final boolean DEBUG = Boolean.parseBoolean("true");
           }
           """.trimIndent(),
            actual.trim()
        )
    }

    @Test
    fun testLong() {
        val tempDir = mTemporaryFolder.newFolder()
        val buildConfigData =
            BuildConfigData.Builder()
                .setOutputPath(tempDir.toPath())
                .setBuildConfigPackageName("my.app.pkg")
                .addLongField("TIME_STAMP", 12343434L)
                .build()
        val generator =
            BuildConfigGenerator(buildConfigData)
        generator.generate()
        val file = generator.generatedFilePath
        Assert.assertTrue(file.exists())
        val actual =
            Files.toString(file, Charsets.UTF_8)
        Assert.assertEquals(
            """           /**
            * Automatically generated file. DO NOT MODIFY
            */
           package my.app.pkg;

           public final class BuildConfig {
             public static final long TIME_STAMP = 12343434L;
           }
           """.trimIndent(),
            actual.trim()
        )
    }

    @Test
    fun testExtra() {
        val tempDir = mTemporaryFolder.newFolder()
        val buildConfigData =
            BuildConfigData.Builder()
                .setOutputPath(tempDir.toPath())
                .setBuildConfigPackageName("my.app.pkg")
                .addIntField("EXTRA", 42, "Extra line")
                .build()
        val generator =
            BuildConfigGenerator(buildConfigData)
        generator.generate()
        val file = generator.generatedFilePath
        Assert.assertTrue(file.exists())
        val actual =
            Files.toString(file, Charsets.UTF_8)
        Assert.assertEquals(
            """           /**
            * Automatically generated file. DO NOT MODIFY
            */
           package my.app.pkg;

           public final class BuildConfig {
             // Extra line
             public static final int EXTRA = 42;
           }
           """.trimIndent(),
            actual.trim()
        )
    }
}