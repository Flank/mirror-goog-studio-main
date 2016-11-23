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
import com.android.builder.dependency.HashCodeUtils;
import com.google.common.base.Preconditions;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.lang.model.SourceVersion;

/**
 * List of symbols identifying resources in an Android application. Symbol tables do not only
 * exist for applications: they can be used for other building blocks for applications, such as
 * libraries or atoms.
 *
 * <p>A symbol table keeps a list of instances of {@link Symbol}, each one with a unique pair
 * class / name. Tables have two main attributes: a name and package. These should be unique and
 * are used to generate the {@code R.java} file. Actually, the name of the table is the class name
 * and the package is the java package so, traditionally, all symbol tables are named {@code R}.
 */
public class SymbolTable {

    /**
     * The default name for a symbol table.
     */
    @NonNull
    public static final String DEFAULT_NAME = "R";

    /**
     * All symbols mapped by IDs (see {@link #key(Symbol)}.
     */
    @NonNull
    private Map<String, Symbol> symbols;

    /**
     * The table name.
     */
    @NonNull
    private String tableName;

    /**
     * The table package. An empty package means the default package.
     */
    @NonNull
    private String tablePackage;

    /**
     * Creates a new, empty, symbol table.
     */
    public SymbolTable() {
        symbols = new HashMap<>();
        tableName = DEFAULT_NAME;
        tablePackage = "";
    }

    /**
     * Obtains the table name. See class description.
     *
     * @return the table name
     */
    @NonNull
    public String getTableName() {
        return tableName;
    }

    /**
     * Sets the table name. See class description.
     *
     * @param tableName the table name; must be a valid java identifier
     */
    public void setTableName(@NonNull String tableName) {
        Preconditions.checkArgument(SourceVersion.isIdentifier(tableName));

        this.tableName = tableName;
    }

    /**
     * Obtains the table package. See class description.
     *
     * @return the table package
     */
    @NonNull
    public String getTablePackage() {
        return tablePackage;
    }

    /**
     * Sets the table package. See class description.
     *
     * @param tablePackage; must be a valid java package name
     */
    public void setTablePackage(@NonNull String tablePackage) {
        if (!tablePackage.isEmpty()) {
            Arrays.asList(tablePackage.split("\\."))
                    .forEach(p -> Preconditions.checkArgument(SourceVersion.isIdentifier(p)));
        }

        this.tablePackage = tablePackage;
    }

    /**
     * Obtains a unique key for a symbol.
     *
     * @param symbol the symbol
     * @return the unique ID
     */
    @NonNull
    private static String key(@NonNull Symbol symbol) {
        return symbol.getResourceType() + " " + symbol.getName();
    }

    /**
     * Obtains all symbols in the table.
     *
     * @return all symbols
     */
    @NonNull
    public Set<Symbol> allSymbols() {
        return new HashSet<>(symbols.values());
    }

    /**
     * Adds a symbol to the table.
     *
     * @param symbol the symbol to add
     */
    public void add(@NonNull Symbol symbol) {
        symbols.put(key(symbol), symbol);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof SymbolTable)) {
            return false;
        }

        SymbolTable other = (SymbolTable) obj;
        return Objects.equals(symbols, other.symbols)
                && Objects.equals(tableName, other.tableName)
                && Objects.equals(tablePackage, other.tablePackage);
    }

    @Override
    public int hashCode() {
        return HashCodeUtils.hashCode(symbols, tableName, tablePackage);
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
        SymbolTable st = new SymbolTable();
        st.setTableName(tableName);
        st.setTablePackage(tablePackage);

        for (Map.Entry<String, Symbol> e : symbols.entrySet()) {
            if (table.symbols.containsKey(e.getKey())) {
                st.symbols.put(e.getKey(), e.getValue());
            }
        }

        return st;
    }

    /**
     * Merges a list of tables into a single table. The merge is order-sensitive: when multiple
     * symbols with the same class / name exist in multiple tables, the first one will be used.
     *
     * @param result the table that will hold the result; symbols that already exist in this table,
     * if any, will not be changed by the merge and symbols in {@code tables} with the same class
     * and name will be ignored
     * @param tables the tables to merge
     */
    public static void merge(@NonNull SymbolTable result, @NonNull List<SymbolTable> tables) {
        for (SymbolTable t : tables) {
            for (Map.Entry<String, Symbol> e : t.symbols.entrySet()) {
                if (!result.symbols.containsKey(e.getKey())) {
                    result.symbols.put(e.getKey(), e.getValue());
                }
            }
        }
    }
}
