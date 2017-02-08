package com.android.build.gradle.integration.testing;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import org.junit.Rule;
import org.junit.Test;

/** Checks that the test_config.properties object is generated correctly. */
public class UnitTestingAndroidResourcesTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("unitTestingAndroidResources").create();

    @Test
    public void appProject() throws Exception {
        project.execute("test");
    }

    @Test
    public void libProject() throws Exception {
        TestFileUtils.searchAndReplace(
                project.getBuildFile(), "com.android.application", "com.android.library");

        project.execute("test");
    }
}
