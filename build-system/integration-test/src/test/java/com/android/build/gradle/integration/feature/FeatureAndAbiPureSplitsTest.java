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

import com.android.SdkConstants;
import com.android.build.OutputFile;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.VariantBuildOutput;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
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
    public void build() throws Exception {
        // Build all the things.
        sProject.executor().with(AaptGeneration.AAPT_V2_JNI).run("clean", "assembleDebug");

        Map<String, ProjectBuildOutput> multi = sProject.model().getMulti(ProjectBuildOutput.class);
        ProjectBuildOutput featureModule = multi.get(":feature_a");
        assertThat(featureModule.getVariantsBuildOutput()).hasSize(4);
        VariantBuildOutput debug = getDebugVariant(featureModule.getVariantsBuildOutput());
        assertThat(debug.getOutputs()).hasSize(1);
        OutputFile aarFile = Iterables.getOnlyElement(debug.getOutputs());

        // TODO : replace all the code below once the InstantApps model is ready.
        File apkFolder =
                new File(
                        aarFile.getOutputFile().getParentFile().getParentFile(),
                        "apk/feature/debug");
        assertThat(apkFolder).exists();
        File[] apkFiles = apkFolder.listFiles();

        List<String> expectedSplitNames =
                ImmutableList.of(
                        "featurea.config.x86",
                        "featurea.config.armeabi_v7a",
                        "featurea.config.hdpi");
        List<String> foundSplitNames = new ArrayList<>();
        Arrays.stream(apkFiles)
                .filter(apk -> apk.getName().endsWith(SdkConstants.DOT_ANDROID_PACKAGE))
                .forEach(
                        apk -> {
                            List<String> manifestContent =
                                    ApkSubject.getManifestContent(apk.toPath());
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
                                                        : targetABI);
                                assertThat(splitName).isEqualTo(split);
                                foundSplitNames.add(splitName);
                            }
                        });
        assertThat(foundSplitNames).containsExactlyElementsIn(expectedSplitNames);
    }

    private String getQuotedValue(String line) {
        int afterQuote = line.indexOf('"') + 1;
        return line.substring(afterQuote, line.indexOf('"', afterQuote));
    }

    private VariantBuildOutput getDebugVariant(Collection<VariantBuildOutput> outputs) {
        return outputs.stream()
                .filter(output -> output.getName().equals("debug"))
                .findFirst()
                .get();
    }
}
