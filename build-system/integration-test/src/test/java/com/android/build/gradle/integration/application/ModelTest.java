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
import static com.android.build.gradle.integration.common.utils.SyncIssueHelperKt.checkIssuesForSameData;
import static com.android.build.gradle.integration.common.utils.SyncIssueHelperKt.checkIssuesForSameSeverity;
import static com.android.build.gradle.integration.common.utils.SyncIssueHelperKt.checkIssuesForSameType;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
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

        Collection<SyncIssue> issues = model.getSyncIssues();
        assertThat(issues).hasSize(10);

        // all the issues should have the same type/severity/data
        checkIssuesForSameSeverity(issues, SyncIssue.SEVERITY_ERROR);
        checkIssuesForSameType(issues, SyncIssue.TYPE_UNRESOLVED_DEPENDENCY);
        checkIssuesForSameData(issues, "foo:bar:1.2.3");

        // now gather and test all the messages
        List<String> messages =
                issues.stream().map(SyncIssue::getMessage).collect(Collectors.toList());
        assertThat(messages)
                .containsExactly(
                        "Unable to resolve dependency for ':@debug/runtimeClasspath': Could not find foo:bar:1.2.3.",
                        "Unable to resolve dependency for ':@debug/compileClasspath': Could not find foo:bar:1.2.3.",
                        "Unable to resolve dependency for ':@debugAndroidTest/compileClasspath': Could not find foo:bar:1.2.3.",
                        "Unable to resolve dependency for ':@debugAndroidTest/runtimeClasspath': Could not find foo:bar:1.2.3.",
                        "Unable to resolve dependency for ':@debugUnitTest/runtimeClasspath': Could not find foo:bar:1.2.3.",
                        "Unable to resolve dependency for ':@debugUnitTest/compileClasspath': Could not find foo:bar:1.2.3.",
                        "Unable to resolve dependency for ':@release/compileClasspath': Could not find foo:bar:1.2.3.",
                        "Unable to resolve dependency for ':@release/runtimeClasspath': Could not find foo:bar:1.2.3.",
                        "Unable to resolve dependency for ':@releaseUnitTest/runtimeClasspath': Could not find foo:bar:1.2.3.",
                        "Unable to resolve dependency for ':@releaseUnitTest/compileClasspath': Could not find foo:bar:1.2.3.");
    }

    @Test
    public void unresolvedDynamicDependencies() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "dependencies {\n"
                        + "    compile 'foo:bar:+'\n"
                        + "}");
        AndroidProject model = project.model().ignoreSyncIssues().getSingle().getOnlyModel();

        Collection<SyncIssue> issues = model.getSyncIssues();
        assertThat(issues).hasSize(10);

        // all the issues should have the same type/severity/data
        checkIssuesForSameSeverity(issues, SyncIssue.SEVERITY_ERROR);
        checkIssuesForSameType(issues, SyncIssue.TYPE_UNRESOLVED_DEPENDENCY);
        checkIssuesForSameData(issues, "foo:bar:+");

        // now gather and test all the messages
        List<String> messages =
                issues.stream().map(SyncIssue::getMessage).collect(Collectors.toList());
        assertThat(messages)
                .containsExactly(
                        "Unable to resolve dependency for ':@debug/runtimeClasspath': Could not find any matches for foo:bar:+ as no versions of foo:bar are available.",
                        "Unable to resolve dependency for ':@debug/compileClasspath': Could not find any matches for foo:bar:+ as no versions of foo:bar are available.",
                        "Unable to resolve dependency for ':@debugAndroidTest/compileClasspath': Could not find any matches for foo:bar:+ as no versions of foo:bar are available.",
                        "Unable to resolve dependency for ':@debugAndroidTest/runtimeClasspath': Could not find any matches for foo:bar:+ as no versions of foo:bar are available.",
                        "Unable to resolve dependency for ':@debugUnitTest/runtimeClasspath': Could not find any matches for foo:bar:+ as no versions of foo:bar are available.",
                        "Unable to resolve dependency for ':@debugUnitTest/compileClasspath': Could not find any matches for foo:bar:+ as no versions of foo:bar are available.",
                        "Unable to resolve dependency for ':@release/compileClasspath': Could not find any matches for foo:bar:+ as no versions of foo:bar are available.",
                        "Unable to resolve dependency for ':@release/runtimeClasspath': Could not find any matches for foo:bar:+ as no versions of foo:bar are available.",
                        "Unable to resolve dependency for ':@releaseUnitTest/runtimeClasspath': Could not find any matches for foo:bar:+ as no versions of foo:bar are available.",
                        "Unable to resolve dependency for ':@releaseUnitTest/compileClasspath': Could not find any matches for foo:bar:+ as no versions of foo:bar are available.");
    }
}
