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
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * A custom class loader that loads a given list of classes and delegates loading other classes to
 * the default class loaders. (To avoid access restriction issues, it also loads any inner classes
 * of the given classes.)
 *
 * <p>Once the client creates the {@link MultiClassLoader} instance with a list of classes to load,
 * it should call {@link #load()} instead of {@link #loadClass(String)}.
 */
@Immutable
public final class MultiClassLoader extends ClassLoader {

    @NonNull private final LinkedHashMap<String, Class<?>> classesToLoad;

    public MultiClassLoader(@NonNull List<String> classesToLoad) {
        this.classesToLoad = Maps.newLinkedHashMap();
        for (String classToLoad : classesToLoad) {
            this.classesToLoad.put(classToLoad, null);
        }
    }

    @NonNull
    public List<Class<?>> load() throws ClassNotFoundException {
        List<Class<?>> loadedClasses = new ArrayList<>(classesToLoad.size());
        for (String classToLoad : classesToLoad.keySet()) {
            loadedClasses.add(loadClass(classToLoad));
        }
        return loadedClasses;
    }

    @NonNull
    @Override
    public Class<?> loadClass(@NonNull String name) throws ClassNotFoundException {
        for (String classToLoad : classesToLoad.keySet()) {
            if (name.equals(classToLoad)) {
                if (classesToLoad.get(classToLoad) == null) {
                    Preconditions.checkState(findLoadedClass(classToLoad) == null);
                    classesToLoad.put(classToLoad, defineClass(classToLoad));
                }
                return classesToLoad.get(classToLoad);
            }

            // Also load any inner classes of the given classes
            if (name.startsWith(classToLoad + "$")) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    loadedClass = defineClass(name);
                }
                return loadedClass;
            }
        }

        // Delegate loading other classes to the default class loaders
        return super.loadClass(name);
    }

    @NonNull
    private Class<?> defineClass(@NonNull String name) {
        String classFile = name.replace('.', '/') + ".class";
        InputStream stream = getClass().getClassLoader().getResourceAsStream(classFile);
        byte[] bytes;
        try {
            bytes = convertStreamToBytes(stream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return defineClass(name, bytes, 0, bytes.length);
    }

    @NonNull
    private static byte[] convertStreamToBytes(@NonNull InputStream stream) throws IOException {
        byte bytes[] = new byte[stream.available()];
        try (DataInputStream dataInputStream = new DataInputStream(stream)) {
            dataInputStream.readFully(bytes);
        }
        return bytes;
    }
}
