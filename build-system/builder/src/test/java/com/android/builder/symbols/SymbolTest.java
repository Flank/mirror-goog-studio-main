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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Test;

public class SymbolTest {

    @Test
    public void symbolData() {
        Symbol s = new Symbol("d", "a", "b", "c");
        assertEquals("a", s.getName());
        assertEquals("b", s.getJavaType());
        assertEquals("c", s.getValue());
        assertEquals("d", s.getResourceType());
    }

    @Test
    public void namesCannotContainSpaces() {
        try {
            new Symbol("d", "a a", "b", "c");
            fail();
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void javaTypesCannotContainSpaces() {
        try {
            new Symbol("d", "a", "b b", "c");
            fail();
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void valuesCanContainSpaces() {
        new Symbol("d", "a", "b", "c c");
    }

    @Test
    public void resourceTypesCannotContainSpaces() {
        try {
            new Symbol("d d", "a", "b", "c");
            fail();
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void nameCannotBeEmpty() {
        try {
            new Symbol("d", "", "b", "c");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void javaTypeCannotBeEmpty() {
        try {
            new Symbol("d", "a", "", "c");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void valueCannotBeEmpty() {
        try {
            new Symbol("d", "a", "b", "");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void resourceTypeCannotBeEmpty() {
        try {
            new Symbol("", "a", "b", "c");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void equalsTest() {
        Symbol sa = new Symbol("a", "b", "c", "d");
        Symbol sb = new Symbol("a", "b", "c", "d");

        assertEquals(sa, sb);
        assertEquals(sa.hashCode(), sb.hashCode());
    }

    @Test
    public void notEqualsClass() {
        Symbol sa = new Symbol("aa", "b", "c", "d");
        Symbol sb = new Symbol("a", "b", "c", "d");

        assertNotEquals(sa, sb);
    }

    @Test
    public void notEqualsName() {
        Symbol sa = new Symbol("a", "bb", "c", "d");
        Symbol sb = new Symbol("a", "b", "c", "d");

        assertNotEquals(sa, sb);
        // Tricky, but should work if Symbol does not get very complex.
        assertNotEquals(sa.hashCode(), sb.hashCode());
    }

    @Test
    public void notEqualsType() {
        Symbol sa = new Symbol("a", "b", "cc", "d");
        Symbol sb = new Symbol("a", "b", "c", "d");

        assertNotEquals(sa, sb);
        // Tricky, but should work if Symbol does not get very complex.
        assertNotEquals(sa.hashCode(), sb.hashCode());
    }

    @Test
    public void notEqualsValue() {
        Symbol sa = new Symbol("a", "b", "c", "dd");
        Symbol sb = new Symbol("a", "b", "c", "d");

        assertNotEquals(sa, sb);
        // Tricky, but should work if Symbol does not get very complex.
        assertNotEquals(sa.hashCode(), sb.hashCode());
    }

    @Test
    public void equalsNull() {
        assertFalse(new Symbol("a", "b", "c", "d").equals(null));
    }

    @Test
    public void equalsNonSymbol() {
        assertFalse(new Symbol("a", "b", "c", "d").equals(3));
    }

    @Test
    public void equalItself() {
        Symbol sa = new Symbol("a", "b", "c", "d");
        assertEquals(sa, sa);
    }

    @Test
    public void useEqualsVerifier() {
        EqualsVerifier.forClass(Symbol.class)
                .suppress(Warning.STRICT_INHERITANCE)
                .verify();
    }
}
