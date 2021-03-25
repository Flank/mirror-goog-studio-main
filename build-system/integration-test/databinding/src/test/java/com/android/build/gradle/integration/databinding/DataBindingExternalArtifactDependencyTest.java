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

package com.android.build.gradle.integration.databinding;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.truth.ScannerSubject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(FilterableParameterized.class)
public class DataBindingExternalArtifactDependencyTest {
    private static final String MAVEN_REPO_ARG_PREFIX = "-Pmaven_repo=";
    private final boolean useNonTransitiveR;

    @Rule public GradleTestProject library;

    @Rule public GradleTestProject app;

    @Rule public TemporaryFolder mavenRepo = new TemporaryFolder();

    public DataBindingExternalArtifactDependencyTest(boolean useNonTransitiveR) {
        String useX = BooleanOption.USE_ANDROID_X.getPropertyName() + "=" + Boolean.TRUE;
        String enableJetifier =
                BooleanOption.ENABLE_JETIFIER.getPropertyName() + "=" + Boolean.TRUE;
        this.useNonTransitiveR = useNonTransitiveR;

        library =
                GradleTestProject.builder()
                        .fromDataBindingIntegrationTest("IndependentLibrary", true)
                        .addGradleProperties(useX)
                        .create();
        app =
                GradleTestProject.builder()
                        .fromDataBindingIntegrationTest("MultiModuleTestApp", true)
                        .addGradleProperties(useX)
                        .addGradleProperties(enableJetifier)
                        .create();
    }

    @Parameterized.Parameters(name = "useNonTransitiveR_{0}")
    public static Iterable<Boolean[]> params() {
        ImmutableList.Builder<Boolean[]> builder = ImmutableList.builder();
        for (boolean useNonTransitiveR : new boolean[] {true, false}) {
            builder.add(new Boolean[] {useNonTransitiveR});
        }
        return builder.build();
    }

    @Before
    public void clean() throws IOException, InterruptedException {
        // just provide maven_repo so that build.gradle does not complain
        library.executor()
                .withArguments(ImmutableList.of(MAVEN_REPO_ARG_PREFIX + "."))
                .withFailOnWarning(false)
                .run("clean");
        app.executor()
                .withArguments(ImmutableList.of(MAVEN_REPO_ARG_PREFIX + "."))
                .withFailOnWarning(false)
                .run("clean");
    }

    @NonNull
    private List<String> createLibraryArtifact() throws IOException, InterruptedException {
        List<String> args =
                ImmutableList.of(MAVEN_REPO_ARG_PREFIX + mavenRepo.getRoot().getAbsolutePath());
        library.executor()
                .withArguments(args)
                .withFailOnWarning(false)
                .run("publishDebugPublicationToTestRepoRepository");
        return args;
    }

    @Test
    public void runTest() throws Exception {
        List<String> args = createLibraryArtifact();
        app.executor()
                .withFailOnWarning(false)
                .with(BooleanOption.NON_TRANSITIVE_R_CLASS, useNonTransitiveR)
                .withArguments(args)
                .run("assembleDebug");
        app.executor()
                .withFailOnWarning(false)
                .with(BooleanOption.NON_TRANSITIVE_R_CLASS, useNonTransitiveR)
                .withArguments(args)
                .run("assembleDebugAndroidTest");
    }

    @Test
    public void expectedMissingResources() throws Exception {
        File layout =
                FileUtils.join(
                        app.getSubproject("app").getMainResDir(),
                        "layout",
                        "layout_with_lib_res_ref_in_db.xml");
        TestFileUtils.searchAndReplace(layout, "app_string", "incorrect_string");

        List<String> args = createLibraryArtifact();
        GradleBuildResult result =
                app.executor()
                        .withFailOnWarning(false)
                        .with(BooleanOption.NON_TRANSITIVE_R_CLASS, useNonTransitiveR)
                        .withArguments(args)
                        .expectFailure()
                        .run("assembleDebug");

        try (Scanner s = result.getStderr()) {
            if (useNonTransitiveR) {
                // If we're namespacing the R class references, we'll actually verify the resources
                // during the package search step in DB, getting the error early on.
                ScannerSubject.assertThat(s)
                        .contains("Resource not found: string incorrect_string");
            } else {
                ScannerSubject.assertThat(s).contains("getString(R.string.incorrect_string)");
            }
        }
    }
}
