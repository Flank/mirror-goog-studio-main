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

import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.SourceVersion;

/**
 * List of symbols identifying resources in an Android application. Symbol tables do not only exist
 * for applications: they can be used for other building blocks for applications, such as libraries
 * or atoms.
 *
 * <p>A symbol table keeps a list of instances of {@link Symbol}, each one with a unique pair class
 * / name. Tables have one main attribute: a package name. This should be unique and are used to
 * generate the {@code R.java} file.
 */
@AutoValue
public abstract class SymbolTable {

    /** Name of {@code R} class. */
    public static final String R_CLASS_NAME = "R";

    /**
     * Creates a new symbol table.
     *
     * @param tablePackage the table package
     * @param symbols the table symbol mapped by {@link #key(Symbol)}
     */
    private static SymbolTable createSymbolTable(
            @NonNull String tablePackage, @NonNull Map<String, Symbol> symbols) {
        return new AutoValue_SymbolTable(tablePackage, ImmutableMap.copyOf(symbols));
    }

    /**
     * Obtains the table package. See class description.
     *
     * @return the table package
     */
    @NonNull
    public abstract String getTablePackage();

    public abstract ImmutableMap<String, Symbol> getSymbols();
    /**
     * Obtains a unique key for a symbol.
     *
     * @param symbol the symbol
     * @return the unique ID
     */
    @NonNull
    private static String key(@NonNull Symbol symbol) {
        return key(symbol.getResourceType(), symbol.getName());
    }

    /**
     * Obtains a unique key for a resource type / name.
     *
     * @param resourceType the resource type
     * @param name the name
     * @return the unique ID
     */
    @NonNull
    private static String key(@NonNull ResourceType resourceType, @NonNull String name) {
        return resourceType.name() + " " + name;
    }

    /**
     * Checks if the table contains a symbol with the given resource type / name.
     *
     * @param resourceType the resource type
     * @param name the name
     * @return does the table contain a symbol with the given resource type / name?
     */
    public boolean contains(@NonNull ResourceType resourceType, @NonNull String name) {
        return getSymbols().containsKey(key(resourceType, name));
    }

    /**
     * Obtains all symbols in the table.
     *
     * @return all symbols
     */
    @NonNull
    public ImmutableCollection<Symbol> allSymbols() {
        return getSymbols().values();
    }

    /**
     * Produces a subset of this symbol table that has the symbols with resource type / name defined
     * in {@code filter}. In other words, a symbol {@code s} will exist in the result if and only
     * if {@code s} exists in {@code this} and there is a symbol {@code s1} in {@code table}
     * such that {@code s.resourceType == s1.resourceType && s.name == s1.name}.
     *
     * @param table the filter table
     * @return the filter result; this table will have the same name and package as this one
     */
    @NonNull
    public SymbolTable filter(@NonNull SymbolTable table) {
        SymbolTable.Builder stb = builder();
        stb.tablePackage(getTablePackage());

        for (Map.Entry<String, Symbol> e : getSymbols().entrySet()) {
            if (table.getSymbols().containsKey(e.getKey())) {
                stb.symbols.put(e.getKey(), e.getValue());
            }
        }

        return stb.build();
    }

    /**
     * Short for merging {@code this} and {@code m}.
     *
     * @param m the table to add to {@code this}
     * @return the result of merging {@code this} with {@code m}
     */
    @NonNull
    public SymbolTable merge(@NonNull SymbolTable m) {
        return merge(Arrays.asList(this, m));
    }

    /**
     * Builds a new symbol table that has the same symbols as this one, but was renamed with
     * the given package.
     *
     * @param tablePackage the table package
     * @return the new renamed symbol table
     */
    @NonNull
    public SymbolTable rename(@NonNull String tablePackage) {
        return builder()
                .tablePackage(tablePackage)
                .addAll(allSymbols())
                .build();
    }

    /**
     * Merges a list of tables into a single table. The merge is order-sensitive: when multiple
     * symbols with the same class / name exist in multiple tables, the first one will be used.
     *
     * @param tables the tables to merge
     * @return the table with the result of the merge; this table will have the package of
     * the first table in {@code tables}, or the default one if there are no tables in
     * {@code tables}
     */
    @NonNull
    public static SymbolTable merge(@NonNull List<SymbolTable> tables) {
        SymbolTable.Builder builder = SymbolTable.builder();

        boolean first = true;
        for (SymbolTable t : tables) {

            if (first) {
                builder.tablePackage(t.getTablePackage());
                first = false;
            }

            for (Symbol s : t.allSymbols()) {
                if (!builder.contains(s)) {
                    builder.add(s);
                }
            }
        }

        return builder.build();
    }

    /**
     * Creates a new builder to create a {@link SymbolTable}.
     *
     * @return a builder
     */
    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    /** Builder that creates a symbol table. */
    public static final class Builder {

        /**
         * Current table package.
         */
        @NonNull
        private String tablePackage;

        /**
         * Symbols to be added to the table.
         */
        @NonNull
        private Map<String, Symbol> symbols;

        /**
         * Creates a new builder.
         */
        private Builder() {
            symbols = new HashMap<>();
            tablePackage = "";
        }

        /**
         * Adds a symbol to the table to be built. The table must not contains a symbol with the
         * same resource type and name.
         *
         * @param symbol the symbol to add
         * @return {@code this} for use with fluent-style notation
         */
        public Builder add(@NonNull Symbol symbol) {
            String key = key(symbol);
            if (symbols.containsKey(key)) {
                throw new IllegalArgumentException(
                        "Duplicate symbol in table with resource "
                                + "type '"
                                + symbol.getResourceType()
                                + "' and symbol name '"
                                + symbol.getName()
                                + "'");
            }

            symbols.put(key, symbol);
            return this;
        }

        /**
         * Adds all symbols in the given collection to the table. This is semantically equivalent
         * to calling {@link #add(Symbol)} for all symbols.
         *
         * @param symbols the symbols to add
         * @return {@code this} for use with fluent-style notation
         */
        public Builder addAll(@NonNull Collection<Symbol> symbols) {
            symbols.forEach(this::add);
            return this;
        }

        /**
         * Sets the table package. See {@link SymbolTable} description.
         *
         * @param tablePackage; must be a valid java package name
         * @return {@code this} for use with fluent-style notation
         */
        public Builder tablePackage(@NonNull String tablePackage) {
            if (!tablePackage.isEmpty()) {
                Arrays.asList(tablePackage.split("\\."))
                        .forEach(p -> Preconditions.checkArgument(SourceVersion.isIdentifier(p)));
            }

            this.tablePackage = tablePackage;
            return this;
        }

        /**
         * Checks if a symbol with the same resource type and name as {@code symbol} have been
         * added.
         *
         * @param symbol the symbol to check
         * @return has a symbol with the same resource type / name been added?
         */
        public boolean contains(@NonNull Symbol symbol) {
            return symbols.containsKey(key(symbol));
        }

        /**
         * Builds a symbol table with all symbols added.
         *
         * @return the symbol table
         */
        @NonNull
        public SymbolTable build() {
            return createSymbolTable(tablePackage, symbols);
        }
    }
}
