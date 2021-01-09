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
package com.android.tools.agent.appinspection.proto.property;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;

/** Loose adoption of android.view.inspector.IntFlagMapping */
public final class IntFlagMapping implements IntFunction<Set<String>> {
    private final List<Flag> mFlags = new ArrayList<>();

    /**
     * Get a set of the names of enabled flags for a given property value.
     *
     * @param value The value of the property
     * @return The names of the enabled flags, empty if no flags enabled
     */
    @Override
    @NonNull
    public Set<String> apply(int value) {
        Set<String> enabledFlagNames = new HashSet<>();
        int alreadyIncluded = 0;

        for (Flag flag : mFlags) {
            if (flag.isEnabledFor(value) && ((alreadyIncluded & flag.mTarget) != flag.mTarget)) {
                enabledFlagNames.add(flag.mName);
                alreadyIncluded |= flag.mTarget;
            }
        }

        return enabledFlagNames;
    }

    /**
     * Add a flag to the map.
     *
     * @param mask The bit mask to compare to and with a value
     * @param target The target value to compare the masked value with
     * @param name The name of the flag to include if enabled
     */
    public void add(int mask, int target, @NonNull String name) {
        mFlags.add(new Flag(mask, target, name));
    }

    /** Inner class that holds the name, mask, and target value of a flag */
    private static final class Flag {
        private final String mName;
        private final int mTarget;
        private final int mMask;

        private Flag(int mask, int target, @NonNull String name) {
            mTarget = target;
            mMask = mask;
            mName = name;
        }

        /**
         * Compare the supplied property value against the mask and target.
         *
         * @param value The value to check
         * @return True if this flag is enabled
         */
        private boolean isEnabledFor(int value) {
            return (value & mMask) == mTarget;
        }
    }
}
