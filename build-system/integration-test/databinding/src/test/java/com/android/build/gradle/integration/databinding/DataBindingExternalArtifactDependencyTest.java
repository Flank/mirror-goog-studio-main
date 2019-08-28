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
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.options.BooleanOption;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(FilterableParameterized.class)
public class DataBindingExternalArtifactDependencyTest {
    private static final String MAVEN_REPO_ARG_PREFIX = "-Ptest_maven_repo=";

    @Rule public GradleTestProject library;

    @Rule public GradleTestProject app;

    @Rule public TemporaryFolder mavenRepo = new TemporaryFolder();

    public DataBindingExternalArtifactDependencyTest(boolean useAndroidX) {
        String useX = BooleanOption.USE_ANDROID_X.getPropertyName() + "=" + useAndroidX;
        String enableJetifier = BooleanOption.ENABLE_JETIFIER.getPropertyName() + "=" + useAndroidX;

        library =
                GradleTestProject.builder()
                        .fromDataBindingIntegrationTest("IndependentLibrary", useAndroidX)
                        .addGradleProperties(useX)
                        .create();
        app =
                GradleTestProject.builder()
                        .fromDataBindingIntegrationTest("MultiModuleTestApp", useAndroidX)
                        .addGradleProperties(useX)
                        .addGradleProperties(enableJetifier)
                        .create();

    }

    @Parameterized.Parameters(name = "useAndroidX_{0}")
    public static Iterable<Boolean[]> params() {
        ImmutableList.Builder<Boolean[]> builder = ImmutableList.builder();
        for (boolean useAndroidX : new boolean[] {true, false}) {
            builder.add(new Boolean[] {useAndroidX});
        }
        return builder.build();
    }

    @Before
    public void clean() throws IOException, InterruptedException {
        // just provide test_maven_repo so that build.gradle does not complain
        library.execute(ImmutableList.of(MAVEN_REPO_ARG_PREFIX + "."), "clean");
        app.execute(ImmutableList.of(MAVEN_REPO_ARG_PREFIX + "."), "clean");
    }

    @NonNull
    private List<String> createLibraryArtifact() throws IOException, InterruptedException {
        List<String> args =
                ImmutableList.of(MAVEN_REPO_ARG_PREFIX + mavenRepo.getRoot().getAbsolutePath());
        library.execute(args, "uploadArchives");
        return args;
    }

    @Test
    public void runTest() throws Exception {
        List<String> args = createLibraryArtifact();
        app.execute(args, "assembleDebug");
        app.execute(args, "assembleDebugAndroidTest");
    }
}
