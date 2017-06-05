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
package com.android.tools.apk.analyzer.dex;

import com.android.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;

public final class DexFiles {
    private DexFiles() {}

    @NonNull
    public static DexBackedDexFile getDexFile(@NonNull Path p) throws IOException {
        return getDexFile(Files.readAllBytes(p));
    }

    @NonNull
    public static DexBackedDexFile getDexFile(@NonNull byte[] contents) {
        return new DexBackedDexFile(Opcodes.forApi(15), contents);
    }
}
