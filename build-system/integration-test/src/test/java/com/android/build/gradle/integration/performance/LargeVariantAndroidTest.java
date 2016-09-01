/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.integration.performance;

import static com.android.build.gradle.integration.performance.BenchmarkMode.EVALUATION;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.fixture.app.VariantBuildScriptGenerator;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

/**
 * Performance test on gradle plugin with a large number of variants
 */
public class LargeVariantAndroidTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.noBuildFile())
            .useExperimentalGradleVersion(true)
            .create();

    @Before
    public void setUp() throws IOException {
        new VariantBuildScriptGenerator()
                .withNumberOfBuildTypes(VariantBuildScriptGenerator.LARGE_NUMBER)
                .withNumberOfProductFlavors(VariantBuildScriptGenerator.LARGE_NUMBER)
                .writeBuildScript(project);

        // Execute before performance test to warm up the cache.
        project.execute("help");
    }

    @Test
    public void performanceTest() {
        project.executeWithBenchmark("LargeVariantAndroid", EVALUATION, "projects");
    }
}
