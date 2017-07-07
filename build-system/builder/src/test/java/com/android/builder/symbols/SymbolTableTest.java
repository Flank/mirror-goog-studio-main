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

import static com.android.builder.symbols.SymbolTestUtils.createSymbol;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.resources.ResourceType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class SymbolTableTest {

    @Test
    public void tablePackageRequiredForEquality() {
        SymbolTable t0 = SymbolTable.builder().tablePackage("foo").build();
        SymbolTable t1 = SymbolTable.builder().tablePackage("bar").build();
        assertNotEquals(t0, t1);

        t1 = SymbolTable.builder().tablePackage("foo").build();
        assertEquals(t0, t1);
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
        assertEquals("", t.getTablePackage());
    }

    @Test
    public void mergeOneTable() {
        SymbolTable t =
                SymbolTable.builder()
                        .tablePackage("bar")
                        .add(createSymbol("attr", "b", "int", "d"))
                        .build();

        SymbolTable m = SymbolTable.merge(Collections.singletonList(t));

        SymbolTable expected =
                SymbolTable.builder()
                        .tablePackage("bar")
                        .add(createSymbol("attr", "b", "int", "d"))
                        .build();

        assertEquals(expected, m);
    }

    @Test
    public void mergeThreeTables() {
        SymbolTable m0 =
                SymbolTable.builder()
                        .tablePackage("bar")
                        .add(createSymbol("attr", "b", "int", "d"))
                        .build();

        SymbolTable m1 =
                SymbolTable.builder()
                        .tablePackage("muu")
                        .add(createSymbol("attr", "b", "int[]", "d1"))
                        .add(createSymbol("string", "b2", "int", "d2"))
                        .build();

        SymbolTable m2 =
                SymbolTable.builder()
                        .tablePackage("moo")
                        .add(createSymbol("attr", "b", "int[]", "d3"))
                        .add(createSymbol("string", "b2", "int[]", "d4"))
                        .add(createSymbol("color", "b5", "int", "d5"))
                        .build();

        SymbolTable r = SymbolTable.merge(Arrays.asList(m0, m1, m2));

        SymbolTable expected =
                SymbolTable.builder()
                        .tablePackage("bar")
                        .add(createSymbol("attr", "b", "int", "d"))
                        .add(createSymbol("string", "b2", "int", "d2"))
                        .add(createSymbol("color", "b5", "int", "d5"))
                        .build();

        assertEquals(expected, r);
    }

    @Test
    public void tableFilter() {
        SymbolTable t =
                SymbolTable.builder()
                        .tablePackage("bar")
                        .add(createSymbol("attr", "b", "int", "d"))
                        .add(createSymbol("string", "f", "int[]", "h"))
                        .build();

        SymbolTable f =
                SymbolTable.builder()
                        .tablePackage("bleh")
                        .add(createSymbol("integer", "j", "int[]", "l"))
                        .add(createSymbol("attr", "b", "int[]", "n"))
                        .build();

        SymbolTable r = t.filter(f);

        SymbolTable expected =
                SymbolTable.builder()
                        .tablePackage("bar")
                        .add(createSymbol("attr", "b", "int", "d"))
                        .build();

        assertEquals(expected, r);
    }

    @Test
    public void renameTest() {
        SymbolTable t = SymbolTable.builder().add(createSymbol("attr", "b", "int", "d")).build();
        SymbolTable r = t.rename("x");
        SymbolTable e =
                SymbolTable.builder()
                        .add(createSymbol("attr", "b", "int", "d"))
                        .tablePackage("x")
                        .build();
        assertEquals(e, r);
    }

    @Test
    public void checkContainsSymbol() {
        SymbolTable t = SymbolTable.builder().add(createSymbol("attr", "b", "int", "d")).build();
        assertTrue(t.getSymbols().contains(ResourceType.ATTR, "b"));
        assertFalse(t.getSymbols().contains(ResourceType.STRING, "b"));
    }
}
