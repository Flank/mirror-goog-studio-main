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
import com.android.flags.ImmutableFlagOverrides;
import java.util.Properties;

/** Read-only collection of override values backed by a set of Java properties. */
public final class PropertyOverrides implements ImmutableFlagOverrides {
    private final Properties properties;

    public PropertyOverrides() {
        this(System.getProperties());
    }

    public PropertyOverrides(Properties properties) {
        this.properties = properties;
    }

    @Nullable
    @Override
    public String get(@NonNull Flag<?> flag) {
        return (String) properties.get(flag.getId());
    }
}
