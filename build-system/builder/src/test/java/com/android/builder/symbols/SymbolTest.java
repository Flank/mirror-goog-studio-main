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

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
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
        Symbol s = new Symbol("attr", "a", "b", "c");
        assertThat("a").isEqualTo(s.getName());
        assertThat("b").isEqualTo(s.getJavaType());
        assertThat("c").isEqualTo(s.getValue());
        assertThat("attr").isEqualTo(s.getResourceType());
    }

    @Test
    public void namesCannotContainSpaces() {
        try {
            new Symbol("attr", "a a", "b", "c");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat("Error: ' ' is not a valid resource name character")
                    .isEqualTo(e.getMessage());
        }
    }

    @Test
    public void valuesCanContainSpaces() {
        new Symbol("attr", "a", "b", "c c");
    }

    @Test
    public void nameCannotBeEmpty() {
        try {
            new Symbol("attr", "", "b", "c");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat("Error: The resource name shouldn't be empty").isEqualTo(e.getMessage());
        }
    }

    @Test
    public void nameCannotBeNull() {
        try {
            new Symbol("attr", null, "", "");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat("Resource name cannot be null").isEqualTo(e.getMessage());
        }
    }

    @Test
    public void nameCannotContainDots() {
        try {
            new Symbol("attr", "b.c", "d", "e");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat("Resource name cannot contain dots: b.c").isEqualTo(e.getMessage());
        }
    }

    @Test
    public void nameCannotContainColons() {
        try {
            new Symbol("attr", "b:c", "d", "e");
            fail();
        } catch (IllegalArgumentException e) {
            assertThat("Error: ':' is not a valid resource name character")
                    .isEqualTo(e.getMessage());
        }
    }

    @Test
    public void equalsTest() {
        Symbol sa = new Symbol("attr", "b", "c", "d");
        Symbol sb = new Symbol("attr", "b", "c", "d");

        assertThat(sa).isEqualTo(sb);
        assertThat(sa.hashCode()).isEqualTo(sb.hashCode());
    }

    @Test
    public void notEqualsClass() {
        Symbol sa = new Symbol("attr", "b", "c", "d");
        Symbol sb = new Symbol("string", "b", "c", "d");

        assertThat(sa).isNotEqualTo(sb);
    }

    @Test
    public void notEqualsName() {
        Symbol sa = new Symbol("attr", "bb", "c", "d");
        Symbol sb = new Symbol("attr", "b", "c", "d");

        assertThat(sa).isNotEqualTo(sb);
        // Tricky, but should work if Symbol does not get very complex.
        assertThat(sa.hashCode()).isNotEqualTo(sb.hashCode());
    }

    @Test
    public void notEqualsType() {
        Symbol sa = new Symbol("attr", "b", "cc", "d");
        Symbol sb = new Symbol("attr", "b", "c", "d");

        assertThat(sa).isNotEqualTo(sb);
        // Tricky, but should work if Symbol does not get very complex.
        assertThat(sa.hashCode()).isNotEqualTo(sb.hashCode());
    }

    @Test
    public void notEqualsValue() {
        Symbol sa = new Symbol("attr", "b", "c", "dd");
        Symbol sb = new Symbol("attr", "b", "c", "d");

        assertThat(sa).isNotEqualTo(sb);
        // Tricky, but should work if Symbol does not get very complex.
        assertThat(sa.hashCode()).isNotEqualTo(sb.hashCode());
    }

    @Test
    public void equalsNull() {
        assertThat(new Symbol("attr", "b", "c", "d")).isNotEqualTo(null);
    }

    @Test
    public void equalsNonSymbol() {
        assertThat(new Symbol("attr", "b", "c", "d")).isNotEqualTo(3);
    }

    @Test
    public void useEqualsVerifier() {
        EqualsVerifier.forClass(Symbol.class)
                .suppress(Warning.STRICT_INHERITANCE)
                .verify();
    }
}
