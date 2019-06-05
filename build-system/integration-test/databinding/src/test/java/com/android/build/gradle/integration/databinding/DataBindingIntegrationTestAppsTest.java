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
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.options.BooleanOption;
import com.android.sdklib.SdkVersionInfo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(FilterableParameterized.class)
public class DataBindingIntegrationTestAppsTest {
    @Rule public GradleTestProject project;

    public DataBindingIntegrationTestAppsTest(String projectName, boolean useAndroidX) {
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
            params.add(new Object[] {"IndependentLibrary", useAndroidX});
            params.add(new Object[] {"TestApp", useAndroidX});
            params.add(new Object[] {"ViewBindingTestApp", useAndroidX});
            params.add(new Object[] {"ProguardedAppWithTest", useAndroidX});
            params.add(new Object[] {"AppWithDataBindingInTests", useAndroidX});
        }
        params.add(new Object[] {"KotlinTestApp", true});
        return params;
    }

    @Before
    public void clean() throws IOException, InterruptedException {
        project.execute("clean");
    }

    @Test
    public void compile() throws Exception {
        project.execute("assembleDebug", "assembleDebugAndroidTest");
    }
}
