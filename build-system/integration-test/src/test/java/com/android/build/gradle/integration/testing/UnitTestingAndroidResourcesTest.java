package com.android.build.gradle.integration.testing;

import static com.android.testutils.truth.MoreTruth.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.RunGradleTasks;
import com.android.build.gradle.integration.common.utils.ModelHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.aapt.AaptGeneration;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.JavaArtifact;
import com.android.builder.model.Variant;
import java.io.File;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import org.junit.Assert;
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
    public static Object[][] data() {
        return new Object[][] {
            {Plugin.APPLICATION, null, AaptGeneration.AAPT_V1},
            {Plugin.APPLICATION, null, AaptGeneration.AAPT_V2_JNI},
            {Plugin.LIBRARY, LibrarySetup.BYPASS_MERGE, AaptGeneration.AAPT_V1},
            {Plugin.LIBRARY, LibrarySetup.MERGE, AaptGeneration.AAPT_V1},
            {Plugin.LIBRARY, LibrarySetup.BYPASS_MERGE, AaptGeneration.AAPT_V2_JNI},
            {Plugin.LIBRARY, LibrarySetup.MERGE, AaptGeneration.AAPT_V2_JNI},
        };
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

        // Check that the model contains the generated file
        AndroidProject model = project.model().getSingle().getOnlyModel();
        Variant debug = ModelHelper.getVariant(model.getVariants(), "debug");
        JavaArtifact debugUnitTest =
                ModelHelper.getJavaArtifact(
                        debug.getExtraJavaArtifacts(), AndroidProject.ARTIFACT_UNIT_TEST);

        Path configFile = getConfigFile(debugUnitTest.getAdditionalClassesFolders());
        assertNotNull(configFile);
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(configFile)) {
            properties.load(reader);
        }
        properties.forEach((name, value) -> assertThat(Paths.get(value.toString())).exists());
    }

    @Nullable
    private static Path getConfigFile(@NonNull Iterable<File> directories) {
        for (File dir : directories) {
            Path candidateConfigFile =
                    dir.toPath()
                            .resolve("com")
                            .resolve("android")
                            .resolve("tools")
                            .resolve("test_config.properties");
            if (Files.isRegularFile(candidateConfigFile)) {
                return candidateConfigFile;
            }
        }
        return null;
    }
}
