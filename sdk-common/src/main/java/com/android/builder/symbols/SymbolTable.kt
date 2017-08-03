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

package com.android.builder.symbols

import com.android.annotations.concurrency.Immutable
import com.android.resources.ResourceType
import com.google.common.base.Splitter
import com.google.common.collect.ImmutableTable
import com.google.common.collect.Maps
import com.google.common.collect.Table
import com.google.common.collect.Tables
import java.util.Arrays
import java.util.HashMap
import java.util.HashSet
import javax.lang.model.SourceVersion

/**
 * List of symbols identifying resources in an Android application. Symbol tables do not only exist
 * for applications: they can be used for other building blocks for applications, such as libraries
 * or atoms.
 *
 * A symbol table keeps a list of instances of [Symbol], each one with a unique pair class
 * / name. Tables have one main attribute: a package name. This should be unique and are used to
 * generate the `R.java` file.
 */
@Immutable
abstract class SymbolTable protected constructor() {

    abstract val tablePackage: String
    abstract val symbols: ImmutableTable<ResourceType, String, Symbol>

    private data class SymbolTableImpl(
            override val tablePackage: String,
            override val symbols: ImmutableTable<ResourceType, String, Symbol>) : SymbolTable()

    /**
     * Produces a subset of this symbol table that has the symbols with resource type / name defined
     * in `filter`. In other words, a symbol `s` will exist in the result if and only
     * if `s` exists in `this` and there is a symbol `s1` in `table`
     * such that `s.resourceType == s1.resourceType && s.name == s1.name`.
     *
     * @param table the filter table
     * @return the filter result; this table will have the same name and package as this one
     */
    fun filter(table: SymbolTable): SymbolTable {
        val builder = ImmutableTable.builder<ResourceType, String, Symbol>()

        for (resourceType in symbols.rowKeySet()) {
            val symbols = symbols.row(resourceType).values
            val filteringSymbolNames = table.symbols.row(resourceType).keys

            for (symbol in symbols) {
                if (filteringSymbolNames.contains(symbol.name)) {
                    builder.put(resourceType, symbol.name, symbol)
                }
            }
        }

        return SymbolTableImpl(tablePackage, builder.build())
    }

    /**
     * Short for merging `this` and `m`.
     *
     * @param m the table to add to `this`
     * @return the result of merging `this` with `m`
     */
    fun merge(m: SymbolTable): SymbolTable {
        return merge(Arrays.asList(this, m))
    }

    /**
     * Builds a new symbol table that has the same symbols as this one, but was renamed with
     * the given package.
     *
     * @param tablePackage the table package
     * @return the new renamed symbol table
     */
    fun rename(tablePackage: String): SymbolTable {
        return SymbolTableImpl(tablePackage, symbols)
    }

    /** Builder that creates a symbol table.  */
    class Builder {

        private var tablePackage = ""

        private val symbols: Table<ResourceType, String, Symbol> =
                Tables.newCustomTable(
                        Maps.newEnumMap<ResourceType, Map<String, Symbol>>(ResourceType::class.java),
                        { HashMap() })

        /**
         * Adds a symbol to the table to be built. The table must not contain a symbol with the same
         * resource type and name.
         *
         * @param symbol the symbol to add
         */
        fun add(symbol: Symbol): Builder {
            if (symbols.contains(symbol.resourceType, symbol.name)) {
                throw IllegalArgumentException(
                        "Duplicate symbol in table with resource type '${symbol.resourceType}' " +
                                "and symbol name '${symbol.name}'")
            }
            symbols.put(symbol.resourceType, symbol.name, symbol)
            return this
        }

        /**
         * Adds all symbols in the given collection to the table. This is semantically equivalent
         * to calling [.add] for all symbols.
         *
         * @param symbols the symbols to add
         */
        fun addAll(symbols: Collection<Symbol>): Builder {
            symbols.forEach { this.add(it) }
            return this
        }

        /**
         * Sets the table package. See `SymbolTable` description.
         *
         * @param tablePackage; must be a valid java package name
         */
        fun tablePackage(tablePackage: String): Builder {
            if (!tablePackage.isEmpty() && !SourceVersion.isName(tablePackage)) {
                for (segment in Splitter.on('.').split(tablePackage)) {
                    if (!SourceVersion.isIdentifier(segment)) {
                        throw IllegalArgumentException(
                                "Package '$tablePackage' from AndroidManifest.xml is not a valid " +
                                        "Java package name as '$segment' is not a valid Java " +
                                        "identifier.")
                    }
                    if (SourceVersion.isKeyword(segment)) {
                        throw IllegalArgumentException(
                                "Package '$tablePackage' from AndroidManifest.xml is not a valid " +
                                        "Java package name as '$segment' is a Java keyword.")
                    }
                }
                // Shouldn't happen.
                throw IllegalArgumentException(
                        "Package '$tablePackage' from AndroidManifest.xml is not a valid Java " +
                                "package name.")
            }
            this.tablePackage = tablePackage
            return this
        }

        /**
         * Checks if a symbol with the same resource type and name as `symbol` have been
         * added.
         *
         * @param symbol the symbol to check
         *
         * @return has a symbol with the same resource type / name been added?
         */
        operator fun contains(symbol: Symbol): Boolean {
            return contains(symbol.resourceType, symbol.name)
        }

        /**
         * Checks if the table contains a symbol with the given resource type / name.
         *
         * @param resourceType the resource type
         *
         * @param name the name
         *
         * @return does the table contain a symbol with the given resource type / name?
         */
        fun contains(resourceType: ResourceType, name: String): Boolean {
            return symbols.contains(resourceType, name)
        }

        /**
         * Returns the symbol form the table matching the provided symbol
         *
         * @param symbol the symbol
         */
        operator fun get(symbol: Symbol): Symbol? {
            return symbols.get(symbol.resourceType, symbol.name)
        }

        /**
         * Builds a symbol table with all symbols added.
         *
         * @return the symbol table
         */
        fun build(): SymbolTable {
            return SymbolTableImpl(tablePackage, ImmutableTable.copyOf(symbols))
        }
    }

    companion object {

        /**
         * Merges a list of tables into a single table. The merge is order-sensitive: when multiple
         * symbols with the same class / name exist in multiple tables, the first one will be used.
         *
         * @param tables the tables to merge
         *
         * @return the table with the result of the merge; this table will have the package of
         *  the first table in `tables`, or the default one if there are no tables in `tables`
         */
        @JvmStatic fun merge(tables: List<SymbolTable>): SymbolTable {
            val builder = ImmutableTable.builder<ResourceType, String, Symbol>()

            val present = HashSet<String>()

            for (resourceType in ResourceType.values()) {
                present.clear()
                for (t in tables) {
                    val tableSymbolMap = t.symbols.row(resourceType)
                    if (tableSymbolMap != null && !tableSymbolMap.isEmpty()) {
                        for (s in tableSymbolMap.values) {
                            val name = s.name
                            if (!present.contains(name)) {
                                present.add(name)
                                builder.put(resourceType, name, s)
                            }
                        }
                    }
                }
            }

            val packageName = if (tables.isEmpty()) "" else tables[0].tablePackage

            return SymbolTableImpl(packageName, builder.build())
        }

        /**
         * Creates a new builder to create a `SymbolTable`.
         *
         * @return a builder
         */
        @JvmStatic fun builder(): Builder {
            return Builder()
        }
    }
}
