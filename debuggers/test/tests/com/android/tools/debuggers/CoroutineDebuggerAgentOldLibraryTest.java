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

package tests.com.android.tools.debuggers;

import java.util.Collection;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import tests.com.android.tools.debuggers.infra.AgentTestBase;

/**
 * Test class used to test behaviour of apps using kotlinx-coroutines-core version lower than 1.6.0
 */
@RunWith(Parameterized.class)
public class CoroutineDebuggerAgentOldLibraryTest extends AgentTestBase {

    @Parameterized.Parameters
    public static Collection<String> artFlags() {
        return ALL_ART_FLAGS;
    }

    public CoroutineDebuggerAgentOldLibraryTest(String flag) {
        super(flag);
    }

    /** The agent is started but debug probes are not installed, because app is running old lib */
    @Test
    public void dumpCoroutinesTest() {
        startupAgent();
        android.loadDex(DEX_LOCATION);
        android.launchActivity(ACTIVITY_CLASS);

        android.triggerMethod(ACTIVITY_CLASS, "dumpCoroutinesNoAgent");
        Assert.assertTrue(
                android.waitForInput("Debug probes are not installed", RETURN_VALUE_TIMEOUT));
    }
}
