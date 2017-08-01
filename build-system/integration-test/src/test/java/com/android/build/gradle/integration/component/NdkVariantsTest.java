package com.android.build.gradle.integration.component;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** Assemble tests for ndkVariants. */
public class NdkVariantsTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .useExperimentalGradleVersion(true)
                    .fromTestProject("ndkVariants")
                    .create();

    @Test
    public void lint() throws IOException, InterruptedException {
        project.execute("lint");
    }

    @Test
    @Category(DeviceTests.class)
    public void connnectedAndroidTest() throws IOException, InterruptedException {
        project.executeConnectedCheck();
    }
}
