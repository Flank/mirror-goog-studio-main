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
import static com.android.build.gradle.integration.common.fixture.app.LargeTestProject.MEDIUM_BREADTH;
import static com.android.build.gradle.integration.common.fixture.app.LargeTestProject.MEDIUM_DEPTH;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.AndroidComponentGradleModule;
import com.android.build.gradle.integration.common.fixture.app.LargeTestProject;

import org.junit.Rule;
import org.junit.Test;

/**
 * test with ~120 projects that queries the IDE model
 */
public class MediumAndroidComponentEvaluationTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(LargeTestProject.builder()
                    .withModule(AndroidComponentGradleModule.class)
                    .withDepth(MEDIUM_DEPTH)
                    .withBreadth(MEDIUM_BREADTH)
                    .create())
            .useExperimentalGradleVersion(true)
            .withHeap("2048m")
            .create();

    @Test
    public void projectsTaskRunOn120Projects() {
        project.executeWithBenchmark("MediumAndroid", EVALUATION, "projects");
    }
}
