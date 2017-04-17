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

package com.android.build.gradle.options;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.android.builder.model.AaptOptions;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

public class EnumOptionsTest {
    @Test
    public void enumOptions() {
        assertThat(new ProjectOptions(ImmutableMap.of()).getEnumOptions().getNamespacing())
                .named("Namespacing no option set")
                .isEqualTo(AaptOptions.Namespacing.DISABLED);
        assertThat(
                        new ProjectOptions(ImmutableMap.of("android.aaptNamespacing", "DISABLED"))
                                .getEnumOptions()
                                .getNamespacing())
                .named("Namespacing set to DISABLED")
                .isEqualTo(AaptOptions.Namespacing.DISABLED);
        assertThat(
                        new ProjectOptions(ImmutableMap.of("android.aaptNamespacing", "REQUIRED"))
                                .getEnumOptions()
                                .getNamespacing())
                .named("Namespacing set to REQUIRED")
                .isEqualTo(AaptOptions.Namespacing.REQUIRED);

        try {
            //noinspection ResultOfObjectAllocationIgnored
            new ProjectOptions(ImmutableMap.of("android.aaptNamespacing", "REQUIRED!"));
            fail("Expected illegal argument exception");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage())
                    .isEqualTo(
                            "Project property android.aaptNamespacing is set to invalid "
                                    + "value 'REQUIRED!'. "
                                    + "Possible values are: DISABLED, REQUIRED.");
        }
    }
}
