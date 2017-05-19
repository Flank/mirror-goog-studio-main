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

import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;
import static com.android.testutils.truth.MoreTruth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.testutils.apk.Apk;
import com.android.testutils.apk.Dex;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.IOException;
import java.util.Optional;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** test for compile jar in app through an aar dependency */
public class JacocoInLibraryTest {

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("projectWithModules").create();

    @BeforeClass
    public static void setUp() throws Exception {
        Files.write("include 'app', 'library'", project.getSettingsFile(), Charsets.UTF_8);

        appendToFile(
                project.getBuildFile(),
                "\nsubprojects {\n"
                        + "    apply from: \"$rootDir/../commonLocalRepo.gradle\"\n"
                        + "}\n");

        appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\ndependencies {\n" + "    compile project(':library')\n" + "}\n");

        appendToFile(
                project.getSubproject("library").getBuildFile(),
                "\nandroid.buildTypes.debug.testCoverageEnabled = true");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkJacocoInApp() throws IOException, InterruptedException {
        project.executor().run("clean", "app:assembleDebug");

        Apk apk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk.getFile()).isFile();

        Optional<Dex> dexOptional = apk.getMainDexFile();
        assertThat(dexOptional).isPresent();

        //noinspection ConstantConditions
        assertThat(dexOptional.get()).containsClasses("Lorg/jacoco/agent/rt/IAgent;");
    }
}
