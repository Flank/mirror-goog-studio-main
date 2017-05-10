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

package com.android.build.gradle.integration.testing;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.TransformOutputContent;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.common.utils.ZipHelper;
import com.android.utils.FileUtils;
import java.io.File;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldNode;

/**
 * Test for a separate test module run against the minified app.
 */
public class SeparateTestModuleWithMinifiedAppTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("separateTestModuleWithMinifiedApp")
            .create();

    @Rule
    public Adb adb = new Adb();

    /** Check that build when proguard needs to be enabled. */
    @Test
    public void checkObfuscationTask() throws Exception {
        // enable minify on debug build as well.
        TestFileUtils.appendToFile(
                project.getSubproject(":app").getBuildFile(),
                "android {\n"
                        + "    buildTypes {\n"
                        + "        debug {\n"
                        + "            minifyEnabled true\n"
                        + "            proguardFiles getDefaultProguardFile('proguard-android.txt')\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        GradleBuildResult result =
                project.executor().expectFailure().run("clean", ":test:assembleDebug");
        assertThat(result.getFailureMessage())
                .contains("Mapping file found in tested application.");

    }

    @Test
    public void checkMappingsApplied() throws Exception {
        project.execute("clean", ":test:assembleMinified");

        GradleTestProject testProject = project.getSubproject("test");

        File outputDir =
                FileUtils.join(
                        testProject.getIntermediatesDir(), "transforms", "proguard", "minified");
        TransformOutputContent content = new TransformOutputContent(outputDir);
        File jarFile = content.getLocation(content.getSingleStream());

        FieldNode stringProviderField = ZipHelper.checkClassFile(
                jarFile, "com/android/tests/basic/MainTest.class", "mUtility");
        assertThat(Type.getType(stringProviderField.desc).getClassName())
                .isEqualTo("com.android.tests.a.a");
    }

    @Ignore("http://b/38419063")
    @Category(DeviceTests.class)
    public void checkRunOnDevice() throws Exception {
        adb.exclusiveAccess();
        project.execute(":test:connectedAndroidTest");
    }
}
