package com.android.build.gradle.integration.testing;

import static com.android.testutils.truth.MoreTruth.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
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

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("unitTestingAndroidResources").create();

    @Parameterized.Parameters
    public static Collection<Object> data() {
        return ImmutableList.of(true, false);
    }

    @Parameterized.Parameter public boolean testLibrary;

    @Before
    public void changePlugin() throws Exception {
        if (testLibrary) {
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
        project.execute("testDebugUnitTest");

        Files.write(project.file("src/main/assets/foo.txt").toPath(), "CHANGE".getBytes());
        GradleBuildResult result = project.executor().run("testDebugUnitTest");

        assertThat(result.getNotUpToDateTasks()).contains(":testDebugUnitTest");

        // Sanity check: make sure we're actually executing Robolectric code.
        File xmlResults =
                project.file(
                        "build/test-results/testDebugUnitTest/"
                                + "TEST-com.android.tests.WelcomeActivityTest.xml");
        assertThat(xmlResults).isFile();
    }
}
