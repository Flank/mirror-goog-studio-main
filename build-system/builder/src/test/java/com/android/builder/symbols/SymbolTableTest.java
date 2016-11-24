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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Test;

public class SymbolTableTest {

    @Test
    public void equalEmptyTable() {
        SymbolTable t0 = SymbolTable.builder().build();
        SymbolTable t1 = SymbolTable.builder().build();

        assertEquals(t0, t1);
        assertEquals(t0.hashCode(), t1.hashCode());
    }

    @Test
    public void equalNonEmptyTable() {
        SymbolTable t0 = SymbolTable.builder().add(new Symbol("1", "2", "3", "4")).build();
        SymbolTable t1 = SymbolTable.builder().add(new Symbol("1", "2", "3", "4")).build();

        assertEquals(t0, t1);
        assertEquals(t0.hashCode(), t1.hashCode());
    }

    @Test
    public void nonEqualTable() {
        SymbolTable t0 = SymbolTable.builder().add(new Symbol("1", "2", "3", "4")).build();
        SymbolTable t1 = SymbolTable.builder().add(new Symbol("1", "2", "3", "5")).build();

        assertNotEquals(t0, t1);
        assertNotEquals(t0.hashCode(), t1.hashCode());
    }

    @Test
    public void tableNameRequiredForEquality() {
        SymbolTable t0 = SymbolTable.builder().tableName("foo").build();
        SymbolTable t1 = SymbolTable.builder().tableName("bar").build();

        assertNotEquals(t0, t1);
        assertNotEquals(t0.hashCode(), t1.hashCode());

        t1 = SymbolTable.builder().tableName("foo").build();

        assertEquals(t0, t1);
        assertEquals(t0.hashCode(), t1.hashCode());
    }

    @Test
    public void tablePackageRequiredForEquality() {
        SymbolTable t0 = SymbolTable.builder().tablePackage("foo").build();
        SymbolTable t1 = SymbolTable.builder().tablePackage("bar").build();

        assertNotEquals(t0, t1);
        assertNotEquals(t0.hashCode(), t1.hashCode());

        t1 = SymbolTable.builder().tablePackage("foo").build();

        assertEquals(t0, t1);
        assertEquals(t0.hashCode(), t1.hashCode());
    }

    @Test
    public void readTableSymbols() {
        SymbolTable t = SymbolTable.builder().build();

        ImmutableCollection<Symbol> syms = t.allSymbols();
        assertEquals(0, syms.size());

        t = SymbolTable.builder().add(new Symbol("x", "y", "z", "w")).build();
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
        SymbolTable t = SymbolTable.builder().tableName("foo").build();
        assertEquals("foo", t.getTableName());
    }

    @Test
    public void defaultTableName() {
        SymbolTable t = SymbolTable.builder().build();
        assertEquals("R", t.getTableName());
    }

    @Test
    public void setInvalidName() {
        try {
            SymbolTable.builder().tableName("f o o");
            fail();
        } catch (IllegalArgumentException e) {
            // Expected.
        }
    }

    @Test
    public void setValidPackage() {
        SymbolTable t = SymbolTable.builder().tablePackage("a.bb.ccc").build();
        assertEquals("a.bb.ccc", t.getTablePackage());
    }

    @Test
    public void setInvalidPackage() {
        try {
            SymbolTable.builder().tablePackage("a+b");
            fail();
        } catch (IllegalArgumentException e) {
            // Expected.
        }
    }

    @Test
    public void defaultTablePackage() {
        SymbolTable t = SymbolTable.builder().build();
        assertEquals("", t.getTablePackage());
    }

    @Test
    public void mergeNoTables() {
        SymbolTable t = SymbolTable.merge(new ArrayList<>());
        assertEquals("R", t.getTableName());
        assertEquals("", t.getTablePackage());
    }

    @Test
    public void mergeOneTable() {
        SymbolTable t =
                SymbolTable.builder()
                        .tableName("foo")
                        .tablePackage("bar")
                        .add(new Symbol("a", "b", "c", "d"))
                        .build();

        SymbolTable m = SymbolTable.merge(Collections.singletonList(t));

        SymbolTable expected =
                SymbolTable.builder()
                        .tableName("foo")
                        .tablePackage("bar")
                        .add(new Symbol("a", "b", "c", "d"))
                        .build();

        assertEquals(expected, m);
    }

    @Test
    public void mergeThreeTables() {
        SymbolTable m0 =
                SymbolTable.builder()
                        .tableName("foo")
                        .tablePackage("bar")
                        .add(new Symbol("a", "b", "c", "d"))
                        .build();

        SymbolTable m1 =
                SymbolTable.builder()
                        .tableName("mu")
                        .tablePackage("muu")
                        .add(new Symbol("a", "b", "c1", "d1"))
                        .add(new Symbol("a2", "b2", "c2", "d2"))
                        .build();

        SymbolTable m2 =
                SymbolTable.builder()
                        .tableName("moo")
                        .tablePackage("moo")
                        .add(new Symbol("a", "b", "c3", "d3"))
                        .add(new Symbol("a2", "b2", "c4", "d4"))
                        .add(new Symbol("a5", "b5", "c5", "d5"))
                        .build();

        SymbolTable r = SymbolTable.merge(Arrays.asList(m0, m1, m2));

        SymbolTable expected =
                SymbolTable.builder()
                        .tableName("foo")
                        .tablePackage("bar")
                        .add(new Symbol("a", "b", "c", "d"))
                        .add(new Symbol("a2", "b2", "c2", "d2"))
                        .add(new Symbol("a5", "b5", "c5", "d5"))
                        .build();

        assertEquals(expected, r);
    }

    @Test
    public void tableFilter() {
        SymbolTable t =
                SymbolTable.builder()
                        .tableName("foo")
                        .tablePackage("bar")
                        .add(new Symbol("a", "b", "c", "d"))
                        .add(new Symbol("e", "f", "g", "h"))
                        .build();

        SymbolTable f =
                SymbolTable.builder()
                        .tableName("blah")
                        .tablePackage("bleh")
                        .add(new Symbol("i", "j", "k", "l"))
                        .add(new Symbol("a", "b", "m", "n"))
                        .build();

        SymbolTable r = t.filter(f);

        SymbolTable expected =
                SymbolTable.builder()
                        .tableName("foo")
                        .tablePackage("bar")
                        .add(new Symbol("a", "b", "c", "d"))
                        .build();

        assertEquals(expected, r);
    }

    @Test
    public void renameTest() {
        SymbolTable t = SymbolTable.builder().add(new Symbol("a", "b", "c", "d")).build();
        SymbolTable r = t.rename("x", "y");
        SymbolTable e =
                SymbolTable.builder()
                        .add(new Symbol("a", "b", "c", "d"))
                        .tableName("y")
                        .tablePackage("x")
                        .build();
        assertEquals(e, r);
    }

    @Test
    public void containsSymbol() {
        SymbolTable t = SymbolTable.builder().add(new Symbol("a", "b", "c", "d")).build();
        assertTrue(t.contains("a", "b"));
        assertFalse(t.contains("aa", "b"));
    }
}
