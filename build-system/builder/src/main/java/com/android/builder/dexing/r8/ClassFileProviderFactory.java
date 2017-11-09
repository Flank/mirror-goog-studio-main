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

package com.android.builder.dexing.r8;

import com.android.annotations.NonNull;
import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.utils.DirectoryClassFileProvider;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Provides {@link ClassFileResourceProvider} suitable for D8/R8 classpath and bootclasspath
 * entries. Some of those may be shared.
 */
public class ClassFileProviderFactory implements Serializable {

    public class Handler implements Closeable {

        @NonNull private final AtomicBoolean closed = new AtomicBoolean(false);

        private Handler() {}

        @NonNull
        public ClassFileResourceProvider getProvider(@NonNull Path path) throws IOException {
            return ClassFileProviderFactory.this.getProvider(path);
        }

        @Override
        public void close() throws IOException {
            if (!closed.getAndSet(true)) {
                handlerClosed();
            }
        }
    }

    private static final long serialVersionUID = 1L;

    @NonNull private static final AtomicLong nextId = new AtomicLong();

    @NonNull
    private static final WeakHashMap<Long, ClassFileProviderFactory> liveInstances =
            new WeakHashMap<>();

    private final long id;

    // Visible for testing.
    @NonNull
    final transient Map<Path, ClassFileResourceProvider> providers =
            // We can allow usage of WeakHashMap because ArchiveClassFileProvider is closing in its
            // finalizer.
            new WeakHashMap<>();

    private transient int openedHandlers = 0;

    public ClassFileProviderFactory() {
        synchronized (liveInstances) {
            id = nextId.addAndGet(1);
            liveInstances.put(Long.valueOf(id), this);
        }
    }

    @NonNull
    private static ClassFileResourceProvider createProvider(@NonNull Path entry)
            throws IOException {
        if (Files.isRegularFile(entry)) {
            return new CachingArchiveClassFileProvider(entry);
        } else if (Files.isDirectory(entry)) {
            return DirectoryClassFileProvider.fromDirectory(entry);
        } else {
            throw new FileNotFoundException(entry.toString());
        }
    }

    @NonNull
    public synchronized Handler open() {
        ++openedHandlers;
        return new Handler();
    }

    private synchronized void handlerClosed() throws IOException {
        --openedHandlers;
        if (openedHandlers == 0) {
            // Close providers and clear
            for (ClassFileResourceProvider provider : providers.values()) {
                if (provider instanceof Closeable) {
                    ((Closeable) provider).close();
                }
            }
            providers.clear();
        }
    }

    @NonNull
    private synchronized ClassFileResourceProvider getProvider(@NonNull Path path)
            throws IOException {
        ClassFileResourceProvider provider = providers.get(path);
        if (provider == null) {
            provider = createProvider(path);
            providers.put(path, provider);
        }
        return provider;
    }

    // Serialization is saving no state, just the id. It is just ensuring that no duplicate per ID
    // is created in each VM.
    @NonNull
    private Object readResolve() throws ObjectStreamException {
        Long key = Long.valueOf(id);
        synchronized (liveInstances) {
            ClassFileProviderFactory existing = liveInstances.get(key);
            if (existing != null) {
                return existing;
            } else {
                liveInstances.put(key, this);
                return this;
            }
        }
    }
}
