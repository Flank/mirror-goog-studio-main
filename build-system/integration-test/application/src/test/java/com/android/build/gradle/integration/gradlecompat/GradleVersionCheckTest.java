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

package com.android.build.gradle.integration.gradlecompat;

import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.testutils.TestUtils;
import com.google.common.base.Throwables;
import java.net.URI;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.junit.Rule;
import org.junit.Test;

/** Tests whether the Gradle version check takes effect. */
public class GradleVersionCheckTest {

    /** An old version of Gradle to use in this test. */
    private static final String OLD_GRADLE_VERSION = "3.4.1";

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Test
    public void testGradleVersionCheck() {
        // Use an old version of Gradle, the build should fail early with a message asking users to
        // update Gradle (regression test for https://issuetracker.google.com/73383831)
        URI oldDistributionURI =
                TestUtils.getWorkspaceFile(
                                "tools/external/gradle/gradle-" + OLD_GRADLE_VERSION + "-bin.zip")
                        .toURI();
        ProjectConnection connection =
                GradleConnector.newConnector()
                        .useDistribution(oldDistributionURI)
                        .forProjectDirectory(project.getTestDir())
                        .connect();
        try {
            connection.newBuild().forTasks("help").run();
        } catch (Exception e) {
            assertThat(Throwables.getRootCause(e).getMessage())
                    .contains(
                            String.format(
                                    "Minimum supported Gradle version is %s."
                                            + " Current version is %s.",
                                    SdkConstants.GRADLE_MINIMUM_VERSION, OLD_GRADLE_VERSION));
        } finally {
            connection.close();
        }
    }
}
