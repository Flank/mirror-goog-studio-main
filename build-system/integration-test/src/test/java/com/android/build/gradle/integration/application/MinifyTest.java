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

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.TransformOutputContent;
import com.android.build.gradle.integration.common.utils.ZipHelper;
import com.android.builder.Version;
import com.android.builder.model.AndroidProject;
import com.android.utils.FileUtils;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldNode;

/** Assemble tests for minify. */
public class MinifyTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("minify").create();

    @Test
    @Category(DeviceTests.class)
    public void connectedCheck() throws Exception {
        project.executeConnectedCheck();
    }

    @Test
    public void appApkIsMinified() throws Exception {
        project.execute("assembleMinified");

        File outputDir= FileUtils.join(project.getIntermediatesDir(), "transforms", "proguard", "minified");
        TransformOutputContent content = new TransformOutputContent(outputDir);
        File jarFile = content.getLocation(content.getSingleStream());

        Set<String> minifiedList = ZipHelper.getZipEntries(jarFile);

        // Ignore JaCoCo stuff.
        minifiedList.removeIf(it -> it.startsWith("org/jacoco"));
        minifiedList.remove("about.html");
        minifiedList.remove("com/vladium/emma/rt/RT.class");

        assertThat(minifiedList)
                .containsExactly(
                        "com/android/tests/basic/a.class", // Renamed StringProvider.
                        "com/android/tests/basic/Main.class",
                        "com/android/tests/basic/IndirectlyReferencedClass.class" // Kept by
                        // ProGuard rules.
                        // No entry for UnusedClass, it gets removed.
                        );

        File defaultProguardFile =
                project.file(
                        "build/"
                                + AndroidProject.FD_INTERMEDIATES
                                + "/proguard-files"
                                + "/proguard-android.txt"
                                + "-"
                                + Version.ANDROID_GRADLE_PLUGIN_VERSION);
        assertThat(defaultProguardFile).exists();

        assertThat(project.getApk("minified"))
                .hasMainClass("Lcom/android/tests/basic/Main;")
                .that()
                // Make sure default ProGuard rules were applied.
                .hasMethod("handleOnClick");
    }

    @Test
    public void testApkIsNotMinified_butMappingsAreApplied() throws Exception {
        // Run just a single task, to make sure task dependencies are correct.
        project.execute("assembleMinifiedAndroidTest");

        File outputDir= FileUtils.join(project.getIntermediatesDir(), "transforms", "proguard", "androidTest", "minified");
        TransformOutputContent content = new TransformOutputContent(outputDir);
        File jarFile = content.getLocation(content.getSingleStream());

        Set<String> minifiedList = ZipHelper.getZipEntries(jarFile);

        List<String> testClassFiles =
                minifiedList
                        .stream()
                        .filter(it -> !it.startsWith("org/hamcrest"))
                        .collect(Collectors.toList());

        assertThat(testClassFiles)
                .containsExactly(
                        "com/android/tests/basic/MainTest.class",
                        "com/android/tests/basic/UnusedTestClass.class",
                        "com/android/tests/basic/UsedTestClass.class",
                        "com/android/tests/basic/test/BuildConfig.class",
                        "com/android/tests/basic/test/R.class");

        FieldNode stringProviderField = ZipHelper.checkClassFile(
                jarFile, "com/android/tests/basic/MainTest.class", "stringProvider");
        assertThat(Type.getType(stringProviderField.desc).getClassName())
                .isEqualTo("com.android.tests.basic.a");
    }
}
