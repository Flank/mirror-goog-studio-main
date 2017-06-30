package com.android.build.gradle.integration.testing;

import static com.android.testutils.truth.MoreTruth.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.RunGradleTasks;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.build.gradle.options.BooleanOption;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Assert;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Checks that the test_config.properties object is generated correctly. */
@RunWith(Parameterized.class)
public class UnitTestingAndroidResourcesTest {

    public static final String PLATFORM_JAR_NAME = "android-all-7.0.0_r1-robolectric-0.jar";

    enum Plugin {
        LIBRARY,
        APPLICATION
    }

    enum LibrarySetup {
        BYPASS_MERGE,
        MERGE
    }

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("unitTestingAndroidResources").create();

    @Parameterized.Parameters(name = "plugin={0}  librarySetup={1}  aaptGeneration={2}")
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[][] {
                    {Plugin.APPLICATION, null, AaptGeneration.AAPT_V1},
                    {Plugin.APPLICATION, null, AaptGeneration.AAPT_V2_JNI},
                    {Plugin.LIBRARY, LibrarySetup.BYPASS_MERGE, AaptGeneration.AAPT_V1},
                    {Plugin.LIBRARY, LibrarySetup.MERGE, AaptGeneration.AAPT_V1},
                    {Plugin.LIBRARY, LibrarySetup.BYPASS_MERGE, AaptGeneration.AAPT_V2_JNI},
                    {Plugin.LIBRARY, LibrarySetup.MERGE, AaptGeneration.AAPT_V2_JNI},
                });
    }

    @Parameterized.Parameter public Plugin plugin;

    @Parameterized.Parameter(value = 1)
    public LibrarySetup librarySetup;

    @Parameterized.Parameter(value = 2)
    public AaptGeneration aaptGeneration;

    @Before
    public void changePlugin() throws Exception {
        if (plugin == Plugin.LIBRARY) {
            TestFileUtils.searchAndReplace(
                    project.getBuildFile(), "com.android.application", "com.android.library");
        }
    }

    /**
     * Copies the Robolectric platform jar into the project directory, so we can run a fully offline
     * build.
     */
    @Before
    public void copyPlatformJar() throws Exception {
        boolean found = false;
        for (Path path : GradleTestProject.getLocalRepositories()) {
            Path platformJar =
                    path.resolve(
                            "org/robolectric/android-all/7.0.0_r1-robolectric-0/"
                                    + PLATFORM_JAR_NAME);
            if (Files.exists(platformJar)) {
                found = true;
                Path robolectricLibs = project.file("robolectric-libs").toPath();
                Files.createDirectory(robolectricLibs);
                Files.copy(platformJar, robolectricLibs.resolve(PLATFORM_JAR_NAME));
                break;
            }
        }

        if (!found) {
            Assert.fail("Failed to find Robolectric platform jar in prebuilts.");
        }
    }

    @Test
    public void runUnitTests() throws Exception {

        if (plugin == Plugin.APPLICATION && aaptGeneration == AaptGeneration.AAPT_V2_JNI) {
            throw new AssumptionViolatedException(
                    "Resources in Unit tests currently broken with AAPT2 b/63155231");
        }

        RunGradleTasks runGradleTasks = project.executor().with(aaptGeneration);

        if (librarySetup != null) {
            runGradleTasks.with(
                    BooleanOption.DISABLE_RES_MERGE_IN_LIBRARY,
                    librarySetup == LibrarySetup.BYPASS_MERGE);
        }

        runGradleTasks.run("testDebugUnitTest");

        Files.write(project.file("src/main/assets/foo.txt").toPath(), "CHANGE".getBytes());
        GradleBuildResult result = runGradleTasks.run("testDebugUnitTest");

        assertThat(result.getNotUpToDateTasks()).contains(":testDebugUnitTest");

        // Sanity check: make sure we're actually executing Robolectric code.
        File xmlResults =
                project.file(
                        "build/test-results/testDebugUnitTest/"
                                + "TEST-com.android.tests.WelcomeActivityTest.xml");
        assertThat(xmlResults).isFile();
    }
}
