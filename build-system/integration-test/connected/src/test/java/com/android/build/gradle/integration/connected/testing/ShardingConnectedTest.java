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

package com.android.build.gradle.integration.connected.testing;

import static com.android.build.gradle.integration.common.truth.ScannerSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.ScannerSubject;
import com.android.build.gradle.integration.connected.utils.EmulatorUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.IntegerOption;

import java.io.IOException;
import java.util.Scanner;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ShardingConnectedTest {

    @ClassRule public static final ExternalResource EMULATOR = EmulatorUtils.getEmulator();

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("appWithTests").create();

    @Before
    public void setUp() throws IOException {
        // fail fast if no response
        project.addAdbTimeout();
        // run the uninstall tasks in order to (1) make sure nothing is installed at the beginning
        // of each test and (2) check the adb connection before taking the time to build anything.
        project.execute("uninstallAll");
    }

    @Test
    public void connectedCheck() throws Exception {
        GradleBuildResult result = project.executor().run("connectedCheck");
        try (Scanner stdout = result.getStdout()) {
            assertThat(stdout).contains("Starting 3 tests on");
        }
    }

    @Test
    public void connectedCheckShardedOn1Device() throws Exception {
        project.executor()
                .with(BooleanOption.ENABLE_TEST_SHARDING, true)
                .run("connectedCheck");
        GradleBuildResult result = project.getBuildResult();
        try (Scanner stdoutScanner = result.getStdout()) {
            ScannerSubject stdout = assertThat(stdoutScanner);
            stdout.contains("will shard tests into 1 shards");
            stdout.contains("Starting 3 tests on");
            stdout.contains("finished 1 of estimated 3 tests");
            stdout.contains("finished 2 of estimated 3 tests");
            stdout.contains("finished 3 of estimated 3 tests");
        }
    }

    @Test
    public void connectedCheckIn7Shards() throws Exception {
        GradleBuildResult result =
                project.executor()
                        .with(BooleanOption.ENABLE_TEST_SHARDING, true)
                        .with(IntegerOption.ANDROID_TEST_SHARD_COUNT, 7)
                        .run("connectedCheck");
        try (Scanner stdout = result.getStdout()) {
            assertThat(stdout).contains("will shard tests into 7 shards");
        }
    }
}
