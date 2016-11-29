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

import com.android.build.gradle.internal.variant.BaseVariantData;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.Assume;

/** Test variant dependencies. */
public class DependencyTest extends BaseDslTest {

    private AppPlugin plugin;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        project.apply(ImmutableMap.of("plugin", "com.android.application"));
        AppExtension android = project.getExtensions().getByType(AppExtension.class);
        android.setCompileSdkVersion(COMPILE_SDK_VERSION);
        android.setBuildToolsVersion(BUILD_TOOL_VERSION);
        plugin = project.getPlugins().getPlugin(AppPlugin.class);
    }

    public void testProvidedDependency() throws IOException {
        File providedJar = File.createTempFile("provided", ".jar");
        providedJar.createNewFile();
        providedJar.deleteOnExit();

        File debugJar = File.createTempFile("providedDebug", ".jar");
        debugJar.createNewFile();
        debugJar.deleteOnExit();

        Assume.assumeFalse(AndroidGradleOptions.isImprovedDependencyResolutionEnabled(project));

        project.getDependencies().add("provided", project.files(providedJar));
        project.getDependencies().add("debugProvided", project.files(debugJar));

        plugin.createAndroidTasks(false);

        List<BaseVariantData<?>> variants = plugin.getVariantManager().getVariantDataList();

        checkDefaultVariants(variants);

        BaseVariantData<?> release = findVariantData(variants, "release");
        assertThat(release.getVariantConfiguration().getProvidedOnlyJars())
                .containsExactly(providedJar);

        BaseVariantData<?> debug = findVariantData(variants, "debug");
        assertThat(debug.getVariantConfiguration().getProvidedOnlyJars())
                .containsExactly(providedJar, debugJar);
    }
}
