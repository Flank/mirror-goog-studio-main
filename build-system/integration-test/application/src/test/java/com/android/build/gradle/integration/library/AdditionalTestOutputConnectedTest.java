/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.integration.library;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.ScannerSubject;
import com.android.build.gradle.options.BooleanOption;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AdditionalTestOutputConnectedTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("additionalTestOutput").create();

    @Test
    @Category(DeviceTests.class)
    public void connectedCheck() throws IOException, InterruptedException {
        GradleBuildResult result =
                project.executor()
                        .with(BooleanOption.ENABLE_ADDITIONAL_ANDROID_TEST_OUTPUT, true)
                        .executeConnectedCheck();

        ScannerSubject.assertThat(result.getStdout()).contains("fetching test data data.json");

        File additionalTestOutputDir =
                new File(
                        project.getOutputDir().getAbsolutePath(),
                        "device_provider_android_test_additional_output/debugAndroidTest/devicePool");

        for (File deviceDir : additionalTestOutputDir.listFiles()) {
            File expectedOutput = new File(deviceDir, "data.json");
            assertThat(Files.readAllLines(expectedOutput.toPath()).get(0))
                    .contains("Sample text to be read by AdditionalTestOutputConnectedTest.");
        }
    }
}
