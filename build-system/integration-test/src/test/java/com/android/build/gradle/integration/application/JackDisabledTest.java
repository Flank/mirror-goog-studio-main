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
import java.util.Locale;
import org.junit.Ignore;
import org.junit.Rule;

/**
 * Test to verify we emit sync warning when Jack is enabled, and that we ignore that setting. Remove
 * this test once we remove jackOptions from the DSL.
 */
public class JackDisabledTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Ignore("http://b.android.com/241060")
    public void checkWarningEmitted() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(), "android.defaultConfig.jackOptions.enabled true");

        AndroidProject model = project.model().ignoreSyncIssues().getSingle().getOnlyModel();
        assertThat(model)
                .hasSingleIssue(
                        SyncIssue.SEVERITY_WARNING,
                        SyncIssue.TYPE_GENERIC,
                        null,
                        "Jack toolchain has been deprecated, and will not run. "
                                + "Please delete the 'jackOptions { ... }' block from your build "
                                + "file, as it will be incompatible with next version of the "
                                + "Android plugin for Gradle.");

        GradleBuildResult result = project.executor().run("assembleDebug");
        result.getNotUpToDateTasks()
                .forEach(task -> assertThat(task.toLowerCase(Locale.US)).doesNotContain("jack"));
    }
}
