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
import com.android.builder.core.BuilderConstants;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
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
        AndroidProject model =
                project.executeAndReturnModel("clean", "assembleDebug").getOnlyModel();
        // Load the custom model for the project
        Collection<Variant> variants = model.getVariants();
        assertEquals("Variant Count", 2, variants.size());

        // get the main artifact of the debug artifact
        Variant debugVariant = ModelHelper.getVariant(variants, BuilderConstants.DEBUG);
        AndroidArtifact debugMainArtifact = debugVariant.getMainArtifact();
        assertNotNull("Debug main info null-check", debugMainArtifact);

        // get the outputs.
        Collection<AndroidArtifactOutput> debugOutputs = debugMainArtifact.getOutputs();
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

        assertEquals(1, debugOutputs.size());
        AndroidArtifactOutput output = debugOutputs.iterator().next();
        assertEquals(8, output.getOutputs().size());
        for (OutputFile outputFile : output.getOutputs()) {
            String filter = ModelHelper.getFilter(outputFile, VariantOutput.DENSITY);
            if (filter == null) {
                filter = ModelHelper.getFilter(outputFile, VariantOutput.ABI);
            }

            assertEquals(
                    filter == null ? VariantOutput.MAIN : VariantOutput.SPLIT,
                    outputFile.getOutputType());

            // with pure splits, all split have the same version code.
            assertEquals(123, output.getVersionCode());
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
