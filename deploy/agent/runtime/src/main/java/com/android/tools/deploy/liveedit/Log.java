/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.deploy.liveedit;

import com.android.tools.deploy.interpreter.Config;

public final class Log {
    public interface Logger {
        void v(String tag, String message);
    }

    private static Logger logger = null;

    public static void setLogger(Logger logger) {
        Log.logger = logger;
    }

    public static void v(String tag, String message) {
        if (logger != null && Config.getInstance().debugEnabled()) {
            logger.v(tag, message);
        }
    }
}
