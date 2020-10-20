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

package com.android.tools.bazel;

import java.util.ArrayList;
import java.util.List;

public class StringBuilderLogger extends BazelToolsLogger {
    private final List<String> output = new ArrayList<String>();

    @Override
    public void error(String message, Object... args) {
        super.error(message, args);
        output.add(String.format(message, args));
    }

    @Override
    public void warning(String message, Object... args) {
        super.warning(message, args);
        output.add(String.format(message, args));
    }

    @Override
    public void info(String message, Object... args) {
        super.info(message, args);
        output.add(String.format(message, args));
    }

    public List<String> getOutput() {
        return output;
    }
}
