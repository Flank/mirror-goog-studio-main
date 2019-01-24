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

package com.android.build.gradle.tasks;

import static com.android.testutils.truth.PathSubject.assertThat;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.truth.Truth;
import java.io.Reader;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.Test;

/** Test for {@link GenerateTestConfig}. */
public class GenerateTestConfigTest {

    @Test
    public void smokeTest() throws Exception {
        FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix());
        Path outputDir = fileSystem.getPath("outputDir");
        GenerateTestConfig.generateTestConfigFile(
                new GenerateTestConfig.TestConfigProperties(
                        null,
                        "/project/build/resources",
                        "/project/build/assets",
                        "/project/build/mergedManifest.xml",
                        "com.example.app"),
                outputDir);

        Path expectedOutputPath = outputDir.resolve("com/android/tools/test_config.properties");
        assertThat(expectedOutputPath).isFile();
        try (Reader reader = Files.newBufferedReader(expectedOutputPath)) {
            Properties result = new Properties();
            result.load(reader);
            Map<String, String> expected = new HashMap<>();
            expected.put("android_merged_resources", "/project/build/resources");
            expected.put("android_merged_assets", "/project/build/assets");
            expected.put("android_merged_manifest", "/project/build/mergedManifest.xml");
            expected.put("android_custom_package", "com.example.app");
            Truth.assertThat(result).containsExactlyEntriesIn(expected);
        }
    }

    @Test
    public void smokeTest_binaryMode() throws Exception {
        FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix());
        Path outputDir = fileSystem.getPath("outputDir");
        GenerateTestConfig.generateTestConfigFile(
                new GenerateTestConfig.TestConfigProperties(
                        "/project/build/app.ap_",
                        null,
                        "/project/build/assets",
                        "/project/build/mergedManifest.xml",
                        "com.example.app"),
                outputDir);

        Path expectedOutputPath = outputDir.resolve("com/android/tools/test_config.properties");
        assertThat(expectedOutputPath).isFile();
        try (Reader reader = Files.newBufferedReader(expectedOutputPath)) {
            Properties result = new Properties();
            result.load(reader);
            Map<String, String> expected = new HashMap<>();
            expected.put("android_resource_apk", "/project/build/app.ap_");
            expected.put("android_merged_assets", "/project/build/assets");
            expected.put("android_merged_manifest", "/project/build/mergedManifest.xml");
            expected.put("android_custom_package", "com.example.app");
            Truth.assertThat(result).containsExactlyEntriesIn(expected);
        }
    }
}
