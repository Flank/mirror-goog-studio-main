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
import com.android.resources.ResourceType;
import com.android.util.Pair;

import org.intellij.lang.annotations.MagicConstant;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Intermediary class implementing parts of both the old and new ProjectCallback from the
 * LayoutLib API.
 * <p/>
 * Even newer LayoutLibs use this directly instead of the the interface. This allows the flexibility
 * to add newer methods without having to update {@link Bridge#API_CURRENT LayoutLib API version}.
 * <p/>
 * Clients should use this instead of {@link IProjectCallback} to target both old and new
 * Layout Libraries.
 */
@SuppressWarnings({"deprecation", "MethodMayBeStatic", "unused"})
public abstract class LayoutlibCallback implements IProjectCallback,
        com.android.layoutlib.api.IProjectCallback {

    /**
     * Like {@link #loadView(String, Class[], Object[])}, but intended for loading classes that may
     * not be custom views.
     *
     * @param name className in binary format (see {@link ClassLoader})
     * @return an new instance created by calling the given constructor.
     * @throws ClassNotFoundException any exceptions thrown when creating the instance is wrapped in
     * ClassNotFoundException.
     * @since API 15
     */
    public Object loadClass(@NonNull String name, @Nullable Class[] constructorSignature,
      @Nullable Object[] constructorArgs) throws ClassNotFoundException {
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

    /**
     * Creates a new XmlPullParser with an optional display name.
     *
     * @param displayName an optional name to aid with debugging.
     * @throws XmlPullParserException
     * @since API 15
     */
    @NonNull
    public XmlPullParser createParser(@Nullable String displayName) throws XmlPullParserException {
        throw new UnsupportedOperationException("createNewParser not supported.");
    }

    // ------ implementation of the old interface using the new interface.

    @Override
    public final Integer getResourceValue(String type, String name) {
        return getResourceId(ResourceType.getEnum(type), name);
    }

    @Override
    public final String[] resolveResourceValue(int id) {
        Pair<ResourceType, String> info = resolveResourceId(id);
        if (info != null) {
            return new String[] { info.getSecond(), info.getFirst().getName() };
        }

        return null;
    }

    @Override
    public final String resolveResourceValue(int[] id) {
        return resolveResourceId(id);
    }
}
