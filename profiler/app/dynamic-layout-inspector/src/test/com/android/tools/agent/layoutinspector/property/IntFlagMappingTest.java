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

import org.junit.Test;

public class IntFlagMappingTest {

    @Test
    public void testBasicMapping() {
        IntFlagMapping mapping = new IntFlagMapping();
        mapping.add(0x03, 0x01, "left");
        mapping.add(0x03, 0x02, "right");
        mapping.add(0x03, 0x03, "justify");
        mapping.add(0x04, 0x04, "dim");

        assertThat(mapping.apply(0)).isEmpty();
        assertThat(mapping.apply(2)).containsExactly("right");
        assertThat(mapping.apply(5)).containsExactly("left", "dim");
        assertThat(mapping.apply(7)).containsExactly("justify", "dim");
    }
}
