/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.integration.connected.databinding;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.connected.utils.EmulatorUtils;
import com.android.build.gradle.options.BooleanOption;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(FilterableParameterized.class)
public class DataBindingExternalArtifactDependencyConnectedTest {
    private static final String MAVEN_REPO_ARG_PREFIX = "-Pmaven_repo=";

    @Rule public GradleTestProject libraryProject;

    @Rule public GradleTestProject appProject;

    @Rule public TemporaryFolder mavenRepo = new TemporaryFolder();

    @ClassRule public static final ExternalResource EMULATOR = EmulatorUtils.getEmulator();

    @Before
    public void setUp() throws Exception {
        // fail fast if no response
        appProject.addAdbTimeout();
        // run the uninstall tasks in order to (1) make sure nothing is installed at the beginning
        // of each test and (2) check the adb connection before taking the time to build anything.
        appProject.execute("uninstallAll");
    }

    public DataBindingExternalArtifactDependencyConnectedTest(boolean useAndroidX) {
        String useX = BooleanOption.USE_ANDROID_X.getPropertyName() + "=" + useAndroidX;
        String enableJetifier = BooleanOption.ENABLE_JETIFIER.getPropertyName() + "=" + useAndroidX;

        libraryProject =
                GradleTestProject.builder()
                        .fromDataBindingIntegrationTest("IndependentLibrary", useAndroidX)
                        .addGradleProperties(useX)
                        .create();
        appProject =
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

    @Test
    public void buildLibraryThenBuildApp_connectedCheck() {
        List<String> args = createLibraryArtifact();
        appProject.execute(args, ":app:connectedCheck");
    }

    @NonNull
    private List<String> createLibraryArtifact() {
        List<String> args =
                ImmutableList.of(MAVEN_REPO_ARG_PREFIX + mavenRepo.getRoot().getAbsolutePath());
        libraryProject.execute(args, "publishDebugPublicationToTestRepoRepository");
        return args;
    }
}
