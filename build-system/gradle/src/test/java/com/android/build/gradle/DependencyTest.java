/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.internal.fixture.TestConstants;
import com.android.build.gradle.internal.fixture.TestProjects;
import com.android.build.gradle.internal.fixture.VariantCheckers;
import com.android.build.gradle.internal.variant.BaseVariantData;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.gradle.api.Project;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Test variant dependencies. */
public class DependencyTest {

    @Rule public TemporaryFolder projectDirectory = new TemporaryFolder();
    private AppPlugin plugin;
    private Project project;

    @Before
    public void setUp() throws Exception {
        project =
                TestProjects.builder(projectDirectory.newFolder("project").toPath())
                        .withPlugin(TestProjects.Plugin.APP)
                        .build();

        AppExtension android = project.getExtensions().getByType(AppExtension.class);
        android.setCompileSdkVersion(TestConstants.COMPILE_SDK_VERSION);
        android.setBuildToolsVersion(TestConstants.BUILD_TOOL_VERSION);
        plugin = project.getPlugins().getPlugin(AppPlugin.class);
    }

    @Test
    public void testProvidedDependency() throws IOException {
        // Ignore if improved dependency resolution enabled
        Assume.assumeFalse(AndroidGradleOptions.isImprovedDependencyResolutionEnabled(project));

        File providedJar = File.createTempFile("provided", ".jar");
        providedJar.createNewFile();
        providedJar.deleteOnExit();

        File debugJar = File.createTempFile("providedDebug", ".jar");
        debugJar.createNewFile();
        debugJar.deleteOnExit();

        project.getDependencies().add("provided", project.files(providedJar));
        project.getDependencies().add("debugProvided", project.files(debugJar));

        plugin.createAndroidTasks(false);

        List<BaseVariantData<?>> variants = plugin.getVariantManager().getVariantDataList();

        VariantCheckers.checkDefaultVariants(variants);

        BaseVariantData<?> release = VariantCheckers.findVariantData(variants, "release");
        assertThat(release.getVariantConfiguration().getProvidedOnlyJars())
                .containsExactly(providedJar);

        BaseVariantData<?> debug = VariantCheckers.findVariantData(variants, "debug");
        assertThat(debug.getVariantConfiguration().getProvidedOnlyJars())
                .containsExactly(providedJar, debugJar);
    }
}
