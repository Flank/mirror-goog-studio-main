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
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.dependency.HashCodeUtils;
import com.android.ide.common.res2.MergingException;
import com.android.ide.common.res2.ValueResourceNameValidator;
import com.android.resources.ResourceType;
import com.google.common.base.Preconditions;
import java.util.Objects;

/**
 * A symbol is a 4-tuple containing a resource type, a name, a java type and a value. Symbols are
 * used to refer to Android resources.
 *
 * <p>A resource type identifies the group or resource. Resources in Android can have various types:
 * drawables, strings, etc. The full list of supported resource types can be found in {@link
 * com.android.resources.ResourceType}.
 *
 * <p>The name of the symbol has to be a valid java identifier and is usually the file name of the
 * resource (without the extension) or the name of the resource if the resource is part of an XML
 * file. While names of resources declared in XML files can contain dots and colons, these should be
 * replaced by underscores before being passed to the constructor. To sanitize the resource name,
 * call {@link SymbolUtils#canonicalizeValueResourceName(String)} before passing the name to the
 * constructor.
 *
 * <p>For example, the resource {@code drawable/foo.png} has name {@code foo}. The string {@code
 * bar} in file {@code values/strings.xml} with name {@code bar} has resource name {@code bar}.
 *
 * <p>The java type is the java data type that contains the resource value. This is generally {@code
 * int}, but other values (such as {@code int[]}) are allowed. Type should not contain any
 * whitespaces, be {@code null} or empty.
 *
 * <p>The value is a java expression that conforms to the resource type and contains the value of
 * the resource. This may be just an integer like {@code 3}, if the resource has type {@code int}.
 * But may be a more complex expression. For example, if the resource has type {@code int[]}, the
 * value may be something such as {@code {1, 2, 3}}.
 *
 * <p>In practice, symbols do not exist by themselves. They are usually part of a symbol table, but
 * this class is independent of any use.
 */
@Immutable
public class Symbol {

    /** The sanitized name of the symbol. */
    @NonNull private final String name;

    /** The java type of the symbol. */
    @NonNull private final String javaType;

    /** The value of the symbol. */
    @NonNull private final String value;

    /** The type of resource. */
    @NonNull private final ResourceType resourceType;

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
    public Symbol(
            @NonNull String resourceType,
            @NonNull String name,
            @NonNull String javaType,
            @NonNull String value) {
        this(
                Preconditions.checkNotNull(
                        ResourceType.getEnum(resourceType),
                        "Invalid resource type %s",
                        resourceType),
                name,
                javaType,
                value);
    }

    /**
     * Creates a new symbol. The {@code name} of the symbol needs to be a valid sanitized resource
     * name. See {@link SymbolUtils#canonicalizeValueResourceName} method and apply it beforehand
     * when necessary.
     *
     * @param resourceType the resource type of the symbol
     * @param name the sanitized name of the symbol
     * @param javaType the java type of the symbol
     * @param value the value of the symbol
     */
    public Symbol(
            @NonNull ResourceType resourceType,
            @NonNull String name,
            @NonNull String javaType,
            @NonNull String value) {

        this.resourceType = resourceType;
        this.name = name;
        this.javaType = javaType;
        this.value = value;

        validateSymbol(name, resourceType);
    }

    /**
     * Checks whether the given resource name meets all the criteria: cannot be null or empty,
     * cannot contain whitespaces or dots. Also checks if the given resource type is a valid.
     *
     * @param name the name of the resource that needs to be validated
     */
    private static void validateSymbol(@Nullable String name, @NonNull ResourceType resourceType) {
        Preconditions.checkArgument(name != null, "Resource name cannot be null");
        Preconditions.checkArgument(
                !name.contains("."), "Resource name cannot contain dots: " + name);
        try {
            ValueResourceNameValidator.validate(name, resourceType, null);
        } catch (MergingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Obtains the resource type.
     *
     * @return the resource type
     */
    @NonNull
    public String getResourceType() {
        return resourceType.getName();
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
     * Obtains the java type of the symbol.
     *
     * @return the java type
     */
    @NonNull
    public String getJavaType() {
        return javaType;
    }

    @Override
    public int hashCode() {
        return HashCodeUtils.hashCode(resourceType, name, javaType, value);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof Symbol)) {
            return false;
        }

        Symbol other = (Symbol) obj;
        return Objects.equals(resourceType, other.resourceType)
                && Objects.equals(name, other.name)
                && Objects.equals(javaType, other.javaType)
                && Objects.equals(value, other.value);
    }
}
