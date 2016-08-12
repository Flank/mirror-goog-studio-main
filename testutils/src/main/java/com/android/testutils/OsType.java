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

package com.android.testutils;

import java.util.Locale;

/**
 * Enumeration of supported OSes, plus some helpful utility methods. Use the
 * {@link #getFolderName()} to get the folder name used on disk which for the OS.
 */
public enum OsType {
    UNKNOWN,

    /**
     * The OS used by Mac.
     */
    DARWIN,
    LINUX,
    WINDOWS;

    /**
     * Get the display-friendly name of the current system's OS.
     */
    public static String getOsName() {
        return System.getProperty("os.name");
    }

    /**
     * Get the matching {@link OsType} for the current system's OS (or {@link #UNKNOWN} if no
     * match).
     */
    public static OsType getHostOs() {
        String os = getOsName();
        if (os.startsWith("Mac")) {
            return DARWIN;
        } else if (os.startsWith("Linux")) {
            return LINUX;
        } else if (os.startsWith("Windows")) {
            return WINDOWS;
        }
        return UNKNOWN;
    }

    /**
     * The name of this OS as it appears on disk. This will always be lower-cased, for consistency.
     */
    public String getFolderName() {
        return name().toLowerCase(Locale.ENGLISH);
    }
}
