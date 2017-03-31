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

package com.android.testutils.classloader;

import com.android.annotations.NonNull;
import com.android.annotations.concurrency.Immutable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/**
 * A custom class loader that loads only one given class and delegates loading other classes to the
 * default class loaders. (To avoid access restriction issues, it also loads any inner classes of
 * the given class.)
 *
 * <p>Once the client creates the {@link SingleClassLoader} instance with a class to load, it should
 * call {@link #load()} instead of {@link #loadClass(String)}.
 */
@Immutable
public final class SingleClassLoader extends ClassLoader {

    @NonNull private final MultiClassLoader multiClassLoader;

    public SingleClassLoader(@NonNull String classToLoad) {
        multiClassLoader = new MultiClassLoader(ImmutableList.of(classToLoad));
    }

    @NonNull
    public Class<?> load() throws ClassNotFoundException {
        return Iterables.getOnlyElement(multiClassLoader.load());
    }

    @NonNull
    @Override
    public Class<?> loadClass(@NonNull String name) {
        throw new IllegalStateException(
                "This method must not be called directly. Use SingleClassLoader#load() instead.");
    }
}
