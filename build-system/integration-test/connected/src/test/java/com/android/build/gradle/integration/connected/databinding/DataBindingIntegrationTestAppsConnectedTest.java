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

package com.android.build.gradle.integration.connected.databinding;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.connected.utils.EmulatorUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.sdklib.SdkVersionInfo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(FilterableParameterized.class)
public class DataBindingIntegrationTestAppsConnectedTest {
    @Rule public GradleTestProject project;

    @ClassRule public static final ExternalResource EMULATOR = EmulatorUtils.getEmulator();

    public DataBindingIntegrationTestAppsConnectedTest(String projectName, boolean useAndroidX) {
        this.projectName = projectName;
        this.useAndroidX = useAndroidX;
        GradleTestProjectBuilder builder =
                GradleTestProject.builder()
                        .fromDataBindingIntegrationTest(projectName, useAndroidX)
                        .addGradleProperties(
                                BooleanOption.USE_ANDROID_X.getPropertyName() + "=" + useAndroidX)
                        .withDependencyChecker(!"KotlinTestApp".equals(projectName));
        if (SdkVersionInfo.HIGHEST_KNOWN_STABLE_API < 28 && useAndroidX) {
            builder.withCompileSdkVersion("28");
        }
        this.project = builder.create();
    }

    @Parameterized.Parameters(name = "app_{0}_useAndroidX_{1}")
    public static Iterable<Object[]> classNames() {
        List<Object[]> params = new ArrayList<>();
        for (boolean useAndroidX : new boolean[] {true, false}) {
            // b/178458738
            // params.add(new Object[] {"TestApp", useAndroidX});

            // b/177370256
            // params.add(new Object[] {"ViewBindingTestApp", useAndroidX});

            params.add(new Object[] {"AppWithDataBindingInTests", useAndroidX});
            params.add(new Object[] {"ProguardedAppWithTest", useAndroidX});
            params.add(new Object[] {"IndependentLibrary", useAndroidX});
        }
        params.add(new Object[] {"KotlinTestApp", true});
        params.add(new Object[] {"ViewBindingWithDataBindingTestApp", true});
        // b/177370256 Support version works fine
        params.add(new Object[] {"ViewBindingTestApp", false});
        return params;
    }

    String projectName;
    Boolean useAndroidX;

    @Before
    public void setUp() throws IOException {
        if ("ViewBindingTestApp".equals(projectName) && !useAndroidX) {
            // Support version has no subprojects
            project.addAdbTimeout();
        } else {
            project.addAdbTimeout();
        }

        // run the uninstall tasks in order to (1) make sure nothing is installed at the beginning
        // of each test and (2) check the adb connection before taking the time to build anything.
        project.execute("uninstallAll");
    }

    @Before
    public void clean() {
        project.execute("clean");
    }

    @Test
    public void connectedCheck() throws Exception {
        project.executor().run("connectedCheck");
    }
}
