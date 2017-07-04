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
import com.google.common.base.Verify;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * An abstract dex archive builder that converts input class files to dex files that are written to
 * dex archive. This class contains the logic for reading the class files from the input, {@link
 * ClassFileInput}, and writing the output to a {@link DexArchive}. Implementation of conversion
 * from the class files to dex files is left to the sub-classes. To trigger the conversion, create
 * an instance of this class, and invoke {@link #convert(ClassFileInput, DexArchive)}.
 */
public abstract class DexArchiveBuilder {

    /** Creates an instance that is using dx to convert class files to dex files. */
    @NonNull
    public static DexArchiveBuilder createDxDexBuilder(@NonNull DexArchiveBuilderConfig config) {
        return new DxDexArchiveBuilder(config);
    }

    /**
     * Converts the specified input, and writes it to the output dex archive. If dex archive does
     * not exist, it will be created. If it exists, entries will be added or replaced.
     */
    public void convert(@NonNull ClassFileInput input, @NonNull DexArchive output)
            throws DexArchiveBuilderException {
        try {
            ensureOutputArchiveExists(output);
            List<DexArchiveEntry> convertedEntries = convertClassFileInput(input);
            writeToArchive(convertedEntries, output);
        } catch (IOException e) {
            throw new DexArchiveBuilderException(e);
        }
    }

    @NonNull
    protected abstract List<DexArchiveEntry> convertClassFileInput(@NonNull ClassFileInput input);

    private static void writeToArchive(
            @NonNull List<DexArchiveEntry> entries, @NonNull DexArchive output) throws IOException {
        for (DexArchiveEntry dexEntry : entries) {
            Verify.verifyNotNull(dexEntry);
            try (ByteArrayInputStream dexContent =
                    new ByteArrayInputStream(dexEntry.getDexFileContent())) {
                Path dexFilePath =
                        ClassFileEntry.withDexExtension(dexEntry.getRelativePathInArchive());
                output.addFile(dexFilePath, dexContent);
            }
        }
    }

    private static void ensureOutputArchiveExists(@NonNull DexArchive dexArchive)
            throws IOException {
        if (Files.notExists(dexArchive.getRootPath())) {
            if (ClassFileInputs.jarMatcher.matches(dexArchive.getRootPath())) {
                Files.createDirectories(dexArchive.getRootPath().getParent());
                Files.createFile(dexArchive.getRootPath());
            } else {
                Files.createDirectories(dexArchive.getRootPath());
            }
        }
    }
}
