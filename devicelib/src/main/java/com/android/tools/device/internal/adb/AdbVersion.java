/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.device.internal.adb;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.google.common.collect.ComparisonChain;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AdbVersion implements Comparable<AdbVersion> {
    static final AdbVersion UNKNOWN = new AdbVersion(-1, -1, -1);

    /**
     * Matches the version string in the output of "adb version", e.g. "Android Debug Bridge version
     * 1.0.36"
     */
    private static final Pattern ADB_VERSION_PATTERN =
            Pattern.compile(".*(\\d+)\\.(\\d+)\\.(\\d+).*");

    public final int major;
    public final int minor;
    public final int micro;

    private AdbVersion(int major, int minor, int micro) {
        this.major = major;
        this.minor = minor;
        this.micro = micro;
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "%1$d.%2$d.%3$d", major, minor, micro);
    }

    @Override
    public int compareTo(@NonNull AdbVersion o) {
        return ComparisonChain.start()
                .compare(major, o.major)
                .compare(minor, o.minor)
                .compare(micro, o.micro)
                .result();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AdbVersion version = (AdbVersion) o;
        return major == version.major && minor == version.minor && micro == version.micro;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, micro);
    }

    @NonNull
    @VisibleForTesting
    static AdbVersion parseFrom(@NonNull String input) {
        Matcher matcher = ADB_VERSION_PATTERN.matcher(input);
        if (matcher.matches()) {
            int major = Integer.parseInt(matcher.group(1));
            int minor = Integer.parseInt(matcher.group(2));
            int micro = Integer.parseInt(matcher.group(3));
            return new AdbVersion(major, minor, micro);
        } else {
            return UNKNOWN;
        }
    }

    /** Executes 'adb version' and parses and returns the version of given adb. */
    @NonNull
    public static AdbVersion get(@NonNull Path adb) throws IOException {
        Process p = new ProcessBuilder(adb.toString(), "version").start();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                AdbVersion version = parseFrom(line);
                if (version != UNKNOWN) {
                    return version;
                }
            }
        }

        return UNKNOWN;
    }
}
