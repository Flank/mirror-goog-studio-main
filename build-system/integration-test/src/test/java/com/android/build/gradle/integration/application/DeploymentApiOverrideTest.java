/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.AssumeUtil;
import com.android.build.gradle.options.IntegerOption;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test to ensure that a build targeted to < 21 will still use native multidex when invoked
 * by the IDE with a build API > 21.
 */
public class DeploymentApiOverrideTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("multiDex")
            .create();

    @BeforeClass
    public static void checkBuildTools() {
        AssumeUtil.assumeBuildToolsAtLeast(21);
    }

    @Test
    public void testMultiDexOnPre21Build() throws Exception {

        GradleBuildResult lastBuild = project.executor().run("clean", "assembleIcsDebug");
        assertThat(lastBuild).isNotNull();
        assertThat(lastBuild.getStdout()).contains("Multidexlist");
    }

    @Test
    public void testMultiDexOnPost21Build() throws Exception {
        GradleBuildResult lastBuild =
                project.executor()
                        .with(IntegerOption.IDE_TARGET_DEVICE_API, 21)
                        .run("clean", "assembleIcsDebug");
        assertThat(lastBuild).isNotNull();
        assertThat(lastBuild.getStdout()).doesNotContain("Multidexlist");

    }

    @Test
    public void testMultiDexOnReleaseBuild() throws Exception {
        GradleBuildResult lastBuild =
                project.executor()
                        .with(IntegerOption.IDE_TARGET_DEVICE_API, 21)
                        .run("clean", "assembleIcsRelease");
        assertThat(lastBuild).isNotNull();
        assertThat(lastBuild.getStdout()).contains("Multidexlist");

    }
}
