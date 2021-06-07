/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.integration.api;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ProfileCapturer;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.TestRun;
import java.util.Collection;
import org.junit.ClassRule;
import org.junit.Test;

public class UnitTestWithGradleMetricsTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("unitTesting")
                    .enableProfileOutput()
                    .create();

    @Test
    public void testUnitTestRun() throws Exception {
        ProfileCapturer capturer = new ProfileCapturer(project, ".trk");
        Collection<AndroidStudioEvent> profiles =
                capturer.captureAndroidEvent(() -> project.execute("testDebugUnitTest"));

        TestRun testRun = profiles.stream().findFirst().get().getTestRun();
        assertThat(testRun).isNotNull();
        assertThat(testRun.getTestInvocationType())
                .isEqualTo(TestRun.TestInvocationType.GRADLE_TEST);
        assertThat(testRun.getNumberOfTestsExecuted()).isEqualTo(17);
        assertThat(testRun.getTestKind()).isEqualTo(TestRun.TestKind.UNIT_TEST);
        // That we only have junit library enabled.
        assertThat(testRun.getTestLibraries().getJunitVersion()).isEqualTo("4.12");
    }
}
