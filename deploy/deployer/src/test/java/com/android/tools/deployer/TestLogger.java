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
import com.android.annotations.Nullable;
import com.android.utils.ILogger;
import java.util.ArrayList;
import java.util.List;

public class TestLogger implements ILogger {
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    List<String> infos = new ArrayList<>();
    List<String> verboses = new ArrayList<>();

    @Override
    public void error(@Nullable Throwable t, @Nullable String msgFormat, Object... args) {
        errors.add(String.format(msgFormat, args));
    }

    @Override
    public void warning(@NonNull String msgFormat, Object... args) {
        warnings.add(String.format(msgFormat, args));
    }

    @Override
    public void info(@NonNull String msgFormat, Object... args) {
        infos.add(String.format(msgFormat, args));
    }

    @Override
    public void verbose(@NonNull String msgFormat, Object... args) {
        verboses.add(String.format(msgFormat, args));
    }
}
