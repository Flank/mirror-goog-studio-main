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

package com.android.tools.device.internal.adb.commands;

import com.android.annotations.Nullable;

public interface CommandResult {
    /** Returns whether adb server responded with OKAY. */
    boolean isOk();

    /** Returns the error string if any. Present only if {@link #isOk()} was false. */
    @Nullable
    String getError();

    CommandResult OKAY =
            new CommandResult() {
                @Override
                public boolean isOk() {
                    return true;
                }

                @Nullable
                @Override
                public String getError() {
                    return null;
                }
            };

    static CommandResult createError(@Nullable String error) {
        return new CommandResult() {
            @Override
            public boolean isOk() {
                return false;
            }

            @Nullable
            @Override
            public String getError() {
                return error;
            }
        };
    }
}
