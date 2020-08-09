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

import static com.android.SdkConstants.FN_R_CLASS_JAR;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.internal.scope.InternalArtifactType.COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.common.utils.VariantUtils;
import com.android.build.gradle.internal.scope.ArtifactTypeUtil;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidGradlePluginProjectFlags.BooleanFlag;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.JavaArtifact;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.Variant;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.truth.Truth;
import java.io.File;
import java.util.Collection;
import java.util.Map;
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

    /** Regression test for bug 133326990. */
    @Test
    public void checkRJarIsIncludedInModel() throws Exception {
        AndroidProject model = project.model().fetchAndroidProjects().getOnlyModel();

        for (Variant variant : model.getVariants()) {
            File rJar =
                    new File(
                            ArtifactTypeUtil.getOutputDir(
                                    COMPILE_AND_RUNTIME_NOT_NAMESPACED_R_CLASS_JAR.INSTANCE,
                                    project.getBuildDir()),
                            variant.getName() + "/" + FN_R_CLASS_JAR);
            assertThat(variant.getMainArtifact().getAdditionalClassesFolders()).contains(rJar);
        }
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
                project.file("build/generated/renderscript_source_output_dir/debug/out"));

        if (!project.getIntermediateFile(
                        InternalArtifactType.COMPILE_BUILD_CONFIG_JAR.INSTANCE.getFolderName())
                .exists()) {
            expectedGeneratedSourceFolders.add(
                    project.file("build/generated/source/buildConfig/debug"));
        }

        assertThat(debugArtifact.getGeneratedSourceFolders())
                .containsExactlyElementsIn(expectedGeneratedSourceFolders.build());

        assertThat(debugArtifact.getGeneratedResourceFolders())
                .containsExactly(
                        project.file("build/generated/res/resValues/debug"),
                        project.file("build/generated/res/rs/debug"));

        AndroidArtifact androidTestArtifact = VariantUtils.getAndroidTestArtifact(debugVariant);

        ImmutableList.Builder<File> expectedGeneratedTestSourceFolders = ImmutableList.builder();
        expectedGeneratedTestSourceFolders.add(
                project.file("build/generated/aidl_source_output_dir/debugAndroidTest/out"),
                project.file("build/generated/ap_generated_sources/debugAndroidTest/out"),
                project.file(
                        "build/generated/renderscript_source_output_dir/debugAndroidTest/out"));

        if (!project.getIntermediateFile(
                        InternalArtifactType.COMPILE_BUILD_CONFIG_JAR.INSTANCE.getFolderName())
                .exists()) {
            expectedGeneratedTestSourceFolders.add(
                    project.file("build/generated/source/buildConfig/androidTest/debug"));
        }

        assertThat(androidTestArtifact.getGeneratedSourceFolders())
                .containsExactlyElementsIn(expectedGeneratedTestSourceFolders.build());

        assertThat(androidTestArtifact.getGeneratedResourceFolders())
                .containsExactly(
                        project.file("build/generated/res/resValues/androidTest/debug"),
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

    @Test
    public void checkFlagsNamespacedRClassOff() throws Exception {
        AndroidProject model =
                project.model()
                        .with(BooleanOption.NON_TRANSITIVE_R_CLASS, false)
                        .with(BooleanOption.USE_NON_FINAL_RES_IDS, false)
                        .fetchAndroidProjects()
                        .getOnlyModel();
        Map<BooleanFlag, Boolean> flags = model.getFlags().getBooleanFlagMap();
        assertThat(flags.get(BooleanFlag.APPLICATION_R_CLASS_CONSTANT_IDS)).isTrue();
        assertThat(flags.get(BooleanFlag.TEST_R_CLASS_CONSTANT_IDS)).isTrue();
        assertThat(flags.get(BooleanFlag.TRANSITIVE_R_CLASS)).isTrue();
    }

    @Test
    public void checkFlagsNamespacedRClassOn() throws Exception {
        AndroidProject model =
                project.model()
                        .with(BooleanOption.NON_TRANSITIVE_R_CLASS, true)
                        .with(BooleanOption.USE_NON_FINAL_RES_IDS, true)
                        .fetchAndroidProjects()
                        .getOnlyModel();
        Map<BooleanFlag, Boolean> flags = model.getFlags().getBooleanFlagMap();
        assertThat(flags.get(BooleanFlag.APPLICATION_R_CLASS_CONSTANT_IDS)).isFalse();
        assertThat(flags.get(BooleanFlag.TEST_R_CLASS_CONSTANT_IDS)).isFalse();
        assertThat(flags.get(BooleanFlag.TRANSITIVE_R_CLASS)).isFalse();
    }

}
