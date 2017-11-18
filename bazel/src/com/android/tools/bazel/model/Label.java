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

package com.android.tools.bazel.model;

public class Label {
    public final String pkg;
    public final String target;

    public Label(String label) {
        if (label.startsWith("@")) {
            int endOfRepo = label.indexOf("//");
            if (endOfRepo < 0) {
                throw new RuntimeException("invalid label: " + label);
            }
            label = label.substring(endOfRepo);
        }
        if (!label.startsWith("//")) {
            throw new RuntimeException("invalid label: " + label);
        }
        // Find the package/suffix separation:
        int colonIndex = label.indexOf(':');
        int splitAt = colonIndex >= 0 ? colonIndex : label.length();
        pkg = label.substring("//".length(), splitAt);
        String suffix = label.substring(splitAt);
        // ('suffix' is empty, or starts with a colon.)

        // "If packagename and version are elided, the colon is not necessary."
        target = suffix.isEmpty()
                // Target name is last package segment: (works in slash-free case too.)
                ? pkg.substring(pkg.lastIndexOf('/') + 1)
                // Target name is what's after colon:
                : suffix.substring(1);
    }
}
