package com.android.build.gradle.integration.connected.application;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.connected.utils.EmulatorUtils;
import com.android.tools.bazel.avd.Emulator;
import java.io.IOException;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/** Connected tests for dependencies. */
public class DependenciesConnectedTest {

    @ClassRule public static final Emulator EMULATOR = EmulatorUtils.getEmulator();

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("dependencies").create();

    @Before
    public void setUp() throws IOException {
        // fail fast if no response
        project.addAdbTimeOutInMs();
    }

    @Test
    public void connectedAndroidTest() throws IOException, InterruptedException {
        project.executor().run("connectedAndroidTest");
    }
}
