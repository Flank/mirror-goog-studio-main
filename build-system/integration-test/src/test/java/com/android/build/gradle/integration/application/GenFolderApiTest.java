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
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.utils.FileUtils;
import java.io.File;
import java.util.Collection;
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
        model = project.executeAndReturnModel("clean", "assembleDebug").getOnlyModel();
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
            Collection<File> genResFolder = mainInfo.getGeneratedResourceFolders();
            String resFolderStart =
                    new File(buildDir, "customRes").getAbsolutePath() + File.separatorChar;
            boolean found = false;
            for (File f : genResFolder) {
                if (f.getAbsolutePath().startsWith(resFolderStart)) {
                    found = true;
                    break;
                }
            }

            assertTrue("custom generated res folder check", found);
        }
    }

    @Test
    public void backwardsCompatible() throws Exception {
        // ATTENTION Author and Reviewers - please make sure required changes to the build file
        // are backwards compatible before updating this test.
        assertThat(FileUtils.sha1(project.file("build.gradle")))
                .isEqualTo("91bcc58922415dacd78c737327e684d082895b66");
    }
}
