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

package com.android.build.gradle.integration.library;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.internal.scope.InternalArtifactType.RUNTIME_LIBRARY_CLASSES_JAR;
import static com.android.testutils.truth.PathSubject.assertThat;
import static com.android.testutils.truth.ZipFileSubject.assertThat;

import com.android.SdkConstants;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldLibraryApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.testutils.apk.Zip;
import com.android.utils.FileUtils;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Test published intermediate artifacts. */
public class LibraryIntermediateArtifactPublishingTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestApp(HelloWorldLibraryApp.create()).create();

    @Before
    public void setUp() throws IOException {

        FileUtils.createFile(
                project.getSubproject(":lib").file("src/main/resources/foo.txt"), "foo");
    }

    @Test
    public void fullJarArtifactIsNotNormallyCreated() throws IOException, InterruptedException {
        GradleBuildResult result =
                project.executor()
                        .with(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM, false)
                        .run(":app:assembleDebug");
        Truth.assertThat(result.findTask(":lib:createFullJarDebug")).isNull();
        assertThat(getJar("full.jar")).doesNotExist();
    }

    @Test
    public void testFullJarUpToDate() throws IOException, InterruptedException {
        GradleBuildResult result = project.executor().run(":lib:createFullJarDebug");
        assertThat(result.getTask(":lib:createFullJarDebug")).didWork();

        result = project.executor().run(":lib:createFullJarDebug");
        assertThat(result.getTask(":lib:createFullJarDebug")).wasUpToDate();
    }

    @Test
    public void jarArtifactIsCreated() throws Exception {
        // Add a task that uses the request 'jar' artifactType as input.
        TestFileUtils.appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n"
                        + "class VerifyTask extends DefaultTask {\n"
                        + "    @InputFiles\n"
                        + "    FileCollection fullJar\n"
                        + "    @TaskAction\n"
                        + "    void verify() {\n"
                        + "        assert fullJar.singleFile.name == '"
                        + SdkConstants.FN_INTERMEDIATE_FULL_JAR
                        + "'\n"
                        + "    }\n"
                        + "}\n"
                        + "android {\n"
                        + "    applicationVariants.all { v ->\n"
                        + "        if (v.name == 'debug') {\n"
                        + "            project.tasks.create('verify', VerifyTask) {\n"
                        + "                def artifactType = Attribute.of('artifactType', String)\n"
                        + "                fullJar = v.compileConfiguration.incoming.artifactView { attributes { it.attribute(artifactType, 'jar') }}.files\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        GradleBuildResult result = project.executor().run(":app:verify");
        assertThat(result.getTask(":lib:createFullJarDebug")).didWork();
        File fullJar = getJar("full.jar");
        try (Zip it = new Zip(fullJar)) {
            assertThat(it).contains("com/example/helloworld/HelloWorld.class");
            assertThat(it).contains("foo.txt");
        }

        File classesJar =
                project.getSubproject(":lib")
                        .getIntermediateFile(
                                RUNTIME_LIBRARY_CLASSES_JAR.INSTANCE.getFolderName()
                                        + "/debug/classes.jar");
        try (Zip it = new Zip(classesJar)) {
            assertThat(it).contains("com/example/helloworld/HelloWorld.class");
            assertThat(it).doesNotContain("foo.txt");
        }

        File resJar = project.getSubproject(":lib").getIntermediateFile("library_java_res/debug/res.jar");
        try (Zip it = new Zip(resJar)) {
            assertThat(it).doesNotContain("com/example/helloworld/HelloWorld.class");
            assertThat(it).contains("foo.txt");
        }
    }

    private File getJar(String fileName) {
        return project.getSubproject(":lib").file("build/intermediates/full_jar/debug/" + fileName);
    }
}
