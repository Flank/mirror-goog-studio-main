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

package com.android.build.gradle.integration.shrinker;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests based on the "minify" test project, which contains unused classes, reflection and JaCoCo
 * classes.
 */
public class MinifyProjectShrinkerTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("minify").create();

    @Before
    public void enableShrinker() throws Exception {

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "  buildTypes {\n"
                        + "    minified {\n"
                        + "      useProguard false\n"
                        + "    }\n"
                        + "  }\n"
                        + "  testBuildType = 'minified'\n"
                        + "}\n");
    }

    @Test
    public void testApkIsCorrect() throws Exception {
        project.execute("assembleMinified");
        ShrinkerTestUtils.checkShrinkerWasUsed(project);
        TruthHelper.assertThatApk(project.getApk(GradleTestProject.ApkType.of("minified", true)))
                .containsClass("Lcom/android/tests/basic/Main;");
        TruthHelper.assertThatApk(project.getApk(GradleTestProject.ApkType.of("minified", true)))
                .containsClass("Lcom/android/tests/basic/StringProvider;");
        TruthHelper.assertThatApk(project.getApk(GradleTestProject.ApkType.of("minified", true)))
                .containsClass("Lcom/android/tests/basic/IndirectlyReferencedClass;");
        TruthHelper.assertThatApk(project.getApk(GradleTestProject.ApkType.of("minified", true)))
                .doesNotContainClass("Lcom/android/tests/basic/UnusedClass;");
    }
}
