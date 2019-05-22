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
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.common.utils.VariantUtils;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.JavaArtifact;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.Variant;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.truth.Truth;
import java.io.File;
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
                project.getBuildFile(), "\ndependencies {\n    api 'foo:bar:1.2.3'\n}\n");

        Collection<SyncIssue> issues =
                project.model().ignoreSyncIssues().fetchAndroidProjects().getOnlyModelSyncIssues();

        assertThat(issues).hasSize(1);

        SyncIssue issue = Iterables.getOnlyElement(issues);
        assertThat(issue).hasType(SyncIssue.TYPE_UNRESOLVED_DEPENDENCY);
        assertThat(issue).hasSeverity(SyncIssue.SEVERITY_ERROR);
        assertThat(issue).hasData("foo:bar:1.2.3");
    }

    @Test
    public void unresolvedDynamicDependencies() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(), "\n" + "dependencies {\n" + "    api 'foo:bar:+'\n" + "}");

        Collection<SyncIssue> issues =
                project.model().ignoreSyncIssues().fetchAndroidProjects().getOnlyModelSyncIssues();
        assertThat(issues).hasSize(1);

        SyncIssue issue = Iterables.getOnlyElement(issues);
        assertThat(issue).hasType(SyncIssue.TYPE_UNRESOLVED_DEPENDENCY);
        assertThat(issue).hasSeverity(SyncIssue.SEVERITY_ERROR);
        assertThat(issue).hasData("foo:bar:+");
    }

    /** Sanity test that makes sure no unexpected directories end up in the model. */
    @Test
    public void generatedSources() throws Exception {
        AndroidProject model =
                project.model().ignoreSyncIssues().fetchAndroidProjects().getOnlyModel();

        final Variant debugVariant = AndroidProjectUtils.getVariantByName(model, "debug");
        AndroidArtifact debugArtifact = debugVariant.getMainArtifact();

        ImmutableList.Builder<File> expectedGeneratedSourceFolders = ImmutableList.builder();
        expectedGeneratedSourceFolders.add(
                project.file("build/generated/aidl_source_output_dir/debug/out"),
                project.file("build/generated/ap_generated_sources/debug/out"),
                project.file("build/generated/source/buildConfig/debug"),
                project.file("build/generated/renderscript_source_output_dir/debug/out"));

        assertThat(debugArtifact.getGeneratedSourceFolders())
                .containsExactlyElementsIn(expectedGeneratedSourceFolders.build());

        // Res values are not included as Studio models them directly
        assertThat(debugArtifact.getGeneratedResourceFolders())
                .containsExactly(project.file("build/generated/res/rs/debug"));

        AndroidArtifact androidTestArtifact = VariantUtils.getAndroidTestArtifact(debugVariant);

        ImmutableList.Builder<File> expectedGeneratedTestSourceFolders = ImmutableList.builder();
        expectedGeneratedTestSourceFolders.add(
                project.file("build/generated/aidl_source_output_dir/debugAndroidTest/out"),
                project.file("build/generated/ap_generated_sources/debugAndroidTest/out"),
                project.file("build/generated/source/buildConfig/androidTest/debug"),
                project.file(
                        "build/generated/renderscript_source_output_dir/debugAndroidTest/out"));

        assertThat(androidTestArtifact.getGeneratedSourceFolders())
                .containsExactlyElementsIn(expectedGeneratedTestSourceFolders.build());

        assertThat(androidTestArtifact.getGeneratedResourceFolders())
                .containsExactly(
                        project.file("build/generated/res/rs/androidTest/debug"));

        JavaArtifact unitTestArtifact = VariantUtils.getUnitTestArtifact(debugVariant);

        assertThat(unitTestArtifact.getGeneratedSourceFolders())
                .containsExactly(
                        project.file("build/generated/ap_generated_sources/debugUnitTest/out"));
    }

    @Test
    public void returnsInstrumentedTestTaskName() throws Exception {
        AndroidProject model = project.model().fetchAndroidProjects().getOnlyModel();

        final Variant debugVariant = AndroidProjectUtils.getVariantByName(model, "debug");
        AndroidArtifact androidTestArtifact = VariantUtils.getAndroidTestArtifact(debugVariant);

        Truth.assertThat(androidTestArtifact.getInstrumentedTestTaskName())
                .isEqualTo("connectedDebugAndroidTest");
    }
}
