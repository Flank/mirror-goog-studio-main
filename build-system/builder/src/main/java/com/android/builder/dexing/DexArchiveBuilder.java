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
import com.android.ide.common.blame.parser.DexParser;
import com.android.ide.common.internal.WaitableExecutor;
import com.google.common.base.Throwables;
import com.google.common.base.Verify;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Converts specified class file inputs to a {@link DexArchive}. To do so, configure the builder in
 * a constructor, and invoke {@link #convert(ClassFileInput, DexArchive)} method that will trigger
 * the actual conversion.
 */
public class DexArchiveBuilder {

    /** Exception thrown if something goes wrong when building a dex archive. */
    public static class DexBuilderException extends RuntimeException {

        public DexBuilderException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @NonNull private final DexArchiveBuilderConfig config;
    @NonNull private final WaitableExecutor executor;

    /**
     * Creates instance that is configured for processing .class input files, and producing a dex
     * archive. For configuring it, please take a look at {@link DexArchiveBuilderConfig} which
     * contains relevant options.
     *
     * <p>{@link DxContext} specified in the config will be used for all conversions. We do not use
     * anything but standard and error output from this object, so by reusing it, we will skip
     * creating unnecessary objects.
     *
     * @param config contains setup for this builder
     */
    public DexArchiveBuilder(@NonNull DexArchiveBuilderConfig config) {
        this.config = config;
        this.executor = WaitableExecutor.useNewFixedSizeThreadPool(config.getNumThreads());
    }

    /**
     * Converts the .CLASS file inputs specified in the {@link #config} to the .DEX format. Class
     * files are read from the specified class file input object, while the output is written to the
     * specified dex archive.
     */
    public void convert(@NonNull ClassFileInput input, @NonNull DexArchive output)
            throws IOException {
        ensureOutputArchiveExists(output);

        for (ClassFileEntry entry : input) {
            processClassFile(entry.relativePath, entry.classFileContent);
        }
        try {
            processOutputs(output);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            config.getDxContext().err.println(DexParser.DX_UNEXPECTED_EXCEPTION);
            config.getDxContext().err.println(Throwables.getRootCause(e).getMessage());
            config.getDxContext().err.print(Throwables.getStackTraceAsString(e));
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new DexBuilderException("Unable to convert input to dex archive.", e.getCause());
        }
    }

    /** Waits for all inputs to be processed, and writes the outputs to the dex archive. */
    private void processOutputs(@NonNull DexArchive output)
            throws IOException, InterruptedException {
        List<DexArchiveEntry> entries = executor.waitForTasksWithQuickFail(true);
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

    private void processClassFile(@NonNull Path relativePath, @NonNull byte[] fileBytes)
            throws IOException {
        DexArchiveBuilderCallable converterCallable =
                new DexArchiveBuilderCallable(
                        relativePath,
                        fileBytes,
                        config.getDxContext(),
                        config.getDexOptions(),
                        config.getCfOptions());
        executor.execute(converterCallable);
    }

    private void ensureOutputArchiveExists(@NonNull DexArchive dexArchive) throws IOException {
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
