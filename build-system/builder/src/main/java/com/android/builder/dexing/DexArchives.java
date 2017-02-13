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

package com.android.builder.dexing;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.apkzlib.zip.ZFile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Predicate;

/** Helper methods for the {@link DexArchive}. */
public class DexArchives {

    public static final Predicate<Path> DEX_ENTRY_FILTER =
            f -> f.toString().endsWith(SdkConstants.DOT_DEX);

    private DexArchives() {
        // empty
    }

    /**
     * Creates a {@link com.android.builder.dexing.DexArchive} from the specified path. It supports
     * .jar files and directories as inputs.
     */
    @NonNull
    public static DexArchive fromInput(@NonNull Path path) throws IOException {
        if (ClassFileInputs.jarMatcher.matches(path)) {
            return new JarDexArchive(new ZFile(path.toFile()));
        } else {
            return new DirDexArchive(path);
        }
    }
}
