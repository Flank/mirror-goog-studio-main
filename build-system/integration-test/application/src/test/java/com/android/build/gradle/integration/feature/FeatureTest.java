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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.testutils.truth.FileSubject.assertThat;
import static com.android.testutils.truth.MoreTruth.assertThatZip;
import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.scope.ArtifactTypeUtil;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSetMetadata;
import com.android.build.gradle.internal.tasks.featuresplit.FeatureSplitDeclaration;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.VariantBuildOutput;
import com.android.testutils.truth.ZipFileSubject;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;

/** Test a simple project with features with a library dependency. */
public class FeatureTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("projectWithFeatures")
                    .withoutNdk()
                    .create();

    @Test
    public void publishApplicationId() throws Exception {
        // Call the task to publish the base feature application ID.
        project.executor()
                .withEnabledAapt2(true)
                .run("clean", ":baseFeature:writeDebugFeatureApplicationId");
    }

    @Test
    public void testOreoAndAboveFeatureIdAllocation() throws Exception {
        testFeatureIdAllocation(26, 0x80);
    }

    @Test
    public void testBeforeOreoFeatureIdAllocation() throws Exception {
        testFeatureIdAllocation(25, 0x7e);
    }

    private void testFeatureIdAllocation(int minSdkVersion, int expectedFeatureId)
            throws Exception {
        GradleTestProject featureProject = project.getSubproject(":feature");

        TestFileUtils.appendToFile(
                project.getSubproject(":app").getBuildFile(),
                "android.defaultConfig.minSdkVersion " + minSdkVersion + "\n");
        TestFileUtils.appendToFile(
                featureProject.getBuildFile(),
                "android.defaultConfig.minSdkVersion " + minSdkVersion + "\n");
        TestFileUtils.appendToFile(
                project.getSubproject(":baseFeature").getBuildFile(),
                "android.defaultConfig.minSdkVersion " + minSdkVersion + "\n");

        // Build all the things.
        project.executor().withEnabledAapt2(true).run("clean", "assemble");

        // check the base feature declared the list of features and their associated IDs.
        GradleTestProject baseProject = project.getSubproject(":baseFeature");
        File idsList =
                baseProject.getIntermediateFile(
                        "feature_set_metadata",
                        "debugFeature",
                        "generateDebugFeatureFeatureMetadata",
                        "feature-metadata.json");
        assertThat(idsList).exists();
        FeatureSetMetadata packageIds = FeatureSetMetadata.load(idsList);
        assertThat(packageIds).isNotNull();
        assertThat(packageIds.getResOffsetFor(":feature")).isEqualTo(expectedFeatureId);

        // Check the R.java file builds with the right IDs.
        File featureResFile =
                FileUtils.join(
                        ArtifactTypeUtil.getOutputDir(
                                InternalArtifactType.NOT_NAMESPACED_R_CLASS_SOURCES,
                                featureProject.file("build")),
                        "debugFeature",
                        "processDebugFeatureResources",
                        SdkConstants.FD_RES_CLASS,
                        "com",
                        "example",
                        "android",
                        "multiproject",
                        "feature",
                        "R.java");
        assertThat(featureResFile).isFile();
        // check the feature ID in hexadecimal format.
        assertThat(featureResFile)
                .containsAllOf(
                        "public static final int feature_value="
                                + String.format("0x%02x", expectedFeatureId));
    }

    @Test
    public void build() throws Exception {
        // Build all the things.
        project.executor().withEnabledAapt2(true).run("clean", "assemble");

        // check the feature declaration file presence.
        GradleTestProject featureProject = project.getSubproject(":feature");
        File featureSplit =
                featureProject.getIntermediateFile(
                        "metadata_feature_declaration",
                        "releaseFeature",
                        "featureReleaseFeatureWriter",
                        "out",
                        "feature-split.json");
        assertThat(featureSplit).exists();
        FeatureSplitDeclaration featureSplitDeclaration =
                FeatureSplitDeclaration.load(featureSplit);
        assertThat(featureSplitDeclaration).isNotNull();
        assertThat(featureSplitDeclaration.getModulePath()).isEqualTo(":feature");

        // Check the feature manifest contains only the feature data.
        File featureManifest =
                featureProject.getIntermediateFile(
                        "merged_manifests",
                        "releaseFeature",
                        "processReleaseFeatureManifest",
                        "merged",
                        "AndroidManifest.xml");
        assertThat(featureManifest).exists();

        assertThat(featureManifest)
                .containsAllOf(
                        "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"",
                        "package=\"com.example.android.multiproject\"",
                        "featureSplit=\"feature\"",
                        "<meta-data",
                        "android:name=\"feature\"",
                        "android:value=\"84\" />",
                        "<activity",
                        "android:name=\"com.example.android.multiproject.feature.MainActivity\"",
                        "android:label=\"@string/app_name\"",
                        "splitName=\"feature\"");
        assertThat(featureManifest).doesNotContain("android:name=\"library\"");
        assertThat(featureManifest).doesNotContain("android:value=\"42\"");

        // Check the feature APK contains the expected classes.
        try (ApkSubject featureApk =
                assertThatApk(featureProject.getFeatureApk(GradleTestProject.ApkType.DEBUG))) {
            featureApk.exists();
            featureApk.containsClass("Lcom/example/android/multiproject/feature/R;");
            featureApk.containsClass("Lcom/example/android/multiproject/feature/R$string;");
            featureApk.containsClass("Lcom/example/android/multiproject/feature/MainActivity;");
            featureApk.doesNotContainClass("Lcom/example/android/multiproject/R;");
            featureApk.doesNotContainClass("Lcom/example/android/multiproject/library/R;");
            featureApk.doesNotContainClass("Lcom/example/android/multiproject/library/PersonView;");
            featureApk.doesNotContainClass("Lcom/example/android/multiproject/base/PersonView2;");
        }

        GradleTestProject baseProject = project.getSubproject(":baseFeature");

        // Check that the base feature manifest contains the expected content.
        File baseFeatureManifest =
                baseProject.getIntermediateFile(
                        "merged_manifests",
                        "releaseFeature",
                        "processReleaseFeatureManifest",
                        "merged",
                        "AndroidManifest.xml");
        assertThat(baseFeatureManifest).exists();
        assertThat(baseFeatureManifest)
                .containsAllOf(
                        "<meta-data",
                        "android:name=\"feature\"",
                        "android:value=\"84\" />",
                        "android:name=\"library\"",
                        "android:value=\"42\" />",
                        "<activity",
                        "android:name=\"com.example.android.multiproject.feature.MainActivity\"",
                        "splitName=\"feature\"",
                        "android:label=\"@string/app_name\" >");
        assertThat(baseFeatureManifest).doesNotContain("featureSplit");

        // Check that the base feature resource package is built properly.
        try (ZipFileSubject baseFeatureResources =
                assertThatZip(
                        baseProject.getIntermediateFile(
                                "processed_res",
                                "debugFeature",
                                "processDebugFeatureResources",
                                "out",
                                "resources-debugFeature.ap_"))) {
            baseFeatureResources.exists();
            baseFeatureResources.contains("AndroidManifest.xml");
            baseFeatureResources.contains("resources.arsc");
        }

        // Check the feature APK contains the expected classes.
        try (ApkSubject baseFeatureApk =
                assertThatApk(baseProject.getFeatureApk(GradleTestProject.ApkType.DEBUG))) {
            baseFeatureApk.exists();
            baseFeatureApk.containsClass("Lcom/example/android/multiproject/base/R;");
            baseFeatureApk.containsClass("Lcom/example/android/multiproject/library/R;");
            baseFeatureApk.containsClass("Lcom/example/android/multiproject/library/PersonView;");
            baseFeatureApk.containsClass("Lcom/example/android/multiproject/base/PersonView2;");
        }

        // Check that the instantApp bundle gets built properly.
        try (ZipFileSubject instantAppBundle =
                assertThatZip(project.getSubproject(":bundle").getInstantAppBundle("release"))) {
            instantAppBundle.exists();
            instantAppBundle.contains("baseFeature-release-unsigned.apk");
            instantAppBundle.contains("feature-release-unsigned.apk");
        }
    }

    @Test
    public void testMinimalisticModel() throws Exception {
        project.executor().withEnabledAapt2(true).run("clean", "assemble");

        // get the initial minimalistic model.
        Map<String, ProjectBuildOutput> multi =
                project.model().fetchMulti(ProjectBuildOutput.class);

        ProjectBuildOutput projectBuildOutput = multi.get(":feature");
        assertThat(projectBuildOutput).isNotNull();

        Collection<VariantBuildOutput> variantsBuildOutput =
                projectBuildOutput.getVariantsBuildOutput();
        assertThat(variantsBuildOutput).hasSize(4);

        List<String> expectedVariantNames =
                Lists.newArrayList(
                        "debugFeature", "releaseFeature", // for the feature split
                        "debug", "release"); // for the aar part.

        variantsBuildOutput.forEach(
                variantBuildOutput -> {
                    assertThat(expectedVariantNames.remove(variantBuildOutput.getName())).isTrue();
                    OutputFile output =
                            Iterators.getOnlyElement(variantBuildOutput.getOutputs().iterator());
                    assertThat(output.getOutputType())
                            .isEqualTo(VariantOutput.OutputType.MAIN.toString());
                    assertThat(output.getFilters()).isEmpty();
                });
        assertThat(expectedVariantNames).isEmpty();

        Map<String, AndroidProject> models =
                project.model().fetchAndroidProjects().getOnlyModelMap();
        assertThat(models.get(":feature").isBaseSplit()).isFalse();
        assertThat(models.get(":baseFeature").isBaseSplit()).isTrue();
    }

    @Test
    public void incrementalAllVariantsBuild() throws Exception {
        project.executor().withEnabledAapt2(true).run("clean", "assemble");

        GradleTestProject featureProject = project.getSubproject(":feature");

        // get the initial minimalistic model.
        Map<String, ProjectBuildOutput> multi =
                project.model().fetchMulti(ProjectBuildOutput.class);

        ProjectBuildOutput projectBuildOutput = multi.get(":feature");
        assertThat(projectBuildOutput).isNotNull();

        Map<String, Long> originalApkTimestamps =
                getVariantNameToTimestamp(projectBuildOutput.getVariantsBuildOutput());

        // now change a source file
        String javaCode = generateClass();

        File addedSource =
                featureProject.file(
                        "src/main/java/com/example/android/multiproject/feature/Hello.java");

        Files.write(javaCode, addedSource, Charsets.UTF_8);

        GradleBuildResult assemble = project.executor().withEnabledAapt2(true).run("assemble");

        multi = project.model().fetchMulti(ProjectBuildOutput.class);

        assertThat(assemble.getNotUpToDateTasks()).contains(":feature:assembleDebug");

        Map<String, File> modifiedApks =
                getVariantNameToOutputFileMap(multi.get(":feature").getVariantsBuildOutput());

        // assert that all APKs are present and contains the new class.
        for (Map.Entry<String, File> stringFileEntry : modifiedApks.entrySet()) {
            File file = stringFileEntry.getValue();
            assertThat(originalApkTimestamps).containsKey(stringFileEntry.getKey());
            assertThat(file).exists();
            if (file.getName().endsWith(SdkConstants.DOT_ANDROID_PACKAGE)) {
                try (ApkSubject apk = assertThatApk(file)) {
                    apk.containsClass("Lcom/example/android/multiproject/feature/Hello;");
                }
            }
        }
        assertThat(addedSource.delete()).isTrue();
    }

    @Test
    public void incrementalBuild() throws Exception {
        project.executor().withEnabledAapt2(true).run("clean", "assemble");

        GradleTestProject featureProject = project.getSubproject(":feature");

        // get the initial minimalistic model.
        Map<String, ProjectBuildOutput> multi =
                project.model().fetchMulti(ProjectBuildOutput.class);

        ProjectBuildOutput projectBuildOutput = multi.get(":feature");
        assertThat(projectBuildOutput).isNotNull();

        Map<String, Long> originalApkTimestamps =
                getVariantNameToOutputFileMap(projectBuildOutput.getVariantsBuildOutput())
                        .entrySet()
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        Map.Entry::getKey, e -> e.getValue().lastModified()));

        // now change a source file
        String javaCode = generateClass();

        File addedSource =
                featureProject.file(
                        "src/main/java/com/example/android/multiproject/feature/Hello.java");

        Files.write(javaCode, addedSource, Charsets.UTF_8);

        GradleBuildResult assembleDebug =
                project.executor().withEnabledAapt2(true).run("assembleDebug");

        multi = project.model().fetchMulti(ProjectBuildOutput.class);

        assertThat(assembleDebug.getNotUpToDateTasks()).contains(":feature:assembleDebug");

        Map<String, File> modifiedApks =
                getVariantNameToOutputFileMap(multi.get(":feature").getVariantsBuildOutput());

        // assert that all APKs were rebuilt.
        for (Map.Entry<String, File> stringFileEntry : modifiedApks.entrySet()) {

            String variantName = stringFileEntry.getKey();
            File file = stringFileEntry.getValue();
            assertThat(originalApkTimestamps).containsKey(variantName);
            if (variantName.contains("release")) {
                assertThat(originalApkTimestamps.get(variantName)).isEqualTo(file.lastModified());
            } else {
                // check the .class file got added.
                assertThat(file).exists();
                if (file.getName().endsWith(SdkConstants.DOT_ANDROID_PACKAGE)) {
                    try (ApkSubject apk = assertThatApk(file)) {
                        apk.containsClass("Lcom/example/android/multiproject/feature/Hello;");
                    }
                }
            }
        }

        assertThat(addedSource.delete()).isTrue();
    }

    @Test
    public void incrementalManifestBuild() throws Exception {
        project.executor().withEnabledAapt2(true).run("clean", "assembleDebug");

        // now change the feature manifest
        GradleTestProject featureProject = project.getSubproject(":feature");
        File featureManifest = featureProject.file("src/main/AndroidManifest.xml");
        String content = Files.toString(featureManifest, Charsets.UTF_8);
        content = content.replace("84", "42");
        Files.write(content, featureManifest, Charsets.UTF_8);

        GradleBuildResult run = project.executor().withEnabledAapt2(true).run("assembleDebug");
        assertThat(run.getNotUpToDateTasks()).contains(":baseFeature:processDebugFeatureManifest");
    }

    private static String generateClass() {
        return "package com.example.android.multiproject.feature; \n"
                + "import android.app.Activity;\n"
                + "import android.os.Bundle;\n"
                + "\n"
                + "public class Hello extends Activity {\n"
                + "   @Override\n"
                + "   public void onCreate(Bundle savedInstanceState) {"
                + "   }\n"
                + "}\n";
    }

    private static Map<String, File> getVariantNameToOutputFileMap(
            Collection<VariantBuildOutput> variantsBuildOutput) {

        ImmutableMap.Builder<String, File> variantNameToApkTimestamp = ImmutableMap.builder();
        variantsBuildOutput.forEach(
                variantBuildOutput -> {
                    OutputFile output =
                            Iterators.getOnlyElement(variantBuildOutput.getOutputs().iterator());
                    variantNameToApkTimestamp.put(
                            variantBuildOutput.getName(), output.getOutputFile());
                });
        return variantNameToApkTimestamp.build();
    }

    private static Map<String, Long> getVariantNameToTimestamp(
            Collection<VariantBuildOutput> variantsBuildOutput) {

        return getVariantNameToOutputFileMap(variantsBuildOutput)
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().lastModified()));
    }
}
