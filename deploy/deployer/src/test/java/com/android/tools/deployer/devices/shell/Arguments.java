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
package com.android.tools.deployer.devices.shell;

/** Implementation based on how the platform parses arguments */
public class Arguments {
    private final String[] args;
    private int next;

    public Arguments(String[] args) {
        this.next = 0;
        this.args = args;
    }

    public String nextOption() {
        if (next < args.length && args[next].startsWith("-")) {
            next++;
            return args[next - 1];
        }
        return null;
    }

    public String nextArgument() {
        return next < args.length ? args[next++] : null;
    }
}
