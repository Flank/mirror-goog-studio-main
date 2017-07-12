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

import static com.android.testutils.truth.MoreTruth.assertThat;

import com.android.utils.FileUtils;
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
        Path buildDirectory = fileSystem.getPath("/project", "build");
        Path outputDir = fileSystem.getPath("outputDir");
        GenerateTestConfig.generateTestConfigForOutput(
                buildDirectory.resolve("mergedAssets"),
                buildDirectory.resolve("mergedResources"),
                fileSystem.getPath("/sdk"),
                buildDirectory.resolve("mergedManifest.xml"),
                outputDir);

        Path expectedOutputPath = outputDir.resolve("com/android/tools/test_config.properties");
        assertThat(expectedOutputPath).isFile();
        try (Reader reader = Files.newBufferedReader(expectedOutputPath)) {
            Properties result = new Properties();
            result.load(reader);
            Map<String, String> expected = new HashMap<>();
            expected.put("android_sdk_home", "/sdk");
            expected.put("android_merged_resources", "/project/build/mergedResources");
            expected.put("android_merged_assets", "/project/build/mergedAssets");
            expected.put("android_merged_manifest", FileUtils.join("", "project", "build", "mergedManifest.xml"));
            Truth.assertThat(result).containsExactlyEntriesIn(expected);
        }
    }
}
