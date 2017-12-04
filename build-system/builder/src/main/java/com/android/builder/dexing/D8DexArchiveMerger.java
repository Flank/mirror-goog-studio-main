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
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.MessageReceiver;
import com.android.ide.common.blame.parser.DexParser;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.Diagnostic;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

final class D8DexArchiveMerger implements DexArchiveMerger {

    @NonNull
    private static final Logger LOGGER = Logger.getLogger(D8DexArchiveMerger.class.getName());

    private static final String ERROR_MULTIDEX =
            "Cannot fit requested classes in a single dex file";

    private final int minSdkVersion;
    @NonNull private final CompilationMode compilationMode;
    @NonNull private final MessageReceiver messageReceiver;
    private volatile boolean hintForMultidex = false;

    public D8DexArchiveMerger(
            @Nonnull MessageReceiver messageReceiver,
            int minSdkVersion,
            @NonNull CompilationMode compilationMode) {
        this.minSdkVersion = minSdkVersion;
        this.compilationMode = compilationMode;
        this.messageReceiver = messageReceiver;
    }

    @Override
    public void mergeDexArchives(
            @NonNull Iterable<Path> inputs,
            @NonNull Path outputDir,
            @Nullable Path mainDexClasses,
            @NonNull DexingType dexingType)
            throws DexArchiveMergerException {
        LOGGER.log(
                Level.INFO,
                "Merging to '"
                        + outputDir.toAbsolutePath().toString()
                        + "' with D8 from "
                        + Streams.stream(inputs)
                                .map(path -> path.toAbsolutePath().toString())
                                .collect(Collectors.joining(", ")));

        if (Iterables.isEmpty(inputs)) {
            return;
        }

        D8DiagnosticsHandler d8DiagnosticsHandler = new InterceptingDiagnosticsHandler();
        D8Command.Builder builder = D8Command.builder(d8DiagnosticsHandler);
        builder.setEnableDesugaring(false);

        for (Path input : inputs) {
            try (DexArchive archive = DexArchives.fromInput(input)) {
                for (DexArchiveEntry dexArchiveEntry : archive.getFiles()) {
                    builder.addDexProgramData(
                            dexArchiveEntry.getDexFileContent(),
                            D8DiagnosticsHandler.getOrigin(dexArchiveEntry));
                }
            } catch (IOException e) {
                throw getExceptionToRethrow(e, input, d8DiagnosticsHandler);
            }
        }
        try {
            if (mainDexClasses != null) {
                builder.addMainDexListFiles(mainDexClasses);
            }
            builder.setMinApiLevel(minSdkVersion)
                    .setMode(compilationMode)
                    .setOutputPath(outputDir)
                    .setEnableDesugaring(false)
                    .setIntermediate(false);
            D8.run(builder.build());
        } catch (CompilationFailedException e) {
            throw getExceptionToRethrow(e, inputs, d8DiagnosticsHandler);
        }
    }

    @NonNull
    private DexArchiveMergerException getExceptionToRethrow(
            @NonNull Throwable t,
            @NonNull Iterable<Path> inputs,
            D8DiagnosticsHandler d8DiagnosticsHandler) {
        StringBuilder msg = new StringBuilder("Error while merging dex archives: ");
        msg.append(Joiner.on(", ").join(inputs));
        for (String hint : d8DiagnosticsHandler.getPendingHints()) {
            msg.append(System.lineSeparator());
            msg.append(hint);
        }
        return new DexArchiveMergerException(msg.toString(), t);
    }


    private class InterceptingDiagnosticsHandler extends D8DiagnosticsHandler {
        public InterceptingDiagnosticsHandler() {
            super(D8DexArchiveMerger.this.messageReceiver);
        }

        @Override
        protected Message convertToMessage(Message.Kind kind, Diagnostic diagnostic) {

            if (diagnostic.getDiagnosticMessage().startsWith(ERROR_MULTIDEX)) {
                addHint(DexParser.DEX_LIMIT_EXCEEDED_ERROR);
            }

            return super.convertToMessage(kind, diagnostic);
        }
    }

}
