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

package com.android.flags;

import com.android.annotations.NonNull;

/** A data class which represents a parent for a collection of {@link Flag}s. */
public final class FlagGroup {
    private final Flags flags;
    private final String name;
    private final String displayName;

    public FlagGroup(@NonNull Flags flags, @NonNull String name, @NonNull String displayName) {
        Flag.verifyFlagIdFormat(name);
        Flag.verifyDispayTextFormat(displayName);
        this.flags = flags;
        this.name = name;
        this.displayName = displayName;
    }

    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    @NonNull
    Flags getFlags() {
        return flags;
    }

    @NonNull
    String getName() {
        return name;
    }
}
