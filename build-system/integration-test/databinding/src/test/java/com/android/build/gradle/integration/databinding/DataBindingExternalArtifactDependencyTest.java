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
import com.android.build.gradle.options.BooleanOption;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.AssumptionViolatedException;
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

    private final boolean incompatible;

    public DataBindingExternalArtifactDependencyTest(boolean libEnableV2, boolean appEnableV2) {
        String libV2 = BooleanOption.ENABLE_DATA_BINDING_V2.getPropertyName() + "=" + libEnableV2;
        String appV2 = BooleanOption.ENABLE_DATA_BINDING_V2.getPropertyName() + "=" + appEnableV2;
        library =
                GradleTestProject.builder()
                        .fromDataBindingIntegrationTest("IndependentLibrary")
                        .addGradleProperties(libV2)
                        .create();
        app =
                GradleTestProject.builder()
                        .fromDataBindingIntegrationTest("MultiModuleTestApp")
                        .addGradleProperties(appV2)
                        .create();

        incompatible = libEnableV2 && !appEnableV2;
    }

    @Parameterized.Parameters(name = "use_lib_V2_{0}_use_app_V2_{1}")
    public static Iterable<Boolean[]> params() {
        return ImmutableList.of(
                new Boolean[] {false, false},
                new Boolean[] {false, true},
                new Boolean[] {true, false},
                new Boolean[] {true, true});
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
    public void compile() throws Exception {
        if (incompatible) {
            throw new AssumptionViolatedException("this tests success");
        }
        List<String> args = createLibraryArtifact();
        app.execute(args, "assembleDebug");
        app.execute(args, "assembleDebugAndroidTest");
    }

    @Test
    public void incompatibilityDetection() throws IOException, InterruptedException {
        if (!incompatible) {
            throw new AssumptionViolatedException("this tests incompatible case");
        }
        List<String> args = createLibraryArtifact();
        GradleBuildResult exception =
                app.executor().withArguments(args).expectFailure().run("assembleDebug");
        String expectedMessage =
                new IncompatibleClassChangeError("android.databinding.test.independentlibrary")
                        .getMessage();
        MatcherAssert.assertThat(
                exception.getFailureMessage(), CoreMatchers.containsString(expectedMessage));
    }
}
