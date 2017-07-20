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

package com.android.builder.symbols;

import static com.google.common.truth.Truth.assertThat;

import com.android.resources.ResourceType;
import org.junit.Test;

public class IdProviderTest {
    @Test
    public void aaptFormat() throws Exception {
        // Sanity check.
        assertThat(ResourceType.ANIM.ordinal()).isEqualTo(0);
        assertThat(ResourceType.ATTR.ordinal()).isEqualTo(3);

        IdProvider provider = IdProvider.sequential();
        assertThat(provider.next(ResourceType.ANIM)).isEqualTo("0x7f010001");
        assertThat(provider.next(ResourceType.ANIM)).isEqualTo("0x7f010002");
        assertThat(provider.next(ResourceType.ATTR)).isEqualTo("0x7f040001");
        assertThat(provider.next(ResourceType.ANIM)).isEqualTo("0x7f010003");
        assertThat(provider.next(ResourceType.ATTR)).isEqualTo("0x7f040002");
    }
}
