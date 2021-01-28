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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.scope.ArtifactTypeUtil;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.options.BooleanOption;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Check that Jacoco runs for a Kotlin-based project. */
public class JacocoWithKotlinTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(KotlinHelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Before
    public void setUpBuildFile() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "  buildTypes {\n"
                        + "    debug {\n"
                        + "      testCoverageEnabled true\n"
                        + "    }\n"
                        + "  }\n"
                        + "}");
    }

    @Test
    public void build() throws IOException, InterruptedException {
        project.executor()
                .with(BooleanOption.FORCE_JACOCO_OUT_OF_PROCESS, true)
                .run("assembleDebug");

        // check HelloWorld class is instrumented
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG))
                .hasMainClass("Lcom/example/helloworld/HelloWorld;")
                .that()
                .hasField("$jacocoData");

        File kotlinModuleFile =
                FileUtils.join(
                        ArtifactTypeUtil.getOutputDir(
                                InternalArtifactType.JACOCO_INSTRUMENTED_CLASSES.INSTANCE,
                                project.getBuildDir()),
                        "debug",
                        "out",
                        "META-INF",
                        "project_debug.kotlin_module");
        assertThat(kotlinModuleFile).isFile();
    }
}
