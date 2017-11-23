/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.integration.application;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.Adb;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.utils.DexInProcessHelper;
import com.google.common.collect.Lists;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(FilterableParameterized.class)
public class MultiDexConnectedTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("multiDex").withHeap("2048M").create();

    @Rule public Adb adb = new Adb();

    @Parameterized.Parameters(name = "dexInProcess = {0}")
    public static List<Boolean> data() {
        return Lists.newArrayList(true, false);
    }

    @Parameterized.Parameter public boolean dexInProcess;

    @Before
    public void disableDexInProcess() throws Exception {
        if (!dexInProcess) {
            DexInProcessHelper.disableDexInProcess(project.getBuildFile());
        }
    }

    @Test
    @Category(DeviceTests.class)
    public void connectedCheck() throws Exception {
        project.execute(
                "assembleIcsDebug",
                "assembleIcsDebugAndroidTest",
                "assembleLollipopDebug",
                "assembleLollipopDebugAndroidTest");
        adb.exclusiveAccess();
        project.execute("connectedCheck");
    }
}
