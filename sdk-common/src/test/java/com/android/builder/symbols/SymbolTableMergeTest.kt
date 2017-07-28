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

package com.android.builder.symbols


import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth
import org.junit.Test
import com.android.builder.symbols.SymbolTestUtils.createSymbol as symbol


/**
 * these are tests for [SymbolUtils.mergeAndRenumberSymbols]
 */
class SymbolTableMergeTest {

    @Test
    fun testBasicMerge() {
        val androidSymbols = SymbolTable.builder()
                .tablePackage("android")
                .build()

        val table1 = SymbolTable.builder()
                .tablePackage("table1")
                .add(symbol("dimen", "a", "int", 1))
                .build()

        val table2 = SymbolTable.builder()
                .tablePackage("table2")
                .add(symbol("attr", "b", "int", 2))
                .build()

        val result = SymbolUtils.mergeAndRenumberSymbols("",
                table1, ImmutableSet.of(table2), androidSymbols)

        val expected = SymbolTable.builder()
                .add(symbol("dimen", "a", "int", 0x7f_08_0001))
                .add(symbol("attr", "b", "int", 0x7f_04_0001))
                .build()

        Truth.assertThat(result).isEqualTo(expected)
    }

    @Test
    fun testMergeWithDuplicated() {
        val androidSymbols = SymbolTable.builder()
                .tablePackage("android")
                .build()
        
        val table1 = SymbolTable.builder()
                .tablePackage("table1")
                .add(symbol("dimen", "a", "int", 0))
                .add(symbol("attr", "b", "int", 0))
                .build()

        val table2 = SymbolTable.builder()
                .tablePackage("table2")
                .add(symbol("attr", "b", "int", 0))
                .build()

        val result = SymbolUtils.mergeAndRenumberSymbols("",
                table1, ImmutableSet.of(table2), androidSymbols)

        val expected = SymbolTable.builder()
                .add(symbol("dimen", "a", "int", 0x7f_08_0001))
                .add(symbol("attr", "b", "int", 0x7f_04_0001))
                .build()

        Truth.assertThat(result).isEqualTo(expected)
    }

    @Test
    fun testMergeWithSimpleStyleable() {
        val androidSymbols = SymbolTable.builder()
                .tablePackage("android")
                .build()

        val table1 = SymbolTable.builder()
                .tablePackage("table1")
                .add(symbol("dimen", "a", "int", 0))
                .build()

        val table2 = SymbolTable.builder()
                .tablePackage("table2")
                .add(symbol("attr", "a1", "int", 12))
                .add(symbol("attr", "a2", "int", 42))
                .add(symbol("styleable", "style1", "int[]", "{ 12, 42 }", listOf("a1", "a2")))
                .build()

        val result = SymbolUtils.mergeAndRenumberSymbols("",
                table1, ImmutableSet.of(table2), androidSymbols)

        val expected = SymbolTable.builder()
                .add(symbol("dimen", "a", "int", 0x7f_08_0001))
                .add(symbol("attr", "a1", "int", 0x7f_04_0001))
                .add(symbol("attr", "a2", "int", 0x7f_04_0002))
                .add(symbol("styleable", "style1", "int[]", "{ 0x7f040001, 0x7f040002 }", listOf("a1", "a2")))
                .build()

        Truth.assertThat(result).isEqualTo(expected)
    }

    @Test
    fun testMergeWithStyleableWithAttrFromAnotherTable() {
        val androidSymbols = SymbolTable.builder()
                .tablePackage("android")
                .build()

        val table1 = SymbolTable.builder()
                .tablePackage("table1")
                .add(symbol("attr", "a1", "int", 27))
                .add(symbol("attr", "a2", "int", 35))
                .build()

        val table2 = SymbolTable.builder()
                .tablePackage("table2")
                .add(symbol("attr", "a1", "int", 12))
                .add(symbol("attr", "a2", "int", 42))
                .add(symbol("styleable", "style1", "int[]", "{ 12, 42 }", listOf("a1", "a2")))
                .build()

        val result = SymbolUtils.mergeAndRenumberSymbols("",
                table1, ImmutableSet.of(table2), androidSymbols)

        val expected = SymbolTable.builder()
                .add(symbol("attr", "a1", "int", 0x7f_04_0001))
                .add(symbol("attr", "a2", "int", 0x7f_04_0002))
                .add(symbol("styleable", "style1", "int[]", "{ 0x7f040001, 0x7f040002 }", listOf("a1", "a2")))
                .build()

        Truth.assertThat(result).isEqualTo(expected)
    }

    @Test
    fun testMergeWithStyleableWithAndroidAttr() {
        val androidSymbols = SymbolTable.builder()
                .tablePackage("android")
                .add(symbol("attr", "foo", "int", 0x10_04_002A))
                .build()

        val table1 = SymbolTable.builder()
                .tablePackage("table1")
                .build()

        val table2 = SymbolTable.builder()
                .tablePackage("table2")
                .add(symbol("attr", "zz", "int", 42))
                .add(symbol("styleable", "style1", "int[]", "{ 12, 42 }", listOf("android:foo", "zz")))
                .build()

        val result = SymbolUtils.mergeAndRenumberSymbols("",
                table1, ImmutableSet.of(table2), androidSymbols)

        val expected = SymbolTable.builder()
                .add(symbol("attr", "zz", "int", 0x7f_04_0001))
                .add(symbol("styleable", "style1", "int[]", "{ 0x1004002a, 0x7f040001 }", listOf("android:foo", "zz")))
                .build()

        Truth.assertThat(result).isEqualTo(expected)
    }

