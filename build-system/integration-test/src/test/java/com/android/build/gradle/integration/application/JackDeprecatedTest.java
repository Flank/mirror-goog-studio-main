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
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
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

        Collection<SyncIssue> issues = model.getSyncIssues();
        assertThat(issues).named("Sync issues").hasSize(1);

        SyncIssue singleIssue = Iterables.getOnlyElement(issues);
        assertThat(singleIssue.getData())
                .named("Sync issue data")
                .isEqualTo("JackOptions.enabled::4.0");
        assertThat(singleIssue.getSeverity())
                .named("Sync issue severity")
                .isEqualTo(SyncIssue.SEVERITY_WARNING);
        assertThat(singleIssue.getType())
                .named("Sync issue type")
                .isEqualTo(SyncIssue.TYPE_DEPRECATED_DSL);
        assertThat(singleIssue.getMessage())
                .named("Sync issue message")
                .isEqualTo(
                        "The Jack toolchain is deprecated and will not run. "
                                + "To enable support for Java 8 "
                                + "language features built into the plugin, remove "
                                + "'jackOptions { ... }' from your build.gradle file, and add\n\n"
                                + "android.compileOptions.sourceCompatibility 1.8\n"
                                + "android.compileOptions.targetCompatibility 1.8\n\n"
                                + "Future versions of the plugin will not support usage of "
                                + "'jackOptions' in build.gradle.\n"
                                + "To learn more, go to "
                                + "https://d.android.com/r/tools/java-8-support-message.html\n\n"
                                + "DSL element 'JackOptions.enabled' will be removed in version 4.0");

        GradleBuildResult result = project.executor().run("assembleDebug");
        result.getNotUpToDateTasks()
                .forEach(task -> assertThat(task.toLowerCase(Locale.US)).doesNotContain("jack"));
    }
}
