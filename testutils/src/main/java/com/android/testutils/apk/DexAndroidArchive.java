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
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;

@Immutable
public abstract class DexAndroidArchive extends AndroidArchive {

    @Nullable private final Dex mainDex;
    @NonNull private final List<Dex> secondaryDexes;

    public DexAndroidArchive(
            @NonNull Path file, @NonNull String mainDexName, @NonNull String secondaryDexName)
            throws IOException {
        super(file);

        Path mainDexPath = getEntry(mainDexName);

        Dex mainDex =
                mainDexPath != null ?
                        new Dex(mainDexPath, displayName + ":" + mainDexName) : null;
        ImmutableList.Builder<Dex> secondaryDexes = ImmutableList.builder();

        for (int index = 2; true; index++) {
            Path dex = getEntry(String.format(secondaryDexName, index));
            if (dex == null) {
                break;
            }
            secondaryDexes.add(new Dex(dex, displayName + ":" + secondaryDexName));
        }
        this.mainDex = mainDex;
        this.secondaryDexes = secondaryDexes.build();
    }

    @NonNull
    public final Optional<Dex> getMainDexFile() throws IOException {
        return Optional.ofNullable(mainDex);
    }

    @NonNull
    public final List<Dex> getSecondaryDexFiles() throws IOException {
        return secondaryDexes;
    }

    @NonNull
    public final List<Dex> getAllDexes() throws IOException {
        ImmutableList.Builder<Dex> dexListBuilder = ImmutableList.builder();
        if (mainDex != null) {
            dexListBuilder.add(mainDex);
        }
        return dexListBuilder.addAll(secondaryDexes).build();
    }

    @Override
    public final boolean containsMainClass(@NonNull String name) throws IOException {
        AndroidArchive.checkValidClassName(name);
        return mainDex != null && mainDex.getClasses().containsKey(name);
    }

    @Override
    public final boolean containsSecondaryClass(@NonNull String name) throws IOException {
        AndroidArchive.checkValidClassName(name);
        for (Dex dex : secondaryDexes) {
            if (dex.getClasses().containsKey(name)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public final DexBackedClassDef getClass(@NonNull String className) throws IOException {
        if (mainDex != null) {
            DexBackedClassDef foundClass = mainDex.getClasses().get(className);
            if (foundClass != null) {
                return foundClass;
            }
        }
        for (Dex dex : secondaryDexes) {
            DexBackedClassDef foundClass = dex.getClasses().get(className);
            if (foundClass != null) {
                return foundClass;
            }
        }
        return null;
    }
}
