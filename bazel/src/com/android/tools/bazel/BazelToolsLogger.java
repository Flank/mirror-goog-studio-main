/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.bazel;

public class BazelToolsLogger {
    private int errorCount = 0;
    private int warningCount = 0;

    public void error(String message, Object... args) {
        errorCount++;
        System.err.println(String.format(message, args));
    }

    public void warning(String message, Object... args) {
        warningCount++;
        System.err.println(String.format(message, args));
    }

    public void info(String message, Object... args) {
        System.err.println(String.format(message, args));
    }

    public int getErrorCount() {
        return errorCount;
    }

    public int getWarningCount() {
        return warningCount;
    }

    public int getTotalIssuesCount() {
        return getErrorCount() + getWarningCount();
    }
}
