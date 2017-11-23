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

package com.android.build.gradle.integration.databinding;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(FilterableParameterized.class)
public class DataBindingIntegrationTestAppsTest {
    @Rule public GradleTestProject project;

    public DataBindingIntegrationTestAppsTest(String projectName) {
        project = GradleTestProject.builder().fromDataBindingIntegrationTest(projectName).create();
    }

    @Parameterized.Parameters(name = "_{0}")
    public static Iterable<String> classNames() {
        // "App With Spaces", not supported by bazel :/
        return ImmutableList.of(
                "IndependentLibrary",
                "TestApp",
                "ProguardedAppWithTest",
                "AppWithDataBindingInTests");
    }

    @Before
    public void clean() throws IOException, InterruptedException {
        project.execute("clean");
    }

    @Test
    public void compile() throws Exception {
        project.execute("assembleDebug");
        project.execute("assembleDebugAndroidTest");
    }
}
