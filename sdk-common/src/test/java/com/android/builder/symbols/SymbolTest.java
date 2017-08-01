/*
 * Copyright (C) 2016 The Android Open Source Project
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
import static org.junit.Assert.fail;

import com.android.resources.ResourceType;
import org.junit.Test;

public class SymbolTest {

    @Test
    public void checkMakeValidName() {
        // Copy aapt's behaviour
        assertThat("android_a_b_c")
                .isEqualTo(SymbolUtils.canonicalizeValueResourceName("android:a.b.c"));
    }

    @Test
    public void symbolData() {
        Symbol s = SymbolTestUtils.createSymbol("attr", "a", "int", "c");
        assertThat(s.getName()).isEqualTo("a");
        assertThat(s.getJavaType()).isEqualTo(SymbolJavaType.INT);
        assertThat(s.getValue()).isEqualTo("c");
        assertThat(s.getResourceType()).isEqualTo(ResourceType.ATTR);
    }

    @Test
    public void namesCannotContainSpaces() {
        try {
            SymbolTestUtils.createSymbol("attr", "a a", "int", "c");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat("Error: ' ' is not a valid resource name character")
                    .isEqualTo(e.getMessage());
        }
    }

    @Test
    public void valuesCanContainSpaces() {
        SymbolTestUtils.createSymbol("attr", "a", "int", "c c");
    }

    @Test
    public void nameCannotBeEmpty() {
        try {
            SymbolTestUtils.createSymbol("attr", "", "int", "c");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat("Error: The resource name shouldn't be empty").isEqualTo(e.getMessage());
        }
    }

    @Test
    public void nameCannotBeNull() {
        try {
            // noinspection ConstantConditions
            SymbolTestUtils.createSymbol("attr", null, "int[]", "");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("name");
        }
    }

    @Test
    public void nameCannotContainDots() {
        try {
            SymbolTestUtils.createSymbol("attr", "b.c", "int[]", "e");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat("Resource name cannot contain dots: b.c").isEqualTo(e.getMessage());
        }
    }

    @Test
    public void nameCannotContainColons() {
        try {
            SymbolTestUtils.createSymbol("attr", "b:c", "int[]", "e");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat("Error: ':' is not a valid resource name character")
                    .isEqualTo(e.getMessage());
        }
    }
}
