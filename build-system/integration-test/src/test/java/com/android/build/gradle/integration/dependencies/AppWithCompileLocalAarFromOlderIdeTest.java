/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.integration.dependencies;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;
import static org.junit.Assert.fail;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import java.io.IOException;
import org.gradle.tooling.BuildActionFailureException;
import org.gradle.tooling.BuildException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * test for package (apk) local aar in app
 */
public class AppWithCompileLocalAarFromOlderIdeTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithLocalDeps")
            .create();

    @BeforeClass
    public static void setUp() throws IOException {
        appendToFile(project.getBuildFile(),
                "\n" +
                "apply plugin: \"com.android.application\"\n" +
                "\n" +
                "android {\n" +
                "    compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n" +
                "    buildToolsVersion \"" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "\"\n" +
                "}\n" +
                "\n" +
                "dependencies {\n" +
                "    compile files(\"libs/baseLib-1.0.aar\")\n" +
                "}\n");

    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkModelFailedToLoad() {
        try {
            project.model().asStudio1().getSingle();
            fail("should have failed");
        } catch(BuildException e) {
            assertThat(project.isImprovedDependencyEnabled()).isFalse();
        } catch (BuildActionFailureException e) {
            // If ImprovedDependencyEnabled, then error happens when we build the model.
            assertThat(project.isImprovedDependencyEnabled()).isTrue();
        }
    }
}
