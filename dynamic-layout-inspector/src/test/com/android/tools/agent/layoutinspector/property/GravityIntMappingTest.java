/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.agent.layoutinspector.property;

import static com.google.common.truth.Truth.assertThat;

import android.view.Gravity;
import org.junit.Test;

public class GravityIntMappingTest {

    @Test
    public void testBasicMapping() {
        // A single setting should resolve in a single string gravity value
        GravityIntMapping mapping = new GravityIntMapping();
        assertThat(mapping.apply(Gravity.TOP)).containsExactly("top");
        assertThat(mapping.apply(Gravity.BOTTOM)).containsExactly("bottom");
        assertThat(mapping.apply(Gravity.LEFT)).containsExactly("left");
        assertThat(mapping.apply(Gravity.RIGHT)).containsExactly("right");
        assertThat(mapping.apply(Gravity.START)).containsExactly("start");
        assertThat(mapping.apply(Gravity.END)).containsExactly("end");
        assertThat(mapping.apply(Gravity.CENTER_VERTICAL)).containsExactly("center_vertical");
        assertThat(mapping.apply(Gravity.CENTER_HORIZONTAL)).containsExactly("center_horizontal");
        assertThat(mapping.apply(Gravity.CENTER)).containsExactly("center");
        assertThat(mapping.apply(Gravity.FILL_VERTICAL)).containsExactly("fill_vertical");
        assertThat(mapping.apply(Gravity.FILL_HORIZONTAL)).containsExactly("fill_horizontal");
        assertThat(mapping.apply(Gravity.FILL)).containsExactly("fill");
        assertThat(mapping.apply(Gravity.CLIP_VERTICAL)).containsExactly("clip_vertical");
        assertThat(mapping.apply(Gravity.CLIP_HORIZONTAL)).containsExactly("clip_horizontal");
    }
}
