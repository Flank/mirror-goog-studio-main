package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.android.build.OutputFile;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.TestVersions;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.ProjectBuildOutputUtils;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.common.utils.VariantOutputUtils;
import com.android.build.gradle.internal.errors.DeprecationReporter;
import com.android.builder.core.BuilderConstants;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.Variant;
import com.android.builder.model.VariantBuildOutput;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

/** Test to ensure that "auto" resConfig setting only package application's languages. */
@Ignore("b/77674062")
public class AutoPureSplitsTest {

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
                        + "  api \'com.android.support:support-v4:"
                        + TestVersions.SUPPORT_LIB_VERSION
                        + "\'\n"
                        + "}\n");
    }

    @Test
    public void testAutoResConfigsOnlyPackageAppSpecificLangWithAapt2() throws Exception {
        project.executor().run("clean", "assembleDebug");
        AndroidProject model =
                project.model()
                        .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
                        .fetchAndroidProjects()
                        .getOnlyModel();
        assertThat(model.getSyncIssues()).hasSize(3);
        for (SyncIssue syncIssue : model.getSyncIssues()) {
            if (!syncIssue
                            .getMessage()
                            .contains(
                                    "Configuration APKs are supported by the Google Play Store only when publishing Android Instant Apps. To instead generate stand-alone APKs for different device configurations, set generatePureSplits=false.")
                    && !syncIssue
                            .getMessage()
                            .contains(
                                    "DSL element 'DensitySplitOptions.auto' is obsolete and will be removed "
                                            + DeprecationReporter.DeprecationTarget
                                                    .AUTO_SPLITS_OR_RES_CONFIG.getRemovalTime())
                    && !syncIssue
                            .getMessage()
                            .contains(
                                    "DSL element 'LanguageSplitOptions.auto' is obsolete and will be removed "
                                            + DeprecationReporter.DeprecationTarget
                                                    .AUTO_SPLITS_OR_RES_CONFIG.getRemovalTime())) {
                fail("Unexpected sync issue : " + syncIssue.getMessage());
            }
        }
        // Load the custom model for the project
        Collection<Variant> variants = model.getVariants();
        assertEquals("Variant Count", 2, variants.size());

        // get the main artifact of the debug artifact
        Variant debugVariant = AndroidProjectUtils.getVariantByName(model, BuilderConstants.DEBUG);
        AndroidArtifact debugMainArtifact = debugVariant.getMainArtifact();
        assertNotNull("Debug main info null-check", debugMainArtifact);

        // get the outputs.
        ProjectBuildOutput projectBuildOutput = project.model().fetch(ProjectBuildOutput.class);
        VariantBuildOutput debugVariantOutput =
                ProjectBuildOutputUtils.getDebugVariantBuildOutput(projectBuildOutput);

        // build a map from expected file names to filters
        Map<String, String> expected = Maps.newHashMapWithExpectedSize(7);
        final String apkPrefix = "combinedDensityAndLanguagePureSplits";
        final String apkSuffix = "-debug.apk";

        expected.put(apkPrefix + apkSuffix, null);
        expected.put(apkPrefix + "-mdpi" + apkSuffix, "mdpi");
        expected.put(apkPrefix + "-hdpi" + apkSuffix, "hdpi");
        expected.put(apkPrefix + "-xhdpi" + apkSuffix, "xhdpi");
        expected.put(apkPrefix + "-xxhdpi" + apkSuffix, "xxhdpi");
        expected.put(apkPrefix + "-en" + apkSuffix, "en");
        expected.put(apkPrefix + "-fr" + apkSuffix, "fr,fr-rBE,fr-rCA");

        assertThat(debugVariantOutput.getOutputs()).hasSize(7);
        Map<String, String> actual = new HashMap<>();
        for (OutputFile outputFile : debugVariantOutput.getOutputs()) {
            String fileName = outputFile.getOutputFile().getName();

            String filter = VariantOutputUtils.getFilter(outputFile, OutputFile.DENSITY);
            if (filter == null) {
                filter = VariantOutputUtils.getFilter(outputFile, OutputFile.LANGUAGE);
            }

            assertEquals(
                    filter == null ? OutputFile.MAIN : OutputFile.SPLIT,
                    outputFile.getOutputType());

            // with pure splits, all split have the same version code.
            assertEquals(12, outputFile.getVersionCode());
            actual.put(fileName, filter);

        }

        // this checks we didn't miss any expected output.
        assertThat(actual).isEqualTo(expected);
    }
}
