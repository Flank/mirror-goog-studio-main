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

import static com.android.build.gradle.integration.common.truth.ApkSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Tests for generated source registration APIs.
 *
 * <p>Includes the following APIs:
 *
 * <ul>
 *   <li>registerJavaGeneratingTask
 *   <li>registerResGeneratingTask
 *   <li>registerGeneratedResFolders
 * </ul>
 */
public class GenFolderApiTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("genFolderApi").create();

    private static AndroidProject model;

    @BeforeClass
    public static void setUp() throws Exception {
        project.executor()
                .withArgument("-P" + "inject_enable_generate_values_res=true")
                .run("assembleDebug");
        model =
                project.model()
                        .withArgument("-P" + "inject_enable_generate_values_res=true")
                        .fetchAndroidProjects()
                        .getOnlyModel();
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        model = null;
    }

    @Test
    public void checkTheCustomJavaGenerationTaskRan() throws Exception {
        try (Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG)) {
            assertThat(apk).containsClass("Lcom/custom/Foo;");
        }
    }

    @Test
    public void checkTheCustomResGenerationTaskRan() throws Exception {
        try (Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG)) {
            assertThat(apk).contains("res/xml/generated.xml");
            assertThat(apk)
                    .hasClass("Lcom/android/tests/basic/R$string;")
                    .that()
                    .hasField("generated_string");
        }
    }

    @Test
    public void checkAddingAndRemovingGeneratingTasks() throws Exception {
        project.executor()
                .withArgument("-P" + "inject_enable_generate_values_res=false")
                .run("assembleDebug");

        try (Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG)) {
            assertThat(apk)
                    .hasClass("Lcom/android/tests/basic/R$string;")
                    .that()
                    .doesNotHaveField("generated_string");
        }

        project.executor()
                .withArgument("-P" + "inject_enable_generate_values_res=true")
                .run("assembleDebug");
        try (Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG)) {
            assertThat(apk)
                    .hasClass("Lcom/android/tests/basic/R$string;")
                    .that()
                    .hasField("generated_string");
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
