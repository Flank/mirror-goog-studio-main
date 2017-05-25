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

package com.android.testutils.truth;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;

public final class DexUtils {

    @SuppressWarnings("deprecation") // currently studio is using an older version of the lib
    private static final Opcodes DEX_LIB_OPCODES = Opcodes.forApi(24);

    private DexUtils() {
    }

    @NonNull
    public static DexBackedDexFile loadDex(@NonNull byte[] bytes) {
        return new DexBackedDexFile(DEX_LIB_OPCODES, bytes);
    }

    @NonNull
    public static DexBackedDexFile loadDex(@NonNull Path path) throws IOException {
        return loadDex(Files.readAllBytes(path));
    }

    @NonNull
    public static DexBackedDexFile loadDex(@NonNull File file) throws IOException {
        return loadDex(file.toPath());
    }

    public static DexBackedClassDef getClass(@Nullable DexBackedDexFile dex, @NonNull String name) {
        return dex != null ? getClass(Collections.singleton(dex), name) : null;
    }

    @Nullable
    public static DexBackedClassDef getClass(
            @NonNull Collection<DexBackedDexFile> dexFiles, @NonNull String name) {
        for (DexBackedDexFile dexFile : dexFiles) {
            Optional<? extends DexBackedClassDef> classDef =
                    dexFile.getClasses()
                            .parallelStream()
                            .filter(clazz -> clazz.getType().equals(name))
                            .findAny();
            if (classDef.isPresent()) {
                return classDef.get();
            }
        }
        return null;
    }


}
