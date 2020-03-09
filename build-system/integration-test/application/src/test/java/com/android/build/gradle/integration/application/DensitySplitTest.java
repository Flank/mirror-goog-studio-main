package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.build.api.variant.BuiltArtifact;
import com.android.build.api.variant.BuiltArtifacts;
import com.android.build.api.variant.FilterConfiguration;
import com.android.build.api.variant.VariantOutputConfiguration;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.VariantOutputUtils;
import com.android.builder.core.BuilderConstants;
import com.android.testutils.apk.Apk;
import com.google.common.collect.Maps;
import com.google.common.truth.Truth;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Assemble tests for densitySplit. */
public class DensitySplitTest {
    private static BuiltArtifacts builtArtifacts;

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("densitySplit").create();

    @BeforeClass
    public static void setUp() throws IOException, InterruptedException {
        builtArtifacts =
                project.executeAndReturnOutputModels("clean", "assembleDebug")
                        .get(BuilderConstants.DEBUG);
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void testPackaging() throws IOException {
        Collection<BuiltArtifact> outputFiles = builtArtifacts.getElements();
        assertThat(outputFiles).hasSize(5);

        Apk mdpiApk = project.getApk("mdpi", GradleTestProject.ApkType.DEBUG);
        assertThat(mdpiApk).contains("res/drawable-mdpi-v4/other.png");

    }

    @Test
    public void checkVersionCodeInApk() throws IOException {
        Apk universalApk = project.getApk("universal", GradleTestProject.ApkType.DEBUG);
        assertThat(universalApk).hasVersionCode(112);
        assertThat(universalApk).hasVersionName("version 112");

        Apk mdpiApk = project.getApk("mdpi", GradleTestProject.ApkType.DEBUG);
        assertThat(mdpiApk).hasVersionCode(212);
        assertThat(mdpiApk).hasVersionName("version 212");

        Apk hdpiApk = project.getApk("hdpi", GradleTestProject.ApkType.DEBUG);
        assertThat(hdpiApk).hasVersionCode(312);
        assertThat(hdpiApk).hasVersionName("version 312");

        Apk xhdpiApk = project.getApk("xhdpi", GradleTestProject.ApkType.DEBUG);
        assertThat(xhdpiApk).hasVersionCode(412);
        assertThat(xhdpiApk).hasVersionName("version 412");

        Apk xxhdiApk = project.getApk("xxhdpi", GradleTestProject.ApkType.DEBUG);
        assertThat(xxhdiApk).hasVersionCode(512);
        assertThat(xxhdiApk).hasVersionName("version 512");
    }

    @Test
    public void checkVersionCodeInModel() {

        Collection<BuiltArtifact> debugOutputs = builtArtifacts.getElements();
        assertEquals(5, debugOutputs.size());

        // build a map of expected outputs and their versionCode
        Map<String, Integer> expected = Maps.newHashMapWithExpectedSize(5);
        expected.put(null, 112);
        expected.put("mdpi", 212);
        expected.put("hdpi", 312);
        expected.put("xhdpi", 412);
        expected.put("xxhdpi", 512);

        assertEquals(5, debugOutputs.size());
        for (BuiltArtifact output : debugOutputs) {
            String densityFilter =
                    VariantOutputUtils.getFilter(output, FilterConfiguration.FilterType.DENSITY);

            assertEquals(
                    densityFilter == null
                            ? VariantOutputConfiguration.OutputType.UNIVERSAL
                            : VariantOutputConfiguration.OutputType.ONE_OF_MANY,
                    output.getOutputType());
            assertNotNull(output.getOutputFile());

            Integer value = expected.get(densityFilter);
            // this checks we're not getting an unexpected output.
            assertNotNull(
                    "Check Valid output: " + (densityFilter == null ? "universal" : densityFilter),
                    value);

            assertThat(output.getVersionCode()).isEqualTo(value);
            expected.remove(densityFilter);
        }

        // this checks we didn't miss any expected output.
        assertTrue(expected.isEmpty());
    }
}
