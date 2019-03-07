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

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.builder;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;

/**
 * test for provided library in app
 */
public class AppWithProvidedLibTest {

    @ClassRule
    public static GradleTestProject project = builder().fromTestProject("dynamicApp").create();
    static ModelContainer<AndroidProject> modelContainer;

    @BeforeClass
    public static void setUp() throws Exception {
        // create a library module.
        File rootFolder = project.getSettingsFile().getParentFile();
        File libFolder = new File(rootFolder, "library");
        FileUtils.mkdirs(libFolder);

        Files.asCharSink(new File(libFolder, "build.gradle"), Charsets.UTF_8)
                .write(
                        "apply plugin: 'com.android.library'\n"
                                + "apply from: \"../../commonLocalRepo.gradle\"\n"
                                + "\n"
                                + "android {\n"
                                + "    compileSdkVersion rootProject.latestCompileSdk\n"
                                + "\n"
                                + "}\n");
        final File mainFolder = new File(libFolder, "src/main");
        FileUtils.mkdirs(mainFolder);
        Files.asCharSink(new File(mainFolder, "AndroidManifest.xml"), Charsets.UTF_8)
                .write(
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                + "      package=\"com.example.android.multiproject.library.base\">\n"
                                + "</manifest>\n");

        TestFileUtils.appendToFile(project.getSettingsFile(), "\ninclude 'library'");
        TestFileUtils.appendToFile(project.getSubproject("app").getBuildFile(),
                "\n" +
                "dependencies {\n" +
                "    provided project(\":library\")\n" +
                "}\n");
        modelContainer =
                project.model().withFullDependencies().ignoreSyncIssues().fetchAndroidProjects();
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        modelContainer = null;
    }

    @Test
    public void checkBuildFailure() throws IOException, InterruptedException {
        GradleBuildResult result = project.executor().expectFailure().run("app:assemble");

        assertThat(result.getFailureMessage())
                .isEqualTo(
                        "Android dependency 'project :library' is set to compileOnly/provided which is not supported");
    }

    @Test
    @Ignore
    public void checkModelFailedToLoad() throws Exception {
        // TODO: full dependency should show us broken provided only dependency.
        //final AndroidProject androidProject = modelContainer.getOnlyModelMap().get(":app");
        //assertThat(androidProject).hasIssueSize(2);
        //assertThat(androidProject).hasIssue(
        //        SyncIssue.SEVERITY_ERROR,
        //        SyncIssue.TYPE_NON_JAR_PROVIDED_DEP,
        //        "projectWithModules:library:unspecified:debug@aar");
        //assertThat(androidProject).hasIssue(
        //        SyncIssue.SEVERITY_ERROR,
        //        SyncIssue.TYPE_NON_JAR_PROVIDED_DEP,
        //        "projectWithModules:library:unspecified:release@aar");
    }
}
