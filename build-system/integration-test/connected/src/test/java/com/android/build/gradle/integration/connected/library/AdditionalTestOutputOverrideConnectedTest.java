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

package com.android.build.gradle.integration.connected.library;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.ScannerSubject;
import com.android.build.gradle.integration.connected.utils.EmulatorUtils;
import com.android.build.gradle.options.BooleanOption;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

public class AdditionalTestOutputOverrideConnectedTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("additionalTestOutputOverride").create();

    @ClassRule public static final ExternalResource EMULATOR = EmulatorUtils.getEmulator();

    @Before
    public void setUp() throws IOException {
        // fail fast if no response
        project.addAdbTimeout();
        // run the uninstall tasks in order to (1) make sure nothing is installed at the beginning
        // of each test and (2) check the adb connection before taking the time to build anything.
        project.execute("uninstallAll");
    }

    @Test
    public void connectedCheck() throws IOException, InterruptedException {
        GradleBuildResult result =
                project.executor()
                        .with(BooleanOption.ENABLE_ADDITIONAL_ANDROID_TEST_OUTPUT, true)
                        .run("connectedCheck");

        ScannerSubject.assertThat(result.getStdout()).contains("fetching test data data.json");

        File additionalTestOutputDir =
                new File(
                        project.getOutputDir().getAbsolutePath(),
                        "connected_android_test_additional_output/debugAndroidTest/connected");

        for (File deviceDir : additionalTestOutputDir.listFiles()) {
            File expectedOutput = new File(deviceDir, "data.json");
            assertThat(Files.readAllLines(expectedOutput.toPath()).get(0))
                    .contains("Sample text to be read by AdditionalTestOutputConnectedTest.");
        }
    }
}
