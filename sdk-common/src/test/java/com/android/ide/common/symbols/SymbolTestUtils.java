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

import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.List;

public final class SymbolTestUtils {
    private SymbolTestUtils() {}

    /**
     * Creates a new symbol. The {@code name} of the symbol needs to be a valid sanitized resource
     * name. See {@link SymbolUtils#canonicalizeValueResourceName} method and apply it beforehand
     * when necessary.
     *
     * <p>The parameter {@code resourceType} needs to correspond to a valid {@link ResourceType}.
     *
     * @param resourceType the resource type of the symbol
     * @param name the sanitized name of the symbol
     * @param javaType the java type of the symbol
     * @param value the value of the symbol
     * @param styleableChildren children of declare styleables, otherwise empty
     */
    @NonNull
    public static Symbol createSymbol(
            @NonNull String resourceType,
            @NonNull String name,
            @NonNull String javaType,
            @NonNull String value,
            @NonNull List<String> styleableChildren) {
        boolean maybeDefinition = false;
        if (resourceType.endsWith("?")) {
            Preconditions.checkState(
                    resourceType.equals("attr?"), "Only attributes can have maybe definitions");
            maybeDefinition = true;
            resourceType = ResourceType.ATTR.getName();
        }
        ResourceType type =
                Preconditions.checkNotNull(
                        ResourceType.fromClassName(resourceType),
                        "Invalid resource type %s",
                        resourceType);
        if (javaType.equals("int[]")) {
            Preconditions.checkState(
                    type == ResourceType.STYLEABLE, "Only styleables may have type int[]");
            return Symbol.createAndValidateStyleableSymbol(
                    name,
                    SymbolUtils.parseArrayLiteral(styleableChildren.size(), value),
                    ImmutableList.copyOf(styleableChildren));
        }
        Preconditions.checkState(type != ResourceType.STYLEABLE, "Styleables must have type int[]");

        int intValue;
        try {
            intValue = SymbolUtils.valueStringToInt(value);
        } catch (NumberFormatException e) {
            intValue = -1;
        }

        return Symbol.createAndValidateSymbol(type, name, intValue, maybeDefinition);
    }

    /** @see #createSymbol(String, String, String, String, List) */
    @NonNull
    public static Symbol createSymbol(
            @NonNull String resourceType,
            @NonNull String name,
            @NonNull String javaType,
            @NonNull String value) {
        return createSymbol(resourceType, name, javaType, value, Symbol.NO_CHILDREN);
    }

    /** @see #createSymbol(String, String, String, String, List) */
    @NonNull
    public static Symbol createSymbol(
            @NonNull String resourceType,
            @NonNull String name,
            @NonNull String javaType,
            int numericValue) {
        if ("styleable".equals(resourceType) && "int".equals(javaType)) {
            return createSymbol(resourceType, name, javaType, Integer.toString(numericValue));
        }
        return createSymbol(resourceType, name, javaType, "0x" + Integer.toHexString(numericValue));
    }
}
