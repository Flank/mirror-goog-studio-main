/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleBuildResult
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.build.gradle.integration.common.utils.AssumeUtil
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.builder.core.AndroidBuilder
import com.android.builder.model.AndroidProject
import com.android.builder.model.SyncIssue
import com.google.common.collect.ImmutableList
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static com.google.common.truth.Truth.assertThat

/**
 * Tests to ensure that changing the build tools version in the build.gradle will trigger
 * re-execution of some tasks even if no source file change was detected.
 */
@CompileStatic
class BuildToolsTest {

    private static final String[] COMMON_TASKS = [
            ":compileDebugAidl", ":compileDebugRenderscript",
            ":mergeDebugResources", ":processDebugResources",
            ":compileReleaseAidl", ":compileReleaseRenderscript",
            ":mergeReleaseResources", ":processReleaseResources"
    ]

    private static final List<String> JAVAC_TASKS = ImmutableList.builder().add(COMMON_TASKS)
            .add(":transformClassesWithPreDexForRelease")
            .add(":transformDexWithDexForRelease")
            .build()

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.noBuildFile())
            .create()

    @Before
    public void setUp() {
        project.getBuildFile() << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
}
"""
    }

    @Test
    public void nullBuild() {
        project.executor().withUseDexArchive(false).run("assemble")
        GradleBuildResult result = project.executor().withUseDexArchive(false).run("assemble")

        assertThat(result.getUpToDateTasks()).containsAllIn(JAVAC_TASKS)
    }

    @Test
    public void invalidateBuildTools() {
        // We need at least 2 valid versions of the build tools for this test.
        AssumeUtil.assumeBuildToolsGreaterThan(AndroidBuilder.MIN_BUILD_TOOLS_REV)

        project.getBuildFile() << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion '$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION'
}
"""

        project.execute("assemble")

        String otherBuildToolsVersion = AndroidBuilder.MIN_BUILD_TOOLS_REV;
        // Sanity check:
        assertThat(otherBuildToolsVersion).isNotEqualTo(GradleTestProject.DEFAULT_BUILD_TOOL_VERSION)

        project.getBuildFile() << """
android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion '$otherBuildToolsVersion'
}
"""

        GradleBuildResult result = project.executor().run("assemble");

        assertThat(result.getInputChangedTasks()).containsAllIn(JAVAC_TASKS)
    }

    @Test
    void buildToolsInModel() {
        AndroidProject model = project.model().getSingle().getOnlyModel()
        assertThat(model.getBuildToolsVersion())
                .named("Build Tools Version")
                .isEqualTo(GradleTestProject.DEFAULT_BUILD_TOOL_VERSION)
    }

    @Test
    void buildToolsSyncIssue() {
        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "buildToolsVersion \"" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "\"",
                "buildToolsVersion '24.0.3'");
        AndroidProject model = project.model().ignoreSyncIssues().getSingle().getOnlyModel();
        TruthHelper.assertThat(model)
                .hasIssue(SyncIssue.SEVERITY_ERROR, SyncIssue.TYPE_BUILD_TOOLS_TOO_LOW);
    }
}
