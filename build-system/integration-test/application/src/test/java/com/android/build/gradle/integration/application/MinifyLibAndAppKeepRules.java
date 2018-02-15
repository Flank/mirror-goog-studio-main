/*
 * Copyright (C) 2016 The Android Open Source Project
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
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.scope.CodeShrinker;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Test that keep rules are applied properly when the main app references classes from the library
 * project.
 */
@RunWith(FilterableParameterized.class)
public class MinifyLibAndAppKeepRules {

    @Parameterized.Parameters(name = "codeShrinker = {0}")
    public static CodeShrinker[] data() {
        return new CodeShrinker[] {CodeShrinker.PROGUARD};
    }

    @Parameterized.Parameter() public CodeShrinker codeShrinker;

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("minifyLibWithJavaRes")
            .create();

    @Test
    public void testReleaseClassesPackaging() throws Exception {
        File noPackage =
                FileUtils.join(project.getSubproject("lib").getMainSrcDir(), "NoPackage.java");
        Files.write("public class NoPackage{}", noPackage, Charsets.UTF_8);

        File referencesNoPackage =
                FileUtils.join(
                        project.getSubproject("app").getMainSrcDir(), "ReferencesNoPackage.java");
        Files.write(
                "public class ReferencesNoPackage { static { NoPackage np = new NoPackage(); } }",
                referencesNoPackage,
                Charsets.UTF_8);

        // add the proguard rule that should keep all the classes
        Files.write(
                "-keep class *",
                FileUtils.join(project.getSubproject("app").getTestDir(), "proguard-rules.pro"),
                Charsets.UTF_8);

        TestFileUtils.appendToFile(
                project.getSubproject("app").getBuildFile(),
                "android {\n" +
                        "    buildTypes {\n" +
                        "        release {\n" +
                        "           proguardFiles getDefaultProguardFile('proguard-android.txt')," +
                        "'proguard-rules.pro'\n" +
                        "        }\n" +
                        "    }\n" +
                        "}");

        project.executor().run(":app:assembleRelease");
        assertThat(project.getSubproject("app").getApk("release"))
                .containsClass("LNoPackage;");
    }
}
