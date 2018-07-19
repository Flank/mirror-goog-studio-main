/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.deployer;

import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import java.util.ArrayList;
import java.util.HashMap;

class InstallerNotifier implements Deployer.InstallerCallBack {
    @Override
    public void onInstallationFinished(boolean status) {}
}

public class DeployerRunner {

    private static final ILogger LOGGER = new StdLogger(StdLogger.Level.VERBOSE);

    // Run it from bazel with the following command:
    // bazel run :deployer.runner org.wikipedia.alpha PATH_TO_APK1 PATH_TO_APK2
    public static void main(String[] args) {
        DeployerRunner runner = new DeployerRunner();
        runner.run(args);
    }

    public DeployerRunner() {}

    public void run(String[] args) {
        // Check that we have the parameters we need to run.
        if (args.length < 2) {
            printUsage();
            return;
        }

        // Get package name.
        String packageName = args[0];

        // Get all apks with base and splits.
        ArrayList<String> apks = new ArrayList();
        for (int i = 1; i < args.length; i++) {
            apks.add(args[i]);
        }

        // Run
        Deployer deployer =
                new Deployer(packageName, apks, new InstallerNotifier(), new AdbCmdline());

        HashMap<String, HashMap<String, Apk.ApkEntryStatus>> diffs;

        try {
            diffs = deployer.run();
        } catch (DeployerException e) {
            LOGGER.error(e, null);
            return;
        }

        // Output apks differences found.
        for (String apk : apks) {
            HashMap<String, Apk.ApkEntryStatus> statuses = diffs.get(apk);
            for (String key : statuses.keySet()) {
                Apk.ApkEntryStatus status = statuses.get(key);
                switch (status) {
                    case CREATED:
                        LOGGER.info("%s has been CREATED.", key);
                        break;
                    case DELETED:
                        LOGGER.info("%s has been DELETED.", key);
                        break;
                    case MODIFIED:
                        LOGGER.info("%s has been MODIFIED.", key);
                        break;
                }
            }
        }
    }

    private static void printUsage() {
        LOGGER.info("Usage: DeployerRunner packageName [packageBase,packageSplit1,...]");
    }
}
