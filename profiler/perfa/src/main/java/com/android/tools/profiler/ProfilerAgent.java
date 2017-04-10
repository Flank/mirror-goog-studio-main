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

package com.android.tools.profiler;

import android.util.Log;
import java.net.URL;

public class ProfilerAgent {
    public static void urlOpenConnection(URL url) {
        Log.v("ProfilerAgent", url.toString());
    }

    public static void urlOpenConnection(URL url, java.net.Proxy proxy) {
        Log.v("ProfilerAgent", "proxy");
    }
}
