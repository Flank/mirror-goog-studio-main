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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SymbolTable {

    /**
     * All symbols mapped by IDs (see {@link #key(Symbol)}.
     */
    @NonNull
    private Map<String, Symbol> symbols;

    /**
     * Creates a new, empty, symbol table.
     */
    public SymbolTable() {
        symbols = new HashMap<>();
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
        return Objects.equals(symbols, other.symbols);
    }

    @Override
    public int hashCode() {
        return HashCodeUtils.hashCode(symbols);
    }
}
