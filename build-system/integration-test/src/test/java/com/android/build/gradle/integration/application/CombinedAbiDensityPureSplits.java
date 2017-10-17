package com.android.build.gradle.integration.application;

import static com.android.testutils.truth.MoreTruth.assertThatZip;
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

/** Test drive for the CombinedAbiDensityPureSplits samples test. */
public class CombinedAbiDensityPureSplits {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("combinedAbiDensityPureSplits").create();

    @Before
    public void setup() throws IOException, InterruptedException {
        // This test uses the deprecated NDK integration, which does not work properly on Windows.
        AssumeUtil.assumeNotWindows();
        AssumeUtil.assumeBuildToolsAtLeast(21);
    }

    @Test
    public void testCombinedDensityAndAbiPureSplits() throws Exception {
        ProjectBuildOutput projectBuildOutput =
                project.executeAndReturnModel(ProjectBuildOutput.class, "clean", "assembleDebug");
        VariantBuildOutput debugBuildOutput =
                ModelHelper.getDebugVariantBuildOutput(projectBuildOutput);

        // get the outputs.
        Collection<OutputFile> debugOutputs = debugBuildOutput.getOutputs();
        assertNotNull(debugOutputs);

        // build a set of expected outputs
        Set<String> expected = Sets.newHashSetWithExpectedSize(5);
        expected.add("mdpi");
        expected.add("hdpi");
        expected.add("xhdpi");
        expected.add("xxhdpi");
        expected.add("mips");
        expected.add("x86");
        expected.add("armeabi-v7a");

        assertEquals(8, debugOutputs.size());
        for (OutputFile outputFile : debugOutputs) {
            String filter = ModelHelper.getFilter(outputFile, VariantOutput.DENSITY);
            if (filter == null) {
                filter = ModelHelper.getFilter(outputFile, VariantOutput.ABI);
            }

            assertEquals(
                    filter == null ? VariantOutput.MAIN : VariantOutput.SPLIT,
                    outputFile.getOutputType());

            // with pure splits, all split have the same version code.
            assertEquals(123, outputFile.getVersionCode());
            if (filter != null) {
                expected.remove(filter);

                if (outputFile.getFilterTypes().contains(VariantOutput.ABI)) {
                    // if this is an ABI split, ensure the .so file presence (and only one)
                    assertThatZip(outputFile.getOutputFile()).entries("/lib/.*").hasSize(1);
                    assertThatZip(outputFile.getOutputFile())
                            .contains("lib/" + filter + "/libhello-jni.so");
                }


            } else {
                // main file should not have any lib/ entries.
                assertThatZip(outputFile.getOutputFile()).entries("/lib/.*").isEmpty();
            }

        }

        // this checks we didn't miss any expected output.
        assertTrue(expected.isEmpty());
    }
}
