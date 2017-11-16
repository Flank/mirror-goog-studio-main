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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.utils.FileUtils;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Assemble tests for genFolderApi. */
public class GenFolderApiTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("genFolderApi").create();

    private static AndroidProject model;

    @BeforeClass
    public static void setUp() throws Exception {
        project.executor()
                .withProperty("inject_enable_generate_values_res", "true")
                .run("clean", "assembleDebug");
        model =
                project.model()
                        .withProperty("inject_enable_generate_values_res", "true")
                        .getSingle()
                        .getOnlyModel();
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        model = null;
    }

    @Test
    public void checkTheCustomJavaGenerationTaskRan() throws Exception {
        assertThat(project.getApk("debug")).containsClass("Lcom/custom/Foo;");
    }

    @Test
    public void checkTheCustomResGenerationTaskRan() throws Exception {
        assertThat(project.getApk("debug")).contains("res/xml/generated.xml");
        File intermediateFile =
                project.file("build/intermediates/res/merged/debug/values/values.xml");
        if (!intermediateFile.exists()) {
            intermediateFile =
                    project.file("build/intermediates/res/merged/debug/values_values.arsc.flat");
            // we can't read the contents
            assertThat(intermediateFile).exists();
        } else {
            assertThat(intermediateFile).contains("generated_string");
        }
    }


    @Test
    public void checkAddingAndRemovingGeneratingTasks() throws Exception {
        project.executor()
                .withProperty("inject_enable_generate_values_res", "false")
                .run("assembleDebug");
        assertThat(project.getApk("debug")).contains("res/xml/generated.xml");
        File intermediateFile =
                project.file("build/intermediates/res/merged/debug/values/values.xml");
        if (!intermediateFile.exists()) {
            intermediateFile =
                    project.file("build/intermediates/res/merged/debug/values_values.arsc.flat");
            assertThat(intermediateFile).exists();
        } else {
            assertThat(intermediateFile).doesNotContain("generated_string");
        }

        project.executor()
                .withProperty("inject_enable_generate_values_res", "true")
                .run("assembleDebug");
        assertThat(project.getApk("debug")).contains("res/xml/generated.xml");

        if (intermediateFile.getName().endsWith(".flat")) {
            assertThat(intermediateFile).exists();
        } else {
            assertThat(intermediateFile).contains("generated_string");
        }
    }

    @Test
    public void checkJavaFolderInModel() throws Exception {
        File projectDir = project.getTestDir();

        File buildDir = new File(projectDir, "build");

        for (Variant variant : model.getVariants()) {

            AndroidArtifact mainInfo = variant.getMainArtifact();
            assertNotNull(
                    "Null-check on mainArtifactInfo for " + variant.getDisplayName(), mainInfo);

            // get the generated source folders.
            Collection<File> genSourceFolder = mainInfo.getGeneratedSourceFolders();

            // We're looking for a custom folder
            String sourceFolderStart =
                    new File(buildDir, "customCode").getAbsolutePath() + File.separatorChar;
            boolean found = false;
            for (File f : genSourceFolder) {
                if (f.getAbsolutePath().startsWith(sourceFolderStart)) {
                    found = true;
                    break;
                }
            }

            assertTrue("custom generated source folder check", found);
        }
    }

    @Test
    public void checkResFolderInModel() throws Exception {
        File projectDir = project.getTestDir();

        File buildDir = new File(projectDir, "build");

        for (Variant variant : model.getVariants()) {

            AndroidArtifact mainInfo = variant.getMainArtifact();
            assertNotNull(
                    "Null-check on mainArtifactInfo for " + variant.getDisplayName(), mainInfo);

            // get the generated res folders.
            List<String> genResFolders =
                    mainInfo.getGeneratedResourceFolders()
                            .stream()
                            .map(File::getAbsolutePath)
                            .collect(Collectors.toList());

            assertThat(genResFolders).containsNoDuplicates();
            String buildDirPath = buildDir.getAbsolutePath();

            assertThat(genResFolders)
                    .containsAllOf(
                            FileUtils.join(buildDirPath, "customRes", variant.getName()),
                            FileUtils.join(buildDirPath, "customRes2", variant.getName()));
        }
    }


    @Test
    public void backwardsCompatible() throws Exception {
        // ATTENTION Author and Reviewers - please make sure required changes to the build file
        // are backwards compatible before updating this test.
        assertThat(TestFileUtils.sha1NormalizedLineEndings(project.file("build.gradle")))
                .isEqualTo("073e20ecd397be009b7cd7fd6f6166012d9c39a0");
    }
}
