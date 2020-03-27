/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.ddmlib.IDevice;
import com.google.common.annotations.VisibleForTesting;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ApkVerifierTracker {
    @VisibleForTesting static final String SKIP_VERIFICATION_OPTION = "--skip-verification";
    @VisibleForTesting static final long TIME_BETWEEN_VERIFICATIONS_MS = TimeUnit.HOURS.toMillis(1);

    // (DeviceSerial + ":" + PackageName) -> LastVerifyTimeMs
    private static final Map<String, Long> lastVerifiedTimeMap = new HashMap<>();

    /**
     * Thread-safe method to get the option string to skip APK verification, if available.
     *
     * @param device target for skipping
     * @param packageName package name of APK
     * @return the option string that should be used for skipping if available, or null otherwise
     */
    public static synchronized String getSkipVerificationInstallationFlag(
            @NonNull IDevice device, @NonNull String packageName) {
        return getSkipVerificationInstallationFlag(device, packageName, System.currentTimeMillis());
    }

    /**
     * Internal implementation of the public method of the same name.
     *
     * @param device target for skipping
     * @param packageName package name of APK
     * @param currentTimeMs the input time in ms
     * @return the option string that should be used for skipping if available, or null otherwise
     */
    @VisibleForTesting
    static String getSkipVerificationInstallationFlag(
            @NonNull IDevice device, @NonNull String packageName, long currentTimeMs) {
        // In R, ADB reports both real package name (instead of process name) and also allows
        // the user to skip app verification on install. We use the real package name flag
        // here to avoid creating another flag for a feature that's on the same Android version.
        if (!device.supportsFeature(IDevice.Feature.SKIP_VERIFICATION)) {
            return null;
        }

        String key = getVerifiedTimeMapKey(device, packageName);
        if (!lastVerifiedTimeMap.containsKey(key)) {
            lastVerifiedTimeMap.put(key, currentTimeMs - TIME_BETWEEN_VERIFICATIONS_MS);
        }
        long lastVerifiedTime = lastVerifiedTimeMap.get(key);
        if (currentTimeMs - lastVerifiedTime < TIME_BETWEEN_VERIFICATIONS_MS) {
            return SKIP_VERIFICATION_OPTION;
        } else {
            // Update the time to "restart the timer".
            lastVerifiedTimeMap.put(key, currentTimeMs);
            return null;
        }
    }

    /** Test-only function to clear the last verified time. */
    @VisibleForTesting
    static void clear() {
        lastVerifiedTimeMap.clear();
    }

    @NonNull
    private static String getVerifiedTimeMapKey(
            @NonNull IDevice device, @NonNull String packageName) {
        return String.format("%s:%s", device.getSerialNumber(), packageName);
    }
}
