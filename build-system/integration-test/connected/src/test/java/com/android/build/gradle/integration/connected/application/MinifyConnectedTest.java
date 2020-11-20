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

package com.android.build.gradle.integration.connected.application;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.connected.utils.EmulatorUtils;
import com.android.build.gradle.options.OptionalBooleanOption;
import com.android.builder.model.CodeShrinker;
import java.io.IOException;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(FilterableParameterized.class)
public class MinifyConnectedTest {

    @Parameterized.Parameters(name = "codeShrinker = {0}")
    public static CodeShrinker[] getShrinkers() {
        return new CodeShrinker[] {CodeShrinker.PROGUARD, CodeShrinker.R8};
    }

    @Parameterized.Parameter public CodeShrinker codeShrinker;

    @ClassRule public static final ExternalResource EMULATOR = EmulatorUtils.getEmulator();

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("minify").create();

    @Before
    public void setUp() throws IOException {
        // The android test runner fails with the following message when using Proguard without the
        // default Proguard file added to testProguardFiles:
        // "No tests found. This usually means that your test classes are not in the form that your
        // test runner expects (e.g. don't inherit from TestCase or lack @Test annotations)."
        if (codeShrinker == CodeShrinker.PROGUARD) {
            TestFileUtils.searchAndReplace(
                    project.getBuildFile(),
                    "testProguardFile \"test-proguard-rules.pro\"",
                    "testProguardFiles getDefaultProguardFile('proguard-android.txt'), \"test-proguard-rules.pro\"");
        }
        // fail fast if no response
        project.addAdbTimeout();
        // run the uninstall tasks in order to (1) make sure nothing is installed at the beginning
        // of each test and (2) check the adb connection before taking the time to build anything.
        project.execute("uninstallAll");
    }

    @Test
    public void connectedCheck() throws Exception {
        project.executor()
                .with(
                        OptionalBooleanOption.INTERNAL_ONLY_ENABLE_R8,
                        codeShrinker == CodeShrinker.R8)
                .run("connectedCheck");
    }
}
