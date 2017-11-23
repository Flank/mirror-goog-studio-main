package com.android.build.gradle.integration.application;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.AssumeUtil;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.VariantBuildOutput;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** test driver for combined density and language pure splits test. */
public class CombinedDensityAndLanguageTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("combinedDensityAndLanguagePureSplits")
                    .create();

    @Before
    public void setup() throws IOException, InterruptedException {
        AssumeUtil.assumeBuildToolsAtLeast(21);
    }

    @Test
    public void testCombinedDensityAndLanguagePureSplits() throws Exception {
        ProjectBuildOutput projectBuildOutput =
                project.executeAndReturnModel(ProjectBuildOutput.class, "clean", "assembleDebug");
        VariantBuildOutput debugBuildOutput =
                ModelHelper.getDebugVariantBuildOutput(projectBuildOutput);
        assertNotNull("Debug variant info null-check", debugBuildOutput);

        // get the outputs.
        Collection<OutputFile> debugOutputs = debugBuildOutput.getOutputs();
        assertNotNull(debugOutputs);

        // build a set of expected outputs
        Set<String> expected = Sets.newHashSetWithExpectedSize(5);
        expected.add("mdpi");
        expected.add("hdpi");
        expected.add("xhdpi");
        expected.add("xxhdpi");
        expected.add("en");
        expected.add("fr,fr-rBE");
        expected.add("fr-rCA");

        assertEquals(8, debugOutputs.size());
        for (OutputFile outputFile : debugOutputs) {
            String filter = ModelHelper.getFilter(outputFile, VariantOutput.DENSITY);
            if (filter == null) {
                filter = ModelHelper.getFilter(outputFile, VariantOutput.LANGUAGE);
            }

            assertEquals(
                    filter == null ? VariantOutput.MAIN : VariantOutput.SPLIT,
                    outputFile.getOutputType());

            // with pure splits, all split have the same version code.
            assertEquals(12, outputFile.getVersionCode());
            if (filter != null) {
                expected.remove(filter);
            }

        }

        // this checks we didn't miss any expected output.
        assertTrue(expected.isEmpty());
    }
}
