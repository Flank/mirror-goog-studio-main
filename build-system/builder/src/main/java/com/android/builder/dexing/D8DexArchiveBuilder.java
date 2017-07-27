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
import com.android.builder.dexing.r8.ClassFileProviderFactory;
import com.android.ide.common.blame.parser.DexParser;
import com.android.tools.r8.ApiLevelException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.utils.OutputMode;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

final class D8DexArchiveBuilder extends DexArchiveBuilder {

    private static final Logger LOGGER = Logger.getLogger(D8DexArchiveBuilder.class.getName());

    private static final String INVOKE_CUSTOM =
            "Invoke-customs are only supported starting with Android O";

    private final int minSdkVersion;
    @NonNull private final CompilationMode compilationMode;
    @NonNull private final List<Path> bootClasspath;
    @NonNull private final List<Path> classpath;
    @NonNull private final ClassFileProviderFactory classFileProviderFactory;
    private final boolean desugaring;

    public D8DexArchiveBuilder(
            int minSdkVersion,
            boolean isDebuggable,
            @NonNull List<Path> bootClasspath,
            @NonNull List<Path> classpath,
            @NonNull ClassFileProviderFactory classFileProviderFactory,
            boolean desugaring) {
        this.minSdkVersion = minSdkVersion;
        this.compilationMode = isDebuggable ? CompilationMode.DEBUG : CompilationMode.RELEASE;
        this.bootClasspath = bootClasspath;
        this.classpath = classpath;
        this.classFileProviderFactory = classFileProviderFactory;
        this.desugaring = desugaring;
    }

    @Override
    public void convert(
            @NonNull Stream<ClassFileEntry> input, @NonNull Path output, boolean isIncremental)
            throws DexArchiveBuilderException {
        try (ClassFileProviderFactory.Handler factory = classFileProviderFactory.open()) {
            Iterator<byte[]> data = input.map(D8DexArchiveBuilder::readAllBytes).iterator();
            if (!data.hasNext()) {
                // nothing to do here, just return
                return;
            }

            OutputMode outputMode = isIncremental ? OutputMode.FilePerInputClass : OutputMode.Indexed;
            D8Command.Builder builder =
                    D8Command.builder()
                            .setMode(compilationMode)
                            .setMinApiLevel(minSdkVersion)
                            .setIntermediate(true)
                            .setOutputMode(outputMode);

            if (desugaring) {
                for (Path entry : bootClasspath) {
                    builder.addLibraryResourceProvider(factory.getProvider(entry));
                }
                for (Path entry : classpath) {
                    builder.addClasspathResourceProvider(factory.getProvider(entry));
                }
                builder.setEnableDesugaring(true);
            } else {
                builder.setEnableDesugaring(false);
            }

            while (data.hasNext()) {
                builder.addClassProgramData(data.next());
            }

            builder.setOutputPath(output);
            D8.run(builder.build(), MoreExecutors.newDirectExecutorService());
        } catch (Throwable e) {
            throw getExceptionToRethrow(e);
        }
    }

    @NonNull
    private static byte[] readAllBytes(@NonNull ClassFileEntry entry) {
        try {
            return entry.readAllBytes();
        } catch (IOException ex) {
            throw new DexArchiveBuilderException(ex);
        }
    }

    // TODO (gavra, b/67624381): replace this with error handling through the D8 API
    @NonNull
    private DexArchiveBuilderException getExceptionToRethrow(@NonNull Throwable t) {
        StringBuilder msg = new StringBuilder();
        msg.append("Error while dexing.");
        msg.append(System.lineSeparator());
        if (t instanceof ApiLevelException && t.getMessage().startsWith(INVOKE_CUSTOM)) {
            msg.append(DexParser.ENABLE_DESUGARING);
        }

        return new DexArchiveBuilderException(msg.toString(), t);
    }
}
