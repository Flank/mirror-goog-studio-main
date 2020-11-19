package com.android.build.gradle.integration.connected.application;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.connected.utils.EmulatorUtils;
import java.io.IOException;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

/** Connected tests for dependencies. */
public class DependenciesConnectedTest {

    @ClassRule public static final ExternalResource EMULATOR = EmulatorUtils.getEmulator();

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("dependencies").create();

    @Before
    public void setUp() throws IOException {
        // fail fast if no response
        project.addAdbTimeout();
        // run the uninstall tasks in order to (1) make sure nothing is installed at the beginning
        // of each test and (2) check the adb connection before taking the time to build anything.
        project.execute("uninstallAll");
    }

    @Test
    public void connectedAndroidTest() throws IOException, InterruptedException {
        project.executor().run("connectedAndroidTest");
    }
}
