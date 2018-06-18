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

package com.android.ide.common.symbols;

import static com.android.ide.common.symbols.SymbolTestUtils.createSymbol;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.google.common.collect.ImmutableList;
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
                        .add(createSymbol("attr", "b", "int", "d1"))
                        .add(createSymbol("string", "b2", "int", "d2"))
                        .build();

        SymbolTable m2 =
                SymbolTable.builder()
                        .tablePackage("moo")
                        .add(createSymbol("attr", "b", "int", "d3"))
                        .add(createSymbol("string", "b2", "int", "d4"))
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
                        .add(createSymbol("string", "f", "int", "h"))
                        .build();

        SymbolTable f =
                SymbolTable.builder()
                        .tablePackage("bleh")
                        .add(createSymbol("integer", "j", "int", "l"))
                        .add(createSymbol("attr", "b", "int", "n"))
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
    public void tableFilterWithAarTable() {
        SymbolTable t =
                SymbolTable.builder()
                        .tablePackage("bar")
                        .add(
                                createSymbol(
                                        "styleable",
                                        "Foo.Bar",
                                        "int[]",
                                        "{0}",
                                        ImmutableList.of("child")))
                        .build();

        SymbolTable f =
                SymbolTable.builder()
                        .tablePackage("fromAAR")
                        .add(createSymbol("styleable", "Foo_Bar", "int[]", "{}"))
                        .build();

        SymbolTable r = t.filter(f);

        SymbolTable expected =
                SymbolTable.builder()
                        .tablePackage("bar")
                        .add(
                                createSymbol(
                                        "styleable",
                                        "Foo.Bar",
                                        "int[]",
                                        "{0}",
                                        ImmutableList.of("child")))
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

    @Test
    public void invalidPackageNameKeyword() {
        try {
            SymbolTable.builder().tablePackage("com.example.int");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e)
                    .hasMessage(
                            "Package 'com.example.int' from AndroidManifest.xml is not a valid"
                                    + " Java package name as 'int' is a Java keyword.");
        }
    }

    @Test
    public void invalidPackageNameNotIdentifier() {
        try {
            SymbolTable.builder().tablePackage("com.example.my-package");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e)
                    .hasMessage(
                            "Package 'com.example.my-package' from AndroidManifest.xml is not a"
                                    + " valid Java package name as 'my-package' is not a valid"
                                    + " Java identifier.");
        }
    }

    @Test
    public void filterByAccessibility() {
        SymbolTable table =
                SymbolTable.builder()
                        .add(
                                new Symbol.NormalSymbol(
                                        ResourceType.DRAWABLE, "img", 0, ResourceVisibility.PUBLIC))
                        .add(
                                new Symbol.NormalSymbol(
                                        ResourceType.ID,
                                        "bar",
                                        0,
                                        ResourceVisibility.PRIVATE_XML_ONLY))
                        .add(
                                new Symbol.NormalSymbol(
                                        ResourceType.STRING, "beep", 0, ResourceVisibility.PRIVATE))
                        .add(
                                new Symbol.NormalSymbol(
                                        ResourceType.STRING,
                                        "foo",
                                        0,
                                        ResourceVisibility.PRIVATE_XML_ONLY))
                        .add(
                                new Symbol.NormalSymbol(
                                        ResourceType.TRANSITION, "t", 0, ResourceVisibility.PUBLIC))
                        .add(
                                new Symbol.NormalSymbol(
                                        ResourceType.XML, "xml", 0, ResourceVisibility.PUBLIC))
                        .build();

        assertThat(table.getSymbolByVisibility(ResourceVisibility.PRIVATE_XML_ONLY)).hasSize(2);
        assertThat(table.getSymbolByVisibility(ResourceVisibility.PRIVATE)).hasSize(1);
        assertThat(table.getSymbolByVisibility(ResourceVisibility.PUBLIC)).hasSize(3);
    }

    @Test
    public void testContainsSymbols() {
        SymbolTable table =
                SymbolTable.builder()
                        .add(createSymbol("string", "s1", "int", "0"))
                        .add(createSymbol("integer", "s1", "int", "0"))
                        .add(
                                createSymbol(
                                        "styleable",
                                        "s1",
                                        "int[]",
                                        "{ 0, 0, 0, 0}",
                                        ImmutableList.of(
                                                "android_name",
                                                "android_type",
                                                "name",
                                                "description")))
                        .add(
                                createSymbol(
                                        "styleable",
                                        "s1_s2",
                                        "int[]",
                                        "{ 0, 0}",
                                        ImmutableList.of("android_name", "type")))
                        .add(
                                createSymbol(
                                        "styleable",
                                        "s3",
                                        "int[]",
                                        "{ 0, 0}",
                                        ImmutableList.of("android:color", "android:image")))
                        .build();

        // Basic checks first.
        assertThat(table.containsSymbol(ResourceType.STRING, "s1")).isTrue();
        assertThat(table.containsSymbol(ResourceType.INTEGER, "s1")).isTrue();
        assertThat(table.containsSymbol(ResourceType.STYLEABLE, "s1")).isTrue();
        assertThat(table.containsSymbol(ResourceType.ID, "s1")).isFalse();

        // Check various combination of styleables' names.
        assertThat(table.containsSymbol(ResourceType.STYLEABLE, "s1_android_name")).isTrue();
        assertThat(table.containsSymbol(ResourceType.STYLEABLE, "s1_android_type")).isTrue();
        assertThat(table.containsSymbol(ResourceType.STYLEABLE, "s1_android_description"))
                .isFalse();
        assertThat(table.containsSymbol(ResourceType.STYLEABLE, "s1_name")).isTrue();
        assertThat(table.containsSymbol(ResourceType.STYLEABLE, "s1_type")).isFalse();
        assertThat(table.containsSymbol(ResourceType.STYLEABLE, "s1_description")).isTrue();
        assertThat(table.containsSymbol(ResourceType.STYLEABLE, "s1_")).isFalse();
        assertThat(table.containsSymbol(ResourceType.STYLEABLE, "s1_android")).isFalse();
        assertThat(table.containsSymbol(ResourceType.STYLEABLE, "s1_s2")).isTrue();
        assertThat(table.containsSymbol(ResourceType.STYLEABLE, "s1_s2_name")).isFalse();
        assertThat(table.containsSymbol(ResourceType.STYLEABLE, "s1_s2_android_name")).isTrue();
        assertThat(table.containsSymbol(ResourceType.STYLEABLE, "s2_android_name")).isFalse();
        assertThat(table.containsSymbol(ResourceType.STYLEABLE, "s1_s2_type")).isTrue();
        assertThat(table.containsSymbol(ResourceType.STYLEABLE, "s1_s2_description")).isFalse();
        assertThat(table.containsSymbol(ResourceType.STYLEABLE, "s3_android_color")).isTrue();
        assertThat(table.containsSymbol(ResourceType.STYLEABLE, "s3_android:color")).isTrue();
        assertThat(table.containsSymbol(ResourceType.STYLEABLE, "s3_android_name")).isFalse();
    }
}
