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
import com.android.annotations.Nullable;
import com.android.ide.common.blame.parser.DexParser;
import com.android.tools.r8.CompilationException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.DexOverflowException;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

final class D8DexArchiveMerger implements DexArchiveMerger {

    private static final String ERROR_MULTIDEX =
            "Cannot fit requested classes in a single dex file";

    @NonNull private final OutputStream errorStream;
    private final int minSdkVersion;
    @NonNull private final CompilationMode compilationMode;

    public D8DexArchiveMerger(
            @NonNull OutputStream errorStream,
            int minSdkVersion,
            @NonNull CompilationMode compilationMode) {
        this.errorStream = errorStream;
        this.minSdkVersion = minSdkVersion;
        this.compilationMode = compilationMode;
    }

    @Override
    public void mergeDexArchives(
            @NonNull Iterable<Path> inputs,
            @NonNull Path outputDir,
            @Nullable Path mainDexClasses,
            @NonNull DexingType dexingType)
            throws DexArchiveMergerException {
        if (Iterables.isEmpty(inputs)) {
            return;
        }

        D8Command.Builder builder = D8Command.builder();
        builder.setEnableDesugaring(false);

        for (Path input : inputs) {
            try (DexArchive archive = DexArchives.fromInput(input)) {
                for (DexArchiveEntry dexArchiveEntry : archive.getFiles()) {
                    builder.addDexProgramData(dexArchiveEntry.getDexFileContent());
                }
            } catch (IOException e) {
                throw getExceptionToRethrow(e, input);
            }
        }
        try {
            if (mainDexClasses != null) {
                builder.addMainDexListFiles(mainDexClasses);
            }
            builder.setMinApiLevel(minSdkVersion).setMode(compilationMode).setOutputPath(outputDir);
            D8.run(builder.build());
        } catch (CompilationException | IOException | CompilationError e) {
            throw getExceptionToRethrow(e, inputs);
        }
    }

    // TODO (gavra, b/67624381): replace this with error handling through the D8 API
    @NonNull
    private DexArchiveMergerException getExceptionToRethrow(
            @NonNull Throwable t, @NonNull Iterable<Path> inputs) {
        StringBuilder msg = new StringBuilder("Error while merging dex archives: ");
        msg.append(Joiner.on(", ").join(inputs));
        msg.append(System.lineSeparator());
        if (t instanceof DexOverflowException && t.getMessage().startsWith(ERROR_MULTIDEX)) {
            msg.append(DexParser.DEX_LIMIT_EXCEEDED_ERROR);
        }
        return new DexArchiveMergerException(msg.toString(), t);
    }
}
