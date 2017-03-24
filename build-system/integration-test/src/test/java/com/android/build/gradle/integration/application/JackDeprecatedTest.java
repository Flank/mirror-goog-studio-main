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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test to verify we emit sync warning when Jack is enabled. Remove this test once we remove
 * jackOptions from the DSL.
 */
public class JackDeprecatedTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Test
    public void checkWarningEmitted() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(), "android.defaultConfig.jackOptions.enabled true");

        AndroidProject model = project.model().ignoreSyncIssues().getSingle().getOnlyModel();
        assertThat(model)
                .hasSingleIssue(
                        SyncIssue.SEVERITY_WARNING,
                        SyncIssue.TYPE_GENERIC,
                        null,
                        "The Jack toolchain is deprecated. To enable support for Java 8 "
                                + "language features, remove 'jackOptions { ... }' from your "
                                + "build.gradle file, and add\n\n"
                                + "android.compileOptions.sourceCompatibility 1.8\n"
                                + "android.compileOptions.targetCompatibility 1.8\n\n"
                                + "Future versions of the plugin will not support usage of "
                                + "'jackOptions' in build.gradle.\n"
                                + "To learn more, go to "
                                + "https://d.android.com/r/tools/java-8-support-message.html\n");

        // assert that we still run Jack
        GradleBuildResult result = project.executor().run("assembleDebug");
        assertThat(result.getNotUpToDateTasks())
                .contains(":transformJackAndJavaSourcesWithJackCompileForDebug");
    }
}
