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

package com.android.builder.symbols;

import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.google.common.base.Preconditions;

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
     */
    public static Symbol createSymbol(
            @NonNull String resourceType,
            @NonNull String name,
            @NonNull String javaType,
            @NonNull String value) {
        return Symbol.createSymbol(
                Preconditions.checkNotNull(
                        ResourceType.getEnum(resourceType),
                        "Invalid resource type %s",
                        resourceType),
                name,
                Preconditions.checkNotNull(
                        SymbolJavaType.getEnum(javaType),
                        "Invalid resource java type %s",
                        javaType),
                value);
    }

    /** @see #createSymbol(String, String, String, String) */
    public static Symbol createSymbol(
            @NonNull String resourceType,
            @NonNull String name,
            @NonNull String javaType,
            int numericValue) {
        return createSymbol(resourceType, name, javaType, "0x" + Integer.toHexString(numericValue));
    }
}
