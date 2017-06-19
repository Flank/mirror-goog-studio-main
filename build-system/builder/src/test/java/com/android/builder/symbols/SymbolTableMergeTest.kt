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

/**
 * Utility function to make the test below more readable since there's no static import of methods.
 */
fun symbol(
        resourceType: String,
        name: String,
        javaType: String,
        numericValue: Int) = SymbolTestUtils.createSymbol(resourceType, name, javaType, numericValue)

/**
 * Utility function to make the test below more readable since there's no static import of methods.
 */
fun symbol(
        resourceType: String,
        name: String,
        javaType: String,
        value: String) = SymbolTestUtils.createSymbol(resourceType, name, javaType, value)


/**
 * these are tests for [SymbolUtils.mergeAndRenumberSymbols]
 */
class SymbolTableMergeTest {

    @Test
    fun testBasicMerge() {
        val table1 = SymbolTable.builder()
                .tablePackage("table1")
                .add(symbol("dimen", "a", "int", 1))
                .build()

        val table2 = SymbolTable.builder()
                .tablePackage("table2")
                .add(symbol("attr", "b", "int", 2))
                .build()

        val result = SymbolUtils.mergeAndRenumberSymbols("",
                table1, ImmutableSet.of(table2))

        val expected = SymbolTable.builder()
                .add(symbol("dimen", "a", "int", 0x7f_08_0001))
                .add(symbol("attr", "b", "int", 0x7f_04_0001))
                .build()

        Truth.assertThat(result).isEqualTo(expected)

    }

    @Test
    fun testMergeWithDuplicated() {
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
                table1, ImmutableSet.of(table2))

        val expected = SymbolTable.builder()
                .add(symbol("dimen", "a", "int", 0x7f_08_0001))
                .add(symbol("attr", "b", "int", 0x7f_04_0001))
                .build()

        Truth.assertThat(result).isEqualTo(expected)

    }

    @Test
    fun testMergeWithSimpleStyleable() {
        val table1 = SymbolTable.builder()
                .tablePackage("table1")
                .add(symbol("dimen", "a", "int", 0))
                .build()

        val table2 = SymbolTable.builder()
                .tablePackage("table2")
                .add(symbol("attr", "a1", "int", 12))
                .add(symbol("attr", "a2", "int", 42))
                .add(symbol("styleable", "style1", "int[]", "{12,42}"))
                .add(symbol("styleable", "style1_a1", "int", 55))
                .add(symbol("styleable", "style1_a2", "int", 89))
                .build()

        val result = SymbolUtils.mergeAndRenumberSymbols("",
                table1, ImmutableSet.of(table2))

        val expected = SymbolTable.builder()
                .add(symbol("dimen", "a", "int", 0x7f_08_0001))
                .add(symbol("attr", "a1", "int", 0x7f_04_0001))
                .add(symbol("attr", "a2", "int", 0x7f_04_0002))
                .add(symbol("styleable", "style1", "int[]", "{0x7f040001,0x7f040002}"))
                .add(symbol("styleable", "style1_a1", "int", 0))
                .add(symbol("styleable", "style1_a2", "int", 1))
                .build()

        Truth.assertThat(result).isEqualTo(expected)

    }

    @Test
    fun testMergeWithStyleableWithAttrFromAnotherTable() {
        val table1 = SymbolTable.builder()
                .tablePackage("table1")
                .add(symbol("attr", "a1", "int", 27))
                .add(symbol("attr", "a2", "int", 35))
                .build()

        val table2 = SymbolTable.builder()
                .tablePackage("table2")
                .add(symbol("attr", "a1", "int", 12))
                .add(symbol("attr", "a2", "int", 42))
                .add(symbol("styleable", "style1", "int[]", "{12,42}"))
                .add(symbol("styleable", "style1_a1", "int", 55))
                .add(symbol("styleable", "style1_a2", "int", 89))
                .build()

        val result = SymbolUtils.mergeAndRenumberSymbols("",
                table1, ImmutableSet.of(table2))

        val expected = SymbolTable.builder()
                .add(symbol("attr", "a1", "int", 0x7f_04_0001))
                .add(symbol("attr", "a2", "int", 0x7f_04_0002))
                .add(symbol("styleable", "style1", "int[]", "{0x7f040001,0x7f040002}"))
                .add(symbol("styleable", "style1_a1", "int", 0))
                .add(symbol("styleable", "style1_a2", "int", 1))
                .build()

        Truth.assertThat(result).isEqualTo(expected)

    }

    @Test
    fun testMergeWithRedefinedStyleable() {
        val table1 = SymbolTable.builder()
                .tablePackage("table1")
                .add(symbol("attr", "a1", "int", 27))
                .add(symbol("attr", "a2", "int", 35))
                .add(symbol("styleable", "style1", "int[]", "{27,35}"))
                .add(symbol("styleable", "style1_a1", "int", 33))
                .add(symbol("styleable", "style1_a2", "int", 66))
                .build()

        val table2 = SymbolTable.builder()
                .tablePackage("table2")
                .add(symbol("attr", "b1", "int", 12))
                .add(symbol("attr", "b2", "int", 42))
                .add(symbol("styleable", "style1", "int[]", "{12,42}"))
                .add(symbol("styleable", "style1_b1", "int", 55))
                .add(symbol("styleable", "style1_b2", "int", 89))
                .build()

        val result = SymbolUtils.mergeAndRenumberSymbols("",
                table1, ImmutableSet.of(table2))

        val expected = SymbolTable.builder()
                .add(symbol("attr", "a1", "int", 0x7f_04_0001))
                .add(symbol("attr", "a2", "int", 0x7f_04_0002))
                .add(symbol("attr", "b1", "int", 0x7f_04_0003))
                .add(symbol("attr", "b2", "int", 0x7f_04_0004))
                .add(symbol("styleable", "style1", "int[]", "{0x7f040001,0x7f040002,0x7f040003,0x7f040004}"))
                .add(symbol("styleable", "style1_a1", "int", 0))
                .add(symbol("styleable", "style1_a2", "int", 1))
                .add(symbol("styleable", "style1_b1", "int", 2))
                .add(symbol("styleable", "style1_b2", "int", 3))
                .build()

        Truth.assertThat(result).isEqualTo(expected)

    }

}