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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Test;

public class SymbolTableTest {

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
    public void tableNameRequiredForEquality() {
        SymbolTable t0 = new SymbolTable();
        SymbolTable t1 = new SymbolTable();

        t0.setTableName("foo");
        t1.setTableName("bar");

        assertNotEquals(t0, t1);
        assertNotEquals(t0.hashCode(), t1.hashCode());

        t1.setTableName("foo");

        assertEquals(t0, t1);
        assertEquals(t0.hashCode(), t1.hashCode());
    }

    @Test
    public void tablePackageRequiredForEquality() {
        SymbolTable t0 = new SymbolTable();
        SymbolTable t1 = new SymbolTable();

        t0.setTablePackage("foo");
        t1.setTablePackage("bar");

        assertNotEquals(t0, t1);
        assertNotEquals(t0.hashCode(), t1.hashCode());

        t1.setTablePackage("foo");

        assertEquals(t0, t1);
        assertEquals(t0.hashCode(), t1.hashCode());
    }

    @Test
    public void readTableSymbols() {
        SymbolTable t = new SymbolTable();

        Set<Symbol> syms = t.allSymbols();
        assertEquals(0, syms.size());

        t.add(new Symbol("x", "y", "z", "w"));
        syms = t.allSymbols();
        assertEquals(1, syms.size());
        assertTrue(syms.contains(new Symbol("x", "y", "z", "w")));
    }

    @Test
    public void useEqualsVerifier() {
        EqualsVerifier.forClass(SymbolTable.class)
                .suppress(Warning.STRICT_INHERITANCE)
                .suppress(Warning.NONFINAL_FIELDS)
                .verify();
    }

    @Test
    public void setValidName() {
        SymbolTable t = new SymbolTable();
        t.setTableName("foo");
        assertEquals("foo", t.getTableName());
    }

    @Test
    public void defaultTableName() {
        SymbolTable t = new SymbolTable();
        assertEquals("R", t.getTableName());
    }

    @Test
    public void setInvalidName() {
        SymbolTable t = new SymbolTable();
        try {
            t.setTableName("f o o");
            fail();
        } catch (IllegalArgumentException e) {
            // Expected.
        }
    }

    @Test
    public void setValidPackage() {
        SymbolTable t = new SymbolTable();
        t.setTablePackage("a.bb.ccc");
        assertEquals("a.bb.ccc", t.getTablePackage());
    }

    @Test
    public void setInvalidPackage() {
        SymbolTable t = new SymbolTable();
        try {
            t.setTablePackage("a+b");
            fail();
        } catch (IllegalArgumentException e) {
            // Expected.
        }
    }

    @Test
    public void defaultTablePackage() {
        SymbolTable t = new SymbolTable();
        assertEquals("", t.getTablePackage());
    }

    @Test
    public void mergeNoTables() {
        SymbolTable t = new SymbolTable();
        t.setTableName("foo");
        t.setTablePackage("bar");
        SymbolTable.merge(t, new ArrayList<>());
        assertEquals("foo", t.getTableName());
        assertEquals("bar", t.getTablePackage());
    }

    @Test
    public void mergeOneTable() {
        SymbolTable t = new SymbolTable();
        t.setTableName("foo");
        t.setTablePackage("bar");

        SymbolTable m0 = new SymbolTable();
        m0.setTableName("duh");
        m0.setTablePackage("duhduh");
        m0.add(new Symbol("a", "b", "c", "d"));
        SymbolTable.merge(t, Collections.singletonList(m0));

        SymbolTable expected = new SymbolTable();
        expected.setTableName("foo");
        expected.setTablePackage("bar");
        expected.add(new Symbol("a", "b", "c", "d"));

        assertEquals(expected, t);
    }

    @Test
    public void mergeThreeTables() {
        SymbolTable t = new SymbolTable();
        t.setTableName("foo");
        t.setTablePackage("bar");

        SymbolTable m0 = new SymbolTable();
        m0.setTableName("duh");
        m0.setTablePackage("duhduh");
        m0.add(new Symbol("a", "b", "c", "d"));

        SymbolTable m1 = new SymbolTable();
        m1.setTableName("mu");
        m1.setTablePackage("muu");
        m1.add(new Symbol("a", "b", "c1", "d1"));
        m1.add(new Symbol("a2", "b2", "c2", "d2"));

        SymbolTable m2 = new SymbolTable();
        m2.setTableName("moo");
        m2.setTablePackage("moo");
        m2.add(new Symbol("a", "b", "c3", "d3"));
        m2.add(new Symbol("a2", "b2", "c4", "d4"));
        m2.add(new Symbol("a5", "b5", "c5", "d5"));

        SymbolTable.merge(t, Arrays.asList(m0, m1, m2));

        SymbolTable expected = new SymbolTable();
        expected.setTableName("foo");
        expected.setTablePackage("bar");
        expected.add(new Symbol("a", "b", "c", "d"));
        expected.add(new Symbol("a2", "b2", "c2", "d2"));
        expected.add(new Symbol("a5", "b5", "c5", "d5"));

        assertEquals(expected, t);
    }

    @Test
    public void preexistingSymbolAreKeptDuringMerge() {
        SymbolTable t = new SymbolTable();
        t.setTableName("foo");
        t.setTablePackage("bar");
        t.add(new Symbol("a", "b", "c", "d"));

        SymbolTable m0 = new SymbolTable();
        m0.setTableName("N");
        m0.setTablePackage("p");
        m0.add(new Symbol("a", "b", "c", "dx"));
        m0.add(new Symbol("a1", "b1", "c", "dy"));
        SymbolTable.merge(t, Collections.singletonList(m0));

        SymbolTable expected = new SymbolTable();
        expected.setTableName("foo");
        expected.setTablePackage("bar");
        expected.add(new Symbol("a", "b", "c", "d"));
        expected.add(new Symbol("a1", "b1", "c", "dy"));

        assertEquals(expected, t);
    }

    @Test
    public void tableFilter() {
        SymbolTable t = new SymbolTable();
        t.setTableName("foo");
        t.setTablePackage("bar");
        t.add(new Symbol("a", "b", "c", "d"));
        t.add(new Symbol("e", "f", "g", "h"));

        SymbolTable f = new SymbolTable();
        f.setTableName("blah");
        f.setTablePackage("bleh");
        f.add(new Symbol("i", "j", "k", "l"));
        f.add(new Symbol("a", "b", "m", "n"));

        SymbolTable r = t.filter(f);

        SymbolTable expected = new SymbolTable();
        expected.setTableName("foo");
        expected.setTablePackage("bar");
        expected.add(new Symbol("a", "b", "c", "d"));

        assertEquals(expected, r);
    }
}
