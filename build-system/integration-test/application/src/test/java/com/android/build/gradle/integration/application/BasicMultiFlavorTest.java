package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import org.junit.Rule;
import org.junit.Test;

/** Assemble tests for basicMultiFlavors */
public class BasicMultiFlavorTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("basicMultiFlavors").create();

    @Test
    public void checkResourcesResolution() {
        project.execute("assembleFreeBetaDebug");
        assertThat(project.getApk(GradleTestProject.ApkType.DEBUG, "free", "beta"))
                .containsResource("drawable/free.png");
    }
}
