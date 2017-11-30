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
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DataBindingExternalArtifactDependencyTest {
    private static final String MAVEN_REPO_ARG_PREFIX = "-Ptest_maven_repo=";

    @Rule
    public GradleTestProject library =
            GradleTestProject.builder()
                    .fromDataBindingIntegrationTest("IndependentLibrary")
                    .create();

    @Rule
    public GradleTestProject app =
            GradleTestProject.builder()
                    .fromDataBindingIntegrationTest("MultiModuleTestApp")
                    .create();

    @Rule public TemporaryFolder mavenRepo = new TemporaryFolder();

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
        List<String> args = createLibraryArtifact();
        app.execute(args, "assembleDebug");
        app.execute(args, "assembleDebugAndroidTest");
    }
}
