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
import com.android.annotations.Nullable;
import com.android.flags.overrides.DefaultFlagOverrides;
import com.android.flags.overrides.PropertyOverrides;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Class which represents a collection of flags, usually for a whole program.
 *
 * <p>The recommended way to use this class is by creating a container class, as follows, with a
 * list of publicly exposed static flags:
 *
 * <pre>
 *     public final class GameFlags {
 *         private static final Flags FLAGS = new Flags();
 *
 *         private static final FlagGroup AUDIO = new FlagGroup(FLAGS, "audio", "Audio");
 *         private static final FlagGroup GRAPHICS = new FlagGroup(FLAGS<, "graphics", "Graphics");
 *
 *         public static final Flag{Boolean} USE_3D_AUDIO = Flag.create(AUDIO, ...);
 *         public static final Flag{Integer} FPS_CAP = Flag.create(GRAPHICS, ...);
 *     }
 *
 *     // Elsewhere...
 *     if (GameFlags.USE_3D_AUDIO.get()) {
 *         ...
 *     }
 * </pre>
 */
public final class Flags {
    private final Map<String, Flag<?>> registeredFlags =
            Collections.synchronizedMap(new HashMap<>());
    private final ImmutableFlagOverrides[] fallbackOverridesList;
    private final FlagOverrides mutableOverrides;

    /**
     * Construct a new collection of flags, providing both a main, mutable {@link FlagOverrides} and
     * a list of 0 or more fallback {@link FlagOverrides}. The fallback overrides will be checked in
     * the order they were added.
     *
     * <p>It is likely you will want to pass in at least a {@link PropertyOverrides} instance as a
     * fallback handler, enabling flag defaults to be specified on the command line.
     */
    public Flags(
            @NonNull FlagOverrides mutableOverrides,
            ImmutableFlagOverrides... fallbackOverridesList) {
        this.mutableOverrides = mutableOverrides;
        this.fallbackOverridesList = fallbackOverridesList;
    }

    public Flags(ImmutableFlagOverrides... immutableOverrides) {
        this(new DefaultFlagOverrides(), immutableOverrides);
    }

    /**
     * A mutable set of flags used for overriding flag values at runtime.
     *
     * <p>This collection does not include any of the immutable fallback overrides which may have
     * been specified in the constructor.
     */
    @NonNull
    public FlagOverrides getOverrides() {
        return mutableOverrides;
    }

    /**
     * Returns an overridden flag value, if any, or {@code null} if the value is not overridden by
     * any of the {@link FlagOverrides} instances.
     *
     * <p>To set a flag, use {@link #getOverrides()} and set it through its API.
     */
    @Nullable
    String getOverriddenValue(@NonNull Flag<?> flag) {
        String flagValue = mutableOverrides.get(flag);
        if (flagValue == null) {
            for (ImmutableFlagOverrides flagOverrides : fallbackOverridesList) {
                flagValue = flagOverrides.get(flag);
                if (flagValue != null) {
                    break;
                }
            }
        }

        return flagValue;
    }

    /**
     * Verifies that the target flag has a unique ID across all flags registered with this Flags
     * instance.
     *
     * <p>Although it's unlikely one would define flags across multiple threads, this method is
     * still thread-safe just in case.
     */
    void verifyUniqueId(@NonNull Flag<?> flag) {
        Flag<?> existingFlag = registeredFlags.putIfAbsent(flag.getId(), flag);
        if (existingFlag != null) {
            throw new IllegalArgumentException(
                    String.format(
                            "Flag \"%s\" shares duplicate ID \"%s\" with flag \"%s\"",
                            flag.getDisplayName(), flag.getId(), existingFlag.getDisplayName()));
        }
    }
}
