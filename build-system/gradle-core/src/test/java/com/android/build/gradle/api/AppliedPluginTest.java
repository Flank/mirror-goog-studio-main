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

package com.android.build.gradle.api;

import static com.android.build.gradle.internal.utils.GradlePluginUtils.ANDROID_GRADLE_PLUGIN_ID;
import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.internal.fixture.TestProjects;
import com.android.build.gradle.internal.plugins.AppPlugin;
import com.android.build.gradle.internal.plugins.LibraryPlugin;
import com.android.build.gradle.internal.plugins.TestPlugin;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Test to ensure all plugins applies the AndroidBasePlugin. */
@RunWith(Parameterized.class)
public class AppliedPluginTest {

    @Parameterized.Parameters(name = "plugin_{1}")
    public static List<Object[]> parameters() {
        return Arrays.asList(
                new Object[][] {
                    {AppPlugin.class, "com.android.application"},
                    {AppPlugin.class, "android"},
                    {LibraryPlugin.class, "com.android.library"},
                    {LibraryPlugin.class, "android-library"},
                    {TestPlugin.class, "com.android.test"},
                });
    }

    @Parameterized.Parameter public Class<? extends Plugin> pluginClass;

    @Parameterized.Parameter(1)
    public String pluginName;

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testAppliedPlugin() throws IOException {
        Project project =
                ProjectBuilder.builder().withProjectDir(temporaryFolder.newFolder()).build();
        TestProjects.prepareProject(project, ImmutableMap.of());
        project.getPluginManager().apply(pluginName);
        assertThat(project.getPlugins().findPlugin(pluginName)).isNotNull();
        assertThat(project.getPlugins().findPlugin(pluginClass)).isNotNull();
        assertThat(project.getPlugins().findPlugin(AndroidBasePlugin.class)).isNotNull();
        assertThat(project.getPlugins().findPlugin(ANDROID_GRADLE_PLUGIN_ID)).isNotNull();
    }
}
