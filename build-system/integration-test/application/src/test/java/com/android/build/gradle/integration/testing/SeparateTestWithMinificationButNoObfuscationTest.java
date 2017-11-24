package com.android.build.gradle.integration.testing;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test for a separate test module that has minification turned on but no obfuscation (no
 * mapping.txt file produced)
 */
public class SeparateTestWithMinificationButNoObfuscationTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("separateTestWithMinificationButNoObfuscation")
                    .create();

    @Test
    public void testBuilding() throws IOException, InterruptedException {
        // just building fine is enough to test the regression.
        project.execute("clean", "assemble");
    }
}
