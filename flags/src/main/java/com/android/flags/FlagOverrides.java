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

/**
 * Interface which represents a mutable mapping of flag to value overrides.
 *
 * <p>{@link Flag}s are immutable, which makes defining them a thread-safe operation. However, we
 * still want the ability to override a flag's default value, as this will allow us to run
 * experiments as well as letting a user to manually opt-in or out of some flags. We resolve this by
 * keeping flags immutable while storing their value overrides separately. This interface is the
 * contract for any class that wants to serve that role.
 *
 * @see Flags#getOverrides()
 */
public interface FlagOverrides extends ImmutableFlagOverrides {
    /** Clear all overrides in this collection. */
    void clear();

    /** Add an override into this collection, overwriting any existing value if present. */
    void put(@NonNull Flag<?> flag, @NonNull String value);

    /** Clear an override that was set by {@link #put(Flag, String)}, if any. */
    void remove(@NonNull Flag<?> flag);
}
