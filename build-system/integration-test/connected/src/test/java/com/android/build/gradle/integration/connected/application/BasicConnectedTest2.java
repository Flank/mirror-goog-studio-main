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

package com.android.build.gradle.integration.connected.application;

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.connected.utils.EmulatorUtils;
import com.android.tools.bazel.avd.Emulator;
import java.io.IOException;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class BasicConnectedTest2 {

    @ClassRule public static final Emulator emulator = EmulatorUtils.getEmulator();

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("basic")
                    // b/146163513
                    .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
                    .create();

    @Before
    public void setUp() throws IOException {
        // fail fast if no response
        project.addAdbTimeOutInMs();
    }

    @Test
    public void install() throws Exception {
        project.execute("assembleDebug");
        try {
            project.execute("installDebug");
        } finally {
            project.execute("uninstallAll");
        }
    }

    @Test
    public void connectedCheck() throws Exception {
        project.executor().run("connectedAndroidTest");
    }
}
