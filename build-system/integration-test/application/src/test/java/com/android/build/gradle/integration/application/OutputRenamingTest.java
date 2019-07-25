/*
 * Copyright (C) 2014 The Android Open Source Project
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
import static com.android.builder.core.BuilderConstants.DEBUG;
import static com.android.builder.core.BuilderConstants.RELEASE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ProjectBuildOutputUtils;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.Variant;
import com.android.builder.model.VariantBuildOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Assemble tests for class densitySplitInL */
public class OutputRenamingTest {

    private static AndroidProject model;
    private static ProjectBuildOutput outputModel;

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("densitySplit").create();

    @BeforeClass
    public static void setup() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "applicationVariants.all { variant ->\n"
                        + "    // Custom APK names (do not do this for 'dev' build type)\n"
                        + "    println variant.buildType.name\n"
                        + "    def baseFileName = \"project-${variant.flavorName}-${variant.versionCode}-${variant.buildType.name}\"\n"
                        + "    variant.outputs.all { output -> \n"
                        + "      output.outputFileName = \"${baseFileName}-${output.getFilter(com.android.build.VariantOutput.FilterType.DENSITY)}-signed.apk\"\n"
                        + "    }\n"
                        + "  }\n"
                        + "}");
        outputModel = project.executeAndReturnOutputModel("clean", "assemble");
        model =
                project.model()
                        .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
                        .fetchAndroidProjects()
                        .getOnlyModel();
        Collection<SyncIssue> syncIssues =
                project.model()
                        .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
                        .fetchAndroidProjects()
                        .getOnlyModelSyncIssues();
        assertThat(syncIssues).hasSize(0);
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        model = null;
    }

    @Test
    public void checkSplitOutputs() {
        Collection<Variant> variants = model.getVariants();
        assertEquals("Variant Count", 2, variants.size());

        assertFileRenaming(DEBUG);
        assertFileRenaming(RELEASE);
    }

    private static void assertFileRenaming(String buildType) {
        Collection<VariantBuildOutput> variantBuildOutputs = outputModel.getVariantsBuildOutput();
        assertThat(variantBuildOutputs).hasSize(2);
        VariantBuildOutput buildOutput =
                ProjectBuildOutputUtils.getVariantBuildOutput(outputModel, buildType);

        // get the outputs.
        Collection<OutputFile> outputs = buildOutput.getOutputs();
        assertNotNull(outputs);
        assertThat(outputs).hasSize(5);

        for (OutputFile fileOutput : buildOutput.getOutputs()) {
            String filterValue =
                    fileOutput
                            .getFilters()
                            .stream()
                            .filter(
                                    filterData ->
                                            filterData
                                                    .getFilterType()
                                                    .equals(
                                                            VariantOutput.FilterType.DENSITY
                                                                    .name()))
                            .map(FilterData::getIdentifier)
                            .findFirst()
                            .orElse(VariantOutput.NO_FILTER);

            String expectedFileName =
                    "project--12-"
                            + buildType.toLowerCase(Locale.ENGLISH)
                            + "-"
                            + filterValue
                            + "-signed.apk";

            assertEquals(expectedFileName, fileOutput.getOutputFile().getName());
            assertTrue(fileOutput.getOutputFile().exists());
        }
    }
}
