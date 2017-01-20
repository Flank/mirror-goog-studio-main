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

package com.android.build.gradle.integration.dependencies;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GetAndroidModelAction.ModelContainer;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * test for dependency on a jar with a dependency on a library
 */
public class AppWithJarDependOnLibTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();
    static ModelContainer<AndroidProject> modelContainer;

    @BeforeClass
    public static void setUp() throws Exception {
        Files.write("include 'app', 'library', 'jar'", project.getSettingsFile(), Charsets.UTF_8);

        TestFileUtils.appendToFile(project.getSubproject("app").getBuildFile(),
                "\n" +
                "dependencies {\n" +
                "    compile project(\":jar\")\n" +
                "}\n");

        TestFileUtils.appendToFile(project.getSubproject("jar").getBuildFile(),
                "\n" +
                "dependencies {\n" +
                "    compile project(\":library\")\n" +
                "}\n");
        modelContainer = project.model().ignoreSyncIssues().getMulti();
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        modelContainer = null;
    }

    @Test
    public void checkModelFailedToLoad() throws Exception {
        assertThat(modelContainer.getModelMap().get(":app")).hasSingleIssue(
                SyncIssue.SEVERITY_ERROR,
                SyncIssue.TYPE_JAR_DEPEND_ON_AAR,
                "projectWithModules:jar:unspecified@jar");
    }
}
