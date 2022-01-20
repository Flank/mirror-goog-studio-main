/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.deploy.interpreter;

public class Config {

    private boolean debugEnabled = false;

    private Config() {}

    private static Config instance = new Config();

    public static Config getInstance() {
        return instance;
    }

    public boolean debugEnabled() {
        return debugEnabled;
    }

    public void setDebugMode(boolean enabled) {
        this.debugEnabled = enabled;
    }
}
