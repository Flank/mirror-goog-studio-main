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

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.SyncIssue;
import com.google.common.io.Files;
import java.nio.charset.StandardCharsets;
import org.junit.Rule;
import org.junit.Test;

public class PostprocessingTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Test
    public void model_syncErrors() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android.buildTypes.release { postprocessing.removeUnusedCode = true; minifyEnabled = true\n }");

        AndroidProject model = project.model().ignoreSyncIssues().getSingle().getOnlyModel();
        TruthHelper.assertThat(model)
                .hasSingleIssue(
                        SyncIssue.SEVERITY_ERROR, SyncIssue.TYPE_GENERIC, "setMinifyEnabled");
    }

    @Test
    public void model_correctValue() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android.buildTypes.release { postprocessing.removeUnusedCode = true\n }");

        AndroidProject model = project.model().getSingle().getOnlyModel();
        TruthHelper.assertThat(model).hasIssueSize(0);

        BuildTypeContainer buildTypeContainer =
                model.getBuildTypes()
                        .stream()
                        .filter(c -> c.getBuildType().getName().equals("release"))
                        .findFirst()
                        .orElse(null);

        assertThat(buildTypeContainer).named("release build type container").isNotNull();

        //noinspection deprecation: we're testing the old method returns a value that makes sense.
        assertThat(buildTypeContainer.getBuildType().isMinifyEnabled())
                .named("isMinifyEnabled()")
                .isTrue();
    }

    @Test
    public void features_oldDsl() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android.buildTypes.release {\n"
                        + "minifyEnabled true\n"
                        + "proguardFiles android.getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'\n"
                        + "}\n");

        Files.write(
                "-printconfiguration build/proguard-config.txt",
                project.file("proguard-rules.pro"),
                StandardCharsets.UTF_8);

        project.execute("assembleRelease");

        String proguardConfiguration =
                Files.toString(project.file("build/proguard-config.txt"), StandardCharsets.UTF_8);

        assertThat(proguardConfiguration).contains("-dontoptimize");
        assertThat(proguardConfiguration).doesNotContain("-dontshrink");
        assertThat(proguardConfiguration).doesNotContain("-dontobfuscate");
    }

    @Test
    public void features_newDsl() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android.buildTypes.release.postprocessing {\n"
                        + "removeUnusedCode true\n"
                        + "proguardFile 'proguard-rules.pro'\n"
                        + "}\n");

        Files.write(
                "-printconfiguration build/proguard-config.txt",
                project.file("proguard-rules.pro"),
                StandardCharsets.UTF_8);

        project.execute("assembleRelease");

        String proguardConfiguration =
                Files.toString(project.file("build/proguard-config.txt"), StandardCharsets.UTF_8);

        assertThat(proguardConfiguration).doesNotContain("-dontshrink");
        assertThat(proguardConfiguration).contains("-dontoptimize");
        assertThat(proguardConfiguration).contains("-dontobfuscate");

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android.buildTypes.release.postprocessing.optimizeCode true");

        project.execute("assembleRelease");

        proguardConfiguration =
                Files.toString(project.file("build/proguard-config.txt"), StandardCharsets.UTF_8);

        assertThat(proguardConfiguration).doesNotContain("-dontshrink");
        assertThat(proguardConfiguration).doesNotContain("-dontoptimize");
        assertThat(proguardConfiguration).contains("-dontobfuscate");
    }

    @Test
    public void configFilesValidation() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android.buildTypes.release.postprocessing {\n"
                        + "removeUnusedCode true\n"
                        + "proguardFile 'proguard-rules.pro'\n"
                        + "}\n");

        Files.write("-dontshrink", project.file("proguard-rules.pro"), StandardCharsets.UTF_8);

        GradleBuildResult result = project.executor().expectFailure().run("assembleRelease");

        assertThat(result.getFailureMessage())
                .contains("postprocessing features are configured in the DSL");
    }
}
