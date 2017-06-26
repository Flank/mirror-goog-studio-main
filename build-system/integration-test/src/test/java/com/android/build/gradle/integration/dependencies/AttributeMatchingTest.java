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

package com.android.build.gradle.integration.dependencies;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class AttributeMatchingTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("projectWithModules").create();

    @Before
    public void setUp() throws Exception {
        Files.asCharSink(project.getSettingsFile(), Charsets.UTF_8)
                .write("include 'app', 'library'");

        // add a resources file in the debug build type of the lib.
        File fooTxt =
                FileUtils.join(
                        project.getSubproject("library").getTestDir(),
                        "src",
                        "debug",
                        "resources",
                        "debug_foo.txt");
        FileUtils.mkdirs(fooTxt.getParentFile());
        Files.asCharSink(fooTxt, Charsets.UTF_8).write("foo");

        // add a new build type and a dependency on the app module
        appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    buildTypes {\n"
                        + "        foo {}\n"
                        + "    }\n"
                        + "    buildTypeMatching 'foo', 'debug'\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    implementation project(\":library\")\n"
                        + "}\n");
    }

    @After
    public void cleanUp() {
        project = null;
    }

    @Test
    public void checkBuildType() throws Exception {
        project.executeAndReturnMultiModel("clean", ":app:assembleFoo");

        final Apk apk = project.getSubproject("app").getApk(ApkType.of("foo", false));
        assertThat(apk.getFile()).isFile();
        assertThat(apk).containsJavaResource("/debug_foo.txt");
    }

    @Test
    public void checkFlavors() throws Exception {
        // add flavors on the app
        appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    flavorDimensions 'color'\n"
                        + "    productFlavors {\n"
                        + "        orange {\n"
                        + "            dimension = 'color'\n"
                        + "        }\n"
                        + "    }\n"
                        + "    productFlavorMatching 'color', 'orange', 'red'\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    implementation project(\":library\")\n"
                        + "}\n");

        // and on the library
        appendToFile(
                project.getSubproject("library").getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    flavorDimensions 'color', 'fruit'\n"
                        + "    productFlavors {\n"
                        + "        orange {\n"
                        + "            dimension = 'fruit'\n"
                        + "        }\n"
                        + "        red {\n"
                        + "            dimension = 'color'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        // add a resources file in the red flavor of the lib.
        File fooTxt =
                FileUtils.join(
                        project.getSubproject("library").getTestDir(),
                        "src",
                        "red",
                        "resources",
                        "red_foo.txt");
        FileUtils.mkdirs(fooTxt.getParentFile());
        Files.asCharSink(fooTxt, Charsets.UTF_8).write("foo");

        project.executeAndReturnMultiModel("clean", ":app:assembleOrangeFoo");

        final Apk apk = project.getSubproject("app").getApk(ApkType.of("foo", false), "orange");
        assertThat(apk.getFile()).isFile();
        assertThat(apk).containsJavaResource("/debug_foo.txt");
        assertThat(apk).containsJavaResource("/red_foo.txt");
    }
}
