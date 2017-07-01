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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;

/**
 * Represents a collection of split APKs, such as from a instant run cold swap.
 *
 * <p>This exists to represent a model of the underlying data that assertions can be built upon, for
 * example as a truth subject.
 */
@Immutable
public final class SplitApks implements AutoCloseable {

    @NonNull private final List<Apk> apks;
    // Cached state
    @SuppressWarnings("NonFinalFieldInImmutable")
    @Nullable
    private ImmutableMap<String, DexBackedClassDef> allClasses = null;

    public SplitApks(@NonNull List<Apk> apks) {
        this.apks = ImmutableList.copyOf(apks);
    }

    public Apk get(int index) {
        return apks.get(index);
    }

    public int size() {
        return apks.size();
    }

    public List<Dex> getAllDexes() throws IOException {
        ImmutableList.Builder<Dex> allDexes = ImmutableList.builder();
        for (Apk apk : apks) {
            allDexes.addAll(apk.getAllDexes());
        }
        return allDexes.build();
    }

    public Map<String, DexBackedClassDef> getAllClasses() throws IOException {
        if (allClasses != null) {
            return allClasses;
        }

        ImmutableMap.Builder<String, DexBackedClassDef> allClassesBuilder = ImmutableMap.builder();
        for (Dex dex : getAllDexes()) {
            allClassesBuilder.putAll(dex.getClasses());
        }
        allClasses = allClassesBuilder.build();
        return allClasses;
    }

    @NonNull
    public List<Path> getEntries(@NonNull String name) {
        return apks.stream()
                .map(apk -> apk.getEntry(name))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public void close() throws Exception {
        for (Apk apk : apks) {
            apk.close();
        }
    }

    @Override
    public String toString() {
        return "Apks<" + apks.stream().map(Apk::toString).collect(Collectors.joining(",")) + ">";
    }
}
