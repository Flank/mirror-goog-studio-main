package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.build.OutputFile;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.core.BuilderConstants;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Test to ensure that "auto" resConfig setting only package application's languages. */
public class AutoPureSplits {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("combinedDensityAndLanguagePureSplits")
                    .create();

    @Before
    public void setUpBuildFile() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "  defaultConfig {\n"
                        + "    versionCode 12\n"
                        + "    minSdkVersion 21\n"
                        + "    targetSdkVersion 21\n"
                        + "  }\n"
                        + "\n"
                        + "  splits {\n"
                        + "    density {\n"
                        + "      enable true\n"
                        + "      auto true\n"
                        + "    }\n"
                        + "    language {\n"
                        + "      enable true\n"
                        + "      auto true\n"
                        + "    }\n"
                        + "  }\n"
                        + "}\n"
                        + "dependencies {\n"
                        + "  compile \'com.android.support:support-v4:"
                        + GradleTestProject.SUPPORT_LIB_VERSION
                        + "\'\n"
                        + "}\n");
    }

    @Test
    public void testAutoResConfigsOnlyPackageAppSpecificLanguage() throws Exception {
        project.executor().withEnabledAapt2(false).run("clean", "assembleDebug");
        AndroidProject model = project.model().getSingle().getOnlyModel();

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
        expected.add("mdpi-v4");
        expected.add("hdpi-v4");
        expected.add("xhdpi-v4");
        expected.add("xxhdpi-v4");
        expected.add("en");
        expected.add("fr");
        expected.add("fr-rBE");
        expected.add("fr-rCA");

        assertEquals(1, debugOutputs.size());
        AndroidArtifactOutput output = debugOutputs.iterator().next();
        assertEquals(9, output.getOutputs().size());
        Set<String> actual = new HashSet<>();
        for (OutputFile outputFile : output.getOutputs()) {
            String filter = ModelHelper.getFilter(outputFile, OutputFile.DENSITY);
            if (filter == null) {
                filter = ModelHelper.getFilter(outputFile, OutputFile.LANGUAGE);
            }

            assertEquals(
                    filter == null ? OutputFile.MAIN : OutputFile.SPLIT,
                    outputFile.getOutputType());

            // with pure splits, all split have the same version code.
            assertEquals(12, output.getVersionCode());
            if (filter != null) {
                actual.add(filter);
            }

        }

        // this checks we didn't miss any expected output.
        assertEquals(expected, actual);
    }

    @Test
    public void testAutoResConfigsOnlyPackageAppSpecificLangWithAapt2() throws Exception {
        project.executor().withEnabledAapt2(true).run("clean", "assembleDebug");
        AndroidProject model = project.model().getSingle().getOnlyModel();

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
        expected.add("en");
        expected.add("fr");
        expected.add("fr-rBE");
        expected.add("fr-rCA");

        assertEquals(1, debugOutputs.size());
        AndroidArtifactOutput output = debugOutputs.iterator().next();
        assertEquals(9, output.getOutputs().size());
        Set<String> actual = new HashSet<>();
        for (OutputFile outputFile : output.getOutputs()) {
            String filter = ModelHelper.getFilter(outputFile, OutputFile.DENSITY);
            if (filter == null) {
                filter = ModelHelper.getFilter(outputFile, OutputFile.LANGUAGE);
            }

            assertEquals(
                    filter == null ? OutputFile.MAIN : OutputFile.SPLIT,
                    outputFile.getOutputType());

            // with pure splits, all split have the same version code.
            assertEquals(12, output.getVersionCode());
            if (filter != null) {
                actual.add(filter);
            }

        }

        // this checks we didn't miss any expected output.
        assertThat(actual).containsExactlyElementsIn(expected);
    }
}
