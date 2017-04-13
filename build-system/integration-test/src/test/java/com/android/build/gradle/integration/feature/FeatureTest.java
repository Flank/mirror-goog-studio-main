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

import static com.android.SdkConstants.FD_RES_CLASS;
import static com.android.SdkConstants.FD_SOURCE_GEN;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.testutils.truth.MoreTruth.assertThatZip;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitDeclaration;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitPackageIds;
import com.android.builder.model.AndroidProject;
import com.android.testutils.truth.ZipFileSubject;
import com.android.utils.FileUtils;
import java.io.File;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Test atom project with a dependency on an external library. */
public class FeatureTest {
    @ClassRule
    public static GradleTestProject sProject =
            GradleTestProject.builder()
                    .fromTestProject("projectWithFeatures")
                    .withoutNdk()
                    .create();

    @BeforeClass
    public static void setUp() throws Exception {}

    @AfterClass
    public static void cleanUp() {
        sProject = null;
    }

    @Test
    public void build() throws Exception {
        // just run test for now.
        sProject.executor()
                .with(AaptGeneration.AAPT_V2_JNI)
                .withEnabledFeatureSplitTransitionalAttributes(true)
                .run("assemble");

        // check the feature declaration file presence.
        GradleTestProject featureProject = sProject.getSubproject(":feature");
        File featureSplit =
                featureProject.getIntermediateFile(
                        "feature-split/declaration/feature/release/feature-split.json");
        assertThat(featureSplit.exists());
        FeatureSplitDeclaration featureSplitDeclaration =
                FeatureSplitDeclaration.load(featureSplit);
        assertThat(featureSplitDeclaration).isNotNull();
        assertThat(featureSplitDeclaration.getUniqueIdentifier()).isEqualTo(":feature");

        // Check the feature manifest contains only the feature data.
        File featureManifest =
                featureProject.getIntermediateFile(
                        "manifests/full/feature/release/AndroidManifest.xml");
        assertThat(featureManifest.exists());
        assertThat(featureManifest)
                .containsAllOf(
                        "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"",
                        "package=\"com.example.android.multiproject\"",
                        "split=\"feature\"",
                        "<meta-data",
                        "android:name=\"feature\"",
                        "android:value=\"84\" />",
                        "<activity",
                        "android:name=\"com.example.android.multiproject.feature.MainActivity\"",
                        "android:label=\"@string/app_name\"",
                        "split=\"feature\" >");
        assertThat(featureManifest).doesNotContain("android:name=\"library\"");
        assertThat(featureManifest).doesNotContain("android:value=\"42\"");

        // Check the R.java file builds with the right IDs.
        File featureResFile =
                featureProject.file(
                        FileUtils.join(
                                "build",
                                AndroidProject.FD_GENERATED,
                                FD_SOURCE_GEN,
                                FD_RES_CLASS,
                                "feature",
                                "debug",
                                "com",
                                "example",
                                "android",
                                "multiproject",
                                "feature",
                                "R.java"));
        assertThat(featureResFile).isFile();
        assertThat(featureResFile).containsAllOf("public static final int feature_value=0x80");

        // Check the feature APK contains the expected classes.
        try (ApkSubject featureApk =
                assertThatApk(featureProject.getFeatureApk(GradleTestProject.ApkType.DEBUG))) {
            featureApk.exists();
            featureApk.containsClass("Lcom/example/android/multiproject/feature/R;");
            featureApk.containsClass("Lcom/example/android/multiproject/feature/MainActivity;");
            featureApk.doesNotContainClass("Lcom/example/android/multiproject/R;");
            featureApk.doesNotContainClass("Lcom/example/android/multiproject/library/R;");
            featureApk.doesNotContainClass("Lcom/example/android/multiproject/library/PersonView;");
            featureApk.doesNotContainClass("Lcom/example/android/multiproject/base/PersonView2;");
        }

        // check the base feature declared the list of features and their associated IDs.
        GradleTestProject baseProject = sProject.getSubproject(":baseFeature");
        File idsList =
                baseProject.getIntermediateFile("feature-split/ids/feature/debug/package_ids.json");
        assertThat(idsList.exists());
        FeatureSplitPackageIds packageIds = FeatureSplitPackageIds.load(idsList);
        assertThat(packageIds).isNotNull();
        assertThat(packageIds.getIdFor(":feature")).isEqualTo(FeatureSplitPackageIds.BASE_ID);

        // Check that the base feature manifest contains the expected content.
        File baseFeatureManifest =
                baseProject.getIntermediateFile(
                        "manifests/full/feature/release/AndroidManifest.xml");
        assertThat(baseFeatureManifest.exists());
        assertThat(baseFeatureManifest)
                .containsAllOf(
                        "<meta-data",
                        "android:name=\"feature\"",
                        "android:value=\"84\" />",
                        "android:name=\"library\"",
                        "android:value=\"42\" />",
                        "<activity",
                        "android:name=\"com.example.android.multiproject.feature.MainActivity\"",
                        "split=\"feature\"",
                        "android:label=\"@string/app_name\" >");
        assertThat(baseFeatureManifest).doesNotContain("featureSplit");

        // Check that the base feature resource package is built properly.
        try (ZipFileSubject baseFeatureResources =
                assertThatZip(
                        baseProject.getIntermediateFile("res/feature/debug/resources-debug.ap_"))) {
            baseFeatureResources.contains("AndroidManifest.xml");
            baseFeatureResources.contains("resources.arsc");
        }

        // Check the feature APK contains the expected classes.
        try (ApkSubject baseFeatureApk =
                assertThatApk(baseProject.getFeatureApk(GradleTestProject.ApkType.DEBUG))) {
            baseFeatureApk.exists();
            baseFeatureApk.containsClass("Lcom/example/android/multiproject/R;");
            baseFeatureApk.containsClass("Lcom/example/android/multiproject/library/R;");
            baseFeatureApk.containsClass("Lcom/example/android/multiproject/library/PersonView;");
            baseFeatureApk.containsClass("Lcom/example/android/multiproject/base/PersonView2;");
        }
    }
}
