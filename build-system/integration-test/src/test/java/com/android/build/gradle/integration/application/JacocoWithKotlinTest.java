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

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp;
import com.android.build.gradle.integration.common.fixture.app.TransformOutputContent;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.pipeline.SubStream;
import com.android.utils.FileUtils;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

/** Check that Jacoco runs for a Kotlin-based project. */
public class JacocoWithKotlinTest {
    @ClassRule
    public static GradleTestProject project =
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

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void build() throws IOException, InterruptedException {
        project.execute("transformClassesWithJacocoForDebug");

        File outputDir =
                FileUtils.join(project.getIntermediatesDir(), "transforms", "jacoco", "debug");
        TransformOutputContent content = new TransformOutputContent(outputDir);

        SubStream stream = content.getSingleStream();
        Truth.assertThat(
                        FileUtils.getAllFiles(content.getLocation(stream))
                                .transform(File::getName)
                                .toList())
                .contains("HelloWorld.class");
    }
}
