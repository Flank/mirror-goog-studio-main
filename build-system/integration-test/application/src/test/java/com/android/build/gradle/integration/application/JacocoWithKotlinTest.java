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

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp;
import com.android.build.gradle.integration.common.fixture.app.TransformOutputContent;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.pipeline.SubStream;
import com.android.utils.FileUtils;
import com.google.common.collect.Lists;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.IOException;
import java.util.function.Predicate;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** Check that Jacoco runs for a Kotlin-based project. */
public class JacocoWithKotlinTest {

    @Rule public Adb adb = new Adb();

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

    @After
    public void cleanUp() {
        project = null;
    }

    @Test
    public void build() throws IOException, InterruptedException {
        project.execute("transformClassesWithJacocoForDebug");

        File outputDir =
                FileUtils.join(project.getIntermediatesDir(), "transforms", "jacoco", "debug");
        TransformOutputContent content = new TransformOutputContent(outputDir);

        Predicate<SubStream> containsHelloWorld =
                subStream ->
                        FileUtils.getAllFiles(content.getLocation(subStream))
                                .transform(File::getName)
                                .contains("HelloWorld.class");

        // There's one stream for Java classes and one for Kotlin classes. Exactly one should
        // contain HelloWorld.
        Truth.assertThat(Lists.newArrayList(content).stream().filter(containsHelloWorld).count())
                .isEqualTo(1);
    }

    @Test
    @Category(DeviceTests.class)
    public void createDebugCoverageReport() throws Exception {
        adb.exclusiveAccess();
        project.execute("createDebugCoverageReport");
        assertThat(
                        project.file(
                                "build/reports/coverage/debug/com.example.helloworld/HelloWorld.kt.html"))
                .exists();
    }
}
