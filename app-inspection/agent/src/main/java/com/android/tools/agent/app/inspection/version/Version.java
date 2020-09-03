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

package com.android.tools.agent.app.inspection.version;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class representing a version. Offers an easy way to compare Android version strings.
 *
 * <p>Copied from androidx.build.Version.
 */
class Version implements Comparable<Version> {

    private static class CheckedMatcher {

        private static Matcher matcher(String versionString) {
            Matcher matcher = VERSION_REGEX.matcher(versionString);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Can not parse version: " + versionString);
            }
            return matcher;
        }
    }

    private final int major;

    private final int minor;

    private final int patch;

    private final String extra;

    public Version(int major, int minor, int patch, String extra) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.extra = extra;
    }

    public Version(String versionString) {
        this(
                Integer.parseInt(CheckedMatcher.matcher(versionString).group(1)),
                Integer.parseInt(CheckedMatcher.matcher(versionString).group(2)),
                Integer.parseInt(CheckedMatcher.matcher(versionString).group(3)),
                CheckedMatcher.matcher(versionString).groupCount() == 4
                        ? CheckedMatcher.matcher(versionString).group(4)
                        : null);
    }

    public boolean isPatch() {
        return patch != 0;
    }

    public boolean isSnapshot() {
        return "-SNAPSHOT".equals(extra);
    }

    private boolean extraStartsWith(String pattern) {
        if (extra == null) return false;
        return extra.toLowerCase().startsWith(pattern);
    }

    public boolean isAlpha() {
        return extraStartsWith("-alpha");
    }

    public boolean isBeta() {
        return extraStartsWith("-beta");
    }

    public boolean isDev() {
        return extraStartsWith("-dev");
    }

    public boolean isRC() {
        return extraStartsWith("-rc");
    }

    public boolean isStable() {
        return extra == null;
    }

    @Override
    public int compareTo(Version o) {
        if (major != o.major) {
            return major - o.major;
        } else if (minor != o.minor) {
            return minor - o.minor;
        } else if (patch != o.patch) {
            return patch - o.patch;
        } else if (extra != o.extra) {
            if (extra == null || o.extra == null) {
                // The version containing the "extra" should precede the version that does not sport
                // a suffix. For example: 2.0.0-rc comes before 2.0.0
                return Boolean.compare(extra == null, o.extra == null);
            } else {
                // Compare lexicographically because that's how gradle does it.
                return extra.compareTo(o.extra);
            }
        }
        return 0;
    }

    private static Pattern VERSION_REGEX = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(-.+)?$");

    public static Version parseOrNull(String versionString) {
        Matcher matcher = VERSION_REGEX.matcher(versionString);
        if (matcher.matches()) {
            return new Version(versionString);
        } else {
            return null;
        }
    }
}
