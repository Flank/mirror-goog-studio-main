/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.testutils.truth.PathSubject.assertThat;
import static com.android.testutils.truth.ZipFileSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.testutils.apk.Zip;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Tests for APK and bundle publishing from app module. */
public class AppPublishingTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("densitySplit")
                    // http://b/149978740
                    .addGradleProperties(
                            BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS.getPropertyName()
                                    + "=false")
                    .create();

    @Before
    public void setUp() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: 'maven-publish'\n"
                        + "\n"
                        + "publishing {\n"
                        + "    repositories {\n"
                        + "        maven { url 'testrepo' }\n"
                        + "    }\n"
                        + "}\n");
    }

    @After
    public void cleanUp() {
        project = null;
    }

    @Test
    public void testBundlePublishing() throws Exception {
        // publish the app as a bundle
        setUpAabPublishing();
        project.executor()
                // http://b/149978740 - building bundle always runs the incompatible task
                .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
                .run("publishAppPublicationToMavenRepository");

        // manually check that the app publishing worked.
        Path testRepo = project.getProjectDir().toPath().resolve("testrepo");
        Path groupIdFolder = testRepo.resolve("test/densitysplit");

        Path aabFile = groupIdFolder.resolve("app/1.0/app-1.0.aab");
        assertThat(aabFile).isFile();
    }

    @Test
    public void testApkPublishing() throws Exception {
        // publish the app as an apk.
        setUpApkPublishing();
        project.execute("publishAppPublicationToMavenRepository");

        // manually check that the app publishing worked.
        File testRepo = new File(project.getProjectDir(), "testrepo");
        File groupIdFolder = FileUtils.join(testRepo, "test", "densitysplit");

        File apkFile = FileUtils.join(groupIdFolder, "app", "1.0", "app-1.0.zip");
        assertThat(apkFile).isFile();

        try (Zip it = new Zip(apkFile)) {
            assertThat(it).contains("mapping.txt");
        }
    }

    @Test
    public void testApkPublishingWithNoMapping() throws Exception {
        setUpApkPublishing();
        // disable minification to not have mapping files.
        TestFileUtils.searchAndReplace(
                project.getBuildFile(), "minifyEnabled true", "minifyEnabled false");

        // publish the app as an apk.
        project.execute("publishAppPublicationToMavenRepository");

        // manually check that the app publishing worked.
        File testRepo = new File(project.getProjectDir(), "testrepo");
        File groupIdFolder = FileUtils.join(testRepo, "test", "densitysplit");

        File aabFile = FileUtils.join(groupIdFolder, "app", "1.0", "app-1.0.zip");
        assertThat(aabFile).isFile();
        try (Zip it = new Zip(aabFile)) {
            assertThat(it).doesNotContain("mapping.txt");
        }
    }

    @Test
    public void testApkPublishingWithoutNewPublishingDsl() throws Exception {
        setUpLegacyPublishing();
        project.execute("publishApkPublicationToMavenRepository");
        File testRepo = new File(project.getProjectDir(), "testrepo");
        File groupIdFolder = FileUtils.join(testRepo, "test", "densitysplit");

        File apkFile = FileUtils.join(groupIdFolder, "apk", "1.0", "apk-1.0.zip");
        assertThat(apkFile).isFile();
    }

    private void setUpApkPublishing() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    publishing {\n"
                        + "        singleVariant('release') { publishApk() }\n"
                        + "    }\n"
                        + "}\n");
        addNewPublicationDsl();
    }

    private void setUpAabPublishing() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    publishing {\n"
                        + "        singleVariant('release')\n"
                        + "    }\n"
                        + "}\n");
        addNewPublicationDsl();
    }

    private void addNewPublicationDsl() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "afterEvaluate {\n"
                        + "    publishing {\n"
                        + "        publications {\n"
                        + "            app(MavenPublication) {\n"
                        + "                groupId = 'test.densitysplit'\n"
                        + "                artifactId = 'app'\n"
                        + "                version = '1.0'\n"
                        + "\n"
                        + "                from components.release\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}");
    }

    private void setUpLegacyPublishing() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "afterEvaluate {\n"
                        + "    publishing {\n"
                        + "        publications {\n"
                        + "            bundle(MavenPublication) {\n"
                        + "                groupId = 'test.densitysplit'\n"
                        + "                artifactId = 'bundle'\n"
                        + "                version = '1.0'\n"
                        + "\n"
                        + "                from components.release_aab\n"
                        + "            }\n"
                        + "            apk(MavenPublication) {\n"
                        + "                groupId = 'test.densitysplit'\n"
                        + "                artifactId = 'apk'\n"
                        + "                version = '1.0'\n"
                        + "\n"
                        + "                from components.release_apk\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}");
    }
}
