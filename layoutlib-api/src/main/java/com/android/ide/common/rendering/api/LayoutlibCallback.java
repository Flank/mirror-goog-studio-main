/*
 * Copyright (C) 2015 The Android Open Source Project
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
import org.intellij.lang.annotations.MagicConstant;
import org.xmlpull.v1.XmlPullParser;

/**
 * Intermediary class implementing parts of both the old and new ProjectCallback from the LayoutLib
 * API.
 *
 * <p>Even newer LayoutLibs use this directly instead of the the interface. This allows the
 * flexibility to add newer methods without having to update {@link Bridge#API_CURRENT LayoutLib API
 * version}.
 */
@SuppressWarnings({"MethodMayBeStatic", "unused"})
public abstract class LayoutlibCallback implements IProjectCallback, XmlParserFactory {
    /**
     * Like {@link #loadView(String, Class[], Object[])}, but intended for loading classes that may
     * not be custom views.
     *
     * @param name className in binary format (see {@link ClassLoader})
     * @return an new instance created by calling the given constructor.
     * @throws ClassNotFoundException any exceptions thrown when creating the instance is wrapped in
     *     ClassNotFoundException.
     * @since API 15
     */
    public Object loadClass(
            @NonNull String name,
            @Nullable Class[] constructorSignature,
            @Nullable Object[] constructorArgs)
            throws ClassNotFoundException {
        try {
            return loadView(name, constructorSignature, constructorArgs);
        }
        catch (ClassNotFoundException e) {
            throw e;
        }
        catch (Exception e) {
            throw new ClassNotFoundException(name + " not found.", e);
        }
    }

    /**
     * Returns if the IDE supports the requested feature.
     * @see Features
     * @since API 15
     */
    public abstract boolean supports(
            @MagicConstant(valuesFromClass = Features.class) int ideFeature);

    /**
     * A callback to query arbitrary data. This is similar to {@link RenderParams#setFlag(SessionParams.Key,
     * Object)}. The main difference is that when using this, the IDE doesn't have to compute the
     * value in advance and thus may save on some computation.
     * @since API 15
     */
    @Nullable
    public <T> T getFlag(@NonNull SessionParams.Key<T> key) {
        return null;
    }

    /** @deprecated Use {@link #createXmlParserForFile}. */
    @Deprecated
    @NonNull
    public ParserFactory getParserFactory() {
        throw new UnsupportedOperationException("getParserFactory not supported.");
    }

    /**
     * Find a custom class in the project.
     * <p>
     * Like {@link #loadClass(String, Class[], Object[])}, but doesn't instantiate
     * an object and just returns the class found.
     * @param name className in binary format. (see {@link ClassLoader}.
     * @since API 15
     */
    @NonNull
    public Class<?> findClass(@NonNull String name) throws ClassNotFoundException {
        throw new ClassNotFoundException(name + " not found.");
    }

    /** @deprecated Use {@link XmlParserFactory#createXmlParserForPsiFile}. */
    @Deprecated
    @Nullable
    public XmlPullParser getXmlFileParser(String fileName) {
        return createXmlParserForPsiFile(fileName);
    }

    /**
     * Returns an optional {@link ResourceNamespace.Resolver} that knows namespace prefixes assumed
     * to be declared in every resource file.
     *
     * <p>For backwards compatibility, in non-namespaced projects this contains the "tools" prefix
     * mapped to {@link ResourceNamespace#TOOLS}. Before the IDE understood resource namespaces,
     * this prefix was used for referring to sample data, even if the user didn't define the "tools"
     * prefix using {@code xmlns:tools="..."}.
     *
     * <p>In namespaced projects this method returns an empty resolver, which means sample data
     * won't work without an explicit definition of a namespace prefix for the {@link
     * ResourceNamespace#TOOLS} URI.
     */
    @NonNull
    public ResourceNamespace.Resolver getImplicitNamespaces() {
        return ResourceNamespace.Resolver.EMPTY_RESOLVER;
    }
}
