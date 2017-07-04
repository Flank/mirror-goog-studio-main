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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.builder.testing.api.DeviceConnector;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ide.common.process.ProcessExecutor;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.List;

/** Implementation of {@link TestRunner} that invokes tests through Odo. */
public class OnDeviceOrchestratorTestRunner extends SimpleTestRunner {
    public OnDeviceOrchestratorTestRunner(
            @Nullable File splitSelectExec, @NonNull ProcessExecutor processExecutor) {
        super(splitSelectExec, processExecutor);
    }

    @NonNull
    @Override
    protected RemoteAndroidTestRunner createRemoteAndroidTestRunner(
            @NonNull TestData testData, DeviceConnector device) {
        return new OnDeviceOrchestratorRemoteAndroidTestRunner(testData, device);
    }

    @VisibleForTesting
    static class OnDeviceOrchestratorRemoteAndroidTestRunner extends RemoteAndroidTestRunner {
        public OnDeviceOrchestratorRemoteAndroidTestRunner(
                TestData testData, DeviceConnector device) {
            super(testData.getApplicationId(), testData.getInstrumentationRunner(), device);
        }

        @NonNull
        @Override
        public String getAmInstrumentCommand() {
            List<String> adbArgs = Lists.newArrayList();

            adbArgs.add("CLASSPATH=$(pm path android.support.test.services)");
            adbArgs.add("app_process / android.support.test.services.shellexecutor.ShellMain");

            adbArgs.add("am");
            adbArgs.add("instrument");
            adbArgs.add("-r");
            adbArgs.add("-w");

            adbArgs.add("-e");
            adbArgs.add("targetInstrumentation");
            adbArgs.add(getRunnerPath());

            adbArgs.add(getRunOptions());
            adbArgs.add(getArgsCommand());

            adbArgs.add(
                    "android.support.test.orchestrator/android.support.test.orchestrator.OnDeviceOrchestrator");

            return Joiner.on(' ').join(adbArgs);
        }
    }
}
