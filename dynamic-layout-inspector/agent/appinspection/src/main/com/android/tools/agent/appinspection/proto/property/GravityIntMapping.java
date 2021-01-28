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

import android.view.Gravity;
import androidx.annotation.NonNull;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntFunction;

/** Support for converting android.view.Gravity values into a set of strings. */
public final class GravityIntMapping implements IntFunction<Set<String>> {
    private final IntFlagMapping gravityIntFlagMapping = new IntFlagMapping();

    public GravityIntMapping() {
        gravityIntFlagMapping.add(Gravity.FILL, Gravity.FILL, "fill");

        gravityIntFlagMapping.add(Gravity.FILL_VERTICAL, Gravity.FILL_VERTICAL, "fill_vertical");
        gravityIntFlagMapping.add(Gravity.FILL_VERTICAL, Gravity.TOP, "top");
        gravityIntFlagMapping.add(Gravity.FILL_VERTICAL, Gravity.BOTTOM, "bottom");

        gravityIntFlagMapping.add(
                Gravity.FILL_HORIZONTAL, Gravity.FILL_HORIZONTAL, "fill_horizontal");
        gravityIntFlagMapping.add(Gravity.FILL_HORIZONTAL, Gravity.LEFT, "left");
        gravityIntFlagMapping.add(Gravity.FILL_HORIZONTAL, Gravity.RIGHT, "right");

        gravityIntFlagMapping.add(Gravity.FILL, Gravity.CENTER, "center");
        gravityIntFlagMapping.add(
                Gravity.FILL_VERTICAL, Gravity.CENTER_VERTICAL, "center_vertical");
        gravityIntFlagMapping.add(
                Gravity.FILL_HORIZONTAL, Gravity.CENTER_HORIZONTAL, "center_horizontal");

        gravityIntFlagMapping.add(Gravity.CLIP_VERTICAL, Gravity.CLIP_VERTICAL, "clip_vertical");
        gravityIntFlagMapping.add(
                Gravity.CLIP_HORIZONTAL, Gravity.CLIP_HORIZONTAL, "clip_horizontal");
    }

    @Override
    @NonNull
    public Set<String> apply(int value) {
        Set<String> values = gravityIntFlagMapping.apply(value);
        if ((value & Gravity.RELATIVE_LAYOUT_DIRECTION) != 0) {
            values = new HashSet<>(values);
            if (values.remove("left")) {
                values.add("start");
            }
            if (values.remove("right")) {
                values.add("end");
            }
            values = Collections.unmodifiableSet(values);
        }
        return values;
    }
}
