/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ProfileCapturer;
import com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp;
import com.google.wireless.android.sdk.stats.GradleBuildProfile;
import com.google.wireless.android.sdk.stats.GradleBuildProject;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import java.util.Collection;
import org.junit.Rule;
import org.junit.Test;

/**
 * This test exists to make sure that the profiles we get back from the Android Gradle Plugin meet
 * the expectations we have for them in our benchmarking infrastructure.
 */
public class ProfileContentTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(KotlinHelloWorldApp.forPlugin("com.android.application"))
                    .enableProfileOutput()
                    .create();

    @Test
    public void testProfileProtoContentMakesSense() throws Exception {
        ProfileCapturer capturer = new ProfileCapturer(project);

        Collection<GradleBuildProfile> profiles =
                capturer.capture(
                        () -> {
                            project.model().fetchAndroidProjects();
                            project.execute("assembleDebug");
                            project.execute("assembleDebug");
                        });

        assertThat(profiles).hasSize(3);

        for (GradleBuildProfile profile : profiles) {
            assertThat(profile.getSpanCount()).isGreaterThan(0);

            assertThat(profile.getProjectCount()).isGreaterThan(0);
            GradleBuildProject gbp = profile.getProject(0);
            assertThat(gbp.getCompileSdk()).isEqualTo(GradleTestProject.getCompileSdkHash());
            assertThat(gbp.getKotlinPluginVersion()).isEqualTo(project.getKotlinVersion());

            assertThat(gbp.getVariantCount()).isGreaterThan(0);
            GradleBuildVariant gbv = gbp.getVariant(0);
            assertThat(gbv.getMinSdkVersion().getApiLevel()).isEqualTo(3);
            assertThat(gbv.hasTargetSdkVersion()).named("has target sdk version").isFalse();
            assertThat(gbv.hasMaxSdkVersion()).named("has max sdk version").isFalse();
        }
    }
}
