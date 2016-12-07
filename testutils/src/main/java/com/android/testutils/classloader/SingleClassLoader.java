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
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * A custom class loader that loads only one given class (and its inner classes if any) and
 * delegates everything else to the default class loaders.
 *
 * <p>Once the {@code SingleClassLoader} instance is initialized with the class to load, the client
 * should call {@link #load()} instead of {@link #loadClass(String)}.
 */
@Immutable
public final class SingleClassLoader extends ClassLoader {

    @NonNull private final String classToLoad;

    public SingleClassLoader(@NonNull String classToLoad) {
        this.classToLoad = classToLoad;
    }

    @NonNull
    public Class load() throws ClassNotFoundException {
        return loadClass(classToLoad);
    }

    @NonNull
    @Override
    public Class loadClass(@NonNull String name) throws ClassNotFoundException {
        if (name.startsWith(classToLoad)) {
            Preconditions.checkState(findLoadedClass(name) == null);
            return defineClass(name);
        } else {
            return super.loadClass(name);
        }
    }

    @NonNull
    private Class defineClass(@NonNull String name) throws ClassNotFoundException {
        String classFile = name.replace('.', File.separatorChar) + ".class";
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
    private byte[] convertStreamToBytes(@NonNull InputStream stream) throws IOException {
        byte bytes[] = new byte[stream.available()];
        DataInputStream dataInputStream = new DataInputStream(stream);
        dataInputStream.readFully(bytes);
        dataInputStream.close();
        return bytes;
    }
}
