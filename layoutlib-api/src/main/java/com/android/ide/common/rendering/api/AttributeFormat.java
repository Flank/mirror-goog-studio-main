/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.ide.common.rendering.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

/** Formats of styleable attribute value. */
public enum AttributeFormat {
    BOOLEAN("boolean"),
    COLOR("color"),
    DIMENSION("dimension"),
    ENUM("enum"),
    FLAGS("flags"),
    FLOAT("float"),
    FRACTION("fraction"),
    INTEGER("integer"),
    REFERENCE("reference"),
    STRING("string");

    private final String name;

    AttributeFormat(@NonNull String name) {
        this.name = name;
    }

    /** Returns the name used for the format in XML. */
    @NonNull
    public String getName() {
        return name;
    }

    /**
     * Returns the format given its XML name.
     *
     * @param name the name used for the format in XML
     * @return the format, or null if the given name doesn't match any formats
     */
    @Nullable
    public static AttributeFormat fromName(@NonNull String name) {
        switch (name) {
            case "boolean":
                return BOOLEAN;
            case "color":
                return COLOR;
            case "dimension":
                return DIMENSION;
            case "enum":
                return ENUM;
            case "flags":
                return FLAGS;
            case "float":
                return FLOAT;
            case "fraction":
                return FRACTION;
            case "integer":
                return INTEGER;
            case "reference":
                return REFERENCE;
            case "string":
                return STRING;
        }
        return null;
    }
}
