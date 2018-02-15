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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.internal.scope.CodeShrinker;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Version;
import com.android.testutils.apk.Apk;
import com.android.testutils.apk.Dex;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Assemble tests for minify. */
@RunWith(FilterableParameterized.class)
public class MinifyTest {

    @Parameterized.Parameters(name = "shrinker = {0}")
    public static CodeShrinker[] getConfigurations() {
        return new CodeShrinker[] {CodeShrinker.PROGUARD};
    }

    @Parameterized.Parameter public CodeShrinker codeShrinker;

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("minify").create();

    @Test
    public void appApkIsMinified() throws Exception {
        GradleBuildResult result = project.executor().run("assembleMinified");
        assertThat(result.getStdout()).doesNotContain("Note");
        assertThat(result.getStdout()).doesNotContain("duplicate");

        Apk apk = project.getApk("minified");
        Set<String> allClasses = Sets.newHashSet();
        for (Dex dex : apk.getAllDexes()) {
            allClasses.addAll(
                    dex.getClasses()
                            .keySet()
                            .stream()
                            .filter(
                                    c ->
                                            !c.startsWith("Lorg/jacoco")
                                                    && !c.equals("Lcom/vladium/emma/rt/RT;"))
                            .collect(Collectors.toSet()));
        }
        assertThat(allClasses)
                .containsExactly(
                        "Lcom/android/tests/basic/a;",
                        "Lcom/android/tests/basic/Main;",
                        "Lcom/android/tests/basic/IndirectlyReferencedClass;");

        File defaultProguardFile =
                project.file(
                        "build/"
                                + AndroidProject.FD_INTERMEDIATES
                                + "/proguard-files"
                                + "/proguard-android.txt"
                                + "-"
                                + Version.ANDROID_GRADLE_PLUGIN_VERSION);
        assertThat(defaultProguardFile).exists();

        assertThat(apk)
                .hasMainClass("Lcom/android/tests/basic/Main;")
                .that()
                // Make sure default ProGuard rules were applied.
                .hasMethod("handleOnClick");
    }

    @Test
    public void testApkIsNotMinified_butMappingsAreApplied() throws Exception {
        // Run just a single task, to make sure task dependencies are correct.
        project.executor().run("assembleMinifiedAndroidTest");

        GradleTestProject.ApkType testMinified =
                GradleTestProject.ApkType.of("minified", "androidTest", true);

        Apk apk = project.getApk(testMinified);
        Set<String> allClasses = Sets.newHashSet();
        for (Dex dex : apk.getAllDexes()) {
            allClasses.addAll(
                    dex.getClasses()
                            .keySet()
                            .stream()
                            .filter(c -> !c.startsWith("Lorg/hamcrest"))
                            .collect(Collectors.toSet()));
        }

        assertThat(allClasses)
                .containsExactly(
                        "Lcom/android/tests/basic/MainTest;",
                        "Lcom/android/tests/basic/UnusedTestClass;",
                        "Lcom/android/tests/basic/UsedTestClass;",
                        "Lcom/android/tests/basic/test/BuildConfig;",
                        "Lcom/android/tests/basic/test/R;");

        assertThat(apk)
                .hasClass("Lcom/android/tests/basic/MainTest;")
                .that()
                .hasFieldWithType("stringProvider", "Lcom/android/tests/basic/a;");
    }
}
