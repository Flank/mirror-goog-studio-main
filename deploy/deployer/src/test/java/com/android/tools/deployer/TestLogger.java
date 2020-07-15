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
    List<String> log = new ArrayList<>();

    @Override
    public void error(@Nullable Throwable t, @Nullable String msgFormat, Object... args) {
        log.add("E " + String.format(msgFormat, args));
        System.out.println(log.get(log.size() - 1));
    }

    @Override
    public void warning(@NonNull String msgFormat, Object... args) {
        log.add("W " + String.format(msgFormat, args));
        System.out.println(log.get(log.size() - 1));
    }

    @Override
    public void info(@NonNull String msgFormat, Object... args) {
        log.add("I " + String.format(msgFormat, args));
        System.out.println(log.get(log.size() - 1));
    }

    @Override
    public void verbose(@NonNull String msgFormat, Object... args) {
        log.add("V " + String.format(msgFormat, args));
        System.out.println(log.get(log.size() - 1));
    }
}
