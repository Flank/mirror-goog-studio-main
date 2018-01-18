/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.activity;

import android.app.Activity;

/**
 * The base activity class that provides a common path for all test activities to relay app-specific
 * info to the perf driver that can not be retrieved otherwise (without first attaching an agent
 * anyway). Currently this is used for sending the app's pid so the perf driver knows of its
 * existence, but it can be enhanced to send more data back if needed.
 */
public abstract class PerfdTestActivity extends Activity {
    public PerfdTestActivity(String activityName) {
        super(activityName);

        // Load our jni helper library which is used for sending info to the perf driver.
        System.loadLibrary("jni");
    }
}
