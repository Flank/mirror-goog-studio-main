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
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.JavaArtifact;
import com.android.builder.model.SyncIssue;
import com.google.common.collect.Iterables;
import java.util.Collection;
import org.junit.Rule;
import org.junit.Test;

/** General Model tests */
public class ModelTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Test
    public void unresolvedFixedDependencies() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(), "\ndependencies {\n    compile 'foo:bar:1.2.3'\n}\n");

        AndroidProject model = project.model().ignoreSyncIssues().getSingle().getOnlyModel();

        Collection<SyncIssue> issues = model.getSyncIssues();
        assertThat(issues).hasSize(1);

        SyncIssue issue = Iterables.getOnlyElement(issues);
        assertThat(issue).hasType(SyncIssue.TYPE_UNRESOLVED_DEPENDENCY);
        assertThat(issue).hasSeverity(SyncIssue.SEVERITY_ERROR);
        assertThat(issue).hasData("foo:bar:1.2.3");
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
        assertThat(issues).hasSize(1);

        SyncIssue issue = Iterables.getOnlyElement(issues);
        assertThat(issue).hasType(SyncIssue.TYPE_UNRESOLVED_DEPENDENCY);
        assertThat(issue).hasSeverity(SyncIssue.SEVERITY_ERROR);
        assertThat(issue).hasData("foo:bar:+");
    }

    /** Sanity test that makes sure no unexpected directories end up in the model. */
    @Test
    public void generatedSources() throws Exception {
        AndroidProject model = project.model().ignoreSyncIssues().getSingle().getOnlyModel();

        AndroidArtifact debugArtifact = ModelHelper.getDebugArtifact(model);

        assertThat(debugArtifact.getGeneratedSourceFolders())
                .containsExactly(
                        project.file("build/generated/source/aidl/debug"),
                        project.file("build/generated/source/apt/debug"),
                        project.file("build/generated/source/buildConfig/debug"),
                        project.file("build/generated/source/r/debug"),
                        project.file("build/generated/source/rs/debug"));

        assertThat(debugArtifact.getGeneratedResourceFolders())
                .containsExactly(
                        project.file("build/generated/res/resValues/debug"),
                        project.file("build/generated/res/rs/debug"));

        AndroidArtifact androidTestArtifact = ModelHelper.getAndroidTestArtifact(model);

        assertThat(androidTestArtifact.getGeneratedSourceFolders())
                .containsExactly(
                        project.file("build/generated/source/aidl/androidTest/debug"),
                        project.file("build/generated/source/apt/androidTest/debug"),
                        project.file("build/generated/source/buildConfig/androidTest/debug"),
                        project.file("build/generated/source/r/androidTest/debug"),
                        project.file("build/generated/source/rs/androidTest/debug"));

        assertThat(androidTestArtifact.getGeneratedResourceFolders())
                .containsExactly(
                        project.file("build/generated/res/resValues/androidTest/debug"),
                        project.file("build/generated/res/rs/androidTest/debug"));

        JavaArtifact unitTestArtifact = ModelHelper.getUnitTestArtifact(model);

        assertThat(unitTestArtifact.getGeneratedSourceFolders())
                .containsExactly(project.file("build/generated/source/apt/test/debug"));
    }
}
