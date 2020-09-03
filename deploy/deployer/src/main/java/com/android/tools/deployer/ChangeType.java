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

import com.android.tools.deployer.model.FileDiff;

public enum ChangeType {
    UNKNOWN,
    DEX,
    RESOURCE,
    NATIVE_LIBRARY,
    MANIFEST;

    public static ChangeType getType(FileDiff diff) {
        final String name = diff.getName();
        if (name.endsWith(".dex")) {
            return DEX;
        }

        if (name.startsWith("res/")
                || name.startsWith("assets/")
                || name.equals("resources.arsc")) {
            return RESOURCE;
        }

        if (name.startsWith("lib/") && name.endsWith(".so")) {
            return NATIVE_LIBRARY;
        }

        if (name.equals("AndroidManifest.xml")) {
            return MANIFEST;
        }

        return UNKNOWN;
    }
}
