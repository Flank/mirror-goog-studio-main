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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class BuildConfigGeneratorTest {

    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Test
    public void testFalse() throws Exception {
        File tempDir = mTemporaryFolder.newFolder();
        BuildConfigData buildConfigData =
                new BuildConfigData.Builder()
                        .setOutputPath(tempDir.toPath())
                        .setBuildConfigPackageName("my.app.pkg")
                        .addBooleanField("DEBUG", false)
                        .build();
        BuildConfigGenerator generator = new BuildConfigGenerator(buildConfigData);
        generator.generate();

        File file = generator.getBuildConfigFile();
        assertTrue(file.exists());
        String actual = Files.toString(file, Charsets.UTF_8);
        assertEquals(
                "/**\n"
                        + " * Automatically generated file. DO NOT MODIFY\n"
                        + " */\n"
                        + "package my.app.pkg;\n"
                        + "\n"
                        + "public final class BuildConfig {\n"
                        + "  public static final boolean DEBUG = false;\n"
                        + "}\n",
                actual);
    }

    @Test
    public void testTrue() throws Exception {
        File tempDir = mTemporaryFolder.newFolder();
        BuildConfigData buildConfigData =
                new BuildConfigData.Builder()
                        .setOutputPath(tempDir.toPath())
                        .setBuildConfigPackageName("my.app.pkg")
                        .addBooleanField("DEBUG", true)
                        .build();
        BuildConfigGenerator generator = new BuildConfigGenerator(buildConfigData);
        generator.generate();

        File file = generator.getBuildConfigFile();
        assertTrue(file.exists());
        String actual = Files.toString(file, Charsets.UTF_8);
        assertEquals(
                "/**\n"
                        + " * Automatically generated file. DO NOT MODIFY\n"
                        + " */\n"
                        + "package my.app.pkg;\n"
                        + "\n"
                        + "public final class BuildConfig {\n"
                        + "  public static final boolean DEBUG = Boolean.parseBoolean(\"true\");\n"
                        + "}\n",
                actual);
    }

    @Test
    public void testLong() throws Exception {
        File tempDir = mTemporaryFolder.newFolder();
        BuildConfigData buildConfigData =
                new BuildConfigData.Builder()
                        .setOutputPath(tempDir.toPath())
                        .setBuildConfigPackageName("my.app.pkg")
                        .addLongField("TIME_STAMP", 12343434L)
                        .build();
        BuildConfigGenerator generator = new BuildConfigGenerator(buildConfigData);
        generator.generate();

        File file = generator.getBuildConfigFile();
        assertTrue(file.exists());
        String actual = Files.toString(file, Charsets.UTF_8);
        assertEquals(
                "/**\n"
                        + " * Automatically generated file. DO NOT MODIFY\n"
                        + " */\n"
                        + "package my.app.pkg;\n"
                        + "\n"
                        + "public final class BuildConfig {\n"
                        + "  public static final long TIME_STAMP = 12343434L;\n"
                        + "}\n",
                actual);
    }

    @Test
    public void testExtra() throws Exception {
        File tempDir = mTemporaryFolder.newFolder();
        BuildConfigData buildConfigData =
                new BuildConfigData.Builder()
                        .setOutputPath(tempDir.toPath())
                        .setBuildConfigPackageName("my.app.pkg")
                        .addIntField("EXTRA", 42, "Extra line")
                        .build();
        BuildConfigGenerator generator = new BuildConfigGenerator(buildConfigData);

        generator.generate();

        File file = generator.getBuildConfigFile();
        assertTrue(file.exists());
        String actual = Files.toString(file, Charsets.UTF_8);
        assertEquals(
                "/**\n"
                        + " * Automatically generated file. DO NOT MODIFY\n"
                        + " */\n"
                        + "package my.app.pkg;\n"
                        + "\n"
                        + "public final class BuildConfig {\n"
                        + "  // Extra line\n"
                        + "  public static final int EXTRA = 42;\n"
                        + "}\n",
                actual);
    }
}
