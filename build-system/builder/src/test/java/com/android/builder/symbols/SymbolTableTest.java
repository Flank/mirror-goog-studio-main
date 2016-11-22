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

import java.util.Iterator;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Test;

public class SymbolTableTest {

    @Test
    public void tableEqualItself() {
        SymbolTable t0 = new SymbolTable();
        t0.add(new Symbol("a", "b", "c", "d"));

        assertEquals(t0, t0);
    }

    @Test
    public void equalEmptyTable() {
        SymbolTable t0 = new SymbolTable();
        SymbolTable t1 = new SymbolTable();

        assertEquals(t0, t1);
        assertEquals(t0.hashCode(), t1.hashCode());
    }

    @Test
    public void equalNonEmptyTable() {
        SymbolTable t0 = new SymbolTable();
        SymbolTable t1 = new SymbolTable();

        t0.add(new Symbol("1", "2", "3", "4"));
        t1.add(new Symbol("1", "2", "3", "4"));

        assertEquals(t0, t1);
        assertEquals(t0.hashCode(), t1.hashCode());
    }

    @Test
    public void nonEqualTable() {
        SymbolTable t0 = new SymbolTable();
        SymbolTable t1 = new SymbolTable();

        t0.add(new Symbol("1", "2", "3", "4"));
        t1.add(new Symbol("1", "2", "3", "5"));

        assertNotEquals(t0, t1);
        assertNotEquals(t0.hashCode(), t1.hashCode());
    }

    @Test
    public void readTableSymbols() {
        SymbolTable t = new SymbolTable();

        Iterable<Symbol> it = t.allSymbols();
        assertFalse(it.iterator().hasNext());

        t.add(new Symbol("x", "y", "z", "w"));
        it = t.allSymbols();
        Iterator<Symbol> itIt = it.iterator();
        Symbol s0 = itIt.next();
        assertEquals(new Symbol("x", "y", "z", "w"), s0);
        assertFalse(itIt.hasNext());
    }

    @Test
    public void useEqualsVerifier() {
        EqualsVerifier.forClass(SymbolTable.class)
                .suppress(Warning.STRICT_INHERITANCE)
                .suppress(Warning.NONFINAL_FIELDS)
                .verify();
    }
}
