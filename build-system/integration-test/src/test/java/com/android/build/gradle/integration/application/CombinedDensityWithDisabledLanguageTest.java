package com.android.build.gradle.integration.application;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ApkHelper;
import com.android.build.gradle.integration.common.utils.AssumeUtil;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.VariantBuildOutput;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Tests that produce density pure splits but leave languages in the main APK. */
public class CombinedDensityWithDisabledLanguageTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("combinedDensityAndLanguagePureSplits")
                    .create();

    @Before
    public void setup() throws IOException, InterruptedException {
        AssumeUtil.assumeBuildToolsAtLeast(21);
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "          splits {\n"
                        + "            language {\n"
                        + "              enable false\n"
                        + "            }\n"
                        + "          }\n"
                        + "        }");

    }

    @Test
    public void testCombinedDensityAndDisabledLangPureSplits() throws Exception {
        ProjectBuildOutput projectBuildOutput =
                project.executeAndReturnModel(ProjectBuildOutput.class, "clean", "assembleDebug");
        VariantBuildOutput debugVariantOutput =
                ModelHelper.getDebugVariantBuildOutput(projectBuildOutput);

        // build a set of expected outputs
        Set<String> expected = Sets.newHashSetWithExpectedSize(5);
        expected.add("mdpi");
        expected.add("hdpi");
        expected.add("xhdpi");
        expected.add("xxhdpi");

        Collection<OutputFile> debugOutputs = debugVariantOutput.getOutputs();
        assertEquals(5, debugOutputs.size());
        for (OutputFile outputFile : debugOutputs) {
            String filter = ModelHelper.getFilter(outputFile, VariantOutput.DENSITY);
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

        //// check that our language resources are indeed packaged in the main APK.
        List<String> apkDump =
                ApkHelper.getApkBadging(
                        ModelHelper.getMainOutputFile(debugOutputs).getOutputFile());
        assertThat(apkDump)
                .containsAllOf(
                        "application-label-en:'DensitySplitInLc'",
                        "application-label-fr:'LanguageSplitInFr'",
                        "application-label-fr-CA:'LanguageSplitInFr'",
                        "application-label-fr-BE:'LanguageSplitInFr'");
    }
}
