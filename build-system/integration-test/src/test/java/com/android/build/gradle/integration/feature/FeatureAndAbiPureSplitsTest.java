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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.InstantAppProjectBuildOutput;
import com.android.builder.model.InstantAppVariantBuildOutput;
import com.android.builder.model.Variant;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Tests for instant apps modules with pure ABI config splits */
public class FeatureAndAbiPureSplitsTest {
    @ClassRule
    public static GradleTestProject sProject =
            GradleTestProject.builder().fromTestProject("projectWithFeaturesAndSplitABIs").create();

    @BeforeClass
    public static void setUp() throws Exception {}

    @AfterClass
    public static void cleanUp() {
        sProject = null;
    }

    @Test
    public void buildAndCheckModel() throws Exception {
        // Build all the things.
        sProject.executor().with(AaptGeneration.AAPT_V2_JNI).run("clean", "assembleDebug");

        Map<String, AndroidProject> projectModels = sProject.model().getMulti().getModelMap();
        AndroidProject instantAppProject = projectModels.get(":bundle");
        assertThat(instantAppProject).isNotNull();
        assertThat(instantAppProject.getVariants()).hasSize(2);
        Variant debugVariant =
                instantAppProject
                        .getVariants()
                        .stream()
                        .filter(output -> output.getName().equals("debug"))
                        .findFirst()
                        .get();
        assertThat(debugVariant.getMainArtifact().getOutputs()).hasSize(1);
        debugVariant
                .getMainArtifact()
                .getOutputs()
                .forEach(
                        androidArtifactOutput ->
                                assertThat(androidArtifactOutput.getOutputFile().getName())
                                        .isEqualTo("bundle-debug.zip"));

        Map<String, InstantAppProjectBuildOutput> models =
                sProject.model().getMulti(InstantAppProjectBuildOutput.class);
        assertThat(models).hasSize(1);

        InstantAppProjectBuildOutput instantAppModule = models.get(":bundle");
        assertThat(instantAppModule).isNotNull();
        assertThat(instantAppModule.getInstantAppVariantsBuildOutput()).hasSize(1);
        InstantAppVariantBuildOutput debug =
                getDebugVariant(instantAppModule.getInstantAppVariantsBuildOutput());
        assertThat(debug.getApplicationId()).isEqualTo("com.example.android.multiproject");
        assertThat(debug.getOutput().getOutputFile().getName()).isEqualTo("bundle-debug.zip");
        assertThat(debug.getFeatureOutputs()).hasSize(5);

        List<String> expectedFileNames =
                ImmutableList.of(
                        "baseFeature-debug.apk",
                        "feature_a-debug.apk",
                        "feature_a-x86-debug.apk",
                        "feature_a-armeabi-v7a-debug.apk",
                        "feature_a-hdpi-debug-unsigned.apk");
        List<String> foundFileNames = new ArrayList<>();
        debug.getFeatureOutputs()
                .forEach(outputFile -> foundFileNames.add(outputFile.getOutputFile().getName()));
        assertThat(foundFileNames).containsExactlyElementsIn(expectedFileNames);

        List<String> expectedSplitNames =
                ImmutableList.of(
                        "feature_a.config.x86",
                        "feature_a.config.armeabi_v7a",
                        "feature_a.config.hdpi");
        List<String> foundSplitNames = new ArrayList<>();
        debug.getFeatureOutputs()
                .forEach(
                        outputFile -> {
                            List<String> manifestContent =
                                    ApkSubject.getManifestContent(
                                            outputFile.getOutputFile().toPath());
                            String configForSplit = "";
                            String targetABI = "";
                            String split = "";
                            for (String line : manifestContent) {
                                if (line.contains("configForSplit=")) {
                                    configForSplit = getQuotedValue(line);
                                }
                                if (line.contains("targetABI=")) {
                                    targetABI = getQuotedValue(line);
                                }
                                if (line.contains("split=")) {
                                    split = getQuotedValue(line);
                                }
                            }
                            if (!Strings.isNullOrEmpty(configForSplit)) {
                                String splitName =
                                        configForSplit
                                                + ".config."
                                                + (Strings.isNullOrEmpty(targetABI)
                                                        ? "hdpi"
                                                        : targetABI.replace("-", "_"));
                                assertThat(splitName).isEqualTo(split);
                                foundSplitNames.add(splitName);
                            }
                        });
        assertThat(foundSplitNames).containsExactlyElementsIn(expectedSplitNames);
    }

    private static String getQuotedValue(String line) {
        int afterQuote = line.indexOf('"') + 1;
        return line.substring(afterQuote, line.indexOf('"', afterQuote));
    }

    private static InstantAppVariantBuildOutput getDebugVariant(
            Collection<InstantAppVariantBuildOutput> outputs) {
        return outputs.stream()
                .filter(output -> output.getName().equals("debug"))
                .findFirst()
                .get();
    }
}
