/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tools.ir.common;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

import java.util.logging.Level;

public class Log {

    @Nullable
    public static Logging logging = null;

    public interface Logging {
        void log(@NonNull Level level, @NonNull String string);

        boolean isLoggable(@NonNull Level level);

        void log(@NonNull Level level, @NonNull String string, @Nullable Throwable throwable);
    }

}