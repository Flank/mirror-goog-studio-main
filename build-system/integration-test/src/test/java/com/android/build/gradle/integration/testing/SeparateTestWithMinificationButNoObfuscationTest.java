package com.android.build.gradle.integration.testing;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Test for a separate test module that has minification turned on but no obfuscation (no
 * mapping.txt file produced)
 */
public class SeparateTestWithMinificationButNoObfuscationTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("separateTestWithMinificationButNoObfuscation")
                    .create();

    @Test
    public void testBuilding() throws IOException, InterruptedException {
        // just building fine is enough to test the regression.
        project.execute("clean", "assemble");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedCheck() throws IOException, InterruptedException {
        project.execute(":test:deviceAndroidTest");
    }
}
