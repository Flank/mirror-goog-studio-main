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
package com.android.ddmlib;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IotInstallChecker {
    private static final String TAG = IotInstallChecker.class.getSimpleName();
    private static final String DUMP_PACKAGES_CMD = "dumpsys package -f r";

    public Set<String> getInstalledIotLauncherApps(@NonNull final IDevice device) {
        return getInstalledIotLauncherApps(device, 1, TimeUnit.MINUTES);
    }

    public Set<String> getInstalledIotLauncherApps(
            @NonNull final IDevice device, long timeout, @NonNull TimeUnit unit) {
        if (!device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)) {
            return Collections.emptySet();
        }
        IntentListReceiver listreceiver = new IntentListReceiver();
        try {
            device.executeShellCommand(DUMP_PACKAGES_CMD, listreceiver, timeout, unit);
        } catch (Exception e) {
            Log.e(TAG, e);
        }
        return listreceiver.getPackagesWithIotLauncher();
    }

    @VisibleForTesting
    static class IntentListReceiver extends MultiLineReceiver {
        // android.intent.action.MAIN:
        private static final Pattern ParagraphRegex = Pattern.compile("^([\\w\\.]+):$");
        private static final String MainPart = "android.intent.action.MAIN";
        // 2a41f7f com.android.provision/.DefaultActivity filter 9341c43
        private static final Pattern PackageRegex =
                Pattern.compile("^\\w+ ([\\w\\.]+)/\\.\\w+ filter \\w+$");
        private static final String IotLauncher = "android.intent.category.IOT_LAUNCHER";
        private final Set<String> packagesWithIotLauncher = new HashSet<>();
        private String currentPackage;
        private boolean mainPart = false;
        private boolean isCancelled = false;

        @Override
        public void processNewLines(String[] lines) {
            for (String l : lines) {
                processNewLine(l);
            }
        }

        private void processNewLine(String line) {
            // Detect which paragraph we are in.
            Matcher matcher = ParagraphRegex.matcher(line);
            if (matcher.matches()) {
                if (matcher.group(1).equals(MainPart)) {
                    mainPart = true;
                } else {
                    if (mainPart) {
                        isCancelled = true;
                    }
                }
                return;
            }

            // If we are in the section that lists packages with a filter on action android.intent.action.MAIN,
            // look for packages with a filter on category android.intent.category.IOT_LAUNCHER.
            if (mainPart) {
                matcher = PackageRegex.matcher(line);
                if (matcher.matches()) {
                    currentPackage = matcher.group(1);
                    return;
                }

                // We are currently in the section describing a package.
                if (!packagesWithIotLauncher.contains(currentPackage)
                        && line.contains(IotLauncher)) {
                    packagesWithIotLauncher.add(currentPackage);
                }
            }
        }

        @Override
        public boolean isCancelled() {
            return isCancelled;
        }

        public Set<String> getPackagesWithIotLauncher() {
            return packagesWithIotLauncher;
        }
    }
}
