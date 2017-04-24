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

import com.android.build.OutputFile;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Set;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Assemble tests for pure splits with flavors. */
public class DensitySplitInLWithFlavorsTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("densitySplitInL").create();

    @BeforeClass
    public static void setUp() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "  flavorDimensions \"foo\"\n"
                        + "  productFlavors {\n"
                        + "    f1{\n"
                        + "      dimension \"foo\"\n"
                        + "    }\n"
                        + "    f2{\n"
                        + "      dimension \"foo\"\n"
                        + "    }\n"
                        + "  }\n"
                        + "}");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkSplitOutputs() throws Exception {
        project.executor().run("clean", "assembleDebug");
        AndroidProject model = project.model().getSingle().getOnlyModel();

        // Check we generate all the expected outputs for both flavors.
        checkOutputs(model, "f1Debug");
        checkOutputs(model, "f2Debug");
    }

    private static void checkOutputs(AndroidProject projectModel, String variantName) {
        // build a set of expected outputs
        Set<String> expected = Sets.newHashSetWithExpectedSize(5);
        expected.add(null);
        expected.add("mdpi");
        expected.add("hdpi");
        expected.add("xhdpi");
        expected.add("xxhdpi");

        Collection<? extends OutputFile> outputs = getOutputs(projectModel, variantName);
        assertThat(outputs).hasSize(5);
        for (OutputFile outputFile : outputs) {
            String densityFilter = ModelHelper.getFilter(outputFile, OutputFile.DENSITY);
            if (densityFilter == null) {
                assertThat(outputFile.getOutputType()).contains(OutputFile.MAIN);
            } else {
                assertThat(outputFile.getOutputType()).contains(OutputFile.SPLIT);
            }
            expected.remove(densityFilter);
        }

        // this checks we didn't miss any expected output.
        assertThat(expected).isEmpty();
    }

    private static Collection<? extends OutputFile> getOutputs(
            AndroidProject projectModel, String variantName) {
        Collection<Variant> variants = projectModel.getVariants();
        assertThat(variants).hasSize(4);

        // get the main artifact of the debug artifact
        Variant debugVariant = ModelHelper.getVariant(variants, variantName);
        AndroidArtifact debugMainArtifact = debugVariant.getMainArtifact();
        assertThat(debugMainArtifact).isNotNull();

        // get the outputs.
        Collection<AndroidArtifactOutput> debugOutputs = debugMainArtifact.getOutputs();
        assertThat(debugOutputs).isNotNull();
        assertThat(debugOutputs).hasSize(1);

        AndroidArtifactOutput output = debugOutputs.iterator().next();
        // with pure splits, all split have the same version code.
        assertThat(output.getVersionCode()).isEqualTo(12);

        return output.getOutputs();
    }
}
