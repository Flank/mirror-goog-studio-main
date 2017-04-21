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

package com.android.builder.testing;

import com.android.builder.testing.api.DeviceConnector;
import com.google.common.base.CharMatcher;
import com.google.common.truth.Truth;
import org.junit.Test;
import org.mockito.Mockito;

public class OnDeviceOrchestratorTestRunnerTest {
    @Test
    public void amInstrumentCommand() throws Exception {
        TestData testData =
                new StubTestData(
                        "com.example.app", "android.support.test.runner.AndroidJUnitRunner");
        DeviceConnector deviceConnector = Mockito.mock(DeviceConnector.class);

        OnDeviceOrchestratorTestRunner.OnDeviceOrchestratorRemoteAndroidTestRunner odoRunner =
                new OnDeviceOrchestratorTestRunner.OnDeviceOrchestratorRemoteAndroidTestRunner(
                        testData, deviceConnector);

        odoRunner.addInstrumentationArg("foo", "bar");

        String normalizedCommand =
                CharMatcher.whitespace().collapseFrom(odoRunner.getAmInstrumentCommand(), ' ');

        Truth.assertThat(normalizedCommand)
                .isEqualTo(
                        "CLASSPATH=$(pm path com.google.android.apps.common.testing.services) "
                                + "app_process / android.support.test.services.shellexecutor.ShellMain "
                                + "am instrument -r -w "
                                + "-e targetInstrumentation com.example.app/android.support.test.runner.AndroidJUnitRunner "
                                + "-e foo bar "
                                + "android.support.test.orchestrator/android.support.test.orchestrator.OnDeviceOrchestrator");
    }
}
