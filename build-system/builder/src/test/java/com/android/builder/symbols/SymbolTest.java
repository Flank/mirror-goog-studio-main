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
import static org.junit.Assert.fail;

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
}
