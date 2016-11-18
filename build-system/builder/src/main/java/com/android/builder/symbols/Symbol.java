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
import com.google.common.base.Preconditions;
import java.util.regex.Pattern;

/**
 * A symbol is a 3-tuple containing a name, a type and a value. Symbols are used to refer to
 * Android resources. The name of the symbol has to be a valid java identifier and is usually
 * the file name of the resource (without the extension) or the name of the resource if the
 * resource is part of an XML file.
 *
 * <p>For example, the resource {@code drawable/foo.png} has name {@code foo}. The string
 * {@code bar} in file {@code values/strings.xml} with name {@code bar} has resource name
 * {@code bar}.
 *
 * <p>The resource type is the java data type that contains the resource value. This is generally
 * be {@code int}, but other values (such as {@code int[]}) are allowed. This class poses no
 * restrictions on the type other that it may not contain any spaces.
 *
 * <p>The value is a java expression that conforms to the resource type and contains the value of
 * the resource. This may be just an integer like {@code 3}, if the resource has type {@code int}.
 * But may be a more complex expression. For example, if the resource has type {@code int[]},
 * the value may be something such as {@code {1, 2, 3}}.
 *
 * <p>In practice, symbols do not exist by themselves. They are usually part of a symbol table,
 * but this class is independent of any use.
 */
public class Symbol {

    /**
     * Pattern that validates symbol names.
     */
    private static final Pattern NAME_PATTERN = Pattern.compile("\\S+");

    /**
     * Pattern that validates symbol types.
     */
    private static final Pattern TYPE_PATTERN = Pattern.compile("\\S+");

    /**
     * Pattern that validates symbol values.
     */
    private static final Pattern VALUE_PATTERN = Pattern.compile(".*\\S+.*");

    /**
     * The name of the symbol.
     */
    @NonNull
    private final String name;

    /**
     * The type of the symbol.
     */
    @NonNull
    private final String type;

    /**
     * The value of the symbol.
     */
    @NonNull
    private final String value;

    /**
     * Creates a new symbol.
     *
     * @param name the name of the symbol
     * @param type the type of the symbol
     * @param value the value of the symbol
     */
    public Symbol(@NonNull String name, @NonNull String type, @NonNull String value) {
        Preconditions.checkArgument(NAME_PATTERN.matcher(name).matches());
        Preconditions.checkArgument(TYPE_PATTERN.matcher(type).matches());
        Preconditions.checkArgument(VALUE_PATTERN.matcher(value).matches());

        this.name = name;
        this.type = type;
        this.value = value;
    }

    /**
     * Obtains the value of the symbol.
     *
     * @return the value
     */
    @NonNull
    public String getValue() {
        return value;
    }

    /**
     * Obtains the name of the symbol.
     *
     * @return the name
     */
    @NonNull
    public String getName() {
        return name;
    }

    /**
     * Obtains the type of the symbol.
     *
     * @return the type
     */
    public String getType() {
        return type;
    }
}
