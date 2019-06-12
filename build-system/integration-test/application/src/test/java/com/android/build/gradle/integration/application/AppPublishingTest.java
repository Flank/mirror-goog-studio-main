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

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.testutils.truth.FileSubject;
import com.android.testutils.truth.ZipFileSubject;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Tests for APK and bundle publishing from app module. */
public class AppPublishingTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("densitySplit").create();

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
                        + "}\n"
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

    @After
    public void cleanUp() {
        project = null;
    }

    @Test
    public void testBundlePublishing() throws Exception {
        // publish the app as a bundle and an apk.
        project.execute("publishBundlePublicationToMavenRepository");

        // manually check that the app publishing worked.
        File testRepo = new File(project.getTestDir(), "testrepo");
        File groupIdFolder = FileUtils.join(testRepo, "test", "densitysplit");

        File aabFile = FileUtils.join(groupIdFolder, "bundle", "1.0", "bundle-1.0.aab");
        FileSubject.assertThat(aabFile).isFile();

        File pomFile = FileUtils.join(groupIdFolder, "bundle", "1.0", "bundle-1.0.pom");
        FileSubject.assertThat(pomFile).isFile();
        FileSubject.assertThat(pomFile).contains("<packaging>aab</packaging>");
    }

    @Test
    public void testApkPublishing() throws Exception {
        // publish the app as a bundle and an apk.
        project.execute("publishApkPublicationToMavenRepository");

        // manually check that the app publishing worked.
        File testRepo = new File(project.getTestDir(), "testrepo");
        File groupIdFolder = FileUtils.join(testRepo, "test", "densitysplit");

        File aabFile = FileUtils.join(groupIdFolder, "apk", "1.0", "apk-1.0.zip");
        FileSubject.assertThat(aabFile).isFile();
        ZipFileSubject.assertThatZip(aabFile).contains("mapping.txt");

        File pomFile = FileUtils.join(groupIdFolder, "apk", "1.0", "apk-1.0.pom");
        FileSubject.assertThat(pomFile).isFile();
        FileSubject.assertThat(pomFile).contains("<packaging>zip</packaging>");
    }

    @Test
    public void testApkPublishingWithNoMapping() throws Exception {
        // disable minification to not have mapping files.
        TestFileUtils.searchAndReplace(
                project.getBuildFile(), "minifyEnabled true", "minifyEnabled false");

        // publish the app as a bundle and an apk.
        project.execute("publishApkPublicationToMavenRepository");

        // manually check that the app publishing worked.
        File testRepo = new File(project.getTestDir(), "testrepo");
        File groupIdFolder = FileUtils.join(testRepo, "test", "densitysplit");

        File aabFile = FileUtils.join(groupIdFolder, "apk", "1.0", "apk-1.0.zip");
        FileSubject.assertThat(aabFile).isFile();
        ZipFileSubject.assertThatZip(aabFile).doesNotContain("mapping.txt");

        File pomFile = FileUtils.join(groupIdFolder, "apk", "1.0", "apk-1.0.pom");
        FileSubject.assertThat(pomFile).isFile();
        FileSubject.assertThat(pomFile).contains("<packaging>zip</packaging>");
    }
}
