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

import com.android.build.api.variant.BuildConfigField
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.base.Charsets
import com.google.common.io.Files
import com.google.common.truth.Truth.assertThat
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
                .setNamespace("my.app.pkg")
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
                .setNamespace("my.app.pkg")
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
    fun testDebugTrueWithStringValue() {
        val tempDir = mTemporaryFolder.newFolder()
        val buildConfigData =
            BuildConfigData.Builder()
                .setOutputPath(tempDir.toPath())
                .setNamespace("my.app.pkg")
                .addItem("DEBUG", BuildConfigField("boolean", "true", null))
                .build()
        val generator =
            BuildConfigGenerator(buildConfigData)
        generator.generate()
        val file = generator.generatedFilePath
        assertThat(file).exists()
        val actual = Files.asCharSource(file, Charsets.UTF_8).read().trim()
        assertThat(actual).isEqualTo(
            """
               /**
                * Automatically generated file. DO NOT MODIFY
                */
               package my.app.pkg;

               public final class BuildConfig {
                 public static final boolean DEBUG = Boolean.parseBoolean("true");
               }
           """.trimIndent())
    }


    @Test
    fun testLong() {
        val tempDir = mTemporaryFolder.newFolder()
        val buildConfigData =
            BuildConfigData.Builder()
                .setOutputPath(tempDir.toPath())
                .setNamespace("my.app.pkg")
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
                .setNamespace("my.app.pkg")
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

    @Test
    fun testTypeSpecialHandling() {
        val tempDir = mTemporaryFolder.newFolder()
        val buildConfigData =
            BuildConfigData.Builder()
                .setOutputPath(tempDir.toPath())
                .setNamespace("my.app.pkg")
                .addItem("COMMENT", BuildConfigField("String", "\"String with comment\"", "A string!"))
                .addItem("INT1", BuildConfigField("int", 1, null))
                .addItem("INT2", BuildConfigField("int", "2", null))
                .addItem("LONG1", BuildConfigField("long", 3, null))
                .addItem("LONG2", BuildConfigField("long", 4L, null))
                .addItem("LONG3", BuildConfigField("long", "5", null))
                .addItem("LONG4", BuildConfigField("long", "6L", null))
                .addItem("LONG5", BuildConfigField("long", "calculateLong()", null))
                .addItem("BOOLEAN1", BuildConfigField("boolean", "true", null))
                .addItem("BOOLEAN2", BuildConfigField("boolean", "false", null))
                .addItem("BOOLEAN3", BuildConfigField("boolean", true, null))
                .addItem("BOOLEAN4", BuildConfigField("boolean", false, null))
                .addItem("FLOAT1", BuildConfigField("float", 7.7f, null))
                .addItem("FLOAT2", BuildConfigField("float", 8.8, null))
                .addItem("FLOAT3", BuildConfigField("float", "9.9", null))
                .addItem("FLOAT3", BuildConfigField("float", "9.9f", null))
                .addItem("FLOAT4", BuildConfigField("float", "calculateFloat()", null))
                .addItem("FLOAT5", BuildConfigField("float", "10", null))
                .addItem("FLOAT6", BuildConfigField("float", 11, null))
                .addItem("DOUBLE1", BuildConfigField("double", 12.12f, null))
                .addItem("DOUBLE2", BuildConfigField("double", 13.13, null))
                .addItem("DOUBLE3", BuildConfigField("double", "14.14", null))
                .addItem("DOUBLE4", BuildConfigField("double", "calculateDouble()", null))
                .addItem("STRING1", BuildConfigField("String", "\"String 1\"", null))
                .addItem("STRING2", BuildConfigField("String", "calculateString()", null))
                .addItem("STRING_ARRAY", BuildConfigField("String[]", "new String[]{}", null))
                .build()
        val generator = BuildConfigGenerator(buildConfigData)
        generator.generate()
        val file = generator.generatedFilePath
        assertThat(file).exists()
        val actual = Files.asCharSource(file, Charsets.UTF_8).read().trim()
        assertThat(actual).isEqualTo(
            """
                /**
                 * Automatically generated file. DO NOT MODIFY
                 */
                package my.app.pkg;

                public final class BuildConfig {
                  // A string!
                  public static final String COMMENT = "String with comment";
                  public static final int INT1 = 1;
                  public static final int INT2 = 2;
                  public static final long LONG1 = 3L;
                  public static final long LONG2 = 4L;
                  public static final long LONG3 = 5L;
                  public static final long LONG4 = 6L;
                  public static final long LONG5 = calculateLong();
                  public static final boolean BOOLEAN1 = true;
                  public static final boolean BOOLEAN2 = false;
                  public static final boolean BOOLEAN3 = true;
                  public static final boolean BOOLEAN4 = false;
                  public static final float FLOAT1 = 7.7f;
                  public static final float FLOAT2 = 8.8f;
                  public static final float FLOAT3 = 9.9f;
                  public static final float FLOAT4 = calculateFloat();
                  public static final float FLOAT5 = 10f;
                  public static final float FLOAT6 = 11f;
                  public static final double DOUBLE1 = 12.12;
                  public static final double DOUBLE2 = 13.13;
                  public static final double DOUBLE3 = 14.14;
                  public static final double DOUBLE4 = calculateDouble();
                  public static final String STRING1 = "String 1";
                  public static final String STRING2 = calculateString();
                  public static final String[] STRING_ARRAY = new String[]{};
                }
            """.trimIndent())
    }
}
