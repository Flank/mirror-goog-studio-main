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

package com.android.build.gradle.integration.library;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldLibraryApp;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class GenerateSourcesOnlyTest {


    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(new HelloWorldLibraryApp())
                    .create();

    @Before
    public void createBuildFiles() throws Exception {
        TestFileUtils.appendToFile(project.getSubproject("app").getBuildFile(), "\n"
                + "apply plugin: \"com.android.application\"\n"
                + "\n"
                + "dependencies {\n"
                + "    compile project(':lib')\n"
                + "}\n"
                + "\n"
                + "android {\n"
                + "     compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n"
                + "     buildToolsVersion '" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "'\n"
                + "}");

        TestFileUtils.appendToFile(project.getSubproject("lib").getBuildFile(), "\n"
                + "apply plugin: \"com.android.library\"\n"
                + "\n"
                + "android {\n"
                + "     compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n"
                + "     buildToolsVersion '" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "'\n"
                + "}");
    }

    @Test
    public void checkLibraryNotBuilt() throws Exception {
        List<String> generateSources =
                ModelHelper.getDebugGenerateSourcesCommands(project.model().getMulti().getModelMap());

        GradleBuildResult result =
                project.executor()
                        .withArgument("-Pandroid.injected.generateSourcesOnly=true")
                        .run(generateSources);

        assertThat(result.getStdout()).doesNotContain("compileDebugJava");
        assertThat(result.getStdout()).doesNotContain("compileReleaseJava");

        GradleBuildResult resultWithout = project.executor()
                .run(generateSources);

        assertThat(resultWithout.getStdout()).contains("compileReleaseJava");
    }
}
