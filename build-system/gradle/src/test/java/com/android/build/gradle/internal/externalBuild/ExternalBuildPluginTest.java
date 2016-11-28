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

package com.android.build.gradle.internal.externalBuild;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Test;

/** Basic tests for Blaze plugin instantiation, extension instantiation and tasks population. */
public class ExternalBuildPluginTest {

    @Test
    public void externalBuildExtensionPopulation() {
        Project project = ProjectBuilder.builder().build();
        project.apply(ImmutableMap.of("plugin", "com.android.external.build"));

        ExternalBuildExtension externalBuild =
                project.getExtensions().getByType(ExternalBuildExtension.class);

        externalBuild.setExecutionRoot("/Users/user/project");
        externalBuild.setBuildManifestPath("/usr/tmp/foo");

        assertThat(externalBuild.getBuildManifestPath()).isEqualTo("/usr/tmp/foo");
        assertThat(externalBuild.getExecutionRoot()).isEqualTo("/Users/user/project");
    }
}
