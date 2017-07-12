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
package com.android.ddmlib.testrunner;

import com.android.annotations.NonNull;
import com.android.ddmlib.IShellEnabledDevice;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import java.util.List;

/** Runs an instrumented Android test using the adb command and AndroidTestOrchestrator. */
public class OnDeviceOrchestratorRemoteAndroidTestRunner extends RemoteAndroidTestRunner {
    public OnDeviceOrchestratorRemoteAndroidTestRunner(
            @NonNull String applicationId,
            String instrumentationRunner,
            IShellEnabledDevice device) {
        super(applicationId, instrumentationRunner, device);
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
                "android.support.test.orchestrator/android.support.test.orchestrator.AndroidTestOrchestrator");

        return Joiner.on(' ').join(adbArgs);
    }
}
