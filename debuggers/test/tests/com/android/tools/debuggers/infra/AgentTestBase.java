/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tests.com.android.tools.debuggers.infra;

import com.android.tools.fakeandroid.FakeAndroidDriver;
import com.android.tools.fakeandroid.ProcessRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import org.junit.After;
import org.junit.Before;
import org.junit.rules.TemporaryFolder;

/**
 * A unity class for setting up a minimal Activity running inside FakeAndroid
 *
 * <p>Since FakeAndroid relies on a HOST build of ART, this class will only function in Linux.
 */
public class AgentTestBase {

    protected static final String ACTIVITY_CLASS = "data.app.TestActivityKt";

    // Location of the initial test-app that has the ACTIVITY_CLASS
    protected static final String DEX_LOCATION = ProcessRunner.getProcessPath("app.dex.location");

    protected static final String AGENT_LOCATION =
            ProcessRunner.getProcessPath("swap.agent.location");

    protected static final String PACKAGE = "package.name";

    protected static final String LOCAL_HOST = "127.0.0.1";

    protected static final int RETURN_VALUE_TIMEOUT = 1000;

    protected static final Collection<String> ALL_ART_FLAGS =
            Arrays.asList(null, "-Xopaque-jni-ids:true");

    protected FakeAndroidDriver android;

    protected TemporaryFolder dexLocation;

    protected Path dataDir;

    protected final String artFlag;

    public AgentTestBase(String flag) {
        artFlag = flag;
    }

    @Before
    public void setUp() throws Exception {
        dexLocation = new TemporaryFolder();
        dexLocation.create();

        Path root = Files.createTempDirectory("root_dir");
        String[] env = new String[] {"FAKE_DEVICE_ROOT=" + root.toString()};
        dataDir = root.resolve("/data/data/" + PACKAGE);
        android = new FakeAndroidDriver(LOCAL_HOST, -1, artFlag, env);
        android.start();
    }

    @After
    public void tearDown() {
        android.stop();
    }

    protected void startupAgent() {
        // Ideally, we modify FakeAndroidDriver to do this, but this suffices to exercise the code
        // path for now.
        android.attachAgent(AGENT_LOCATION + "=" + dataDir.toString());
    }
}
