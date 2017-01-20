package com.android.build.gradle.integration.application;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import org.junit.Rule;
import org.junit.Test;

/** Check that we recognize dependency cycles. */
public class DependencyCyclesTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("applibtest").create();

    @Test
    public void cycle() throws Exception {
        TestFileUtils.appendToFile(
                project.getSubproject(":lib").getBuildFile(),
                "dependencies { compile project(':app') }");

        // Make sure we don't crash with a stack overflow exception.
        GradleBuildResult result = project.executor().expectFailure().run("clean", ":app:assemble");

        if (GradleTestProject.IMPROVED_DEPENDENCY_RESOLUTION) {
            // Gradle detects this for us. Unfortunately there's no mention of ":lib" in the error message.
            assertThat(result.getFailureMessage())
                    .contains("Circular dependency between the following tasks:");
        } else {
            // Make sure our code doesn't
            assertThat(result.getFailureMessage()).contains(":app -> :lib -> :app");
        }
    }
}