    @Test
    fun testMergeWithRedefinedStyleable() {
        val androidSymbols = SymbolTable.builder()
                .tablePackage("android")
                .build()

        val table1 = SymbolTable.builder()
                .tablePackage("table1")
                .add(symbol("attr", "a1", "int", 27))
                .add(symbol("attr", "a2", "int", 35))
                .add(symbol("styleable", "style1", "int[]", "{ 27, 35 }", listOf("a1", "a2")))
                .build()

        val table2 = SymbolTable.builder()
                .tablePackage("table2")
                .add(symbol("attr", "b1", "int", 12))
                .add(symbol("attr", "b2", "int", 42))
                .add(symbol("styleable", "style1", "int[]", "{ 12, 42 }", listOf("b1", "b2")))
                .build()

        val result = SymbolUtils.mergeAndRenumberSymbols("",
                table1, ImmutableSet.of(table2), androidSymbols)

        val expected = SymbolTable.builder()
                .add(symbol("attr", "a1", "int", 0x7f_04_0001))
                .add(symbol("attr", "a2", "int", 0x7f_04_0002))
                .add(symbol("attr", "b1", "int", 0x7f_04_0003))
                .add(symbol("attr", "b2", "int", 0x7f_04_0004))
                .add(symbol("styleable", "style1", "int[]",
                        "{ 0x7f040001, 0x7f040002, 0x7f040003, 0x7f040004 }",
                        listOf("a1", "a2", "b1", "b2")))
                .build()

        Truth.assertThat(result).isEqualTo(expected)
    }

    @Test
    fun testMergeWithRedefinedStyleableAndNestedChildren() {
        val androidSymbols = SymbolTable.builder()
                .tablePackage("android")
                .build()

        val table1 = SymbolTable.builder()
                .tablePackage("table1")
                .add(symbol("attr", "a1", "int", 27))
                .add(symbol("attr", "a2", "int", 35))
                .add(symbol("styleable", "style1", "int[]", "{ 27 }", listOf("a1")))
                .build()

        val table2 = SymbolTable.builder()
                .tablePackage("table2")
                .add(symbol("attr", "b1", "int", 12))
                .add(symbol("attr", "b2", "int", 42))
                .add(symbol("styleable", "style1", "int[]", "{ 12, 42 }", listOf("b1", "b2")))
                .build()

        val result = SymbolUtils.mergeAndRenumberSymbols("",
                table1, ImmutableSet.of(table2), androidSymbols)

        val expected = SymbolTable.builder()
                .add(symbol("attr", "a1", "int", 0x7f_04_0001))
                .add(symbol("attr", "a2", "int", 0x7f_04_0002))
                .add(symbol("attr", "b1", "int", 0x7f_04_0003))
                .add(symbol("attr", "b2", "int", 0x7f_04_0004))
                .add(symbol("styleable", "style1", "int[]",
                        "{ 0x7f040001, 0x7f040003, 0x7f040004 }",
                        listOf("a1", "b1", "b2")))
                .build()

        Truth.assertThat(result).isEqualTo(expected)
    }

    @Test
    fun testDifferentIdsForAttributesAndStyleables() {
        val androidSymbols = SymbolTable.builder()
                .tablePackage("android")
                .build()

        // The incorrect IDs actually look like this: the attribute and the styleable have different
        // values for their IDs, while the declare styleable has the same values as the children
        // (instead of children having index values).
        val table1 = SymbolTable.builder()
                .tablePackage("table1")
                .add(symbol("attr", "a1", "int", 0))
                .add(symbol("attr", "a2", "int", 1))
                .add(symbol("styleable", "style1", "int[]", "{2,3}", listOf("a1", "a2")))
                .build()

        val table2 = SymbolTable.builder()
                .tablePackage("table2")
                .add(symbol("attr", "b1", "int", 0))
                .add(symbol("attr", "b2", "int", 1))
                .add(symbol("styleable", "style1", "int[]", "{2,3}", listOf("b1", "b2")))
                .build()

        val result = SymbolUtils.mergeAndRenumberSymbols("",
                table1, ImmutableSet.of(table2), androidSymbols)

        val expected = SymbolTable.builder()
                .add(symbol("attr", "a1", "int", 0x7f_04_0001))
                .add(symbol("attr", "a2", "int", 0x7f_04_0002))
                .add(symbol("attr", "b1", "int", 0x7f_04_0003))
                .add(symbol("attr", "b2", "int", 0x7f_04_0004))
                .add(symbol("styleable", "style1", "int[]",
                        "{ 0x7f040001, 0x7f040002, 0x7f040003, 0x7f040004 }",
                        listOf("a1", "a2", "b1", "b2")))
                .build()

        Truth.assertThat(result).isEqualTo(expected)
    }
}