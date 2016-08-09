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

package com.android.build.gradle.integration.common.utils;

import com.android.annotations.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;

public final class DexUtils {

    private static final Opcodes DEX_LIB_OPCODES = Opcodes.forApi(24);

    private DexUtils() {
    }

    @Nullable
    public static DexBackedDexFile loadDex(@Nullable byte[] bytes) {
        return bytes != null ? new DexBackedDexFile(DEX_LIB_OPCODES, bytes) : null;
    }

    @Nullable
    public static DexBackedDexFile loadDex(@Nullable Path path) throws IOException {
        return loadDex(path != null ? Files.readAllBytes(path) : null);
    }

    @Nullable
    public static DexBackedDexFile loadDex(@Nullable File file) throws IOException {
        return loadDex(file != null ? file.toPath() : null);
    }


}
