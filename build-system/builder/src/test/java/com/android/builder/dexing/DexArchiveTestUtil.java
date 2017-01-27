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

import com.android.annotations.NonNull;
import com.android.dx.command.dexer.DxContext;
import java.io.IOException;
import java.nio.file.Path;

/** Helper methods for testing dex archives. */
public final class DexArchiveTestUtil {

    private static final int NUM_THREADS = 4;
    private static DxContext dxContext = new DxContext(System.out, System.err);

    /** Converts the class files to the dex archive with the specified path. */
    public static void convertClassesToDexArchive(
            @NonNull Path classes, @NonNull Path dexArchiveOutput) throws IOException {
        try (DexArchive dexArchive = DexArchives.fromInput(dexArchiveOutput)) {
            ClassFileInput inputs = ClassFileInputs.fromPath(classes, e -> true);
            DexArchiveBuilderConfig config =
                    new DexArchiveBuilderConfig(NUM_THREADS, dxContext, true, false);

            DexArchiveBuilder dexArchiveBuilder = new DexArchiveBuilder(config);
            dexArchiveBuilder.convert(inputs, dexArchive);
        }
    }
}
