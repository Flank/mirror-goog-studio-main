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

package com.android.build.gradle.integration.feature;

import static com.android.testutils.truth.MoreTruth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.testutils.apk.Apk;
import com.android.testutils.apk.Dex;
import java.util.Optional;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;

public class FeatureLibraryDepTest {
    @ClassRule
    public static GradleTestProject sProject =
            GradleTestProject.builder()
                    .fromTestProject("projectWithFeatures")
                    .withoutNdk()
                    .create();

    @BeforeClass
    public static void setUp() throws Exception {
        TestFileUtils.appendToFile(sProject.getSettingsFile(), "include 'libfeat'\n");
        TestFileUtils.appendToFile(
                sProject.getSubproject("feature").getBuildFile(),
                "dependencies {\n" + "    implementation project(':libfeat')\n" + "}\n");
    }

    @AfterClass
    public static void cleanUp() {
        sProject = null;
    }

    @Test
    public void build() throws Exception {
        // Build all the things.
        sProject.executor().with(AaptGeneration.AAPT_V2_JNI).run("clean", "assemble");

        // Check the library class was not packaged in the feature APK.
        Apk apk = sProject.getSubproject("feature").getFeatureApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk.getFile()).isFile();
        Optional<Dex> dex = apk.getMainDexFile();
        assertThat(dex).isPresent();

        //noinspection OptionalGetWithoutIsPresent
        final Dex dexFile = dex.get();
        assertThat(dexFile)
                .doesNotContainClasses("Lcom/example/android/multiproject/library/PersonView;");
    }
}
