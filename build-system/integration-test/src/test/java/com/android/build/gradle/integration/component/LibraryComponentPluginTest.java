/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.integration.component;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;

import com.android.build.gradle.integration.common.category.SmokeTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldLibraryApp;
import com.android.testutils.apk.Aar;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Basic integration test for LibraryComponentModelPlugin.
 */
@Category(SmokeTests.class)
public class LibraryComponentPluginTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestApp(new HelloWorldLibraryApp())
            .useExperimentalGradleVersion(true)
            .create();


    @Test
    public void checkBuildConfigFileIsIncluded() throws Exception {
        appendToFile(project.getSubproject("app").getBuildFile(),
                "apply plugin: \"com.android.model.application\"\n"
                + "\n"
                + "dependencies {\n"
                + "    compile project(\":lib\")\n"
                + "}\n"
                + "\n"
                + "model {\n"
                + "    android {\n"
                + "        compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n"
                + "        buildToolsVersion \"" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "\"\n"
                + "    }\n"
                + "}\n");

        appendToFile(project.getSubproject("lib").getBuildFile(),
                "apply plugin: \"com.android.model.library\"\n"
                + "\n"
                + "dependencies {\n"
                + "    /* Depend on annotations to trigger the creation of the ExtractAnnotations task */\n"
                + "    compile 'com.android.support:support-annotations:" + GradleTestProject.SUPPORT_LIB_VERSION + "'\n"
                + "}\n"
                + "\n"
                + "model {\n"
                + "    android {\n"
                + "        compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n"
                + "        buildToolsVersion \"" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "\"\n"
                + "    }\n"
                + "}\n");

        project.execute("assemble");
        Aar releaseAar = project.getSubproject("lib").getAar("release");
        assertThat(releaseAar).containsClass("Lcom/example/helloworld/BuildConfig;");
    }

    @Test
    public void checkMultiFlavorDependencies() throws Exception {
        appendToFile(
                project.getSubproject("app").getBuildFile(),
                "apply plugin: \"com.android.model.application\"\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    compile project(path: \":lib\")\n"
                        + "}\n"
                        + "\n"
                        + "model {\n"
                        + "    android {\n"
                        + "        compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "        buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "        flavorSelection[\"pricing\"] = \"free\"\n"
                        + "    }\n"
                        + "}\n");

        GradleTestProject lib = project.getSubproject("lib");
        appendToFile(lib.getBuildFile(),
                "apply plugin: \"com.android.model.library\"\n"
                        + "\n"
                        + "configurations {\n"
                        + "    freeDebug\n"
                        + "}\n"
                        + "\n"
                        + "model {\n"
                        + "    android {\n"
                        + "        compileSdkVersion " + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION + "\n"
                        + "        buildToolsVersion \"" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "\"\n"
                        + "\n"
                        + "        productFlavors {\n"
                        + "            create(\"free\") {\n"
                        + "                dimension \"pricing\"\n"
                        + "            }\n"
                        + "            create(\"premium\") {\n"
                        + "                dimension \"pricing\"\n"
                        + "            }\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        project.execute(":app:assembleDebug");
        assertThat(lib.file("build/intermediates/bundles/freeDebug")).isDirectory();
    }
}
