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

package com.android.testutils.apk;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.testutils.truth.DexUtils;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;

@Immutable
public final class Dex {

    @NonNull private final Supplier<byte[]> fileBytesSupplier;
    @NonNull private final String name;

    // Lazily initialized properties
    @SuppressWarnings("NonFinalFieldInImmutable")
    @Nullable
    private ImmutableMap<String, DexBackedClassDef> classes;

    public Dex(@NonNull Path path) {
        this(path, path.toString());
    }

    public Dex(@NonNull File file) {
        this(file.toPath());
    }

    public Dex(@NonNull byte[] fileContent, @NonNull String name) {
        this.fileBytesSupplier = () -> fileContent;
        this.name = name;
    }

    Dex(@NonNull Path path, @NonNull String name) {
        this.fileBytesSupplier =
                () -> {
                    try {
                        return Files.readAllBytes(path);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                };
        this.name = name;
    }

    @NonNull
    public ImmutableMap<String, DexBackedClassDef> getClasses() throws IOException {
        if (classes != null) {
            return classes;
        }
        ImmutableMap.Builder<String, DexBackedClassDef> mapBuilder = ImmutableMap.builder();
        for (DexBackedClassDef classDef : DexUtils.loadDex(fileBytesSupplier.get()).getClasses()) {
            mapBuilder.put(classDef.getType(), classDef);
        }
        classes = mapBuilder.build();
        return classes;
    }

    @Override
    public String toString() {
        return "Dex<" + name + ">";
    }
}
