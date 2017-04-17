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

import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.SplitScope;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.ide.common.build.ApkData;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.Reader;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Test for {@link GenerateTestConfig}. */
public class GenerateTestConfigTest {

    @Mock SplitScope splitScope;

    @Mock ApkData apkData;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void smokeTest() throws Exception {
        Project project = ProjectBuilder.builder().build();
        FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix());
        Path buildDirectory = fileSystem.getPath("/project", "build");

        GenerateTestConfig generateTestConfig =
                project.getTasks().create("generateTestConfig", GenerateTestConfig.class);
        generateTestConfig.resourcesDirectory = buildDirectory.resolve("mergedResources");
        generateTestConfig.assetsDirectory = buildDirectory.resolve("mergedAssets");
        generateTestConfig.manifests = project.files();
        generateTestConfig.sdkHome = fileSystem.getPath("/sdk");
        generateTestConfig.generatedJavaResourcesDirectory =
                buildDirectory.resolve("generatedJavaResources");
        generateTestConfig.splitScope = splitScope;

        generateTestConfig.generateTestConfigForOutput(
                new BuildOutput(
                        TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS,
                        apkData,
                        new File(buildDirectory.resolve("mergedManifest.xml").toString())));

        Path expectedOutputPath =
                fileSystem.getPath(
                        "/project/build/generatedJavaResources/com/android/tools/test_config.properties");
        assertThat(expectedOutputPath).isFile();
        try (Reader reader = Files.newBufferedReader(expectedOutputPath)) {
            Properties result = new Properties();
            result.load(reader);
            Map<String, String> expected = new HashMap<>();
            expected.put("android_sdk_home", "/sdk");
            expected.put("android_merged_resources", "/project/build/mergedResources");
            expected.put("android_merged_assets", "/project/build/mergedAssets");
            expected.put("android_merged_manifest", "/project/build/mergedManifest.xml");
            Truth.assertThat(result).containsExactlyEntriesIn(expected);
        }
    }
}
