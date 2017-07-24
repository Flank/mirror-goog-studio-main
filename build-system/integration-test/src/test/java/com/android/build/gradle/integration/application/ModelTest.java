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

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** General Model tests */
public class ModelTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestApp(HelloWorldApp.noBuildFile()).create();

    @Before
    public void setUp() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "}");
    }

    @Test
    public void unresolvedFixedDependencies() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(), "\ndependencies {\n    compile 'foo:bar:1.2.3'\n}\n");

        AndroidProject model = project.model().ignoreSyncIssues().getSingle().getOnlyModel();
        assertThat(model)
                .hasSingleIssue(
                        SyncIssue.SEVERITY_ERROR,
                        SyncIssue.TYPE_UNRESOLVED_DEPENDENCY,
                        "foo:bar:1.2.3");
    }

    @Test
    public void unresolvedDynamicDependencies() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n" + "dependencies {\n" + "    compile 'foo:bar:+'\n" + "}");
        AndroidProject model = project.model().ignoreSyncIssues().getSingle().getOnlyModel();
        assertThat(model)
                .hasSingleIssue(
                        SyncIssue.SEVERITY_ERROR,
                        SyncIssue.TYPE_UNRESOLVED_DEPENDENCY,
                        "foo:bar:+");
    }

    @Test
    public void unresolvedMultipleDependencies() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "dependencies {\n"
                        + "    compile 'foo:bar:+'\n"
                        + "    compile 'bar:foo:1.2.3'\n"
                        + "}");
        AndroidProject model = project.model().ignoreSyncIssues().getSingle().getOnlyModel();
        assertThat(model).hasIssueSize(2);
        assertThat(model)
                .hasIssue(
                        SyncIssue.SEVERITY_ERROR,
                        SyncIssue.TYPE_UNRESOLVED_DEPENDENCY,
                        "foo:bar:+");
        assertThat(model)
                .hasIssue(
                        SyncIssue.SEVERITY_ERROR,
                        SyncIssue.TYPE_UNRESOLVED_DEPENDENCY,
                        "bar:foo:1.2.3");
    }
}
