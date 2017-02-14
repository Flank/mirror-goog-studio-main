package com.android.build.gradle.integration.testing;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.common.truth.Truth;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Checks that the test_config.properties object is generated correctly. */
@RunWith(Parameterized.class)
public class UnitTestingAndroidResourcesTest {
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

    @Test
    public void runUnitTests() throws Exception {
        project.execute("testDebugUnitTest");

        Files.write("CHANGE", project.file("src/main/assets/foo.txt"), StandardCharsets.UTF_8);
        GradleBuildResult result = project.executor().run("testDebugUnitTest");

        Truth.assertThat(result.getNotUpToDateTasks()).contains(":testDebugUnitTest");
    }
}
