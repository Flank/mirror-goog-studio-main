package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.build.OutputFile;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.TemporaryProjectModification;
import com.android.build.gradle.integration.common.utils.AssumeUtil;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.builder.core.BuilderConstants;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.testutils.apk.Zip;
import com.android.testutils.truth.MoreTruth;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

/** Test drive for the abiPureSplits samples test. */
public class AbiPureSplits {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("abiPureSplits").create();

    @BeforeClass
    public static void setup() {
        AssumeUtil.assumeBuildToolsAtLeast(21);
    }

    @Test
    public void testAbiPureSplits() throws Exception {
        AndroidProject model = assembleAndGetModel();

        // build a set of expected outputs
        Set<String> expected = Sets.newHashSetWithExpectedSize(5);
        expected.add("mips");
        expected.add("x86");
        expected.add("armeabi-v7a");

        List<? extends OutputFile> outputs = getOutputs(model);
        assertEquals(4, outputs.size());
        for (OutputFile outputFile : outputs) {
            String filter = ModelHelper.getFilter(outputFile, OutputFile.ABI);
            assertEquals(
                    filter == null ? OutputFile.MAIN : OutputFile.SPLIT,
                    outputFile.getOutputType());

            // with pure splits, all split have the same version code.
            if (filter != null) {
                assertTrue(expected.remove(filter));

                if (outputFile.getFilterTypes().contains(OutputFile.ABI)) {
                    // if this is an ABI split, ensure the .so file presence (and only one)
                    MoreTruth.assertThatZip(outputFile.getOutputFile())
                            .entries("/lib/.*")
                            .containsExactly("/lib/" + filter + "/libhello-jni.so");
                }


            } else {
                try (Zip zip = new Zip(outputFile.getOutputFile())) {
                    // main file should not have any lib/ entries.
                    assertThat(zip.getEntries(Pattern.compile("^/lib/.*"))).isEmpty();
                    // assert that our resources got packaged in the main file.
                    assertThat(zip.getEntries(Pattern.compile("^/res/.*"))).hasSize(5);
                }

            }

        }

        // this checks we didn't miss any expected output.
        assertTrue(expected.isEmpty());
    }

    @Test
    public void testAddingAnAbiPureSplit() throws Exception {
        // This test uses the deprecated NDK integration, which does not work properly on Windows.
        AssumeUtil.assumeNotWindows();

        AndroidProject model = assembleAndGetModel();

        // get the last modified time of the initial APKs so we can make sure incremental build
        // does not rebuild things unnecessarily.
        Map<String, Long> lastModifiedTimePerAbi = getApkModifiedTimePerAbi(getOutputs(model));

        TemporaryProjectModification.doTest(
                project,
                modifiedProject -> {
                    modifiedProject.replaceInFile(
                            "build.gradle",
                            "include 'x86', 'armeabi-v7a', 'mips'",
                            "include 'x86', 'armeabi-v7a', 'mips', 'armeabi'");
                    AndroidProject incrementalModel =
                            project.executeAndReturnModel("assembleDebug").getOnlyModel();

                    List<? extends OutputFile> outputs = getOutputs(incrementalModel);
                    for (OutputFile output : outputs) {
                        System.out.println("found " + output.getOutputFile().getAbsolutePath());
                    }

                    assertThat(outputs).hasSize(5);
                    boolean foundAddedAPK = false;
                    for (OutputFile output : outputs) {

                        String filter = ModelHelper.getFilter(output, OutputFile.ABI);

                        if (Objects.equals(filter, "armeabi")) {
                            // found our added abi, done.
                            foundAddedAPK = true;
                        } else {
                            // check that the APK was not rebuilt.
                            // uncomment once packageAbiRes is incremental.
                            //                    assertTrue("APK should not have been rebuilt in incremental mode : " + filter,
                            //                            lastModifiedTimePerAbi.get(filter).longValue()
                            //                                    == output.getOuputFile().lastModified())

                        }
                    }

                    if (!foundAddedAPK) {
                        Assert.fail("Could not find added ABI split : armeabi");
                    }
                });
    }

    @Test
    public void testDeletingAnAbiPureSplit() throws Exception {
        // This test uses the deprecated NDK integration, which does not work properly on Windows.
        AssumeUtil.assumeNotWindows();

        AndroidProject model = assembleAndGetModel();

        // record the build time of each APK to ensure we don't rebuild those in incremental mode.
        Map<String, Long> lastModifiedTimePerAbi = getApkModifiedTimePerAbi(getOutputs(model));

        TemporaryProjectModification.doTest(
                project,
                modifiedProject -> {
                    modifiedProject.replaceInFile(
                            "build.gradle",
                            "include 'x86', 'armeabi-v7a', 'mips'",
                            "include 'x86', 'armeabi-v7a'");
                    AndroidProject incrementalModel =
                            project.executeAndReturnModel("assembleDebug").getOnlyModel();

                    List<? extends OutputFile> outputs = getOutputs(incrementalModel);
                    assertThat(outputs).hasSize(3);
                    for (OutputFile output : outputs) {

                        String filter = ModelHelper.getFilter(output, OutputFile.ABI);
                        if (Objects.equals(filter, "mips")) {
                            Assert.fail("Found deleted ABI split : mips");
                        } else {
                            // check that the APK was not rebuilt.
                            //                    assertNotNull("Cannot find initial APK for ABI : " + filter);
                            // uncomment once packageAbiRes is incremental.
                            //                    assertTrue("APK should not have been rebuilt in incremental mode",
                            //                            lastModifiedTimePerAbi.get(filter).longValue()
                            //                                    == output.getOuputFile().lastModified())
                        }
                    }
                });
    }

    private List<? extends OutputFile> getOutputs(AndroidProject projectModel) {
        // Load the custom model for the project
        Collection<Variant> variants = projectModel.getVariants();
        assertEquals("Variant Count", 2, variants.size());

        // get the main artifact of the debug artifact
        Variant debugVariant = ModelHelper.getVariant(variants, BuilderConstants.DEBUG);
        AndroidArtifact debugMainArtifact = debugVariant.getMainArtifact();
        assertNotNull("Debug main info null-check", debugMainArtifact);

        // get the outputs.
        Collection<AndroidArtifactOutput> debugOutputs = debugMainArtifact.getOutputs();
        assertNotNull(debugOutputs);

        assertEquals(1, debugOutputs.size());
        AndroidArtifactOutput output = debugOutputs.iterator().next();

        // all splits have the same version.
        assertEquals(123, output.getVersionCode());

        return ImmutableList.copyOf(output.getOutputs());
    }

    @NonNull
    private static Map<String, Long> getApkModifiedTimePerAbi(
            Collection<? extends OutputFile> outputs) {
        ImmutableMap.Builder<String, Long> builder = ImmutableMap.builder();
        for (OutputFile output : outputs) {
            String key = output.getOutputType() + ModelHelper.getFilter(output, OutputFile.ABI);
            builder.put(key, output.getOutputFile().lastModified());
        }

        return builder.build();
    }

    private AndroidProject assembleAndGetModel() throws IOException, InterruptedException {
        project.executor().withLocalAndroidSdkHome().run("clean", "assembleDebug");
        return project.model().getSingle().getOnlyModel();
    }
}
