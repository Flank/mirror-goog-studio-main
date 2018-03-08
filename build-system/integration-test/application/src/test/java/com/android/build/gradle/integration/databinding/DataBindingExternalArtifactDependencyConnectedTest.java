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
import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.options.BooleanOption;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(FilterableParameterized.class)
public class DataBindingExternalArtifactDependencyConnectedTest {
    private static final String MAVEN_REPO_ARG_PREFIX = "-Ptest_maven_repo=";

    @Rule public GradleTestProject library;

    @Rule public GradleTestProject app;

    @Rule public TemporaryFolder mavenRepo = new TemporaryFolder();

    @Rule public Adb adb = new Adb();

    public DataBindingExternalArtifactDependencyConnectedTest(
            boolean libEnableV2, boolean appEnableV2) {
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

    }

    @Parameterized.Parameters(name = "use_lib_V2_{0}_use_app_V2_{1}")
    public static Iterable<Boolean[]> params() {
        return ImmutableList.of(
                new Boolean[] {false, false},
                new Boolean[] {false, true},
                new Boolean[] {true, true});
    }

    @Test
    @Category(DeviceTests.class)
    public void buildLibraryThenBuildApp_connectedCheck() throws IOException, InterruptedException {
        List<String> args = createLibraryArtifact();
        app.executeConnectedCheck(args);
    }

    @NonNull
    private List<String> createLibraryArtifact() throws IOException, InterruptedException {
        List<String> args =
                ImmutableList.of(MAVEN_REPO_ARG_PREFIX + mavenRepo.getRoot().getAbsolutePath());
        library.execute(args, "uploadArchives");
        return args;
    }
}
