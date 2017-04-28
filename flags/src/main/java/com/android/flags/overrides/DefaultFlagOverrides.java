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

package com.android.flags.overrides;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.flags.Flag;
import com.android.flags.FlagOverrides;
import java.util.HashMap;
import java.util.Map;

/** Default, map-backed implementation for a collection of flag overrides. */
public final class DefaultFlagOverrides implements FlagOverrides {
    private final Map<Flag<?>, String> overrides = new HashMap<>();

    @Override
    public void clear() {
        overrides.clear();
    }

    @Override
    public void put(@NonNull Flag<?> flag, @NonNull String value) {
        overrides.put(flag, value);
    }

    @Override
    public void remove(@NonNull Flag<?> flag) {
        overrides.remove(flag);
    }

    @Nullable
    @Override
    public String get(@NonNull Flag<?> flag) {
        return overrides.get(flag);
    }
}
